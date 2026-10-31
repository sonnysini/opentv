/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.launcher

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import app.opentv.core.ServiceLocator
import app.opentv.data.model.Channel
import app.opentv.data.model.Programme
import app.opentv.data.model.TimeShift
import app.opentv.data.model.shownName
import app.opentv.data.repo.FootballRepository
import app.opentv.data.repo.FootballRepository.Phase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Read-only rows for a home-screen launcher signed with the same key (the provider is guarded by a
 * signature permission): the channels played lately, the favourites with what is on now, and the
 * Serie A matches of the Football tab. The launcher opens them with MainActivity's extras.
 *
 * - `content://<applicationId>.launcher/channels/recent`
 * - `content://<applicationId>.launcher/channels/favourites`
 * - `content://<applicationId>.launcher/football`
 */
class LauncherProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val context = context ?: return null
        val graph = ServiceLocator.get(context)
        val segments = uri.pathSegments
        return runCatching {
            runBlocking {
                when (segments.firstOrNull()) {
                    "channels" -> {
                        val dao = graph.database.channels()
                        val channels = when (segments.getOrNull(1)) {
                            "recent" -> graph.settings.recentChannelIds.mapNotNull { dao.byId(it) }
                            // One row per logical channel: favourite quality variants share a groupKey.
                            "favourites" -> dao.observeFavourites().first()
                                .distinctBy { it.groupKey.ifEmpty { "#${it.id}" } }
                            else -> return@runBlocking null
                        }.filter { !it.hidden }
                        channelCursor(channels, limit(uri, 20))
                    }
                    "football" -> withTimeoutOrNull(FOOTBALL_TIMEOUT_MILLIS) { footballCursor(graph.footballRepository) }
                    else -> null
                }
            }
        }.onFailure { Log.w(TAG, "query $uri", it) }.getOrNull()
    }

    private fun limit(uri: Uri, default: Int) =
        uri.getQueryParameter("limit")?.toIntOrNull()?.coerceIn(1, 100) ?: default

    private suspend fun channelCursor(channels: List<Channel>, limit: Int): Cursor {
        val programmes = ServiceLocator.get(context!!).database.programmes()
        val now = System.currentTimeMillis()
        val cursor = MatrixCursor(CHANNEL_COLUMNS)
        for (c in channels.take(limit)) {
            val on = nowOn(c, now) { id, at -> programmes.nowOn(id, at) }
            cursor.addRow(
                arrayOf(
                    c.id, c.shownName, c.logoUrl ?: c.guideLogoUrl, c.number, c.qualityLabel,
                    on?.title, on?.startUtcMillis, on?.endUtcMillis, on?.iconUrl,
                ),
            )
        }
        return cursor
    }

    /** What is on [c] at [now]: the first guide id with a programme, a timeshift read off its base. */
    private suspend fun nowOn(c: Channel, now: Long, lookUp: suspend (String, Long) -> Programme?): Programme? {
        for (id in c.epgCandidates) {
            val shift = TimeShift.decode(id)
            val p = lookUp(id, now) ?: shift?.let { (base, offset) ->
                lookUp(base, now - offset)?.let {
                    it.copy(startUtcMillis = it.startUtcMillis + offset, endUtcMillis = it.endUtcMillis + offset)
                }
            }
            if (p != null) return p
        }
        return null
    }

    private suspend fun footballCursor(repo: FootballRepository): Cursor {
        // Serie A and the Italian teams' cup games: a launcher row has no room for a cup's thirty.
        val italian = repo.italianTeams()
        fun ours(m: FootballRepository.Match) =
            m.league == FootballRepository.SERIE_A || m.home.id in italian || m.away.id in italian
        val today = repo.today().filter(::ours)
        val upcoming = repo.upcoming().filter(::ours).take(UPCOMING)
        val todayIds = today.mapTo(HashSet()) { it.id }
        val round = if (today.any { it.phase != Phase.FINISHED }) emptyList()
        else repo.lastRound().filter { it.id !in todayIds }.take(ROUND)
        val channels = repo.channelsFor(today.filter { it.phase == Phase.SCHEDULED || it.phase == Phase.LIVE } + upcoming)
        val cursor = MatrixCursor(FOOTBALL_COLUMNS)
        fun add(m: FootballRepository.Match, section: String, highlights: String?) {
            val names = channels[m.id].orEmpty().filter { !it.spare }.map { it.name }.distinct()
            cursor.addRow(
                arrayOf(
                    m.id, section, m.phase.name, m.kickoffUtcMillis, m.minute, m.round,
                    // No score: a game is watched later too.
                    m.home.name, m.home.crestUrl, null, m.away.name, m.away.crestUrl, null,
                    names.joinToString("|"), highlights,
                ),
            )
        }
        for (m in today.filter { it.phase == Phase.LIVE }) add(m, "live", null)
        for (m in today.filter { it.phase == Phase.SCHEDULED }) add(m, "today", null)
        for (m in today.filter { it.phase == Phase.FINISHED }.sortedByDescending { it.kickoffUtcMillis }) {
            add(m, "finished", repo.knownHighlights(m) ?: withTimeoutOrNull(HIGHLIGHTS_TIMEOUT_MILLIS) { repo.highlightsFor(m) })
        }
        for (m in upcoming) add(m, "upcoming", null)
        for (m in round) add(m, "finished", repo.knownHighlights(m) ?: withTimeoutOrNull(HIGHLIGHTS_TIMEOUT_MILLIS) { repo.highlightsFor(m) })
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private companion object {
        const val TAG = "LauncherProvider"
        const val FOOTBALL_TIMEOUT_MILLIS = 15_000L
        const val HIGHLIGHTS_TIMEOUT_MILLIS = 3_000L
        const val UPCOMING = 6
        const val ROUND = 10

        val CHANNEL_COLUMNS = arrayOf(
            "id", "name", "logo", "number", "quality",
            "now_title", "now_start", "now_end", "now_icon",
        )

        /** `section`: live | today | finished | upcoming. `channels`: names, `|`-separated. */
        val FOOTBALL_COLUMNS = arrayOf(
            "id", "section", "phase", "kickoff", "minute", "round",
            "home", "home_crest", "home_score", "away", "away_crest", "away_score",
            "channels", "highlights",
        )
    }
}
