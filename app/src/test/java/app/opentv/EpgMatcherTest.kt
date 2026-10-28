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

    // ---- Matcher v2: found on a real Italian list against iptv-epg.org + epgshare01 ----

    @Test
    fun `a guide in doubt is left out and the other guides decide`() {
        val idx = EpgMatcher.buildIndexOf(
            listOf(
                // epgshare lists two ids for one name; iptv-epg has it once.
                ch("Rai.1.it", "Rai 1", feed = 2, weight = 100),
                ch("Rai.1.HD.it", "Rai 1 HD", feed = 2, weight = 90),
                ch("Rai1.it", "IT - Rai 1", feed = 1, weight = 150),
            ),
        )

        assertThat(idx.match(keyOf("RAI 1 FHD"))).isEqualTo("Rai1.it")
    }

    @Test
    fun `a bare plus is part of the name, not dropped`() {
        val idx = EpgMatcher.buildIndexOf(
            listOf(
                ch("SkyUno.it", "IT - Sky Uno", feed = 1, weight = 151),
                ch("SkyUno+.it", "IT - Sky Uno +", feed = 1, weight = 84),
                ch("SkyUno+1.it", "IT - Sky Uno +1", feed = 1, weight = 152),
            ),
        )

        assertThat(idx.match(keyOf("Sky Uno FHD"))).isEqualTo("SkyUno.it")
        assertThat(idx.match(keyOf("Sky Uno Plus HD"))).isEqualTo("SkyUno+.it")
        assertThat(idx.match(keyOf("Sky Uno +1 HD"))).isEqualTo("SkyUno+1.it")
    }

    @Test
    fun `a timeshift never prefix-matches another timeshift`() {
        // `+2` is a prefix of `+24`: Sky Cinema Due +2 got the +24 channel's guide.
        val idx = index("SkyCinemaDue+24.it" to "IT - Sky Cinema Due +24")

        assertThat(idx.match(keyOf("Sky Cinema Due +2 HD"))).isNull()
        assertThat(idx.match(keyOf("Sky Cinema Due +24"))).isEqualTo("SkyCinemaDue+24.it")
    }

    @Test
    fun `small typos on long names are forgiven`() {
        val idx = index(
            "SkyCinemaSuspense.it" to "IT - Sky Cinema Suspense",
            "SkyDocumentaries+1.it" to "IT - Sky Documentaries +1",
            "ItalianFishingTV.it" to "IT - Italian Fishing TV",
        )

        assertThat(idx.match(keyOf("Sky Cinema Suspence SD"))).isEqualTo("SkyCinemaSuspense.it")
        assertThat(idx.match(keyOf("SKY DOCUMENTARIS +1 HD"))).isEqualTo("SkyDocumentaries+1.it")
        assertThat(idx.match(keyOf("ITALIAN FISCHING TV FHD"))).isEqualTo("ItalianFishingTV.it")
    }

    @Test
    fun `typos never cross a number or a timeshift`() {
        val idx = index(
            "Mediaset20.it" to "IT - 20 Mediaset",
            "RaiRadio2" to "Rai Radio 2",
            "SkyUno+1.it" to "IT - Sky Uno +1",
        )

        assertThat(idx.match(keyOf("27 Mediaset FHD"))).isNull()
        assertThat(idx.match(keyOf("Rai Radio 1"))).isNull()
        assertThat(idx.match(keyOf("Sky Uno HD +2"))).isNull()
    }

    @Test
    fun `typos are not forgiven on short names`() {
        assertThat(index("Rai4.it" to "Rai 4").match(keyOf("Rai 5"))).isNull()
        assertThat(index("Nove.it" to "Nove").match(keyOf("Neve"))).isNull()
    }

    @Test
    fun `a typo match needs a single candidate name`() {
        val idx = index("a.it" to "Sky Documentaries", "b.it" to "Sky Documentarias")

        assertThat(idx.match(keyOf("Sky Documentarius"))).isNull()
    }

    @Test
    fun `similar names - the provider id check`() {
        assertThat(EpgMatcher.similar("discoverychannel", "discovery")).isTrue()
        assertThat(EpgMatcher.similar("hgvtv", "hgtv")).isFalse()
        assertThat(EpgMatcher.similar("disneyplusanimazione", "discoverychannel")).isFalse()
        assertThat(EpgMatcher.similar("skyserie+1", "skyserie")).isFalse()
        assertThat(EpgMatcher.similar("skycinemasuspence", "skycinemasuspense")).isTrue()
    }
}
