/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.opentv.data.db.OpenTvDatabase
import app.opentv.data.model.Channel
import app.opentv.data.model.EpgChannelAlias
import app.opentv.data.model.EpgFeed
import app.opentv.data.model.Programme
import app.opentv.data.remote.XtreamApi
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The guide matcher is skipped when nothing it reads has changed, and runs again — with the same
 * answer it always gave — as soon as anything does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MatcherSkipTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: OpenTvDatabase
    private val now = 1_800_000_000_000L

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, OpenTvDatabase::class.java).build()
        context.getSharedPreferences("matcher-test", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun tearDown() { db.close() }

    private fun repo() = EpgRepository(
        programmeDao = db.programmes(),
        feedDao = db.epgFeeds(),
        aliasDao = db.epgAliases(),
        channelDao = db.channels(),
        sourceDao = db.sources(),
        api = XtreamApi(OkHttpClient()),
        http = OkHttpClient(),
        database = db,
        matcherMemo = context.getSharedPreferences("matcher-test", Context.MODE_PRIVATE),
    )

    private fun channel(streamId: String, name: String, groupKey: String) = Channel(
        sourceId = 1, streamId = streamId, name = name, groupKey = groupKey,
        categoryId = "1", logoUrl = null, epgChannelId = null, number = null, streamUrl = "http://x/$streamId",
    )

    private suspend fun seed() {
        val feed = db.epgFeeds().insert(EpgFeed(name = "Guide", url = "http://guide", lastSyncMillis = now))
        db.epgAliases().upsertAll(
            listOf(
                EpgChannelAlias(feed, "rai1.it", "Rai 1", "rai1"),
                EpgChannelAlias(feed, "rai2.it", "Rai 2", "rai2"),
            ),
        )
        db.programmes().upsertAll(
            listOf(
                Programme(feedId = feed, epgChannelId = "rai1.it", startUtcMillis = now, endUtcMillis = now + 3_600_000, title = "TG1"),
                Programme(feedId = feed, epgChannelId = "rai2.it", startUtcMillis = now, endUtcMillis = now + 3_600_000, title = "TG2"),
            ),
        )
        db.channels().upsertAll(listOf(channel("1", "IT| RAI 1 FHD", "rai1"), channel("2", "IT| RAI 2 HD", "rai2")))
    }

    @Test
    fun `matches, then skips while nothing changed`() = runTest {
        seed()
        val repo = repo()
        val first = repo.runMatcherIfChanged()
        assertThat(repo.matcherRuns).isEqualTo(1)
        assertThat(first).isEqualTo(2 to 2)
        assertThat(db.channels().allForMatching().map { it.matchedEpgId }).containsExactly("rai1.it", "rai2.it")

        // A fresh start (new repository, same stored memo): the same answer, without matching.
        val again = repo()
        assertThat(again.runMatcherIfChanged()).isEqualTo(first)
        assertThat(again.matcherRuns).isEqualTo(0)
    }

    @Test
    fun `runs again when a channel, a guide or its programmes change`() = runTest {
        seed()
        repo().runMatcherIfChanged()

        // A new channel from the provider.
        db.channels().upsertAll(listOf(channel("3", "IT| RAI 3", "rai3")))
        repo().also { it.runMatcherIfChanged(); assertThat(it.matcherRuns).isEqualTo(1) }
        repo().also { it.runMatcherIfChanged(); assertThat(it.matcherRuns).isEqualTo(0) }

        // A guide downloaded again.
        val feed = db.epgFeeds().all().single()
        db.epgFeeds().update(feed.copy(lastSyncMillis = now + 1))
        repo().also { it.runMatcherIfChanged(); assertThat(it.matcherRuns).isEqualTo(1) }

        // A guide channel running out of programmes.
        db.programmes().deleteEndedBefore(now + 7_200_000)
        repo().also { it.runMatcherIfChanged(); assertThat(it.matcherRuns).isEqualTo(1) }

        // Someone else touching the matcher's own answer (a regroup clears it): matched again.
        repo().also { it.runMatcherIfChanged(); assertThat(it.matcherRuns).isEqualTo(0) }
        val ch = db.channels().allForMatching().first()
        db.channels().setMatchedEpgId(ch.id, "something.else")
        repo().also { it.runMatcherIfChanged(); assertThat(it.matcherRuns).isEqualTo(1) }
    }

    @Test
    fun `an explicit run always matches`() = runTest {
        seed()
        repo().runMatcherIfChanged()
        val repo = repo()
        repo.runMatcher()
        assertThat(repo.matcherRuns).isEqualTo(1)
    }
}
