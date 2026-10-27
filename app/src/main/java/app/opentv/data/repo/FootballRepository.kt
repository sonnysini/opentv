/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.repo

import android.util.Log
import app.opentv.data.db.ChannelDao
import app.opentv.data.db.ProgrammeDao
import app.opentv.data.model.Channel
import app.opentv.data.model.TimeShift
import app.opentv.data.remote.YouTubeStreams
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The football home: today's Serie A, the next fixtures, and for each match the channels of the
 * user's own lists that carry it.
 *
 * - Fixtures, scores and the live minute come from FotMob's public match list (the same feed the
 *   website uses; no key).
 * - Channels are not looked up anywhere online: the guide already on disk says "Udinese - Milan"
 *   on Zona DAZN, so a match is the programme naming both teams around kick-off. Replays sit at
 *   other hours and so never match.
 * - Highlights come from the Serie A YouTube channel (no key either): the Italian upload is
 *   titled "… | UDINESE-MILAN | HIGHLIGHTS | SERIE A ENILIVE …". The RSS feed has the latest
 *   fifteen; older ones are found in the channel's full video list.
 *
 * The calendar is fixed weeks ahead, so all of it is kept on disk under [dir]: a day is read
 * again once a day, an hour before each kick-off (a game put off) and every minute only while
 * a game is on. Channels are kept until the channel list or the guide changes. Opening the
 * football home is then a disk read, not a dozen downloads and guide searches.
 */
