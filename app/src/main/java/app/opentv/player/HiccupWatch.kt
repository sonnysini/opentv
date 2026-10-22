/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.player

/**
 * Notices a live stream that keeps stuttering: short pauses that each recover on their own, too
 * brief for [StallWatch], but frequent enough to spoil watching. A pause counts once it lasts
 * [minPauseMillis]; [limit] of them within [windowMillis] is a stream worth leaving.
 *
 * Fed the same samples as [StallWatch]. Pure so the rules can be unit-tested.
 */
class HiccupWatch(
    val limit: Int,
    private val windowMillis: Long = WINDOW_MILLIS,
    private val minPauseMillis: Long = MIN_PAUSE_MILLIS,
) {
    private val pauses = ArrayDeque<Long>()
    private var lastProgressAt = -1L
    private var lastPosition = 0L
    private var lastFrames = 0L
    private var counted = false

    /** Starts over: a new tune, a seek, or resuming from pause. Earlier stutters are forgotten too. */
    fun reset() {
        lastProgressAt = -1L
        counted = false
        pauses.clear()
    }

    /** Returns true when [limit] pauses have happened within the last [windowMillis]. */
    fun sample(nowMillis: Long, positionMillis: Long, renderedFrames: Long, hasVideo: Boolean): Boolean {
        val moved = positionMillis != lastPosition && (!hasVideo || renderedFrames != lastFrames)
        if (lastProgressAt < 0 || moved) {
            lastProgressAt = nowMillis
            lastPosition = positionMillis
            lastFrames = renderedFrames
            counted = false
        } else if (!counted && nowMillis - lastProgressAt >= minPauseMillis) {
            counted = true
            pauses.addLast(nowMillis)
        }
        while (pauses.isNotEmpty() && nowMillis - pauses.first() > windowMillis) pauses.removeFirst()
        return limit > 0 && pauses.size >= limit
    }

    companion object {
        const val WINDOW_MILLIS = 60_000L
        const val MIN_PAUSE_MILLIS = 750L
    }
}
