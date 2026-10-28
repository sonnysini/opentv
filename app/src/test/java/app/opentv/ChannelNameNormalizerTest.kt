/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.data.parser.ChannelNameNormalizer
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Every case in here is a naming style seen on a real provider. When a channel groups or
 * matches wrongly in the wild, the fix starts with a new test named after it.
 */
class ChannelNameNormalizerTest {

    @Test
    fun `strips a UK pipe prefix and an FHD tag`() {
        val n = ChannelNameNormalizer.normalize("UK| BBC ONE FHD")

        assertThat(n.baseName).isEqualTo("BBC ONE")
        assertThat(n.groupKey).isEqualTo("bbc1")
        assertThat(n.qualityRank).isEqualTo(300)
        assertThat(n.qualityLabel).isEqualTo("FHD")
        assertThat(n.region).isEqualTo("UK")
    }

    @Test
    fun `quality variants of one channel share a group key`() {
        val variants = listOf(
            "UK| BBC ONE SD",
            "UK| BBC ONE HD",
            "UK| BBC ONE FHD",
            "UK - BBC One UHD",
        ).map { ChannelNameNormalizer.normalize(it) }

        assertThat(variants.map { it.groupKey }.distinct()).hasSize(1)
        // And ranks order them correctly for "best plays by default".
        assertThat(variants.map { it.qualityRank }).isEqualTo(listOf(100, 200, 300, 400))
    }

    @Test
    fun `provider name meets the guide name in the middle`() {
        val provider = ChannelNameNormalizer.normalize("UK| BBC ONE FHD").groupKey
        val guide = ChannelNameNormalizer.groupKeyOf("BBC One")

        assertThat(provider).isEqualTo(guide)
    }

    @Test
    fun `word and digit channel numbers fold together`() {
        assertThat(ChannelNameNormalizer.groupKeyOf("BBC One"))
            .isEqualTo(ChannelNameNormalizer.groupKeyOf("BBC 1"))
        assertThat(ChannelNameNormalizer.groupKeyOf("Channel Four"))
            .isEqualTo(ChannelNameNormalizer.groupKeyOf("Channel 4"))
    }

    @Test
    fun `RAW and fps markers strip into the label, bare RAW ranks as FHD`() {
        val n = ChannelNameNormalizer.normalize("UK| PRIME RAW 60fps")

        assertThat(n.baseName).isEqualTo("PRIME")
        assertThat(n.qualityRank).isEqualTo(310)
        assertThat(n.qualityLabel).isEqualTo("RAW 60fps")
    }

    @Test
    fun `HEVC beats RAW beats plain inside a resolution, below 4K`() {
        fun rank(name: String) = ChannelNameNormalizer.normalize(name).qualityRank
        assertThat(rank("Sky Atlantic HEVC")).isEqualTo(320)
        assertThat(rank("Sky Uno HD [1080p] HEVC")).isEqualTo(320)
        assertThat(rank("Rai 1 RAW")).isEqualTo(310)
        assertThat(rank("Sky Atlantic FHD")).isEqualTo(300)
        assertThat(rank("Sky Atlantic 4K")).isEqualTo(400)
        assertThat(ChannelNameNormalizer.resolutionTier(320)).isEqualTo(300)
    }

    @Test
    fun `Full HD in two words is FHD and folds with the other variants`() {
        val n = ChannelNameNormalizer.normalize("Sky Atlantic Full HD")

        assertThat(n.baseName).isEqualTo("Sky Atlantic")
        assertThat(n.groupKey).isEqualTo(ChannelNameNormalizer.normalize("Sky Atlantic FHD").groupKey)
        assertThat(n.qualityRank).isEqualTo(300)
        assertThat(n.qualityLabel).isEqualTo("FHD")
    }

    @Test
    fun `hash decorations are stripped`() {
        val n = ChannelNameNormalizer.normalize("####  PRIME RAW 60fps #####")

        assertThat(n.baseName).isEqualTo("PRIME")
    }

    @Test
    fun `bracketed country prefixes work too`() {
        assertThat(ChannelNameNormalizer.normalize("[UK] ITV 1 HD").region).isEqualTo("UK")
        assertThat(ChannelNameNormalizer.normalize("(US) CNN HD").region).isEqualTo("US")
        assertThat(ChannelNameNormalizer.normalize("USA - ESPN").region).isEqualTo("USA")
    }

