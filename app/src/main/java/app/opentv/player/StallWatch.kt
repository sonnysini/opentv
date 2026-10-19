/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.player

/**
 * Notices a live stream that has stopped moving after it started: stuck buffering, an error the
 * silent restart can't clear, or a frozen picture while the player still claims to be playing.
 *
 * Fed a sample every fraction of a second; progress means the playback position moved and, when
 * the stream has video, new frames were rendered (a frozen picture often keeps the audio clock
 * running). No progress for [limitMillis] is a stall. Pure so the rules can be unit-tested.
 */
class StallWatch(var limitMillis: Long) {
    private var lastProgressAt = -1L
    private var lastPosition = 0L
    private var lastFrames = 0L

    /** Starts the clock over: a new tune, a seek, or resuming from pause. */
    fun reset() {
        lastProgressAt = -1L
    }

    /** Returns true when the stream hasn't progressed for [limitMillis]. */
    fun sample(nowMillis: Long, positionMillis: Long, renderedFrames: Long, hasVideo: Boolean): Boolean {
        if (lastProgressAt < 0) {
            mark(nowMillis, positionMillis, renderedFrames)
            return false
        }
        val moved = positionMillis != lastPosition
        val drew = !hasVideo || renderedFrames != lastFrames
        if (moved && drew) {
            mark(nowMillis, positionMillis, renderedFrames)
            return false
        }
        return nowMillis - lastProgressAt >= limitMillis
    }

    private fun mark(nowMillis: Long, positionMillis: Long, renderedFrames: Long) {
        lastProgressAt = nowMillis
        lastPosition = positionMillis
        lastFrames = renderedFrames
    }

    companion object {
        const val SAMPLE_MILLIS = 250L

        /**
         * The variant to move to when [currentId] stalls: the next one after it in [order], wrapping
         * round, so a feed that only hiccuped comes back into rotation later. Null when there is
         * nothing else to try.
         */
        fun <T> nextAfter(order: List<T>, currentId: Long?, id: (T) -> Long): T? {
            val index = order.indexOfFirst { id(it) == currentId }
            val rotated = if (index < 0) order else order.drop(index + 1) + order.take(index)
            return rotated.firstOrNull { id(it) != currentId }
        }
    }
}
