/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.football

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.opentv.core.ServiceLocator
import app.opentv.data.repo.FootballRepository
import app.opentv.data.repo.FootballRepository.Match
import app.opentv.data.repo.FootballRepository.MatchChannel
import app.opentv.data.repo.FootballRepository.Phase
import app.opentv.data.model.Channel
import app.opentv.data.model.shownName
import app.opentv.BuildConfig
import app.opentv.player.MatchFeeds
import app.opentv.player.PlaybackQueue
import app.opentv.player.ScoreboardReader
import app.opentv.player.StreamProbe
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FootballViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: FootballRepository = ServiceLocator.get(app).footballRepository
    private val youTube = ServiceLocator.get(app).youTubeStreams
    private val graph = ServiceLocator.get(app)

    /** A match with what the home needs to act on it. */
    data class Item(
        val match: Match,
        val channels: List<MatchChannel> = emptyList(),
        /** YouTube id of the highlights, for a finished match. */
        val highlights: String? = null,
    )

    data class UiState(
        val loading: Boolean = true,
        /** The big card: the live match, else the next one to start. */
        val hero: Item? = null,
        /** Games on at the same time, as tabs on the big card: two or more, else empty. */
        val heroes: List<Item> = emptyList(),
        val live: List<Item> = emptyList(),
        val today: List<Item> = emptyList(),
        val upcoming: List<Item> = emptyList(),
        /** The latest matchday before today, finished: highlights on days without games. */
        val lastRound: List<Item> = emptyList(),
        /** FotMob unreachable and nothing to show. */
        val failed: Boolean = false,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    /** The highlights video being looked up for our own player, while it is. */
    private val _opening = MutableStateFlow<String?>(null)
    val opening: StateFlow<String?> = _opening.asStateFlow()
    private var openJob: Job? = null

    /** One feed on the "Watch automatically" screen. */
    data class ScanRow(
        val id: Long,
        val name: String,
        /** "1080p · 50 fps · 5.1", once looked at. */
        val detail: String? = null,
        /** The team codes the scoreboard showed: "SCF BAY". */
        val board: String? = null,
        /** Null while still to be looked at. */
        val verdict: AutoScan.Verdict? = null,
        /** 1-based place once ranked. */
        val place: Int? = null,
    )

    data class Scan(val title: String, val rows: List<ScanRow>, val done: Int, val finished: Boolean = false)

    private val _scan = MutableStateFlow<Scan?>(null)
    val scan: StateFlow<Scan?> = _scan.asStateFlow()

    /** The feed to open once a scan is over (its ranking already handed to [MatchFeeds]). */
    private val _autoPlay = MutableStateFlow<Channel?>(null)
    val autoPlay: StateFlow<Channel?> = _autoPlay.asStateFlow()

    /** A match's feed with what its last look found: [looked] false while still to be looked at. */
    data class Source(
        val feed: Channel,
        val name: String,
        val looked: Boolean,
        val probe: AutoScan.Probe?,
        val verdict: AutoScan.Verdict?,
        /** When it was looked at. */
        val at: Long? = null,
    ) {
        val usable get() = !looked || verdict == AutoScan.Verdict.OK || verdict == AutoScan.Verdict.CONFIRMED
    }

    /** The scan going on by itself, for the thin bar: "Roma - Cagliari · 12/30". */
    data class Progress(val matchId: Long, val title: String, val done: Int, val total: Int)

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress.asStateFlow()

    /** Bumped on every look stored: what the screen re-reads [sources] on. */
    private val _looks = MutableStateFlow(0)
    val looks: StateFlow<Int> = _looks.asStateFlow()

    private val store = ScanStore.get(app)

    /** Each match's feeds, best quality first within a channel, as looked at and listed. */
    @Volatile private var feeds: Map<Long, List<Channel>> = emptyMap()

    private var active = false
    private var worker: Job? = null
    /** The match whose "Watch now" was pressed: its feeds go before anything else. */
    @Volatile private var focus: Item? = null
    private var focusStarted = 0L
    /** Wakes the worker out of its idle wait the moment "Watch now" is pressed. */
    private val wake = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)

    fun autoPlayed() {
        _autoPlay.value = null
    }

    fun cancelScan() {
        focus = null
        _scan.value = null
    }

    /**
     * The football home is on screen (or not): only then do feeds get looked at, one at a time —
     * the account allows one connection, and the player needs it the moment it opens.
     */
    fun setActive(on: Boolean) {
        if (on == active) return
        active = on
        if (on) {
            startWorker()
        } else {
            worker?.cancel()
            worker = null
            focus = null
            _scan.value = null
            _progress.value = null
        }
    }

    fun feedsOf(item: Item): List<Channel> = feeds[item.match.id].orEmpty()

    /**
     * [item]'s feeds best first: the ones looked at ranked by [AutoScan], then the ones still to
     * look at in channel order, then the dead and the wrong game.
     */
    fun sources(item: Item): List<Source> {
        val m = item.match
        val all = feedsOf(item)
        val looks = all.mapNotNull { f -> store.fresh(f.id)?.let { f to it.probe } }
        val ranked = AutoScan.rank(looks, m.home.name, m.away.name, channel = { it.groupKey.ifEmpty { "#${it.id}" } })
        val lookedIds = looks.mapTo(HashSet()) { it.first.id }
        val out = ranked.map { Source(it.item, nameOf(it.item), true, it.probe, it.verdict, store.fresh(it.item.id)?.at) }
        val unlooked = all.filter { it.id !in lookedIds }.map { Source(it, nameOf(it), false, null, null) }
        return out.filter { it.usable } + unlooked + out.filterNot { it.usable }
    }

    /** [feedId]'s picture from its last look. */
    fun frame(feedId: Long): java.io.File? = store.frame(feedId)

    /** Whether every feed of [item] has a fresh look. */
    fun ready(item: Item): Boolean = feedsOf(item).let { fs -> fs.isNotEmpty() && fs.all { store.fresh(it.id) != null } }

    /** "Scan again": [item]'s looks dropped, looked at afresh. */
    fun rescan(item: Item) {
        store.forget(feedsOf(item).map { it.id })
        _looks.value++
        autoWatch(item)
    }

    /**
     * "Watch now": [item]'s feeds not looked at lately are (on the scan screen, picking up where any
     * earlier scan stopped), then the best one plays with the rest of the ranking as its fallback.
     */
    fun autoWatch(item: Item) {
        if (feedsOf(item).isEmpty()) return
        // Every feed looked at lately: straight to the best, no scan screen.
        if (ready(item)) {
            val best = sources(item).firstOrNull { it.usable }?.feed ?: feedsOf(item).first()
            play(item, best)
            return
        }
        focus = item
        focusStarted = System.currentTimeMillis()
        publishFocus(item)
        startWorker()
        wake.trySend(Unit)
    }

    /** Plays [feed] of [item] as picked by hand: up/down then zaps along the match's feeds only. */
    fun play(item: Item, feed: Channel) {
        viewModelScope.launch {
            // The feed being looked at holds the one connection: let it go first.
            worker?.cancelAndJoin()
            worker = null
            handOver(item, feed)
        }
    }

    private fun handOver(item: Item, first: Channel) {
        val order = sources(item).filter { it.usable }.map { it.feed }.ifEmpty { feedsOf(item) }
        val ordered = listOf(first) + order.filter { it.id != first.id }
        MatchFeeds.set(ordered.map { it.id }, ordered.associate { it.id to nameOf(it) })
        PlaybackQueue.items = ordered.map { PlaybackQueue.Item(it.id, nameOf(it), it.logoUrl) }
        _autoPlay.value = first
    }

    private fun startWorker() {
        if (!active || worker?.isActive == true) return
        worker = viewModelScope.launch {
            val prober = StreamProbe(graph.streamingHttpClient, getApplication<Application>().cacheDir)
            val reader = ScoreboardReader(getApplication())
            try {
                while (true) {
                    val f = focus
                    if (f != null) {
                        val overBudget = System.currentTimeMillis() - focusStarted > SCAN_BUDGET_MILLIS
                        val next = if (overBudget) null else feedsOf(f).firstOrNull { store.fresh(it.id) == null }
                        if (next == null) {
                            finishFocus(f)
                            // The player is about to take the connection: no more looks until back.
                            return@launch
                        }
                        look(next, prober, reader)
                        if (focus === f) publishFocus(f)
                        continue
                    }
                    val bg = nextInBackground()
                    if (bg == null) {
                        _progress.value = null
                        withTimeoutOrNull(IDLE_MILLIS) { wake.receive() }
                        continue
                    }
                    val (item, feed) = bg
                    val all = feedsOf(item)
                    val m = item.match
                    _progress.value = Progress(m.id, "${m.home.name} - ${m.away.name}", all.count { store.fresh(it.id) != null }, all.size)
                    look(feed, prober, reader)
                }
            } finally {
                reader.close()
            }
        }
    }

    /** The next feed to look at by itself: matches on now or starting soon, soonest first. */
    private fun nextInBackground(): Pair<Item, Channel>? {
        val now = System.currentTimeMillis()
        val window = if (BuildConfig.DEBUG) DEBUG_WINDOW_MILLIS else AHEAD_MILLIS
        val ui = _ui.value
        val candidates = (listOfNotNull(ui.hero) + ui.live + ui.today + ui.upcoming)
            .distinctBy { it.match.id }
            .filter { it.match.phase == Phase.LIVE || (it.match.phase == Phase.SCHEDULED && it.match.kickoffUtcMillis - now <= window) }
            .sortedBy { it.match.kickoffUtcMillis }
        for (item in candidates) {
            val next = feedsOf(item).firstOrNull { store.fresh(it.id) == null } ?: continue
            return item to next
        }
        return null
    }

    private suspend fun look(feed: Channel, prober: StreamProbe, reader: ScoreboardReader) {
        val catalog = graph.catalogRepository
        val source = graph.sourceRepository.byId(feed.sourceId)
        val url = catalog.resolvePlaybackUrl(feed, source)
        val r = prober.probe(url, source?.userAgent ?: "OpenTV/0.1 (Android)")
        val text = r?.corner?.let { reader.read(it) }
        r?.corner?.recycle()
        val board = text?.let { AutoScan.parseBoard(it) }
        val p = r?.let { AutoScan.Probe(it.height, it.fps, it.audioChannels, it.millis, board) }
        android.util.Log.i(TAG, "${feed.shownName} ${feed.qualityLabel}: ${p?.let(::detail) ?: "dead"} | ocr=${text?.take(80)} | board=$board")
        store.put(feed.id, p, r?.preview)
        _looks.value++
    }

    /** The scan screen for [item], from what's known of its feeds so far. */
    private fun publishFocus(item: Item) {
        val m = item.match
        val all = feedsOf(item)
        val rows = all.map { f ->
            val e = store.fresh(f.id)
            val p = e?.probe
            val verdict = when {
                e == null -> null
                p == null -> AutoScan.Verdict.DEAD
                p.board != null -> AutoScan.gameCheck(p.board, m.home.name, m.away.name) ?: AutoScan.Verdict.OK
                else -> AutoScan.Verdict.OK
            }
            ScanRow(f.id, nameOf(f), p?.let(::detail), p?.board?.let(::boardText), verdict)
        }
        _scan.value = Scan("${m.home.name} - ${m.away.name}", rows, rows.count { it.verdict != null })
    }

    /** The ranking shown a moment, then the best feed plays. */
    private suspend fun finishFocus(item: Item) {
        val m = item.match
        val src = sources(item)
        val rows = src.mapIndexed { i, s ->
            ScanRow(
                s.feed.id, s.name, s.probe?.let(::detail), s.probe?.board?.let(::boardText),
                verdict = if (s.looked) s.verdict else null,
                place = (i + 1).takeIf { s.looked && s.usable },
            )
        }
        _scan.value = Scan("${m.home.name} - ${m.away.name}", rows, rows.size, finished = true)
        src.forEachIndexed { i, s -> android.util.Log.i(TAG, "#${i + 1} ${s.name}: ${s.verdict}") }
        if (focus !== item) return
        focus = null
        _scan.value = null
        val first = src.firstOrNull { it.usable }?.feed ?: feedsOf(item).first()
        handOver(item, first)
    }

    private fun nameOf(c: Channel) = listOf(c.shownName, c.qualityLabel).filter(String::isNotBlank).joinToString(" · ")

    private fun detail(p: AutoScan.Probe) = listOfNotNull(
        "${p.height}p",
        p.fps?.let { "$it fps" },
        p.audioChannels?.takeIf { it >= 6 }?.let { "5.1" },
        String.format(java.util.Locale.ITALY, "%.1f s", p.millis / 1000.0),
    ).joinToString(" · ")

    private fun boardText(b: AutoScan.Board) = b.codes.take(2).joinToString(" ")

    init {
        viewModelScope.launch {
            while (true) {
                load()
                // A minute while a game is on (score and minute move), five otherwise.
                delay(if (_ui.value.hero?.match?.phase == Phase.LIVE) 60_000L else 300_000L)
            }
        }
    }

    /**
     * Finds a stream for highlights [videoId] and hands it to [onStream]; when YouTube can't be
     * read (an extractor YouTube has just broken) [onFallback] opens the YouTube app instead.
     */
    fun openHighlights(videoId: String, onStream: (String) -> Unit, onFallback: () -> Unit) {
        if (openJob?.isActive == true) return
        openJob = viewModelScope.launch {
            _opening.value = videoId
            val url = youTube.resolve(videoId)
            _opening.value = null
            if (url != null) onStream(url) else onFallback()
        }
    }

    private suspend fun load() {
        val today = repo.today()
        val upcoming = repo.upcoming()
        if (today.isEmpty() && upcoming.isEmpty()) {
            _ui.value = _ui.value.copy(loading = false, failed = _ui.value.hero == null && _ui.value.today.isEmpty())
            return
        }
        val channels = repo.channelsFor(
            today.filter { it.phase == Phase.SCHEDULED || it.phase == Phase.LIVE } + upcoming,
        )
        // Highlights already found only: looking up new ones waits until the page is up.
        val items = today.map { m ->
            when (m.phase) {
                Phase.FINISHED -> Item(m, highlights = repo.knownHighlights(m))
                Phase.OFF -> Item(m)
                else -> Item(m, channels = channels[m.id].orEmpty())
            }
        }
        val next = upcoming.map { Item(it, channels = channels[it.id].orEmpty()) }
        val live = items.filter { it.match.phase == Phase.LIVE }
        val hero = live.firstOrNull()
            ?: items.firstOrNull { it.match.phase == Phase.SCHEDULED }
            ?: next.firstOrNull()
        // Games on together (or about to be) share the big card as tabs.
        val now = System.currentTimeMillis()
        val together = items.filter {
            it.match.phase == Phase.LIVE ||
                (it.match.phase == Phase.SCHEDULED && it.match.kickoffUtcMillis - now <= TABS_AHEAD_MILLIS)
        }.sortedBy { it.match.kickoffUtcMillis }
        val heroes = if (together.size >= 2) together else emptyList()
        // The big card's feeds before first paint (its source list); the rest's right after:
        // looking them up is a query per channel, a second or so for a whole matchday.
        val first = (listOfNotNull(hero) + heroes).distinctBy { it.match.id }
        feeds = feeds + first.filter { it.channels.isNotEmpty() }.associate { it.match.id to feedsFor(it) }
        _ui.value = UiState(
            loading = false,
            hero = hero,
            heroes = heroes,
            live = live.filter { it !== hero && it !in heroes },
            // Today's row: still to come first, then the finished ones latest first.
            today = items.filter { it !== hero && it !in heroes && it.match.phase == Phase.SCHEDULED } +
                items.filter { it.match.phase == Phase.FINISHED }.sortedByDescending { it.match.kickoffUtcMillis },
            upcoming = next.filter { it !== hero },
            lastRound = _ui.value.lastRound,
        )
        val firstIds = first.mapTo(HashSet()) { it.match.id }
        val known = feeds
        feeds = (items + next).filter { it.channels.isNotEmpty() }
            .associate { item ->
                val id = item.match.id
                id to ((if (id in firstIds) known[id] else null) ?: feedsFor(item))
            }
        // The cards re-read their feeds along with the looks.
        _looks.value++
        // After the rest is on screen: highlights not found yet (the YouTube feed; older ones may
        // need YouTube's full video list, a few seconds on a slow box).
        if (items.any { it.match.phase == Phase.FINISHED && it.highlights == null }) {
            val withHighlights = items.map { if (it.match.phase == Phase.FINISHED && it.highlights == null) finished(it.match) else it }
                .associateBy { it.match.id }
            _ui.value = _ui.value.let { u ->
                u.copy(today = u.today.map { withHighlights[it.match.id] ?: it })
            }
        }
        val todayIds = today.mapTo(HashSet()) { it.id }
        val round = repo.lastRound().filter { it.id !in todayIds }.map { finished(it) }
        _ui.value = _ui.value.copy(lastRound = round)
    }

    private suspend fun feedsFor(item: Item): List<Channel> {
        val catalog = graph.catalogRepository
        return item.channels
            .flatMap { mc -> catalog.variants(mc.channel).sortedByDescending { it.qualityRank } }
            .distinctBy { it.id }
            .filterNot { SD.containsMatchIn(it.qualityLabel) }
            .take(MAX_FEEDS)
    }

    private companion object {
        const val TAG = "AutoScan"
        /** About 1 s a feed: a minute of waiting at most. */
        const val MAX_FEEDS = 30
        const val SCAN_BUDGET_MILLIS = 60_000L
        /** Matches looked at by themselves from this long before kick-off: earlier the feeds aren't up yet. */
        const val AHEAD_MILLIS = 45 * 60_000L
        /** Test builds look at the whole day, so there's something to try between games. */
        const val DEBUG_WINDOW_MILLIS = 24 * 60 * 60_000L
        const val IDLE_MILLIS = 30_000L
        const val TABS_AHEAD_MILLIS = 15 * 60_000L
        val SD = Regex("\\bSD\\b", RegexOption.IGNORE_CASE)
    }

    private suspend fun finished(m: Match) = Item(m, highlights = repo.highlightsFor(m))
}