class FootballRepository(
    private val http: OkHttpClient,
    private val programmeDao: ProgrammeDao,
    private val channelDao: ChannelDao,
    private val youTube: YouTubeStreams,
    private val dir: File,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    @Serializable
    data class Team(val id: Long, val name: String) {
        val crestUrl: String get() = "https://images.fotmob.com/image_resources/logo/teamlogo/$id.png"
    }

    @Serializable
    enum class Phase { SCHEDULED, LIVE, FINISHED, OFF }

    @Serializable
    data class Match(
        val id: Long,
        val kickoffUtcMillis: Long,
        val home: Team,
        val away: Team,
        val homeScore: Int?,
        val awayScore: Int?,
        val phase: Phase,
        /** "54'", "HT"… while live; null otherwise. */
        val minute: String?,
        /** The matchday ("8"), when FotMob says. */
        val round: String?,
    )

    /** One logical channel showing a match: [channel] is what plays, the player picks the quality. */
    @Serializable
    data class MatchChannel(val channel: Channel, val name: String, val variants: Int)

    private val zone: ZoneId = ZoneId.systemDefault()

    private var feedCache: Pair<Long, List<Video>>? = null
    private var uploadsCache: Pair<Long, List<Video>>? = null

    /** What's kept on disk: the calendar day by day, channels per match, highlights found. */
    @Serializable
    private data class Disk(
        val days: Map<String, Day> = emptyMap(),
        /** [guideStamp][ChannelDao.guideStamp] the channels below were found with. */
        val stamp: String = "",
        val channels: Map<String, List<MatchChannel>> = emptyMap(),
        val highlights: Map<Long, String> = emptyMap(),
    )

    @Serializable
    private data class Day(val fetchedMillis: Long, val matches: List<Match>)

    private val file = File(dir, "football.json")
    private val lock = Mutex()
    private var disk: Disk? = null

    private fun disk(): Disk = disk ?: runCatching { json.decodeFromString<Disk>(file.readText()) }
        .getOrElse { Disk() }.also { disk = it }

    private fun save(d: Disk) {
        disk = d
        runCatching {
            dir.mkdirs()
            val tmp = File(dir, "football.json.tmp")
            tmp.writeText(json.encodeToString(d))
            tmp.renameTo(file)
        }.onFailure { Log.w(TAG, "save", it) }
    }

    /** Today's matches, kick-off order. Empty when FotMob can't be reached and nothing is on disk. */
    suspend fun today(): List<Match> = days(listOf(LocalDate.now(zone))).first()

    /** The fixtures after today, a week ahead at most and [limit] at most. */
    suspend fun upcoming(limit: Int = 10): List<Match> {
        val today = LocalDate.now(zone)
        return days((1..7).map { today.plusDays(it.toLong()) }).flatten().take(limit)
    }

    /**
     * The finished matches of the latest matchday played before today, latest first: a row to
     * watch highlights from on days without games.
     */
    suspend fun lastRound(): List<Match> {
        val today = LocalDate.now(zone)
        val out = ArrayList<Match>()
        var round: String? = null
        for (day in days((1..10).map { today.minusDays(it.toLong()) })) {
            val played = day.filter { it.phase == Phase.FINISHED }
            if (played.isEmpty()) continue
            if (round == null) round = played.last().round
            val same = played.filter { it.round == round }
            // A day of the matchday before: this one is complete.
            if (same.isEmpty()) break
            out += same
            if (out.size >= ROUND_SIZE) break
        }
        return out.sortedByDescending { it.kickoffUtcMillis }
    }

    /** The matches of each of [dates], from disk when still good, else FotMob (all at once). */
    private suspend fun days(dates: List<LocalDate>): List<List<Match>> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val kept = lock.withLock { disk().days }
        val stale = dates.filter { d -> kept[d.toString()]?.let { !isFresh(d, it, now) } ?: true }
        val fetched = coroutineScope {
            stale.map { d -> async { d to fixtures(d) } }.awaitAll()
        }.mapNotNull { (d, m) -> m?.let { d.toString() to Day(now, it) } }.toMap()
        if (stale.isNotEmpty()) Log.i(TAG, "FotMob: ${stale.size} days in ${System.currentTimeMillis() - now} ms")
        val days = lock.withLock {
            val d = disk()
            if (fetched.isNotEmpty()) {
                val oldest = LocalDate.now(zone).minusDays(KEEP_DAYS).toString()
                save(d.copy(days = (d.days + fetched).filterKeys { it >= oldest }))
            }
            disk().days
        }
        // FotMob down: the copy on disk, however old, beats an empty page.
        dates.map { days[it.toString()]?.matches.orEmpty() }
    }

    /**
     * Whether [day] read at [Day.fetchedMillis] can still be shown: games over stay over; else
     * read again on a new day, an hour before a kick-off and each minute once a game has begun.
     */
    private fun isFresh(date: LocalDate, day: Day, now: Long): Boolean {
        val fetched = day.fetchedMillis
        val open = day.matches.filter { it.phase == Phase.SCHEDULED || it.phase == Phase.LIVE }
        val fetchedOn = Instant.ofEpochMilli(fetched).atZone(zone).toLocalDate()
        // A day gone by: final once read after it ended (a game left "scheduled" was put off).
        if (date < LocalDate.now(zone)) return open.isEmpty() || fetchedOn > date
        if (fetchedOn != LocalDate.now(zone)) return false
        return open.none { m ->
            val k = m.kickoffUtcMillis
            when {
                // On (or should be by now): score and minute move.
                k <= now -> now - fetched >= LIVE_TTL_MILLIS
                // The last hour before kick-off: once, to catch a game put off.
                k - now <= PRE_MATCH_MILLIS -> fetched < k - PRE_MATCH_MILLIS
                else -> false
            }
        }
    }

    /**
     * Channels carrying each of [matches], one entry per logical channel:
     * - first the two team channels ("Milan", "DAZN Serie A Milan", "DAZN SERIE A 3 FHD MILAN"…),
     *   the provider's own feed of that team's game;
     * - then the channels whose guide names both teams through the game (Zona DAZN, DAZN 1,
     *   Sky Sport…). Club channels ("Inter TV": a radio commentary in the guide), boxing feeds
     *   matched to a DAZN guide by name, replays and timeshift copies are left out.
     * Kept on disk until the channel list or the guide changes; the guide is searched once for
     * all the matches not known yet.
     */
    suspend fun channelsFor(matches: List<Match>): Map<Long, List<MatchChannel>> = withContext(Dispatchers.IO) {
        val stamp = runCatching { channelDao.guideStamp() }.getOrElse { "" }
        fun key(m: Match) = "${m.id}@${m.kickoffUtcMillis}"
        val kept = lock.withLock { disk().let { if (it.stamp == stamp) it.channels else emptyMap() } }
        val missing = matches.filter { key(it) !in kept }
        val found = if (missing.isEmpty()) emptyMap() else {
            val started = System.currentTimeMillis()
            val guide = guideIdsFor(missing)
            // Two passes over the channels for all the matches, not two per match.
            val named = namedAny(missing.flatMap { listOf(keyword(it.home.name), keyword(it.away.name)) })
            val allIds = guide.values.flatten().distinct()
            val guided = if (allIds.isEmpty()) emptyList()
            else runCatching { channelDao.withGuideIds(allIds) }.getOrElse { emptyList() }
            missing.associate { key(it) to lookUp(it, guide[it.id].orEmpty(), named, guided) }
                .also { Log.i(TAG, "channels for ${missing.size} matches in ${System.currentTimeMillis() - started} ms") }
        }
        if (found.isNotEmpty() || lock.withLock { disk().stamp } != stamp) lock.withLock {
            val ids = matches.mapTo(HashSet()) { key(it) }
            // Only the matches still asked for: the file doesn't grow over a season.
            save(disk().copy(stamp = stamp, channels = (kept + found).filterKeys { it in ids }))
        }
        matches.associate { it.id to (kept[key(it)] ?: found[key(it)].orEmpty()) }
    }

    /**
     * One query over the guide for all of [matches]: each match's guide ids naming both teams.
     * Each part reads only the programmes ending around its own game (the end-time index), not
     * the days of guide after it.
     */
    private suspend fun guideIdsFor(matches: List<Match>): Map<Long, Set<String>> {
        val windows = matches.map { m ->
            Triple(m, m.kickoffUtcMillis + 5 * MINUTE, m.kickoffUtcMillis + 100 * MINUTE)
        }
        val args = ArrayList<Any>()
        val sql = windows.joinToString(" UNION ALL ") { (m, from, to) ->
            args += from
            args += to + LONGEST_PROGRAMME_MILLIS
            args += to
            args += "%${keyword(m.home.name)}%"
            args += "%${keyword(m.away.name)}%"
            "SELECT * FROM programmes WHERE endUtcMillis > ? AND endUtcMillis < ? AND startUtcMillis < ?" +
                " AND title LIKE ? AND title LIKE ?"
        }
        val programmes = runCatching { programmeDao.query(SimpleSQLiteQuery(sql, args.toTypedArray())) }
            .getOrElse { Log.w(TAG, "guide", it); emptyList() }
        return windows.associate { (m, from, to) ->
            val a = keyword(m.home.name)
            val b = keyword(m.away.name)
            m.id to programmes
                .filter { it.endUtcMillis > from && it.startUtcMillis < to }
                .filter { names(it.title, a, b) && !RADIO.containsMatchIn(fold(it.title).lowercase()) }
                .mapTo(LinkedHashSet()) { it.epgChannelId }
        }
    }

    /** Visible channels whose name contains any of [words] (LIKE: case-blind for ASCII). */
    private suspend fun namedAny(words: List<String>): List<Channel> {
        val distinct = words.distinct()
        if (distinct.isEmpty()) return emptyList()
        val sql = "SELECT * FROM channels WHERE hidden = 0 AND (" +
            distinct.joinToString(" OR ") { "displayName LIKE ?" } + ")"
        return runCatching { channelDao.query(SimpleSQLiteQuery(sql, distinct.map { "%$it%" }.toTypedArray())) }
            .getOrElse { Log.w(TAG, "channels", it); emptyList() }
    }

    private fun lookUp(match: Match, guideIds: Set<String>, named: List<Channel>, guidedAll: List<Channel>): List<MatchChannel> {
        val a = keyword(match.home.name)
        val b = keyword(match.away.name)
        val teams = named.filter { it.displayName.contains(a, ignoreCase = true) || it.displayName.contains(b, ignoreCase = true) }
            // "*==== Lazio ====*": a provider's section divider, not a channel.
            .filter { it.isLive() && "==" !in it.displayName && "--" !in it.displayName }
            .mapNotNull { c -> teamOf(c.displayName, match)?.let { it to c } }
        val home = logical(teams.filter { it.first == 0 }.map { it.second })
        val away = logical(teams.filter { it.first == 1 }.map { it.second })

        val guided = guidedAll
            .filter { c -> c.epgOverrideId in guideIds || c.epgChannelId in guideIds || c.matchedEpgId in guideIds }
            .filter { c ->
                val n = fold(c.displayName).lowercase()
                c.isLive() && !NOT_THE_GAME.containsMatchIn(n) &&
                    // A team's name in a guide-matched channel: its club channel, not the game.
                    !names(n, a, a) && !names(n, b, b)
            }
            .groupBy { it.groupKey.ifEmpty { "#${it.id}" } }
            .mapNotNull { (_, variants) -> logical(variants) }
            // The channel with the most feeds first: more ways to get a picture if one is down.
            .sortedWith(compareByDescending<MatchChannel> { it.variants }.thenBy { it.name })
        return (listOfNotNull(home, away) + guided).distinctBy { it.channel.groupKey.ifEmpty { "#${it.channel.id}" } }
    }

    private fun Channel.isLive() =
        matchedEpgId?.let { TimeShift.decode(it) } == null && !TIMESHIFT.containsMatchIn(displayName)

    /** One logical channel out of its feeds: the best quality plays, the player offers the rest. */
    private fun logical(variants: List<Channel>): MatchChannel? {
        val best = variants.maxByOrNull { it.qualityRank } ?: return null
        return MatchChannel(best, best.customName?.takeIf { it.isNotBlank() } ?: best.displayName, variants.size)
    }

    /**
     * 0 when [channelName] is the home team's channel, 1 the away team's, null otherwise: what's
     * left of the name once the DAZN / Serie A / number / quality words are gone is the team.
     * "Milan TV" leaves "milan tv" and so isn't.
     */
    private fun teamOf(channelName: String, match: Match): Int? {
        val rest = fold(channelName).lowercase().split(NON_WORD)
            .filter { it.isNotEmpty() && it !in TEAM_CHANNEL_NOISE && !it.all(Char::isDigit) }
            .joinToString(" ")
        fun same(team: Team) = rest == keyword(team.name) || rest == fold(team.name).lowercase()
        return when {
            rest.isEmpty() -> null
            same(match.home) -> 0
            same(match.away) -> 1
            else -> null
        }
    }

    /**
     * The YouTube id of [match]'s highlights, once the Serie A channel has put them up: the RSS
     * feed first, then — for a match a few days old, out of the feed by now — the full video list.
     */
    suspend fun highlightsFor(match: Match): String? = withContext(Dispatchers.IO) {
        knownHighlights(match)?.let { return@withContext it }
        val id = pickHighlights(match, highlightsFeed())
            ?: if (System.currentTimeMillis() - match.kickoffUtcMillis > OLD_MATCH_MILLIS) pickHighlights(match, uploads()) else null
        id?.also { lock.withLock { disk().let { d -> save(d.copy(highlights = d.highlights + (match.id to it))) } } }
    }

    /** Highlights found before (they stay found: the feed only keeps the latest uploads). */
    suspend fun knownHighlights(match: Match): String? = lock.withLock { disk().highlights[match.id] }

    private fun pickHighlights(match: Match, videos: List<Video>): String? {
        val a = Regex.escape(keyword(match.home.name).uppercase())
        val b = Regex.escape(keyword(match.away.name).uppercase())
        val pair = Regex("\\b$a\\b.{0,24}-.{0,24}\\b$b\\b")
        val hits = videos.filter { v ->
            val t = fold(v.title).uppercase()
            // "CLASSIC HIGHLIGHTS" is last season's game between the same two teams.
            "HIGHLIGHTS" in t && "CLASSIC" !in t && pair.containsMatchIn(t)
        }
        // The plain Italian cut (ENILIVE, the English one doesn't say it) before the extended
        // and "cinematic" ones.
        fun plain(v: Video) = fold(v.title).uppercase().let { "EXTENDED" !in it && "CINEMATIC" !in it }
        return (hits.firstOrNull { plain(it) && "ENILIVE" in it.title.uppercase() }
            ?: hits.firstOrNull { plain(it) }
            ?: hits.firstOrNull())?.id
    }

    // ---- FotMob ---------------------------------------------------------------------------------

    /** [day]'s Serie A matches; null when FotMob couldn't be read. */
    private fun fixtures(day: LocalDate): List<Match>? {
        val url = "https://www.fotmob.com/api/data/matches?date=${day.format(DateTimeFormatter.BASIC_ISO_DATE)}"
        val body = get(url) ?: return null
        return runCatching {
            json.parseToJsonElement(body).jsonObject["leagues"]?.jsonArray.orEmpty()
                .map { it.jsonObject }
                .filter { it["primaryId"]?.jsonPrimitive?.intOrNull == SERIE_A }
                .flatMap { it["matches"]?.jsonArray.orEmpty() }
                .mapNotNull { parseMatch(it.jsonObject) }
                .sortedBy { it.kickoffUtcMillis }
        }.getOrElse { Log.w(TAG, "fixtures $day", it); null }
    }

    private fun parseMatch(m: JsonObject): Match? {
        val id = m["id"]?.jsonPrimitive?.longOrNull ?: return null
        val home = team(m["home"]?.jsonObject) ?: return null
        val away = team(m["away"]?.jsonObject) ?: return null
        val status = m["status"]?.jsonObject ?: return null
        val kickoff = status["utcTime"]?.jsonPrimitive?.contentOrNull
            ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: return null
        fun flag(key: String) = status[key]?.jsonPrimitive?.booleanOrNull == true
        val phase = when {
            flag("cancelled") -> Phase.OFF
            flag("finished") -> Phase.FINISHED
            flag("started") -> Phase.LIVE
            else -> Phase.SCHEDULED
        }
        val minute = status["liveTime"]?.jsonObject?.get("short")?.jsonPrimitive?.contentOrNull
            ?.filterNot { it == '‎' || it == '‏' }?.replace('’', '\'')?.trim()?.ifEmpty { null }
        val started = phase == Phase.LIVE || phase == Phase.FINISHED
        return Match(
            id = id,
            kickoffUtcMillis = kickoff,
            home = home,
            away = away,
            homeScore = if (started) m["home"]?.jsonObject?.get("score")?.jsonPrimitive?.intOrNull else null,
            awayScore = if (started) m["away"]?.jsonObject?.get("score")?.jsonPrimitive?.intOrNull else null,
            phase = phase,
            minute = if (phase == Phase.LIVE) minute else null,
            round = m["tournamentStage"]?.jsonPrimitive?.contentOrNull,
        )
    }

    private fun team(o: JsonObject?): Team? {
        o ?: return null
        val id = o["id"]?.jsonPrimitive?.longOrNull ?: return null
        val name = o["name"]?.jsonPrimitive?.contentOrNull ?: return null
        return Team(id, name)
    }

    // ---- YouTube --------------------------------------------------------------------------------

    private data class Video(val id: String, val title: String)

    /** The Serie A channel's latest uploads. Re-read at most every few minutes. */
    private fun highlightsFeed(): List<Video> {
        val now = System.currentTimeMillis()
        feedCache?.takeIf { now - it.first < FEED_TTL_MILLIS }?.let { return it.second }
        val xml = get("https://www.youtube.com/feeds/videos.xml?channel_id=$SERIE_A_CHANNEL") ?: return emptyList()
        val videos = ENTRY.findAll(xml).mapNotNull { e ->
            val id = VIDEO_ID.find(e.value)?.groupValues?.get(1) ?: return@mapNotNull null
            val title = TITLE.find(e.value)?.groupValues?.get(1) ?: return@mapNotNull null
            Video(id, unescape(title))
        }.toList()
        feedCache = now to videos
        return videos
    }

    /** Five pages of the channel's video list (a hundred uploads, about two weeks). */
    private suspend fun uploads(): List<Video> {
        val now = System.currentTimeMillis()
        uploadsCache?.takeIf { now - it.first < UPLOADS_TTL_MILLIS && it.second.isNotEmpty() }?.let { return it.second }
        return youTube.uploads(SERIE_A_CHANNEL, pages = 5).map { (id, title) -> Video(id, title) }
            .also { uploadsCache = now to it }
    }

    private fun unescape(s: String) =
        s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")

    // ---- Helpers --------------------------------------------------------------------------------

    private fun get(url: String): String? = runCatching {
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0")
            .header("Referer", "https://www.fotmob.com/")
            .build()
        http.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
    }.getOrElse { Log.w(TAG, "GET $url", it); null }

    /** What a guide or video title calls a team: "Hellas Verona" → "verona". */
    private fun keyword(name: String): String =
        fold(name).lowercase().split(' ').lastOrNull { it.length >= 3 } ?: fold(name).lowercase()

    /** Both teams named as words (not inside another word) in [title]. */
    private fun names(title: String, a: String, b: String): Boolean {
        val t = fold(title).lowercase()
        return word(a).containsMatchIn(t) && word(b).containsMatchIn(t)
    }

    private fun fold(s: String): String =
        if (s.all { it.code < 128 }) s else Normalizer.normalize(s, Normalizer.Form.NFD).replace(MARKS, "")

    // Compiled once: building a regex per channel and per programme took seconds on a Fire TV.
    private val words = HashMap<String, Regex>()

    private fun word(w: String): Regex = synchronized(words) {
        words.getOrPut(w) { Regex("\\b${Regex.escape(w)}\\b") }
    }

    companion object {
        private const val TAG = "Football"
        private const val SERIE_A = 55
        private const val SERIE_A_CHANNEL = "UCBJeMCIeLQos7wacox4hmLQ"
        private const val MINUTE = 60_000L
        private const val LIVE_TTL_MILLIS = 50_000L
        private const val PRE_MATCH_MILLIS = 60 * MINUTE
        private const val KEEP_DAYS = 14L
        /** A game's programme ends at most this long after the game: bounds the guide search. */
        private const val LONGEST_PROGRAMME_MILLIS = 6 * 60 * MINUTE
        private const val FEED_TTL_MILLIS = 5 * MINUTE
        private const val UPLOADS_TTL_MILLIS = 30 * MINUTE
        private const val OLD_MATCH_MILLIS = 12 * 60 * MINUTE
        private const val ROUND_SIZE = 10
        private val TIMESHIFT = Regex("\\+\\s?\\d")
        /** Words around a team's name on its channel: "DAZN SERIE A 3 FHD MILAN", "Milan HD". */
        private val TEAM_CHANNEL_NOISE = setOf(
            "dazn", "serie", "a", "it", "fhd", "hd", "sd", "uhd", "4k", "raw", "web", "hq", "lq", "hevc", "h265",
        )
        /** Guide-matched channels that don't show the game: boxing feeds named like DAZN 1, replays. */
        private val NOT_THE_GAME = Regex("\\b(box|boxe|ufc|differita|replay|serie b)\\b")
        private val RADIO = Regex("\\bradio")
        private val MARKS = Regex("\\p{M}+")
        private val NON_WORD = Regex("[^a-z0-9]+")
        private val ENTRY = Regex("<entry>.*?</entry>", RegexOption.DOT_MATCHES_ALL)
        private val VIDEO_ID = Regex("<yt:videoId>([^<]+)</yt:videoId>")
        private val TITLE = Regex("<title>([^<]*)</title>")
    }
}
