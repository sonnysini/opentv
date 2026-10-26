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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FootballViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: FootballRepository = ServiceLocator.get(app).footballRepository
    private val youTube = ServiceLocator.get(app).youTubeStreams

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

    private suspend fun finished(m: Match) =
        Item(m, highlights = highlights[m.id] ?: repo.highlightsFor(m)?.also { highlights[m.id] = it })
}
