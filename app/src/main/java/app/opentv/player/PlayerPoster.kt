/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.player

import java.io.File

/**
 * A picture for the player to hold over the screen until a channel's first frame: the football
 * scan grows the best feed's last picture to the whole screen, the player opens on that same
 * picture and the game takes its place as soon as it plays. Taken once, by that channel only.
 */
object PlayerPoster {
    private class Poster(val channelId: Long, val file: File, val at: Long)

    @Volatile private var poster: Poster? = null

    fun set(channelId: Long, file: File?) {
        poster = file?.takeIf { it.exists() }?.let { Poster(channelId, it, System.currentTimeMillis()) }
    }

    /** The picture for [channelId] set just now, once; null for any other. */
    fun take(channelId: Long?): File? {
        val p = poster ?: return null
        poster = null
        return p.file.takeIf { p.channelId == channelId && System.currentTimeMillis() - p.at < FRESH_MILLIS }
    }

    private const val FRESH_MILLIS = 10_000L
}
