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
import app.opentv.player.MatchFeeds
import app.opentv.player.ScoreboardReader
import app.opentv.player.StreamProbe
import kotlinx.coroutines.Job
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
    private var scanJob: Job? = null

    fun autoPlayed() {
        _autoPlay.value = null
    }

    fun cancelScan() {
        scanJob?.cancel()
        _scan.value = null
    }

    /**
     * "Watch automatically": every feed of [item]'s channels looked at in turn (one at a time —
     * the account allows one connection), ranked by [AutoScan], then the best one plays with the
     * rest as its fallback order.
     */
    fun autoWatch(item: Item) {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            val m = item.match
            val catalog = graph.catalogRepository
            val feeds = item.channels
                .flatMap { mc -> catalog.variants(mc.channel).sortedByDescending { it.qualityRank } }
                .distinctBy { it.id }
                .filterNot { SD.containsMatchIn(it.qualityLabel) }
                .take(MAX_FEEDS)
            if (feeds.isEmpty()) return@launch
            val title = "${m.home.name} - ${m.away.name}"
            var rows = feeds.map { ScanRow(it.id, listOf(it.shownName, it.qualityLabel).filter(String::isNotBlank).joinToString(" · ")) }
            _scan.value = Scan(title, rows, 0)
            val prober = StreamProbe(graph.streamingHttpClient, getApplication<Application>().cacheDir)
            val reader = ScoreboardReader(getApplication())
            val results = ArrayList<Pair<Channel, AutoScan.Probe?>>()
            val deadline = System.currentTimeMillis() + SCAN_BUDGET_MILLIS
            try {
                for ((i, feed) in feeds.withIndex()) {
                    if (System.currentTimeMillis() > deadline) break
                    val source = graph.sourceRepository.byId(feed.sourceId)
                    val url = catalog.resolvePlaybackUrl(feed, source)
                    val r = prober.probe(url, source?.userAgent ?: "OpenTV/0.1 (Android)")
                    val text = r?.corner?.let { reader.read(it) }
                    r?.corner?.recycle()
                    val board = text?.let { AutoScan.parseBoard(it) }
                    val p = r?.let { AutoScan.Probe(it.height, it.fps, it.audioChannels, it.millis, board) }
                    results += feed to p
                    android.util.Log.i(TAG, "${feed.shownName} ${feed.qualityLabel}: ${p?.let(::detail) ?: "dead"} | ocr=${text?.take(80)} | board=$board")
                    val verdict = when {
                        p == null -> AutoScan.Verdict.DEAD
                        board != null -> AutoScan.gameCheck(board, m.home.name, m.away.name) ?: AutoScan.Verdict.OK
                        else -> AutoScan.Verdict.OK
                    }
                    rows = rows.toMutableList().also { it[i] = it[i].copy(detail = p?.let(::detail), board = board?.let(::boardText), verdict = verdict) }
                    _scan.value = Scan(title, rows, i + 1)
                }
            } finally {
                reader.close()
            }
            val ranked = AutoScan.rank(
                results, m.home.name, m.away.name,
                channel = { it.groupKey.ifEmpty { "#${it.id}" } },
            )
            val byId = rows.associateBy { it.id }
            val unlooked = feeds.drop(results.size)
            rows = ranked.mapIndexed { i, r -> byId.getValue(r.item.id).copy(verdict = r.verdict, place = i + 1) } +
                unlooked.map { byId.getValue(it.id) }
            _scan.value = Scan(title, rows, feeds.size, finished = true)
            ranked.forEachIndexed { i, r ->
                android.util.Log.i(TAG, "#${i + 1} ${r.item.shownName} ${r.item.qualityLabel}: ${r.verdict} score=${r.score}")
            }
            val good = setOf(AutoScan.Verdict.OK, AutoScan.Verdict.CONFIRMED)
            val order = (ranked.filter { it.verdict in good }.map { it.item } + unlooked).ifEmpty { feeds }
            delay(RANKING_SHOWN_MILLIS)
            MatchFeeds.set(order.map { it.id })
            _scan.value = null
            _autoPlay.value = order.first()
        }
    }

    private fun detail(p: AutoScan.Probe) = listOfNotNull(
        "${p.height}p",
        p.fps?.let { "$it fps" },
        p.audioChannels?.takeIf { it >= 6 }?.let { "5.1" },
        String.format(java.util.Locale.ITALY, "%.1f s", p.millis / 1000.0),
    ).joinToString(" · ")

    private fun boardText(b: AutoScan.Board) = b.codes.take(2).joinToString(" ")

    // Highlights found stay found: the feed only keeps the latest uploads.
    private val highlights = HashMap<Long, String>()

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
        val items = today.map { m ->
            when (m.phase) {
                Phase.FINISHED -> finished(m)
                Phase.OFF -> Item(m)
                else -> Item(m, channels = repo.channelsFor(m))
            }
        }
        val next = upcoming.map { Item(it, channels = repo.channelsFor(it)) }
        val live = items.filter { it.match.phase == Phase.LIVE }
        val hero = live.firstOrNull()
            ?: items.firstOrNull { it.match.phase == Phase.SCHEDULED }
            ?: next.firstOrNull()
        _ui.value = UiState(
            loading = false,
            hero = hero,
            live = live.filter { it !== hero },
            // Today's row: still to come first, then the finished ones latest first.
            today = items.filter { it !== hero && it.match.phase == Phase.SCHEDULED } +
                items.filter { it.match.phase == Phase.FINISHED }.sortedByDescending { it.match.kickoffUtcMillis },
            upcoming = next.filter { it !== hero },
            lastRound = _ui.value.lastRound,
        )
        // After the rest is on screen: older highlights may need YouTube's full video list,
        // a few seconds on a slow box.
        val todayIds = today.mapTo(HashSet()) { it.id }
        val round = repo.lastRound().filter { it.id !in todayIds }.map { finished(it) }
        _ui.value = _ui.value.copy(lastRound = round)
    }

    private companion object {
        const val TAG = "AutoScan"
        /** About 1 s a feed: a minute of waiting at most. */
        const val MAX_FEEDS = 30
        const val SCAN_BUDGET_MILLIS = 60_000L
        const val RANKING_SHOWN_MILLIS = 2_500L
        val SD = Regex("\\bSD\\b", RegexOption.IGNORE_CASE)
    }

    private suspend fun finished(m: Match) =
        Item(m, highlights = highlights[m.id] ?: repo.highlightsFor(m)?.also { highlights[m.id] = it })
}
