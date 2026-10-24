/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.core

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Picks the screen refresh rate that fits the video, the way Kodi and Vimu do: a 25 fps channel on a
 * 59.94 Hz screen repeats frames unevenly and judders, the same channel at 50 Hz moves smoothly.
 * Pure so the rule can be unit-tested; the player applies the answer to its window.
 */
object RefreshRate {

    data class Mode(val id: Int, val width: Int, val height: Int, val hz: Float)

    /**
     * The mode, at the screen's current resolution, whose refresh rate is a whole multiple of
     * [fps] (the highest such rate, so 25 fps goes to 50 Hz rather than 25 Hz); null when the
     * current mode already fits or nothing does.
     */
    fun pick(modes: List<Mode>, current: Mode, fps: Float): Mode? {
        if (fps <= 0f) return null
        if (fits(current.hz, fps)) return null
        return modes
            .filter { it.width == current.width && it.height == current.height && fits(it.hz, fps) }
            .maxByOrNull { it.hz }
    }

    /**
     * The mode the app sits in between channels: one that fits 25 fps (50 Hz) at the screen's current
     * resolution, so Italian channels, nearly all 25/50 fps, start without a switch (and the second
     * of black it brings). The current mode when it already fits; null when the TV has no such mode.
     */
    fun home(modes: List<Mode>, current: Mode): Mode? =
        if (fits(current.hz, HOME_FPS)) current else pick(modes, current, HOME_FPS)

    private const val HOME_FPS = 25f

    /** [hz] shows every frame of [fps] the same number of times (60.000004 Hz counts as 60). */
    fun fits(hz: Float, fps: Float): Boolean {
        val ratio = hz / fps
        val whole = ratio.roundToInt()
        return whole >= 1 && abs(ratio - whole) / whole < 0.0005f
    }
}
