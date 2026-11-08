/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.repo

import android.content.Context
import android.util.Log
import androidx.sqlite.db.SimpleSQLiteQuery
import app.opentv.data.db.OpenTvDatabase
import app.opentv.data.model.Programme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Title search over the guide, for the Search screen.
 *
 * A plain `title LIKE '%x%'` reads the whole programmes table (120k rows on a big guide: 0.4–1.6 s
 * a search on a Fire TV). An FTS4 index of the titles from now to [DAYS] days ahead answers in a
 * couple of milliseconds. It matches at the start of words ("juve" finds "Juventus", not "Mirai"
 * for "rai"), the way people type a title anyway.
 *
 * The index is a table of its own, outside Room's schema (Room checks only its entities), so no
 * migration: it is created on first use and rebuilt after each guide download, in the background
 * (a couple of seconds). Rows point at programmes by id; a search joins back to the programmes
 * table, so a row left over from before a refresh simply matches nothing.
 */
class GuideSearchIndex(private val context: Context, private val database: OpenTvDatabase) {
    private val lock = Mutex()
    private val prefs by lazy { context.getSharedPreferences("guide_search", Context.MODE_PRIVATE) }

    /** Rebuilds the index when it is missing or older than [MAX_AGE_MILLIS]. */
    suspend fun ensureFresh() {
        val builtAt = prefs.getLong(KEY_BUILT_AT, 0L)
        if (System.currentTimeMillis() - builtAt > MAX_AGE_MILLIS) rebuild()
    }

    /** Indexes every programme still to end within the next [DAYS] days. */
    suspend fun rebuild() = withContext(Dispatchers.IO) {
        lock.withLock {
            runCatching {
                val started = System.currentTimeMillis()
                val until = started + DAYS * DAY_MILLIS
                val db = database.openHelper.writableDatabase
                db.execSQL(CREATE)
                database.runInTransaction {
                    db.execSQL("DELETE FROM $TABLE")
                    db.execSQL(
                        "INSERT INTO $TABLE(docid, title) SELECT id, title FROM programmes " +
                            "WHERE endUtcMillis > $started AND startUtcMillis < $until",
                    )
                }
                prefs.edit().putLong(KEY_BUILT_AT, started).apply()
                Log.i(TAG, "index built in ${System.currentTimeMillis() - started} ms")
            }.onFailure { Log.w(TAG, "rebuild", it) }
        }
    }

    /**
     * Programmes whose title has words starting with every word of [query], not yet over,
     * soonest first. Empty if the index isn't there yet.
     */
    suspend fun search(query: String, nowUtcMillis: Long, limit: Int = 200): List<Programme> = withContext(Dispatchers.IO) {
        val match = matchExpression(query) ?: return@withContext emptyList()
        runCatching {
            database.openHelper.writableDatabase.execSQL(CREATE)
            database.programmes().query(
                SimpleSQLiteQuery(
                    "SELECT p.* FROM $TABLE JOIN programmes p ON p.id = $TABLE.docid " +
                        "WHERE $TABLE MATCH ? AND p.endUtcMillis > ? ORDER BY p.startUtcMillis LIMIT ?",
                    arrayOf(match, nowUtcMillis, limit),
                ),
            )
        }.getOrElse { Log.w(TAG, "search", it); emptyList() }
    }

    companion object {
        private const val TAG = "GuideSearch"
        private const val TABLE = "programme_fts"
        private const val CREATE = "CREATE VIRTUAL TABLE IF NOT EXISTS $TABLE USING fts4(title, tokenize=unicode61)"
        private const val KEY_BUILT_AT = "built_at"
        const val DAYS = 2L
        private const val DAY_MILLIS = 24L * 3600 * 1000
        private const val MAX_AGE_MILLIS = 12L * 3600 * 1000

        /** "Milan-Inter" → `milan* inter*`: every word, as a prefix. Null when nothing is left. */
        internal fun matchExpression(query: String): String? {
            val words = query.lowercase()
                .split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.isNotEmpty() }
            if (words.isEmpty()) return null
            return words.joinToString(" ") { "$it*" }
        }
    }
}
