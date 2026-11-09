/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The v15 indexes: the 14 → 15 migration leaves exactly the schema Room expects (Room checks the
 * indexes too, and refuses to open on a mismatch), and the browse/search queries do use them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PerfIndexesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "perf-indexes-test.db"

    @Before fun clean() { context.deleteDatabase(name) }
    @After fun cleanUp() { context.deleteDatabase(name) }

    private fun open(): OpenTvDatabase =
        Room.databaseBuilder(context, OpenTvDatabase::class.java, name)
            .addMigrations(OpenTvDatabase.MIGRATION_14_15)
            .allowMainThreadQueries()
            .build()

    @Test
    fun `migration from 14 rebuilds the indexes Room expects`() {
        // A v15 database, taken back to what v14 had: the same tables without the new indexes.
        open().apply { openHelper.writableDatabase; close() }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            for (index in NEW_INDEXES) db.execSQL("DROP INDEX `$index`")
            db.version = 14
        }
        // Room migrates, then validates every table (indexes included) against the entities.
        val db = open()
        val indexes = db.openHelper.writableDatabase
            .query("SELECT name FROM sqlite_master WHERE type = 'index'")
            .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        assertThat(indexes).containsAtLeastElementsIn(NEW_INDEXES)
        db.close()
    }

    @Test
    fun `category browse and name search use the new indexes`() {
        val db = open()
        fun plan(sql: String, vararg args: Any): String =
            db.openHelper.writableDatabase.query("EXPLAIN QUERY PLAN $sql", args).use { c ->
                buildString { while (c.moveToNext()) append(c.getString(c.columnCount - 1)).append('\n') }
            }
        assertThat(plan("SELECT * FROM movies WHERE categoryId = ? ORDER BY addedMillis DESC, name LIMIT 3000", "1"))
            .contains("index_movies_categoryId")
        assertThat(plan("SELECT * FROM series WHERE categoryId = ? ORDER BY addedMillis DESC, name LIMIT 3000", "1"))
            .contains("index_series_categoryId")
        assertThat(plan("SELECT * FROM channels WHERE hidden = 0 AND categoryId IN (?, ?) ORDER BY sortIndex, displayName", "1", "2"))
            .contains("index_channels_categoryId")
        assertThat(plan("SELECT * FROM movies WHERE name LIKE '%' || ? || '%' ORDER BY name LIMIT 200", "ab"))
            .contains("index_movies_name")
        db.close()
    }

    private companion object {
        val NEW_INDEXES = listOf(
            "index_channels_categoryId",
            "index_movies_categoryId",
            "index_movies_name",
            "index_series_categoryId",
            "index_series_name",
        )
    }
}
