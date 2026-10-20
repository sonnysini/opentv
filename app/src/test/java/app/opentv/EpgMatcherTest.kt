/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.data.model.TimeShift
import app.opentv.data.parser.ChannelNameNormalizer
import app.opentv.data.repo.EpgMatcher
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The matcher's contract: prefer no match to a wrong match. A missing guide entry is an
 * annoyance with a manual override; a wrong one convinces the user the whole guide lies.
 */
class EpgMatcherTest {

    /** Shorthand: build an index from (epgId, displayName) pairs. */
    private fun index(vararg aliases: Pair<String, String>) =
        EpgMatcher.buildIndex(aliases.toList())

    private fun keyOf(providerName: String) =
        ChannelNameNormalizer.normalize(providerName).groupKey

    @Test
    fun `provider channel matches guide channel by normalised name`() {
        val idx = index("bbc1.uk" to "BBC One", "itv1.uk" to "ITV1")

        assertThat(idx.match(keyOf("UK| BBC ONE FHD"))).isEqualTo("bbc1.uk")
        assertThat(idx.match(keyOf("UK - ITV 1 HD"))).isEqualTo("itv1.uk")
    }

    @Test
    fun `the raw guide id is matchable too`() {
        // Providers that do fill in epg_channel_id often use the id form directly.
        val idx = index("bbc1.uk" to "BBC One")

        assertThat(idx.match(ChannelNameNormalizer.groupKeyOf("bbc1.uk"))).isEqualTo("bbc1.uk")
    }

    @Test
    fun `unique prefix matches are accepted`() {
        val idx = index("bbc1london.uk" to "BBC One London")

        // Provider says just "BBC One"; the only candidate is the London region — take it.
        assertThat(idx.match(keyOf("UK| BBC ONE"))).isEqualTo("bbc1london.uk")
    }

    @Test
    fun `ambiguous prefix matches are refused`() {
        val idx = index(
            "bbc1london.uk" to "BBC One London",
            "bbc1wales.uk" to "BBC One Wales",
        )

        // Two regions fit and nothing exact does. Guessing here shows the wrong regional
        // news against the channel — refuse, and let the manual override decide.
        assertThat(idx.match(keyOf("UK| BBC ONE"))).isNull()
    }

    @Test
    fun `colliding display names disable exact matching for that key`() {
        val idx = index(
            "cnn.us" to "CNN",
            "cnn.int" to "CNN",
        )

        assertThat(idx.match(keyOf("CNN"))).isNull()
    }

    @Test
    fun `unknown channels match nothing`() {
        val idx = index("bbc1.uk" to "BBC One")

        assertThat(idx.match(keyOf("UK| SOME SHOP CHANNEL"))).isNull()
    }

    @Test
    fun `keys too short to mean anything never match`() {
        val idx = index("e.uk" to "E")

        assertThat(idx.match("e")).isNull()
        // But two characters is a real channel name in Britain.
        val e4 = index("e4.uk" to "E4")
        assertThat(e4.match(keyOf("UK| E4 HD"))).isEqualTo("e4.uk")
    }

    @Test
    fun `end to end - the exact names from the reporting provider`() {
        // The names that started all this, verbatim from the screen.
        val idx = index(
            "bbc1.uk" to "BBC One",
            "bbc2.uk" to "BBC Two",
            "channel4.uk" to "Channel 4",
            "skyatlantic.uk" to "Sky Atlantic",
        )

        assertThat(idx.match(keyOf("UK| BBC ONE HD/RAW"))).isEqualTo("bbc1.uk")
        assertThat(idx.match(keyOf("UK| BBC TWO FHD"))).isEqualTo("bbc2.uk")
        assertThat(idx.match(keyOf("UK| CHANNEL 4 hevc"))).isEqualTo("channel4.uk")
        assertThat(idx.match(keyOf("UK| SKY ATLANTIC ᴴᴰ"))).isEqualTo("skyatlantic.uk")
    }

    private fun ch(id: String, name: String, feed: Long, weight: Int) =
        EpgMatcher.GuideChannel(id, name, feed, weight)

    @Test
    fun `the same channel in two guides is not ambiguous - the fuller guide wins`() {
        val idx = EpgMatcher.buildIndexOf(
            listOf(
                ch("Rai3.it", "IT - Rai 3", feed = 7, weight = 118),
                ch("Rai.3.HD.it", "Rai 3 HD", feed = 8, weight = 141),
            ),
        )

        assertThat(idx.match(keyOf("RAI 3 HD"))).isEqualTo("Rai.3.HD.it")
    }

    @Test
    fun `two channels of one guide under one name stay ambiguous`() {
        val idx = EpgMatcher.buildIndexOf(
            listOf(ch("cnn.us", "CNN", feed = 1, weight = 50), ch("cnn.int", "CNN", feed = 1, weight = 60)),
        )

        assertThat(idx.match(keyOf("CNN"))).isNull()
    }

    @Test
    fun `a timeshift never prefix-matches its base channel`() {
        val idx = index("Rai1.it" to "Rai 1")

        // `rai1+1` extends `rai1`; matching it would show Rai 1's schedule an hour early.
        assertThat(idx.match(keyOf("Rai 1 +1 HD"))).isNull()
        assertThat(index("Rai1Plus1.it" to "Rai 1 +1").match(keyOf("Rai 1 +1 HD"))).isEqualTo("Rai1Plus1.it")
    }

    @Test
    fun `timeshift ids round-trip`() {
        val id = TimeShift.encode("Rai1.it", 2)

        assertThat(TimeShift.decode(id)).isEqualTo("Rai1.it" to 7_200_000L)
        assertThat(TimeShift.decode("Rai1.it")).isNull()
        assertThat(TimeShift.splitKey(keyOf("Sky Uno HD +3"))).isEqualTo("skyuno" to 3)
        assertThat(TimeShift.splitKey(keyOf("Sky Uno HD"))).isNull()
    }

    @Test
    fun `a bare plus is the word plus, not punctuation`() {
        // Guide says `Sky Uno +`, provider says `Sky Uno Plus`. Dropping the `+` keyed the
        // guide entry as `skyuno` and made it collide with `Sky Uno` itself: neither matched.
        val idx = index(
            "SkyUno.it" to "Sky Uno",
            "SkyUnoPlus.it" to "Sky Uno +",
            "SkyUno1.it" to "Sky Uno +1",
            "Disney.it" to "Disney+",
        )

        assertThat(idx.match(keyOf("Sky Uno Plus FHD"))).isEqualTo("SkyUnoPlus.it")
        assertThat(idx.match(keyOf("Sky Uno FHD"))).isEqualTo("SkyUno.it")
        assertThat(idx.match(keyOf("Sky Uno +1 HD"))).isEqualTo("SkyUno1.it")
        assertThat(idx.match(keyOf("Disney Plus"))).isEqualTo("Disney.it")
        assertThat(keyOf("Sky Uno+")).isEqualTo(keyOf("Sky Uno Plus"))
    }
}
