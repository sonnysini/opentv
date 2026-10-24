/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.player

import android.view.Display
import android.view.Window
import app.opentv.core.RefreshRate

/** Applies [RefreshRate]'s answers to the app's window. */
object DisplayModes {

    fun modes(display: Display): List<RefreshRate.Mode> =
        display.supportedModes.map { RefreshRate.Mode(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate) }

    /**
     * The mode id the app keeps outside a channel that needs another rate: 50 Hz when [enabled] and
     * the TV offers it, else 0 (the system's choice).
     */
    fun homeId(window: Window, enabled: Boolean): Int {
        if (!enabled) return 0
        @Suppress("DEPRECATION")
        val display = window.decorView.display ?: window.windowManager.defaultDisplay ?: return 0
        val modes = modes(display)
        val current = modes.firstOrNull { it.id == display.mode.modeId } ?: return 0
        return RefreshRate.home(modes, current)?.id ?: 0
    }

    /** 0 lets the system choose again. */
    fun set(window: Window, modeId: Int) {
        if (window.attributes.preferredDisplayModeId == modeId) return
        window.attributes = window.attributes.apply { preferredDisplayModeId = modeId }
    }
}