    @Test
    fun `superscript HD decorations are understood`() {
        val n = ChannelNameNormalizer.normalize("UK| SKY ATLANTIC ᴴᴰ")

        assertThat(n.baseName).isEqualTo("SKY ATLANTIC")
        assertThat(n.qualityRank).isEqualTo(200)
    }

    @Test
    fun `broadcaster names that look like prefixes are left alone`() {
        // Four-letter and pipe-less names must not lose their first word.
        assertThat(ChannelNameNormalizer.normalize("BEIN| SPORTS 1 HD").baseName)
            .contains("SPORTS")
        val sky = ChannelNameNormalizer.normalize("Sky Sports Main Event")
        assertThat(sky.baseName).isEqualTo("Sky Sports Main Event")
        assertThat(sky.region).isNull()
    }

    @Test
    fun `hevc and h265 are stream tags not part of the name`() {
        val n = ChannelNameNormalizer.normalize("UK| GENERAL hevc")

        assertThat(n.baseName).isEqualTo("GENERAL")
        assertThat(n.qualityLabel).isEqualTo("hevc")
    }

    @Test
    fun `a name that is nothing but junk still yields a usable key`() {
        val n = ChannelNameNormalizer.normalize("HD")

        // Everything stripped: fall back to the raw name rather than an empty key that
        // would group all such channels into one giant accidental family.
        assertThat(n.groupKey).isNotEmpty()
    }

    @Test
    fun `group keys ignore case punctuation and spacing`() {
        val keys = listOf("BBC One", "bbc-one", "B B C ONE", "BbcOne")
            .map { ChannelNameNormalizer.groupKeyOf(it) }

        assertThat(keys.distinct()).hasSize(1)
    }

    @Test
    fun `a trailing RS is a source label, not part of the name`() {
        val n = ChannelNameNormalizer.normalize("Sky Uno HD RS")

        assertThat(n.groupKey).isEqualTo("skyuno")
        assertThat(n.qualityLabel).isEqualTo("HD RS")
    }

    @Test
    fun `sky and discovery prefixes fold only when the bare channel exists`() {
        val aliases = ChannelNameNormalizer.keyAliases(
            setOf("skymtv", "mtv", "discoverygiallo", "giallo", "skyuno", "skycinemafamily", "cinemafamily"),
        )

        assertThat(aliases).containsExactly("skymtv", "mtv", "discoverygiallo", "giallo")
    }

    @Test
    fun `provider typos are left to the user, not folded`() {
        val aliases = ChannelNameNormalizer.keyAliases(setOf("nicklodeon", "nickelodeon", "skynickelodeon"))

        assertThat(aliases).containsExactly("skynickelodeon", "nickelodeon")
    }

    /** Made-up restream sources; the real ones live in SourceTags. */
    private val sources = ChannelNameNormalizer.BUILTIN_SOURCE_TAGS + setOf("ALPHA", "BRAVO", "GAMMA", "DELTA", "ECHO")

    private fun normalize(name: String) = ChannelNameNormalizer.normalize(name, sources, leadingTags = setOf("ECHO"))

    private fun key(name: String) = normalize(name).groupKey

    @Test
    fun `source tags in channel names fold into the plain channel`() {
        assertThat(key("[ALPHA] DAZN-LIGUE-WOMEN-1")).isEqualTo(key("[BRAVO] DAZN-LIGUE-WOMEN-1"))
        assertThat(key("Sky Sport F1 FHD (Q)")).isEqualTo(key("Sky Sport F1 FHD"))
        assertThat(key("Sky Sport F1 HD (DELTA)")).isEqualTo(key("Sky Sport F1 HD"))
        assertThat(key("SKY SPORT BUSINESS 1 FHD GAMMA")).isEqualTo(key("SKY SPORT BUSINESS 1 bRAVO"))
        assertThat(key("Sky Sport Moto Gp Bravo FHD")).isEqualTo(key("Sky Sport Moto GP HD"))
        assertThat(key("Echo ComoTV 1")).isEqualTo(key("COMO TV 1"))
        assertThat(key("Sky Sport 256 FHD Duplicate")).isEqualTo(key("Sky Sport 256 HD"))
        assertThat(key("Sky Sport F1 4K RAW Richiede 30 Mbps")).isEqualTo(key("Sky Sport F1 HD"))
    }

    @Test
    fun `the stripped source is reported, the quality untouched`() {
        val n = normalize("SKY SPORT BUSINESS 1 FHD GAMMA")
        assertThat(n.baseName).isEqualTo("SKY SPORT BUSINESS 1")
        assertThat(n.qualityLabel).isEqualTo("FHD")
        assertThat(n.source).isEqualTo("Gamma")
        assertThat(ChannelNameNormalizer.normalize("Sky Sport F1 FHD (Q)").source).isEqualTo("Q")
    }

