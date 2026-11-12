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
import app.opentv.data.model.Movie
import app.opentv.data.model.VodLink
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v16: every category of a title, read back in the provider's order; the 15 → 16 migration. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class VodLinksTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "vod-links-test.db"

    @Before fun clean() { context.deleteDatabase(name) }
    @After fun cleanUp() { context.deleteDatabase(name) }

    private fun open(): OpenTvDatabase =
        Room.databaseBuilder(context, OpenTvDatabase::class.java, name)
            .addMigrations(OpenTvDatabase.MIGRATION_15_16)
            .allowMainThreadQueries()
            .build()

    private fun movie(id: String, category: String) = Movie(
        sourceId = 1, streamId = id, name = "Film $id", categoryId = category, posterUrl = null, rating = null,
        year = null, plot = null, durationSeconds = null, containerExtension = "mkv", streamUrl = "http://x/$id.mkv",
    )

    @Test
    fun `a film shows in every category it is listed in, in the provider's order`() = runBlocking {
        val db = open()
        db.movies().upsertAll(listOf(movie("a", "adventure"), movie("b", "best")))
        db.vodLinks().upsertAll(
            listOf(
                VodLink(VodLink.MOVIE, 1, "adventure", "a", 0, 1),
                VodLink(VodLink.MOVIE, 1, "week", "b", 1, 1),
                VodLink(VodLink.MOVIE, 1, "week", "a", 2, 1),
                VodLink(VodLink.MOVIE, 1, "best", "b", 3, 1),
            ),
        )
        assertThat(db.vodLinks().movies(1, "week", 10).map { it.streamId }).containsExactly("b", "a").inOrder()
        assertThat(db.vodLinks().counts(VodLink.MOVIE).first { it.categoryId == "week" }.n).isEqualTo(2)
        db.vodLinks().deleteStale(VodLink.MOVIE, 1, 2)
        assertThat(db.vodLinks().count(VodLink.MOVIE)).isEqualTo(0)
        db.close()
    }

    @Test
    fun `migration from 15 adds the table Room expects`() {
        open().apply { openHelper.writableDatabase; close() }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("DROP TABLE `vod_links`")
            db.version = 15
        }
        val db = open()
        val tables = db.openHelper.writableDatabase
            .query("SELECT name FROM sqlite_master WHERE type = 'table'")
            .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        assertThat(tables).contains("vod_links")
        db.close()
    }
}
