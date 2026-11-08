/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.opentv.core.ServiceLocator
import app.opentv.data.model.Channel
import app.opentv.data.model.Movie
import app.opentv.data.model.Programme
import app.opentv.data.model.Recording
import app.opentv.data.model.RecordingStatus
import app.opentv.data.model.Series
import app.opentv.data.model.TimeShift
import app.opentv.data.repo.FootballRepository
import app.opentv.ui.football.FootballViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.Normalizer

/**
 * The Search screen: one query, a row of results per tab in the menu's order — live TV (channels,
 * then guide programmes from now on), football, movies, series, recordings. Empty rows are left out.
 */
class SearchViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = ServiceLocator.get(app)

    /** Something on live TV: a channel by its name, or a programme in the guide. */
    sealed interface LiveHit {
        val key: String

        data class OnChannel(val channel: Channel, val now: Programme?) : LiveHit {
            override val key get() = "c" + (channel.groupKey.ifEmpty { channel.id.toString() })
        }

        data class InGuide(val channel: Channel, val programme: Programme, val booked: Boolean) : LiveHit {
            override val key get() = "p${channel.id}:${programme.startUtcMillis}"
        }
    }

    data class Results(
        val query: String = "",
        /** False until the local rows (all but football) are in. */
        val done: Boolean = false,
        val live: List<LiveHit> = emptyList(),
        val matches: List<FootballViewModel.Item> = emptyList(),
        val movies: List<Movie> = emptyList(),
        val series: List<Series> = emptyList(),
        val recordings: List<Recording> = emptyList(),
    ) {
        val isEmpty get() = live.isEmpty() && matches.isEmpty() && movies.isEmpty() && series.isEmpty() && recordings.isEmpty()
    }

    private val _results = MutableStateFlow(Results())
    val results: StateFlow<Results> = _results.asStateFlow()

    private var job: Job? = null

    init {
        // An index older than half a day (or never built) is rebuilt while the keyboard is up.
        viewModelScope.launch { graph.guideSearchIndex.ensureFresh() }
    }

    /** Searches for [text] after a short pause; [now] skips the pause (the keyboard's Search key). */
    fun search(text: String, now: Boolean = false) {
        val q = text.trim()
        if (q == _results.value.query && _results.value.done) return
        job?.cancel()
        if (q.length < MIN_LENGTH) {
            _results.value = Results(query = q, done = true)
            return
        }
        job = viewModelScope.launch {
            if (!now) delay(DEBOUNCE_MILLIS)
            val local = withContext(Dispatchers.IO) { local(q) }
            _results.value = local
            val matches = withTimeoutOrNull(FOOTBALL_TIMEOUT_MILLIS) { matches(q) }.orEmpty()
            if (matches.isNotEmpty()) _results.value = local.copy(matches = matches)
        }
    }

    /** The same query again, now: after booking or a reminder, so the REC mark shows. */
    fun refresh() {
        val q = _results.value.query
        _results.value = _results.value.copy(done = false)
        search(q, now = true)
    }

    private suspend fun local(q: String): Results {
        val now = System.currentTimeMillis()
        val channels = runCatching { graph.catalogRepository.searchChannels(q).first() }.getOrElse { emptyList() }
            // One per logical channel, its best quality (what plays, as in the guide).
            .groupBy { it.groupKey.ifEmpty { "#${it.id}" } }
            .map { (_, variants) -> variants.maxBy { it.qualityRank } }
            .take(MAX_CHANNELS)
        val programmes = graph.database.programmes()
        val onChannels = channels.map { c ->
            val on = c.epgCandidates.firstNotNullOfOrNull { id -> runCatching { programmes.nowOn(id, now) }.getOrNull() }
            LiveHit.OnChannel(c, on)
        }
        val movies = runCatching { graph.catalogRepository.searchMovies(q).first() }.getOrElse { emptyList() }.take(MAX_VOD)
        val series = runCatching { graph.catalogRepository.searchSeries(q).first() }.getOrElse { emptyList() }.take(MAX_VOD)
        val recordings = runCatching { graph.recordingRepository.all() }.getOrElse { emptyList() }
            .filter { it.status == RecordingStatus.COMPLETED || it.status == RecordingStatus.RECORDING }
            .filter { fold(it.title).contains(fold(q)) || fold(it.channelName).contains(fold(q)) }
            .sortedByDescending { it.startedAtMillis }
            .take(MAX_VOD)
        return Results(
            query = q,
            done = true,
            live = onChannels + guide(q, now),
            movies = movies,
            series = series,
            recordings = recordings,
        )
    }

    /** Guide programmes from now on whose title matches, each on the channel that shows it. */
    private suspend fun guide(q: String, now: Long): List<LiveHit.InGuide> {
        val hits = graph.guideSearchIndex.search(q, now)
        if (hits.isEmpty()) return emptyList()
        val ids = hits.map { it.epgChannelId }.distinct()
        val channels = ids.chunked(ID_CHUNK).flatMap { part ->
            runCatching { graph.database.channels().withGuideIds(part) }.getOrElse { emptyList() }
        }
        // The channel each guide id is really the guide of (a +1 copy is shifted: not this one),
        // favourites first, then the best quality.
        val byGuide = channels
            .filter { c -> c.matchedEpgId?.let { TimeShift.decode(it) } == null }
            .groupBy { c -> c.epgOverrideId ?: c.matchedEpgId ?: c.epgChannelId }
            .mapValues { (_, list) -> list.sortedWith(compareByDescending<Channel> { it.favourite }.thenByDescending { it.qualityRank }).first() }
        val booked = runCatching { graph.recordingRepository.all() }.getOrElse { emptyList() }
            .filter { it.status == RecordingStatus.SCHEDULED }
            .mapTo(HashSet()) { it.channelId to it.scheduledStartMillis }
        return hits
            .mapNotNull { p -> byGuide[p.epgChannelId]?.let { c -> c to p } }
            // The same programme from two guides of one channel: once.
            .distinctBy { (c, p) -> Triple(c.groupKey.ifEmpty { c.id.toString() }, p.startUtcMillis, p.title) }
            // On now first, then the soonest.
            .sortedWith(compareByDescending<Pair<Channel, Programme>> { (_, p) -> p.startUtcMillis <= now }.thenBy { (_, p) -> p.startUtcMillis })
            .take(MAX_PROGRAMMES)
            .map { (c, p) -> LiveHit.InGuide(c, p, (c.id to p.startUtcMillis) in booked) }
    }

    /** Today's and the coming week's games with a team (or the goal show) named like [q]. */
    private suspend fun matches(q: String): List<FootballViewModel.Item> = withContext(Dispatchers.IO) {
        val repo = graph.footballRepository
        val all = runCatching { repo.today() + repo.upcoming() }.getOrElse { emptyList() }
        val needle = fold(q)
        all.distinctBy { it.id }
            .filter { m -> fold(m.home.name).contains(needle) || (!m.goals && fold(m.away.name).contains(needle)) }
            .filter { it.phase != FootballRepository.Phase.OFF }
            .sortedBy { it.kickoffUtcMillis }
            .take(MAX_VOD)
            .map { m ->
                val highlights = if (m.phase == FootballRepository.Phase.FINISHED) runCatching { repo.knownHighlights(m) }.getOrNull() else null
                FootballViewModel.Item(m, highlights = highlights)
            }
    }

    companion object {
        private const val MIN_LENGTH = 2
        private const val DEBOUNCE_MILLIS = 400L
        private const val FOOTBALL_TIMEOUT_MILLIS = 6_000L
        private const val MAX_CHANNELS = 20
        private const val MAX_PROGRAMMES = 40
        private const val MAX_VOD = 30
        private const val ID_CHUNK = 300

        /** Lower case, accents off: "Bodø/Glimt" and "bodo" meet. */
        internal fun fold(s: String): String =
            Normalizer.normalize(s.lowercase().replace('ø', 'o'), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    }
}
