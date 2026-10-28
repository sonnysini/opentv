/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A one-shot "open Football" request from outside the app (a home-screen launcher's match card),
 * the football twin of [PlayRequests]: the nav graph goes back to the home, the home switches to
 * the Football tab, and the Football screen acts on [matchId] once its matches are loaded.
 */
object FootballRequests {
    enum class Action { OPEN, WATCH, HIGHLIGHTS }

    data class Request(
        val action: Action,
        /** FotMob match id; 0 to just open the tab. */
        val matchId: Long = 0L,
        /** YouTube id of the highlights, when the caller already knows it. */
        val highlightsId: String? = null,
        val requestedAtMillis: Long = System.currentTimeMillis(),
    )

    private val _request = MutableStateFlow<Request?>(null)
    val request: StateFlow<Request?> = _request.asStateFlow()

    fun request(r: Request) {
        _request.value = r
    }

    fun consume() {
        _request.value = null
    }
}
