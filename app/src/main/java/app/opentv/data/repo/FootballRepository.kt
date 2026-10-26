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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 */
class FootballRepository(
    private val http: OkHttpClient,
    private val programmeDao: ProgrammeDao,
    private val channelDao: ChannelDao,
    private val youTube: YouTubeStreams,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    data class Team(val id: Long, val name: String) {
        val crestUrl: String get() = "https://images.fotmob.com/image_resources/logo/teamlogo/$id.png"
    }

    enum class Phase { SCHEDULED, LIVE, FINISHED, OFF }

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
    data class MatchChannel(val channel: Channel, val name: String, val variants: Int)

    private val zone: ZoneId = ZoneId.systemDefault()

    private var upcomingCache: Pair<Long, List<Match>>? = null
    private var feedCache: Pair<Long, List<Video>>? = null
    private var uploadsCache: Pair<Long, List<Video>>? = null
    private var lastRoundCache: Pair<Long, List<Match>>? = null

    /** Today's matches, kick-off order. Empty when FotMob can't be reached. */
    suspend fun today(): List<Match> = withContext(Dispatchers.IO) {
        fixtures(LocalDate.now(zone))
    }

    /** The fixtures after today, a week ahead at most and [limit] at most. Cached for an hour. */
    suspend fun upcoming(limit: Int = 10): List<Match> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        upcomingCache?.takeIf { now - it.first < UPCOMING_TTL_MILLIS && it.second.isNotEmpty() }?.let {
            return@withContext it.second
        }
        val out = ArrayList<Match>()
        val today = LocalDate.now(zone)
        for (d in 1..7) {
            out += fixtures(today.plusDays(d.toLong()))
            if (out.size >= limit) break
        }
        out.take(limit).also { upcomingCache = now to it }
    }

    /**
     * The finished matches of the latest matchday played before today, latest first: a row to
     * watch highlights from on days without games. Cached for an hour.
     */
    suspend fun lastRound(): List<Match> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        lastRoundCache?.takeIf { now - it.first < UPCOMING_TTL_MILLIS && it.second.isNotEmpty() }?.let {
            return@withContext it.second
        }
        val out = ArrayList<Match>()
        var round: String? = null
        val today = LocalDate.now(zone)
        for (d in 1..10) {
            val played = fixtures(today.minusDays(d.toLong())).filter { it.phase == Phase.FINISHED }
            if (played.isEmpty()) continue
            if (round == null) round = played.last().round
            val same = played.filter { it.round == round }
            // A day of the matchday before: this one is complete.
            if (same.isEmpty()) break
            out += same
            if (out.size >= ROUND_SIZE) break
        }
        out.sortedByDescending { it.kickoffUtcMillis }.also { lastRoundCache = now to it }
    }

    /**
     * Channels carrying [match]: guide programmes naming both teams that run through the game.
     * One entry per logical channel; timeshift copies are left out (they show it an hour late).
     */
    suspend fun channelsFor(match: Match): List<MatchChannel> = withContext(Dispatchers.IO) {
        val a = keyword(match.home.name)
        val b = keyword(match.away.name)
        val from = match.kickoffUtcMillis + 5 * MINUTE
        val to = match.kickoffUtcMillis + 100 * MINUTE
        val guideIds = runCatching { programmeDao.titledBoth(a, b, from, to) }.getOrElse { emptyList() }
            .filter { names(it.title, a, b) }
            .mapTo(LinkedHashSet()) { it.epgChannelId }
        if (guideIds.isEmpty()) return@withContext emptyList()
        channelDao.withGuideIds(guideIds.toList())
            .filter { c -> c.matchedEpgId?.let { TimeShift.decode(it) } == null && !TIMESHIFT.containsMatchIn(c.displayName) }
            .groupBy { it.groupKey.ifEmpty { "#${it.id}" } }
            .map { (_, variants) ->
                val best = variants.maxBy { it.qualityRank }
                MatchChannel(best, best.customName?.takeIf { it.isNotBlank() } ?: best.displayName, variants.size)
            }
            // The channel with the most feeds first: more ways to get a picture if one is down.
            .sortedWith(compareByDescending<MatchChannel> { it.variants }.thenBy { it.name })
    }

    /**
     * The YouTube id of [match]'s highlights, once the Serie A channel has put them up: the RSS
     * feed first, then — for a match a few days old, out of the feed by now — the full video list.
     */
    suspend fun highlightsFor(match: Match): String? = withContext(Dispatchers.IO) {
        pickHighlights(match, highlightsFeed())
            ?: if (System.currentTimeMillis() - match.kickoffUtcMillis > OLD_MATCH_MILLIS) pickHighlights(match, uploads()) else null
    }

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

    private fun fixtures(day: LocalDate): List<Match> {
        val url = "https://www.fotmob.com/api/data/matches?date=${day.format(DateTimeFormatter.BASIC_ISO_DATE)}"
        val body = get(url) ?: return emptyList()
        return runCatching {
            json.parseToJsonElement(body).jsonObject["leagues"]?.jsonArray.orEmpty()
                .map { it.jsonObject }
                .filter { it["primaryId"]?.jsonPrimitive?.intOrNull == SERIE_A }
                .flatMap { it["matches"]?.jsonArray.orEmpty() }
                .mapNotNull { parseMatch(it.jsonObject) }
                .sortedBy { it.kickoffUtcMillis }
        }.getOrElse { Log.w(TAG, "fixtures $day", it); emptyList() }
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
        return Regex("\\b${Regex.escape(a)}\\b").containsMatchIn(t) && Regex("\\b${Regex.escape(b)}\\b").containsMatchIn(t)
    }

    private fun fold(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    companion object {
        private const val TAG = "Football"
        private const val SERIE_A = 55
        private const val SERIE_A_CHANNEL = "UCBJeMCIeLQos7wacox4hmLQ"
        private const val MINUTE = 60_000L
        private const val UPCOMING_TTL_MILLIS = 60 * MINUTE
        private const val FEED_TTL_MILLIS = 5 * MINUTE
        private const val UPLOADS_TTL_MILLIS = 30 * MINUTE
        private const val OLD_MATCH_MILLIS = 12 * 60 * MINUTE
        private const val ROUND_SIZE = 10
        private val TIMESHIFT = Regex("\\+\\s?\\d")
        private val ENTRY = Regex("<entry>.*?</entry>", RegexOption.DOT_MATCHES_ALL)
        private val VIDEO_ID = Regex("<yt:videoId>([^<]+)</yt:videoId>")
        private val TITLE = Regex("<title>([^<]*)</title>")
    }
}