    @Test
    fun `a source tag alone is still a name`() {
        assertThat(normalize("BRAVO").baseName).isEqualTo("BRAVO")
        assertThat(key("Moto GP Bravissimo 4K RAW")).isEqualTo("motogpbravissimo")
    }

    @Test
    fun `learned tags strip only when the provider uses them`() {
        assertThat(key("Sky Sport F1 Zeus")).isEqualTo("skysportf1zeus")
        val tags = ChannelNameNormalizer.BUILTIN_SOURCE_TAGS + "ZEUS"
        assertThat(ChannelNameNormalizer.normalize("Sky Sport F1 Zeus", tags).groupKey).isEqualTo(key("Sky Sport F1"))
    }

    @Test
    fun `timeshift stays apart from channel numbers`() {
        assertThat(key("Discovery HD +2")).isNotEqualTo(key("Discovery 2"))
        assertThat(key("Grande Fratello VIP +1")).isNotEqualTo(key("Grande Fratello VIP 1"))
        assertThat(key("Eurosport 1 +2")).isNotEqualTo(key("eurosport 12"))
        assertThat(key("Rai 5 + 1")).isEqualTo(key("Rai 5 +1 HD"))
        assertThat(key("Food Network+1")).isEqualTo(key("Food Network +1"))
        assertThat(ChannelNameNormalizer.normalize("CANALE 5 +1 HD").baseName).isEqualTo("CANALE 5 +1")
    }

    @Test
    fun `aliases follow timeshift copies`() {
        val aliases = ChannelNameNormalizer.keyAliases(
            setOf("comedycentral", "skycomedycentral+1"),
        )
        assertThat(aliases["skycomedycentral+1"]).isEqualTo("comedycentral+1")
    }

    @Test
    fun `accented letters fold instead of vanishing`() {
        assertThat(key("DAZN SERIE B Südtirol")).isEqualTo(key("DAZN Serie B SudTirol"))
    }

    @Test
    fun `Plus is part of a name unless it follows HDR`() {
        assertThat(ChannelNameNormalizer.normalize("Disney Plus Film HD").baseName).isEqualTo("Disney Plus Film")
        val hdr = ChannelNameNormalizer.normalize("Sky Sport Uno UHD HDR PLUS")
        assertThat(hdr.baseName).isEqualTo("Sky Sport Uno")
        assertThat(hdr.qualityLabel).isEqualTo("UHD HDR PLUS")
    }

    @Test
    fun `brand-only UHD channels keep their 4K`() {
        assertThat(ChannelNameNormalizer.normalize("Rai 4K RAW").baseName).isEqualTo("Rai 4K")
        assertThat(normalize("SKY SPORT 4K GAMMA").baseName).isEqualTo("SKY SPORT 4K")
        assertThat(key("Rai 4K RAW")).isNotEqualTo(key("Rai 4"))
        assertThat(key("Rai 2 4K")).isEqualTo(key("Rai 2 HD"))
        assertThat(key("Sky Sport F1 4K")).isEqualTo(key("Sky Sport F1 HD"))
    }

    private fun folded(vararg names: String): Map<String, String> {
        val keys = names.map { key(it) }
        val aliases = ChannelNameNormalizer.keyAliases(keys.toSet())
        return names.zip(keys).associate { (n, k) -> n to (aliases[k] ?: k) }
    }

    @Test
    fun `DAZN team feeds join the bare team channel`() {
        val f = folded(
            "Milan FHD", "DAZN Serie A Milan HD", "DAZN SERIE A 3 FHD  MILAN",
            "Cesena HD", "DAZN SERIE B Cesena FHD", "DAZN 5 SERIE B FHD CESENA", "DAZN 5 SERIE B WEB CESENA",
            "Sassuolo HD", "DAZN SERIE A 13 WEB SASSUOLO",
        )
        assertThat(f.filterKeys { "MILAN" in it.uppercase() }.values.toSet()).containsExactly("milan")
        assertThat(f.filterKeys { "CESENA" in it.uppercase() }.values.toSet()).containsExactly("cesena")
        assertThat(f.filterKeys { "SASSUOLO" in it.uppercase() }.values.toSet()).containsExactly("sassuolo")
    }

