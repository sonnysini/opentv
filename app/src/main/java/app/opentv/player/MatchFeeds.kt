/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.player

/**
 * A match's feeds, best first, handed to the player by the football home: playing one of them, the
 * player falls back, stall-switches and cycles (☰) along this ranking — across channels — instead
 * of the one channel's qualities. Kept until another list is played from, so zapping between the
 * match's feeds stays in it.
 */
object MatchFeeds {
    @Volatile private var ranked: List<Long> = emptyList()

    fun set(ids: List<Long>) {
        ranked = ids
    }

    fun clear() {
        ranked = emptyList()
    }

    /** The whole ranking, best first. */
    fun ranking(): List<Long> = ranked

    /** [channelId]'s 1-based place in the ranking, null when not one of the match's feeds. */
    fun place(channelId: Long): Int? = ranked.indexOf(channelId).takeIf { it >= 0 }?.plus(1)

    /** The ranking with [channelId] first, when it is one of the match's feeds. */
    fun forFeed(channelId: Long): List<Long>? {
        val r = ranked
        if (channelId !in r) return null
        return listOf(channelId) + r.filter { it != channelId }
    }
}
