/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.repo

import android.util.Log
import app.opentv.data.db.ChannelDao
import app.opentv.data.model.Channel
import app.opentv.data.parser.ChannelNameNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Tonight's big programmes for the top of the live TV page.
 *
 * Read from Sky Italia's public TV guide (the one guidatv.sky.it uses; no key), which also carries
 * the free-to-air networks. Only the evening is picked: what people talk about is on between nine
 * and ten, and what is on right now is already in the channel list below. Sport is left to its own
 * page. Each programme is tied to a channel of the user's own lists by name; one the lists don't
 * have isn't offered, since OK on it would have nothing to play.
 *
 * The guide is asked one channel at a time (several at once come back with gaps) and only for the
 * couple of dozen channels worth a place. The evening is known by the morning, so the guide is read
 * once a day: the first time the app asks that day. The result is kept on disk too ([file]), so a
 * cold start shows tonight's programmes straight away instead of after the downloads. What has
 * ended drops off by the clock; a failed read is tried again on the next ask.
 */
class TvHighlightsRepository(
    private val http: OkHttpClient,
    private val channelDao: ChannelDao,
    /** Where tonight's list is kept between launches; null, memory only. */
    private val file: File? = null,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    enum class Badge { NONE, PREMIERE, LIVE, FINALE }

    @Serializable
    data class Highlight(
        val eventId: String,
        /** The channel's group key ("rai1"): ids change when a list is imported again, names don't. */
        val channelKey: String,
        /** The network's own name, as the guide writes it without the "HD": "Rai 1". */
        val network: String,
        val title: String,
        val synopsis: String,
        val genre: String?,
        val season: Int?,
        val episode: Int?,
        val startUtcMillis: Long,
        val endUtcMillis: Long,
        val badge: Badge,
        /** 16:9 still for the backdrop; needs [IMAGE_HEADERS]. */
        val imageUrl: String?,
        /** The network's white logo, for a dark page. */
        val logoUrl: String?,
    )

    /** What is kept: the day the guide was read for, and what it gave. */
    @Serializable
    private data class Stored(val version: Int, val day: String, val items: List<Highlight>)

    private val lock = Mutex()
    @Volatile private var stored: Stored? = null
    @Volatile private var diskRead = false
    private var guideChannels: Pair<LocalDate, Map<String, Int>>? = null

    /**
     * What is on hand right now, without waiting: for a screen's first frame, so the top is there
     * together with the channels. Null until the disk copy has been read.
     */
    fun cached(now: Long = System.currentTimeMillis()): List<Highlight>? =
        stored?.items?.filter { it.endUtcMillis > now } ?: if (diskRead) emptyList() else null

    /** Up to [MAX] programmes, live ones first; empty when the guide can't be read. */
    suspend fun tonight(now: Long = System.currentTimeMillis()): List<Highlight> = lock.withLock {
        readDisk()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString()
        stored?.takeIf { it.day == today }?.let { s -> return@withLock s.items.filter { it.endUtcMillis > now } }
        val fresh = runCatching { load(now) }.getOrElse { Log.w(TAG, "highlights", it); null }
        // None found (the lists still syncing, the guide half down) is asked again next time.
        if (!fresh.isNullOrEmpty()) save(Stored(VERSION, today, fresh))
        (fresh ?: stored?.items.orEmpty()).filter { it.endUtcMillis > now }
    }

    /** [h]'s channel as the lists have it now, every quality: what its row in the channel list holds. */
    suspend fun channelsFor(h: Highlight): List<Channel> =
        runCatching { channelDao.withGroupKeys(listOf(h.channelKey)) }.getOrElse { emptyList() }

    private suspend fun readDisk() {
        if (diskRead) return
        val f = file
        if (f != null) withContext(Dispatchers.IO) {
            runCatching {
                if (f.exists()) json.decodeFromString(Stored.serializer(), f.readText()).takeIf { it.version == VERSION }
                else null
            }.onFailure { Log.w(TAG, "Unreadable highlights, ignoring", it) }.getOrNull()
        }?.let { if (stored == null) stored = it }
        diskRead = true
    }

    private suspend fun save(s: Stored) {
        stored = s
        val f = file ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                val tmp = File(f.path + ".tmp")
                tmp.writeText(json.encodeToString(Stored.serializer(), s))
                tmp.renameTo(f)
            }.onFailure { Log.w(TAG, "Could not save highlights", it) }
        }
    }

    private suspend fun load(now: Long): List<Highlight>? = withContext(Dispatchers.IO) {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val ids = channelIds(today) ?: return@withContext null
        val from = ZonedDateTime.of(today, LocalTime.of(20, 0), zone).toInstant()
        val to = ZonedDateTime.of(today.plusDays(1), LocalTime.of(2, 0), zone).toInstant()
        val events = coroutineScope {
            ids.map { (name, id) -> async { name to events(id, from, to) } }.awaitAll()
        }
        if (events.all { it.second == null }) return@withContext null
        val candidates = events.flatMap { (name, list) -> list.orEmpty().mapNotNull { candidate(name, it, today) } }
            .filter { it.end > now }
            .sortedByDescending { it.score }
        // Which of the networks the user's lists carry, in one query.
        val keys = candidates.map { it.key }.distinct()
        val carried = if (keys.isEmpty()) emptySet() else runCatching { channelDao.withGroupKeys(keys) }
            .getOrElse { emptyList() }
            .filter { TIMESHIFT !in it.displayName }
            .mapTo(HashSet()) { it.groupKey }
        val seenNetworks = HashSet<String>()
        val seenTitles = HashSet<String>()
        candidates.mapNotNull { c ->
            if (c.key !in carried) return@mapNotNull null
            if (!seenNetworks.add(c.key) || !seenTitles.add(c.title.lowercase())) return@mapNotNull null
            c.toHighlight()
        }.take(MAX)
            // Live ones first; otherwise the score's order (the sort is stable).
            .sortedBy { it.badge != Badge.LIVE }
            .also { Log.i(TAG, "${it.size} highlights from ${candidates.size} candidates, ${byKey.size} networks matched") }
    }

    /** Sky's guide ids of the networks in [WEIGHTS], read once a day. */
    private fun channelIds(today: LocalDate): Map<String, Int>? {
        guideChannels?.takeIf { it.first == today }?.let { return it.second }
        val body = get("$API/channels?env=DTH") ?: return guideChannels?.second
        return runCatching {
            json.parseToJsonElement(body).jsonObject["channels"]!!.jsonArray
                .map { it.jsonObject }
                .mapNotNull { c ->
                    val name = c["name"]?.jsonPrimitive?.contentOrNull?.trim() ?: return@mapNotNull null
                    val id = c["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                    if (name in WEIGHTS) name to id else null
                }
                // The guide lists some networks twice under two categories.
                .distinctBy { it.first }
                .toMap()
        }.getOrNull()?.also { guideChannels = today to it }
    }

    private fun events(id: Int, from: Instant, to: Instant): List<JsonObject>? {
        val url = "$API/events?from=${UTC.format(from)}&to=${UTC.format(to)}&pageSize=999&pageNum=0&env=DTH&channels=$id"
        val body = get(url) ?: return null
        return runCatching {
            json.parseToJsonElement(body).jsonObject["events"]!!.jsonArray.map { it.jsonObject }
        }.getOrNull()
    }

    private class Candidate(
        val e: JsonObject,
        val network: String,
        val key: String,
        val title: String,
        val start: Long,
        val end: Long,
        val score: Double,
    )

    /** A programme worth a place, scored; null when it isn't one (news, sport, kids, short, not 9–10 pm). */
    private fun candidate(network: String, e: JsonObject, today: LocalDate): Candidate? {
        val content = e["content"]?.jsonObject ?: return null
        val genre = content["genre"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
        val sub = content["subgenre"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
        val raw = e["epgEventTitle"]?.jsonPrimitive?.contentOrNull ?: return null
        val title = cleanTitle(raw)
        if (genre in SKIP_GENRES || sub in SKIP_SUBGENRES || NEWS.containsMatchIn(title)) return null
        val start = e["starttime"]?.jsonPrimitive?.contentOrNull?.let { Instant.parse(it) } ?: return null
        val end = e["endtime"]?.jsonPrimitive?.contentOrNull?.let { Instant.parse(it) } ?: return null
        val minutes = (end.epochSecond - start.epochSecond) / 60.0
        if (minutes < MIN_MINUTES) return null
        val local = start.atZone(zone)
        val at = local.hour * 60 + local.minute
        if (local.toLocalDate() != today || at !in PRIME_FROM..PRIME_TO) return null
        val score = WEIGHTS.getValue(network) +
            (if (genre == "Film") 15 else 0) +
            (SUBGENRE_POINTS[sub] ?: 0) +
            when (e["programHighlights"]?.jsonPrimitive?.contentOrNull) {
                "1" -> 15
                "L" -> 12
                "F" -> 15
                else -> 0
            } +
            minOf(minutes, 120.0) / 15
        val shown = network.removeSuffix(" HD").trim()
        return Candidate(e, shown, ChannelNameNormalizer.normalize(network).groupKey, title, start.toEpochMilli(), end.toEpochMilli(), score)
    }

    private fun Candidate.toHighlight(): Highlight {
        val content = e["content"]!!.jsonObject
        val images = content["imagesMap"]?.jsonArray.orEmpty().associate { i ->
            val o = i.jsonObject
            o["key"]?.jsonPrimitive?.contentOrNull to o["img"]?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
        }
        val still = listOf("background", "scene", "scene_key_art").firstNotNullOfOrNull { images[it] }
        val logo = e["channel"]?.jsonObject?.let { c ->
            (c["logoDark"] ?: c["logo"])?.jsonPrimitive?.contentOrNull
        }
        return Highlight(
            eventId = e["eventId"]?.jsonPrimitive?.contentOrNull ?: "$key@$start",
            channelKey = key,
            network = network,
            title = title,
            synopsis = (e["eventSynopsis"]?.jsonPrimitive?.contentOrNull ?: "").replace(EPISODE_PREFIX, "").trim(),
            genre = content["subgenre"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull,
            season = content["seasonNumber"]?.jsonPrimitive?.longOrNull?.toInt()?.takeIf { it > 0 },
            episode = content["episodeNumber"]?.jsonPrimitive?.longOrNull?.toInt()?.takeIf { it > 0 },
            startUtcMillis = start,
            endUtcMillis = end,
            badge = when (e["programHighlights"]?.jsonPrimitive?.contentOrNull) {
                "1" -> Badge.PREMIERE
                "L" -> Badge.LIVE
                "F" -> Badge.FINALE
                else -> Badge.NONE
            },
            imageUrl = still?.let { SITE + it },
            logoUrl = logo?.let { SITE + it },
        )
    }

    private fun get(url: String): String? = runCatching {
        val req = Request.Builder().url(url).header("User-Agent", BROWSER).build()
        http.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
    }.getOrElse { Log.w(TAG, "GET $url", it); null }

    companion object {
        private const val TAG = "TvHighlights"
        private const val API = "https://apid.sky.it/gtv/v1"
        private const val SITE = "https://guidatv.sky.it"
        private const val BROWSER = "Mozilla/5.0"
        /** The images are refused without a browser's user agent. */
        val IMAGE_HEADERS = mapOf("User-Agent" to BROWSER)
        const val MAX = 8
        /** Bump when [Highlight] changes shape: an old file is then ignored rather than misread. */
        private const val VERSION = 2
        private const val MIN_MINUTES = 40
        // Prime time starts between nine and ten; what starts at half eight is the warm-up.
        private const val PRIME_FROM = 21 * 60
        private const val PRIME_TO = 22 * 60
        private const val TIMESHIFT = "+1"
        private val zone: ZoneId get() = ZoneId.systemDefault()
        private val UTC = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneId.of("UTC"))

        /** The networks worth a place, as the guide names them, and how much each weighs. */
        private val WEIGHTS = mapOf(
            "Rai 1 HD" to 35, "Canale 5 HD" to 35,
            "Rai 2 HD" to 20, "Rai 3 HD" to 20, "Italia 1 HD" to 20, "LA7 HD" to 20, "TV8 HD" to 20,
            "Sky Uno" to 20, "Sky Atlantic" to 20,
            "Rete 4 HD" to 18, "NOVE HD" to 18, "Sky Serie" to 18, "Sky Cinema Uno" to 18,
            "Real Time HD" to 12,
            "20Mediaset HD" to 10, "Iris HD" to 10, "Rai 4" to 10, "Rai Movie" to 10, "Sky Investigation" to 10,
            "cielo" to 8, "DMAX HD" to 8, "Sky Documentaries" to 8,
        )
        private val SKIP_GENRES = setOf("Sport", "Informazione", "Ragazzi e Musica")
        private val SKIP_SUBGENRES = setOf("Notiziario", "News", "Meteo", "Rubrica", "Televendita")
        private val SUBGENRE_POINTS = mapOf(
            "Fiction" to 12, "Show" to 10, "Reality Show" to 10, "Quiz" to 8, "Musica" to 8,
            "Gioco" to 6, "Talk Show" to 5, "Documentario" to 3,
        )
        private val NEWS = Regex("(?i)^(tg\\d?|telegiornale)\\b|\\bnews\\b")
        private val EPISODE_PREFIX = Regex("^S\\d+ Ep\\d+ - ")

        /** "S13 Ep10 - Pechino Express 1^TV" → "Pechino Express"; "Bake Off - Stag. 14 Ep. 9" → "Bake Off". */
        fun cleanTitle(raw: String): String =
            raw.replace(Regex("^(S\\d+ Ep\\d+ - |Stag\\. \\d+ - )"), "")
                .replace("1^TV", "")
                .replace(Regex(" - Stag\\. \\d+.*$"), "")
                .trim()
    }
}
