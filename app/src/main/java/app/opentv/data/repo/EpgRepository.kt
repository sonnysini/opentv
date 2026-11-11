/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.repo

import android.content.SharedPreferences
import android.util.Log
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import app.opentv.core.SettingsBackup
import app.opentv.data.db.ChannelDao
import app.opentv.data.db.EpgChannelAliasDao
import app.opentv.data.db.EpgFeedDao
import app.opentv.data.db.ProgrammeDao
import app.opentv.data.db.SourceDao
import app.opentv.data.model.Channel
import app.opentv.data.model.EpgChannelAlias
import app.opentv.data.model.EpgFeed
import app.opentv.data.model.Programme
import app.opentv.data.model.Source
import app.opentv.data.model.TimeShift
import app.opentv.data.parser.ChannelCountry
import app.opentv.data.parser.ChannelNameNormalizer
import app.opentv.data.parser.XmltvParser
import app.opentv.data.remote.XtreamApi
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import app.opentv.data.db.throttleWhile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import app.opentv.core.AppText
import app.opentv.R

/**
 * Owns the electronic programme guide, end to end: feeds in, matched channels out.
 *
 * ## The feed model
 *
 * Guide data can come from three places at once — the provider's own XMLTV (usually thin or
 * empty), the curated free sources shipped with the app, and URLs the user adds. Each is an
 * [EpgFeed] row; every enabled feed is downloaded and merged into one guide. After a sync,
 * [EpgMatcher] joins provider channels to guide channels by normalised name, which is what
 * makes a free national guide light up channels named `UK| BBC ONE FHD`.
 *
 * ## The failure this class exists to prevent
 *
 * The standard way to refresh an EPG is: delete everything, download the new XMLTV, insert
 * it. It is simple and it is why so many IPTV players lose their guide — one stalled
 * download and the user is left with nothing, plus advice to reinstall.
 *
 * OpenTV never deletes before it has the replacement:
 *
 * - Programmes are **upserted in batches** as they stream out of the parser. The unique index
 *   on `(feedId, epgChannelId, startUtcMillis)` makes a re-run idempotent, so a sync that
 *   dies at 60% leaves 60% of a fresher guide behind — strictly better than before.
 * - Old programmes are pruned **by age**, only after at least one feed succeeds.
 * - A failed feed leaves that feed's previous data intact and records the reason on the
 *   feed row, where the settings screen shows it — no silent failure.
 */
