/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.player

/**
 * A match's feeds ranked by "Watch automatically", handed to the player: it plays the first and
 * falls back, stall-switches and cycles (☰) along the ranking — across channels — instead of the
 * one channel's qualities. Taken once, by the player opening the first feed.
 */
object MatchFeeds {
    @Volatile private var ranked: List<Long> = emptyList()

    fun set(ids: List<Long>) {
        ranked = ids
    }

    /** The ranking, when [channelId] is the feed it starts with; clears it either way. */
    fun take(channelId: Long): List<Long>? {
        val r = ranked
        ranked = emptyList()
        return r.takeIf { it.firstOrNull() == channelId }
    }
}
