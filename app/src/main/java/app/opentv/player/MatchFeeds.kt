/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.player

/**
 * The games on now, each with its feeds best first, handed to the player by the football home.
 * Channel up/down moves between the games ([PlaybackQueue] holds one entry per game); within a
 * game the player falls back, stall-switches and cycles (☰, Quality) along its ranking — across
 * channels — instead of one channel's qualities. Kept until another list is played from.
 */
object MatchFeeds {
    data class Game(
        val matchId: Long,
        /** "Inter - Milan". */
        val title: String,
        /** Best first; numbered by this order in the picker. */
        val feeds: List<Long>,
        /** As the football home shows them ("Lazio · FHD · DVS"). */
        val names: Map<Long, String> = emptyMap(),
    )

    @Volatile private var games: List<Game> = emptyList()
    /** Per game: the feed last watched, where coming back to it starts. */
    private val lead = java.util.concurrent.ConcurrentHashMap<Long, Long>()

    fun set(games: List<Game>) {
        this.games = games
        lead.clear()
        games.forEach { g -> g.feeds.firstOrNull()?.let { lead[g.matchId] = it } }
    }

    fun clear() {
        games = emptyList()
        lead.clear()
    }

    fun games(): List<Game> = games

    /** The game [channelId] is a feed of. */
    fun gameOf(channelId: Long): Game? = games.firstOrNull { channelId in it.feeds }

    fun indexOf(channelId: Long): Int = games.indexOfFirst { channelId in it.feeds }

    fun name(channelId: Long): String? = gameOf(channelId)?.names?.get(channelId)

    /** [channelId]'s 1-based place in its game's ranking, null when not a match feed. */
    fun place(channelId: Long): Int? = gameOf(channelId)?.feeds?.indexOf(channelId)?.takeIf { it >= 0 }?.plus(1)

    /** Its game's ranking with [channelId] first, when it is a match feed. */
    fun forFeed(channelId: Long): List<Long>? {
        val r = gameOf(channelId)?.feeds ?: return null
        return listOf(channelId) + r.filter { it != channelId }
    }

    /** [channelId] is playing: its game starts from it when zapped back to. */
    fun watched(channelId: Long) {
        gameOf(channelId)?.let { lead[it.matchId] = channelId }
    }

    /** Where [game] starts: the feed last watched, else its best. */
    fun leadOf(game: Game): Long? = lead[game.matchId] ?: game.feeds.firstOrNull()
}