class EpgRepository(
    private val programmeDao: ProgrammeDao,
    private val feedDao: EpgFeedDao,
    private val aliasDao: EpgChannelAliasDao,
    private val channelDao: ChannelDao,
    private val sourceDao: SourceDao,
    private val api: XtreamApi,
    private val http: OkHttpClient,
    private val searchIndex: GuideSearchIndex? = null,
    /**
     * Commits to the programmes table (see [app.opentv.data.db.tableChanges]). When set, the guide
     * window is re-read on these signals — paced while a download runs — instead of through a
     * Room Flow that re-runs the whole window query after every 500-row batch.
     */
    private val programmeChanges: Flow<Unit>? = null,
    /** Runs the matcher's writes as one transaction: one list rebuild instead of one per channel. */
    private val database: RoomDatabase? = null,
    /** The home country ([AppSettings.effectiveHomeCountry]): its channels take its guides, others theirs. */
    private val homeCountry: () -> String = { "IT" },
    /** Where the matcher remembers what it last matched from (see [runMatcherIfChanged]). */
    private val matcherMemo: SharedPreferences? = null,
) {

    /** Guide feeds being downloaded right now (several syncs can overlap: start-up, worker, refresh). */
    private val downloads = java.util.concurrent.atomic.AtomicInteger()
    private val _downloading = MutableStateFlow(false)

    /** True while any guide feed is being downloaded and written batch by batch. */
    val downloading: StateFlow<Boolean> = _downloading.asStateFlow()

    data class SyncSummary(
        val feedsSucceeded: Int,
        val feedsFailed: Int,
        val programmesWritten: Int,
        val channelsMatched: Int,
        val channelsTotal: Int,
    )

    // ---- Reads -----------------------------------------------------------------------------

    fun observeFeeds(): Flow<List<EpgFeed>> = feedDao.observeAll()

    /**
     * Everything overlapping the window, re-read when the guide changes. While a download is
     * writing, at most once per [DOWNLOAD_REFRESH_MILLIS] and once more when it ends: the same
     * rows land on screen, without re-reading and regrouping the whole window for every batch.
     */
    fun observeWindow(fromUtcMillis: Long, toUtcMillis: Long): Flow<List<Programme>> {
        val changes = programmeChanges ?: return programmeDao.observeWindow(fromUtcMillis, toUtcMillis)
        return changes
            .throttleWhile(downloading, DOWNLOAD_REFRESH_MILLIS)
            .conflate()
            .map { programmeDao.window(fromUtcMillis, toUtcMillis) }
    }

    fun observeNow(nowUtcMillis: Long): Flow<List<Programme>> =
        programmeDao.observeNow(nowUtcMillis)

    suspend fun upcoming(epgChannelId: String, nowUtcMillis: Long, limit: Int = 12): List<Programme> {
        TimeShift.decode(epgChannelId)?.let { (base, offset) ->
            return TimeShift.shift(upcoming(base, nowUtcMillis - offset, limit), offset, epgChannelId)
        }
        // Over-fetch: two guides can carry the same id, and only one of them is shown.
        return onePerChannel(programmeDao.upcoming(epgChannelId, nowUtcMillis, limit * 3)).take(limit)
    }

    // ---- Feed management -------------------------------------------------------------------

    suspend fun addCustomFeed(name: String, url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty() || feedDao.byUrl(trimmed) != null) return
        feedDao.insert(
            EpgFeed(name = name.trim().ifBlank { trimmed }, url = trimmed, enabled = true),
        )
    }

    suspend fun setFeedEnabled(id: Long, enabled: Boolean) {
        feedDao.setEnabled(id, enabled)
        if (!enabled) {
            // A switched-off guide must stop showing and stop matching: drop its data, and
            // clear the stamp so switching it back on downloads it straight away.
            programmeDao.deleteForFeed(id)
            aliasDao.deleteForFeed(id)
            feedDao.markSynced(id, 0, "")
        }
    }

    suspend fun removeFeed(feed: EpgFeed) {
        // A removed feed takes its guide data with it — orphaned programmes would otherwise
        // keep matching channels forever with content that never refreshes.
        programmeDao.deleteForFeed(feed.id)
        aliasDao.deleteForFeed(feed.id)
        feedDao.delete(feed.id)
    }

    /** Built-in and custom feeds for the settings backup; provider feeds come back with their source. */
    suspend fun feedsForBackup(): List<SettingsBackup.Feed> = feedDao.all()
        .filter { it.providerSourceId == null && !it.url.isNullOrBlank() }
        .map { SettingsBackup.Feed(it.name, it.url!!, it.enabled, it.builtIn) }

    /**
     * Restores feeds from a backup: a URL already here takes the backup's on/off, a missing custom
     * one is added. Feeds that aren't in the backup are left alone. Returns how many changed.
     */
    suspend fun restoreFeeds(feeds: List<SettingsBackup.Feed>): Int {
        ensureFeeds()
        var changed = 0
        for (feed in feeds) {
            val url = feed.url.trim()
            val existing = feedDao.byUrl(url)
            if (existing == null) {
                if (feed.builtIn || url.isEmpty()) continue
                addCustomFeed(feed.name, url)
                if (!feed.enabled) feedDao.byUrl(url)?.let { setFeedEnabled(it.id, false) }
                changed++
            } else if (existing.enabled != feed.enabled) {
                setFeedEnabled(existing.id, feed.enabled)
                changed++
            }
        }
        return changed
    }

    /**
     * Makes sure the standing feed rows exist: one per provider source, plus the curated
     * built-in list. Insert-if-absent, so user toggles survive every call.
     */
    suspend fun ensureFeeds() {
        for (source in sourceDao.enabled()) {
            if (feedDao.forProvider(source.id) == null) {
                feedDao.insert(
                    EpgFeed(
                        name = "${source.name} (provider guide)",
                        providerSourceId = source.id,
                        enabled = true,
                    ),
                )
            }
        }
        val italian = Locale.getDefault().language == "it"
        for ((name, url) in BUILT_IN_FEEDS) {
            if (feedDao.byUrl(url) == null) {
                // Off by default: shipping a switched-on 20 MB download for a country the
                // user may not live in would be rude. The EPG settings screen makes
                // enabling one a single click. The Italian guides are the exception on an
                // Italian device — once, when they first appear; the user's toggle rules after.
                val on = italian && name in ITALIAN_FEEDS
                feedDao.insert(EpgFeed(name = name, url = url, builtIn = true, enabled = on))
            }
        }
    }

    // ---- Sync ------------------------------------------------------------------------------

    /** Downloads every enabled feed, merges, prunes, and re-runs the matcher. */
    suspend fun syncAll(nowUtcMillis: Long, force: Boolean = false): SyncSummary =
        withContext(Dispatchers.IO) { syncLock.withLock { syncAllLocked(nowUtcMillis, force) } }

    /**
     * One pass at a time: launch, the background worker and the refresh button can all ask at
     * once. The second waits, then finds the feeds fresh and the matcher's fingerprint unchanged,
     * so it costs nothing instead of downloading and matching everything a second time.
     */
    private val syncLock = Mutex()

    /**
     * "Refresh the guide now", run outside any screen: leaving the guide settings no longer
     * cancels a download halfway. A second tap while one is running joins it.
     */
    private val manualScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var manual: Deferred<SyncSummary>? = null

    fun refreshNow(): Deferred<SyncSummary> = synchronized(this) {
        manual?.takeIf { it.isActive } ?: manualScope.async {
            syncAll(System.currentTimeMillis(), force = true)
        }.also { manual = it }
    }

    /** The manual refresh still running, if any (a screen reopened mid-download picks it up). */
    fun runningRefresh(): Deferred<SyncSummary>? = manual?.takeIf { it.isActive }

    private suspend fun syncAllLocked(nowUtcMillis: Long, force: Boolean): SyncSummary =
        run {
            ensureFeeds()
            maybeAutoEnableRegionalFeed()

            var succeeded = 0
            var failed = 0
            var written = 0

            for (feed in feedDao.enabled()) {
                if (!force && syncedToday(feed.lastSyncMillis, nowUtcMillis)) {
                    succeeded++
                    continue
                }
                when (val result = downloadingWhile { syncFeed(feed, nowUtcMillis) }) {
                    is FeedResult.Success -> {
                        succeeded++
                        written += result.programmes
                        feedDao.markSynced(
                            feed.id,
                            nowUtcMillis,
                            "${result.programmes} programmes, ${result.channels} channels",
                        )
                    }
                    is FeedResult.Failed -> {
                        failed++
                        // The stamp is NOT advanced on failure, so the next sync retries
                        // rather than waiting out the interval on a feed that never landed.
                        feedDao.markSynced(feed.id, feed.lastSyncMillis, result.reason)
                        Log.w(TAG, "Feed '${feed.name}' failed: ${result.reason}")
                    }
                }
            }

            if (succeeded > 0) {
                programmeDao.deleteEndedBefore(nowUtcMillis - RETENTION_PAST_MILLIS)
            }

            val (matched, total) = runMatcherIfChanged()
            // New programmes: the Search screen's title index follows (a couple of seconds).
            if (written > 0) searchIndex?.rebuild() else searchIndex?.ensureFresh()
            SyncSummary(succeeded, failed, written, matched, total)
        }

    private suspend fun <T> downloadingWhile(block: suspend () -> T): T {
        _downloading.value = downloads.incrementAndGet() > 0
        try {
            return block()
        } finally {
            _downloading.value = downloads.decrementAndGet() > 0
        }
    }

    private sealed interface FeedResult {
        data class Success(val programmes: Int, val channels: Int) : FeedResult
        data class Failed(val reason: String) : FeedResult
    }

    private suspend fun syncFeed(feed: EpgFeed, nowUtcMillis: Long): FeedResult {
        val batch = ArrayList<Programme>(BATCH_SIZE)
        val aliases = ArrayList<EpgChannelAlias>(256)
        var written = 0

        try {
            openFeedStream(feed).use { stream ->
                val stats = XmltvParser.parse(
                    input = stream,
                    feedId = feed.id,
                    onChannelAlias = { id, displayName, iconUrl ->
                        aliases += EpgChannelAlias(
                            feedId = feed.id,
                            epgId = id,
                            displayName = displayName ?: id,
                            normalizedKey = ChannelNameNormalizer.normalize(displayName ?: id).groupKey,
                            iconUrl = iconUrl,
                        )
                    },
                    onProgramme = { programme ->
                        // Skip anything that finished before the retention cut-off; no point
                        // writing rows we are about to prune.
                        if (programme.endUtcMillis >= nowUtcMillis - RETENTION_PAST_MILLIS) {
                            batch += programme
                            if (batch.size >= BATCH_SIZE) {
                                programmeDao.upsertAll(batch)
                                written += batch.size
                                batch.clear()
                            }
                        }
                    },
                )
                if (batch.isNotEmpty()) {
                    programmeDao.upsertAll(batch)
                    written += batch.size
                }
                if (aliases.isNotEmpty()) {
                    aliases.chunked(BATCH_SIZE).forEach { aliasDao.upsertAll(it) }
                }

                if (written == 0 && stats.programmeCount == 0) {
                    return FeedResult.Failed(AppText.get(R.string.epg_no_programmes))
                }
                return FeedResult.Success(written, stats.channelCount)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Deliberately no cleanup. Whatever was written is newer than what was there,
            // and what was there is still there.
            return FeedResult.Failed(e.message ?: AppText.get(R.string.epg_download_failed))
        }
    }

    /**
     * Opens a feed as a decompressed XML stream.
     *
     * Several of the free guide sources publish `.gz` files (a national guide compresses
     * roughly 10:1), and providers occasionally gzip xmltv.php without saying so. Sniffing
     * the first two bytes for the gzip magic number handles both, regardless of what the
     * URL or the headers claim.
     */
    private suspend fun openFeedStream(feed: EpgFeed): InputStream {
        val raw: InputStream = when {
            feed.providerSourceId != null -> {
                val source: Source = sourceDao.byId(feed.providerSourceId)
                    ?: throw IllegalStateException(AppText.get(R.string.epg_provider_gone))
                api.openEpgStream(source)
            }
            feed.url != null -> {
                val response = http.newCall(Request.Builder().url(feed.url).build()).execute()
                if (!response.isSuccessful) {
                    response.close()
                    throw IllegalStateException(AppText.get(R.string.xt_guide_http, response.code))
                }
                response.body?.byteStream()
                    ?: throw IllegalStateException(AppText.get(R.string.xt_guide_empty))
            }
            else -> throw IllegalStateException(AppText.get(R.string.epg_feed_no_url))
        }

        val buffered = BufferedInputStream(raw, 8 * 1024)
        buffered.mark(2)
        val b1 = buffered.read()
        val b2 = buffered.read()
        buffered.reset()
        return if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(buffered) else buffered
    }

    // ---- Matching --------------------------------------------------------------------------

    /**
     * [runMatcher], unless nothing it reads has changed since it last ran: the same channels (name,
     * key, guide ids, logos, its own last answers), the same guide feeds at the same download, the
     * same guide channels with programmes left and the same name rules. Then its answer would be
     * the one already stored, so the work — every channel against every guide, at every launch —
     * is skipped. Explicit requests (regroup, a restore) call [runMatcher] and always run.
     */
    suspend fun runMatcherIfChanged(): Pair<Int, Int> {
        val memo = matcherMemo ?: return runMatcher()
        val startedAt = System.currentTimeMillis()
        val inputs = runCatching { matcherInputs() }.getOrNull()
        if (inputs != null && memo.getString(KEY_MATCH_INPUTS, null) == inputs) {
            val matched = memo.getInt(KEY_MATCHED, -1)
            val total = memo.getInt(KEY_TOTAL, -1)
            if (matched >= 0 && total >= 0) {
                app.opentv.core.EventLog.log("guide", "matcher skipped, nothing changed (${System.currentTimeMillis() - startedAt} ms)")
                return matched to total
            }
        }
        return runMatcher()
    }

    /** A fingerprint of everything [runMatcher] reads (see [runMatcherIfChanged]). */
    private suspend fun matcherInputs(): String {
        val feeds = feedDao.enabled().sortedBy { it.id }
            .joinToString(",") { "${it.id}@${it.lastSyncMillis}/${it.providerSourceId}" }
        val populated = programmeDao.channelIdsWithProgrammes().sorted()
        val aliases = aliasDao.count()
        val rows = channelDao.matchInputs()
        var hash = 17L
        for (row in rows) hash = hash * 31 + row.hashCode()
        return "m$MATCHER_INPUTS_VERSION|n${CatalogRepository.NORMALIZER_VERSION}|h${homeCountry()}|$feeds|a$aliases|" +
            "${populated.size}:${populated.hashCode()}|${rows.size}:$hash"
    }

    /** How many times [runMatcher] has really run in this process — for tests. */
    @androidx.annotation.VisibleForTesting
    internal var matcherRuns = 0

    /**
     * Joins every channel to the merged guide by normalised name.
     * Returns (channels with a working guide id, total channels).
     */
    suspend fun runMatcher(): Pair<Int, Int> {
        matcherRuns++
        val startedAt = System.currentTimeMillis()
        // Every change is collected and written at the end in one transaction (see [database]).
        val writes = ArrayList<suspend () -> Unit>()
        // Only ids that actually have programmes count — and only those are matched to. A
        // guide that lists a channel but never fills it (epgshare's empty `TG.NORBA.24.it`)
        // must not win over another guide that does.
        val counts = programmeDao.programmeCounts().associate { it.epgId to it.n }
        val enabled = feedDao.enabled()
        val enabledFeeds = enabled.map { it.id }.toHashSet()
        val providerFeeds = enabled.filter { it.providerSourceId != null }.map { it.id }.toHashSet()
        val guideChannels = aliasDao.all().filter { it.feedId in enabledFeeds && (counts[it.epgId] ?: 0) > 0 }
        val index = EpgMatcher.buildIndexOf(
            guideChannels.map { EpgMatcher.GuideChannel(it.epgId, it.displayName, it.feedId, counts[it.epgId] ?: 0) },
        )
        fun populated(id: String?) = id != null && (counts[id] ?: 0) > 0

        // Each channel's guide logo: shown where the provider has none, or instead of the
        // provider's by choice. Still the user's data (their guide feeds), never bundled. The
        // provider's own guide mostly repeats the provider's logos, so a free or added guide's
        // icon wins: by the channel's own key first, then by the key of the guide it is matched to.
        val guideIcons = HashMap<String, String>()
        val freeIconByKey = HashMap<String, String>()
        val keyOfGuide = HashMap<String, String>()
        // Guides sometimes hand a channel a sibling's icon (iptv-epg gives Sky Sport Max the
        // Sky Sport Football logo). When guides disagree, an icon whose address spells the
        // channel's key (`…/248skysportmax_Light_Fit.png`) wins over one that doesn't.
        fun namesKey(icon: String, key: String) =
            key.length >= 4 && icon.lowercase().filter { it.isLetterOrDigit() }.contains(key)
        for (alias in guideChannels) {
            keyOfGuide.putIfAbsent(alias.epgId, alias.normalizedKey)
            val icon = alias.iconUrl ?: continue
            guideIcons.putIfAbsent(alias.epgId, icon)
            if (alias.feedId in providerFeeds) continue
            val key = alias.normalizedKey
            val current = freeIconByKey[key]
            if (current == null || (!namesKey(current, key) && namesKey(icon, key))) freeIconByKey[key] = icon
        }
        fun guideLogoOf(groupKey: String, guideId: String?): String? =
            freeIconByKey[groupKey]
                ?: guideId?.let { keyOfGuide[it] }?.let(freeIconByKey::get)
                ?: guideId?.let(guideIcons::get)

        val channels = channelDao.allForMatching()
        var matched = 0
        var guideLogos = 0

        val rawKeys = HashMap<Long, String>()
        fun rawKey(channel: Channel) =
            rawKeys.getOrPut(channel.id) { ChannelNameNormalizer.normalize(channel.name).groupKey }

        // A channel only takes a guide of its own country (an id with no country suffix fits any):
        // `de:skynature` never gets `SkyNature.it`, Italian Sky Sport never a `….at`.
        val home = homeCountry()
        val anyGuide: (String) -> Boolean = { true }
        val homeGuide: (String) -> Boolean = { id -> ChannelCountry.ofGuideId(id)?.let { ChannelCountry.isHome(it, home) } ?: true }
        val countryGuides = HashMap<String, (String) -> Boolean>()
        fun accepts(channel: Channel): (String) -> Boolean = when (val cc = ChannelCountry.ofKey(channel.groupKey)) {
            null -> homeGuide
            ChannelCountry.WORLD -> anyGuide
            else -> countryGuides.getOrPut(cc) { { id -> ChannelCountry.ofGuideId(id)?.let { it == cc } ?: true } }
        }
        /** The key without its country, as guides know it. */
        fun bareKey(channel: Channel) = ChannelCountry.bare(channel.groupKey)
        /** The country part of the key (`de:`), to keep raw-name lookups apart per country. */
        fun prefixOf(channel: Channel) = channel.groupKey.substring(0, channel.groupKey.length - bareKey(channel).length)

        /**
         * The channel's guide by name. Its stored key first; then, exact only, the key before
         * [ChannelNameNormalizer.keyAliases] folded it (`Sky Nature` filed as `nature` because the
         * list also has a `Nature`): guides spell the full name.
         */
        fun byName(channel: Channel): EpgMatcher.Match? {
            val accept = accepts(channel)
            val key = bareKey(channel)
            return index.find(key, accept) ?: rawKey(channel)
                .takeIf { it != key }
                ?.let { index.exact(it, accept) }
                ?.let { EpgMatcher.Match(it, EpgMatcher.Tier.EXACT) }
        }

        /**
         * Whether the provider's own guide id can be believed. Providers copy ids between
         * channels (`Discovery Channel` → `Disney Plus Animazione`), so the id has to look like
         * the channel — unless the name finds nothing better, in which case it is all there is.
         */
        fun providerAgrees(channel: Channel, id: String, exactByName: String?): Boolean {
            if (exactByName == null || exactByName == id) return true
            val keys = listOf(bareKey(channel), rawKey(channel))
            return index.keysOf(id).any { guideKey -> keys.any { EpgMatcher.similar(guideKey, it) } }
        }

        // Pass 1: ordinary channels. The matched id is the matcher's final word — the provider's
        // id when it holds up, else the name match — so every reader can simply try it first.
        // Remember each one's guide and logo by key, for the timeshift copies in pass 2.
        val baseGuide = HashMap<String, String>()
        val baseLogo = HashMap<String, String>()
        val baseGuideLogo = HashMap<String, String>()
        val shifted = ArrayList<Pair<Channel, Pair<String, Int>>>()
        for (channel in channels) {
            val split = TimeShift.splitKey(channel.groupKey)
            if (split != null) {
                shifted += channel to split
                continue
            }
            val named = byName(channel)
            val exactByName = named?.takeIf { it.tier == EpgMatcher.Tier.EXACT }?.epgId
            val provider = channel.epgChannelId?.takeIf {
                populated(it) && accepts(channel)(it) && providerAgrees(channel, it, exactByName)
            }
            val newMatch = provider ?: named?.epgId
            if (newMatch != channel.matchedEpgId) writes += { channelDao.setMatchedEpgId(channel.id, newMatch) }
            val working = (listOfNotNull(channel.epgOverrideId, newMatch)).firstOrNull(::populated)
            if (working != null) {
                matched++
                baseGuide.putIfAbsent(channel.groupKey, working)
                baseGuide.putIfAbsent(prefixOf(channel) + rawKey(channel), working)
            }
            channel.logoUrl?.takeIf { it.isNotBlank() }?.let { baseLogo.putIfAbsent(channel.groupKey, it) }
            val guideLogo = guideLogoOf(bareKey(channel), working)
            guideLogo?.let { baseGuideLogo.putIfAbsent(channel.groupKey, it) }
            if (guideLogo != null) guideLogos++
            if (guideLogo != channel.guideLogoUrl) writes += { channelDao.setGuideLogo(channel.id, guideLogo) }
        }

        // Pass 2: `+1`/`+2`/`+3` copies. Their own guide if one exists; otherwise the base
        // channel's, moved later. A provider id has to be a guide for the same timeshift: one
        // that is just the base's guide (it happens: `Italia 1 +1` → `Italia1.it`) would show
        // the schedule an hour early. Only an exact name beats the base's guide moved later —
        // a typo-tolerant or prefix one may well be another channel's `+1`.
        for ((channel, split) in shifted) {
            val (baseKey, hours) = split
            val base = baseGuide[baseKey] ?: TimeShift.splitKey(rawKey(channel))?.first?.let { baseGuide[prefixOf(channel) + it] }
            val own = byName(channel)?.takeIf { it.epgId != base && (it.tier == EpgMatcher.Tier.EXACT || base == null) }
            val shift = EpgMatcher.splitShift(channel.groupKey).second
            val providerOwn = channel.epgChannelId?.takeIf { id ->
                populated(id) && id != base && accepts(channel)(id) &&
                    index.keysOf(id).any { EpgMatcher.splitShift(it).second == shift }
            }
            // A free guide's own `+1` first, then the base's guide moved later, and only then the
            // provider's `+1`: providers often ship copies that mix shifted and unshifted times
            // (Sky Sport F1 +1 listed the same slot twice, an hour apart).
            val newMatch = when {
                own != null -> own.epgId
                base != null -> TimeShift.encode(base, hours)
                providerOwn != null -> providerOwn
                else -> null
            }
            if (newMatch != channel.matchedEpgId) writes += { channelDao.setMatchedEpgId(channel.id, newMatch) }
            if (populated(channel.epgOverrideId) || newMatch != null) matched++
            if (channel.logoUrl.isNullOrBlank()) baseLogo[baseKey]?.let { logo -> writes += { channelDao.setLogo(channel.id, logo) } }
            // The base channel's logo first: a `+1` is the same channel, and guides often give
            // the copies a poorer icon or a mismatched one.
            val ownGuide = listOfNotNull(channel.epgOverrideId, providerOwn, own?.epgId).firstOrNull(::populated)
            val guideLogo = baseGuideLogo[baseKey] ?: ownGuide?.let { guideLogoOf(bareKey(channel), it) }
            if (guideLogo != null) guideLogos++
            if (guideLogo != channel.guideLogoUrl) writes += { channelDao.setGuideLogo(channel.id, guideLogo) }
        }

        if (writes.isNotEmpty()) {
            val db = database
            if (db != null) db.withTransaction { for (write in writes) write() } else for (write in writes) write()
        }
        val took = System.currentTimeMillis() - startedAt
        Log.i(
            TAG,
            "Matcher: $matched of ${channels.size} channels have a working guide " +
                "(${counts.size} populated guide channels, ${shifted.size} timeshift copies, $guideLogos with a guide logo, " +
                "${writes.size} changes) in $took ms",
        )
        app.opentv.core.EventLog.log("guide", "matcher ran: ${channels.size} channels, ${writes.size} changes, $took ms")
        // What it matched from, read after its own writes: the next unchanged start skips it.
        matcherMemo?.let { memo ->
            runCatching { matcherInputs() }.getOrNull()?.let { inputs ->
                memo.edit()
                    .putString(KEY_MATCH_INPUTS, inputs)
                    .putInt(KEY_MATCHED, matched)
                    .putInt(KEY_TOTAL, channels.size)
                    .apply()
            }
        }
        return matched to channels.size
    }

    suspend fun setManualOverride(channelId: Long, epgId: String?) =
        channelDao.setEpgOverride(channelId, epgId)

    /** Channel menu › Find guide: the user's pick, on every copy of the channel (all its qualities). */
    suspend fun setManualOverride(channel: Channel, epgId: String?) =
        if (channel.groupKey.isEmpty()) channelDao.setEpgOverride(channel.id, epgId)
        else channelDao.setEpgOverrideForGroup(channel.groupKey, epgId)

    /** One guide channel found by [searchGuides]: enough to tell at a glance if it is the right one. */
    data class GuideHit(
        val epgId: String,
        val name: String,
        val feedName: String,
        val iconUrl: String?,
        val programmes: Int,
        val nowTitle: String?,
    )

    /**
     * Manual guide search over the enabled feeds' channel lists, by name, guide id or normalised
     * key (so `sky uno+` still finds `Sky Uno +`). Guides that carry programmes come first;
     * empty ones are listed last because a feed may simply not have been refreshed yet.
     */
    suspend fun searchGuides(query: String, limit: Int = 60): List<GuideHit> {
        val text = query.trim()
        if (text.isEmpty()) return emptyList()
        val key = ChannelNameNormalizer.groupKeyOf(text)
        val aliases = aliasDao.search("%$text%", if (key.isEmpty()) "\u0000" else "%$key%", limit * 2)
        if (aliases.isEmpty()) return emptyList()
        val feeds = feedDao.all().associate { it.id to it.name }
        val now = System.currentTimeMillis()
        return aliases
            .distinctBy { it.epgId }
            .map { a ->
                GuideHit(
                    epgId = a.epgId,
                    name = a.displayName,
                    feedName = feeds[a.feedId].orEmpty(),
                    iconUrl = a.iconUrl,
                    programmes = programmeDao.countForChannel(a.epgId),
                    nowTitle = programmeDao.nowOn(a.epgId, now)?.title,
                )
            }
            .sortedWith(compareByDescending<GuideHit> { it.programmes > 0 }.thenBy { it.name.length })
            .take(limit)
    }

    /**
     * Turns on the free guide for the user's region, once, on a pristine install.
     *
     * The reasoning: a provider guide is usually empty, and "go and find Guide settings"
     * is a hurdle most people meet as a blank guide and give up on. The app already knows
     * the region — the normaliser reads it off the `UK|` prefixes the provider itself
     * ships — so when a clear majority of channels agree, enable that region's built-in.
     *
     * Guard rails: only when every built-in is untouched (never enabled, never synced)
     * and no custom feed exists. Switch it off and it stays off — a choice the user has
     * made is never overridden.
     */
    private suspend fun maybeAutoEnableRegionalFeed() {
        val all = feedDao.all()
        val builtIns = all.filter { it.builtIn }
        if (builtIns.isEmpty()) return
        val pristine = builtIns.all { !it.enabled && it.lastSyncMillis == 0L } &&
            all.none { !it.builtIn && it.providerSourceId == null }
        if (!pristine) return

        val regionCounts = HashMap<String, Int>()
        for (channel in channelDao.allForMatching()) {
            val region = ChannelNameNormalizer.normalize(channel.name).region ?: continue
            regionCounts.merge(region, 1, Int::plus)
        }
        val top = regionCounts.maxByOrNull { it.value } ?: return
        if (top.value < MIN_CHANNELS_FOR_AUTO_REGION) return

        val feedName = REGION_TO_FEED[top.key] ?: return
        val feed = builtIns.firstOrNull { it.name == feedName } ?: return
        feedDao.setEnabled(feed.id, true)
        Log.i(TAG, "Auto-enabled '${feed.name}' — ${top.value} channels tagged ${top.key}")
    }

    companion object {
        private const val TAG = "EpgRepository"

        /** Writes per transaction. Large enough to be fast, small enough not to hold WAL open. */
        const val BATCH_SIZE = 500

        /** Bump whenever [runMatcher]'s logic changes, so the next start matches again. */
        const val MATCHER_INPUTS_VERSION = 1
        private const val KEY_MATCH_INPUTS = "inputs"
        private const val KEY_MATCHED = "matched"
        private const val KEY_TOTAL = "total"

        /** How often the guide on screen catches up with a download still in progress. */
        const val DOWNLOAD_REFRESH_MILLIS = 20_000L

        /** Keep finished programmes for a day so "what was on" still works. */
        val RETENTION_PAST_MILLIS: Long = TimeUnit.DAYS.toMillis(1)

        /**
         * A guide is good for the whole day: once it landed today (local calendar day), it isn't
         * downloaded again until tomorrow's first sync. A late change on air (a special news
         * edition) is what the "refresh the guide now" button is for (force = true).
         */
        fun syncedToday(lastSyncMillis: Long, nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): Boolean {
            if (lastSyncMillis <= 0L || lastSyncMillis > nowMillis) return false
            val last = Calendar.getInstance(zone).apply { timeInMillis = lastSyncMillis }
            val now = Calendar.getInstance(zone).apply { timeInMillis = nowMillis }
            return last.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                last.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
        }

        /**
         * The curated built-in list. Criteria for being here: free, no key or signup,
         * openly licensed or explicitly public, and reachable as plain XMLTV(.gz).
         * All ship disabled; the user turns on the one for where they live.
         *
         * The UK Freeview entry is GPL-3.0 (same licence as OpenTV), regenerated every
         * 12 h, ~270 channels with logos — verified working before it earned this slot.
         */
        /** A region needs at least this many prefixed channels before we act on it. */
        const val MIN_CHANNELS_FOR_AUTO_REGION = 5

        /** Region tag (as providers write it) → built-in feed name to auto-enable. */
        val REGION_TO_FEED: Map<String, String> = mapOf(
            "UK" to "UK — Freeview (free-to-air)",
            "GB" to "UK — Freeview (free-to-air)",
            "US" to "USA — epgshare01",
            "USA" to "USA — epgshare01",
            "CA" to "Canada — epgshare01",
            "AU" to "Australia — epgshare01",
            "AUS" to "Australia — epgshare01",
            "IT" to FEED_IT_IPTV_EPG,
            "ITA" to FEED_IT_IPTV_EPG,
        )

        const val FEED_IT_IPTV_EPG = "Italia — iptv-epg.org"
        const val FEED_IT_EPGSHARE = "Italia — epgshare01"

        /** Switched on by themselves, once, on a device set to Italian. */
        val ITALIAN_FEEDS = setOf(FEED_IT_IPTV_EPG, FEED_IT_EPGSHARE)

        /**
         * One guide per channel id. Two guides can publish the same id (`Rai3.it` from the
         * provider and from a free guide); merged, their schedules overlap in the grid. Keeps
         * the guide that reads like a schedule: most entries, minus the ones that start before
         * the previous one ended. A provider copy listing every slot twice an hour apart has
         * more entries than the clean guide, and must not win on that.
         */
        fun onePerChannel(programmes: List<Programme>): List<Programme> {
            if (programmes.isEmpty()) return programmes
            val byFeed = programmes.groupBy { it.feedId }
            if (byFeed.size == 1) return programmes
            val best = byFeed.maxBy { (_, list) -> scheduleScore(list) }.key
            return programmes.filter { it.feedId == best }
        }

        /** Entries in a feed's schedule for one channel, each overlap counted against it twice. */
        internal fun scheduleScore(programmes: List<Programme>): Int {
            val sorted = programmes.sortedBy { it.startUtcMillis }
            var overlaps = 0
            var lastEnd = Long.MIN_VALUE
            for (p in sorted) {
                if (p.startUtcMillis < lastEnd) overlaps++
                lastEnd = maxOf(lastEnd, p.endUtcMillis)
            }
            return sorted.size - 2 * overlaps
        }

        val BUILT_IN_FEEDS: List<Pair<String, String>> = listOf(
            "UK — Freeview (free-to-air)" to
                "https://raw.githubusercontent.com/dp247/Freeview-EPG/master/epg.xml",
            "UK — epgshare01 (Sky lineup)" to
                "https://epgshare01.online/epgshare01/epg_ripper_UK1.xml.gz",
            "USA — epgshare01" to
                "https://epgshare01.online/epgshare01/epg_ripper_US1.xml.gz",
            "Canada — epgshare01" to
                "https://epgshare01.online/epgshare01/epg_ripper_CA1.xml.gz",
            "Australia — epgshare01" to
                "https://epgshare01.online/epgshare01/epg_ripper_AU1.xml.gz",
            // Italy: iptv-epg.org (~300 channels, 10 days, descriptions) and epgshare01
            // (~200 channels, 4 days, programme images). Both free, no key, plain XMLTV.gz.
            FEED_IT_IPTV_EPG to "https://iptv-epg.org/files/epg-it.xml.gz",
            FEED_IT_EPGSHARE to "https://epgshare01.online/epgshare01/epg_ripper_IT1.xml.gz",
        )
    }
}