    @Test
    fun `DAZN team feeds stay put without a bare team channel`() {
        val f = folded("DAZN Serie B Pisa HD", "DAZN 9 SERIE B FHD PISA", "SERIE A 01 RAW", "DAZN 1 HD")
        assertThat(f["DAZN Serie B Pisa HD"]).isEqualTo("daznseriebpisa")
        assertThat(f["DAZN 9 SERIE B FHD PISA"]).isEqualTo("dazn9seriebpisa")
        assertThat(f["SERIE A 01 RAW"]).isEqualTo("seriea01")
    }

    @Test
    fun `a DAZN prefix drops when the bare channel exists`() {
        val f = folded(
            "DAZN UFC 1 UHD", "UFC 1 HD", "IT DAZN| Eurosport 1", "Eurosport 1 HD",
            "[DELTA] DAZN-SERIE-A-WOMEN-1", "SERIE A WOMEN 1", "DAZN CHANNEL HD",
        )
        assertThat(f["DAZN UFC 1 UHD"]).isEqualTo("ufc1")
        assertThat(f["IT DAZN| Eurosport 1"]).isEqualTo("eurosport1")
        assertThat(f["[DELTA] DAZN-SERIE-A-WOMEN-1"]).isEqualTo("serieawomen1")
        assertThat(f["DAZN CHANNEL HD"]).isEqualTo("daznchannel")
    }

    @Test
    fun `a brand prefix never leaves a bare number`() {
        val f = folded("DAZN 1 HD", "VH- 1", "Sky 1 HD")
        assertThat(f["DAZN 1 HD"]).isEqualTo("dazn1")
        assertThat(f["Sky 1 HD"]).isEqualTo("sky1")
    }

    @Test
    fun `a single bracketed letter in front is a source`() {
        val n = ChannelNameNormalizer.normalize("[V] BOX 01 HD")
        assertThat(n.groupKey).isEqualTo("box01")
        assertThat(n.source).isNotNull()
        assertThat(key("[V] BOX 01 HD")).isEqualTo(key("BOX 01 SD"))
    }

    @Test
    fun `quality tags glued with dashes are found`() {
        val n = ChannelNameNormalizer.normalize("SERIE-A1-FHD")
        assertThat(n.baseName).isEqualTo("SERIE A1")
        assertThat(n.qualityLabel).isEqualTo("FHD")
        assertThat(key("SERIE-A1-SD")).isEqualTo(n.groupKey)
        // No tag inside: the dashed name is left alone.
        assertThat(ChannelNameNormalizer.normalize("Brixen-PALLAMANO-FEMMINILE").baseName).isEqualTo("Brixen-PALLAMANO-FEMMINILE")
    }

    @Test
    fun `a country-like prefix that is the whole name stays`() {
        assertThat(key("VH- 1")).isEqualTo(key("VH1"))
        assertThat(ChannelNameNormalizer.normalize("VH- 1").region).isNull()
        assertThat(ChannelNameNormalizer.normalize("UK| BBC ONE").region).isEqualTo("UK")
    }

    @Test
    fun `a bare plus reads as Plus, a timeshift stays a timeshift`() {
        assertThat(ChannelNameNormalizer.normalize("IT - Sky Uno +").groupKey).isEqualTo("skyunoplus")
        assertThat(ChannelNameNormalizer.normalize("Sky UNO Plus FHD").groupKey).isEqualTo("skyunoplus")
        assertThat(ChannelNameNormalizer.normalize("Disney+ HD").groupKey).isEqualTo("disneyplus")
        assertThat(ChannelNameNormalizer.normalize("Sky Uno +1 HD").groupKey).isEqualTo("skyuno+1")
        assertThat(ChannelNameNormalizer.normalize("Sky Uno + 1").groupKey).isEqualTo("skyuno+1")
    }

    @Test
    fun `public list notes and line counts are not part of the name`() {
        val n = ChannelNameNormalizer.normalize("12 TV Parma (540p) [Not 24/7]")
        assertThat(n.groupKey).isEqualTo("12tvparma")
        assertThat(n.qualityLabel).isEqualTo("540P")
        assertThat(ChannelNameNormalizer.normalize("Antenna Tre (480p) [Geo-blocked]").groupKey).isEqualTo("antennatre")
        assertThat(ChannelNameNormalizer.normalize("Rai 1 (1080i)").qualityRank).isEqualTo(300)
        // A number that isn't a line count stays: `Sky Sport 251`.
        assertThat(ChannelNameNormalizer.normalize("Sky Sport 251").groupKey).isEqualTo("skysport251")
    }
}
