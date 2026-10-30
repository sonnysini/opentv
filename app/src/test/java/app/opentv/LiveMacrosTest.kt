/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.data.parser.CategoryMacro
import app.opentv.data.parser.CategoryMacro.Macro
import app.opentv.ui.ChannelsViewModel.CategoryGroup
import app.opentv.ui.LiveMacros
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The tabs above the live groups: which tab a category lands in, and what each tab lists. */
class LiveMacrosTest {

    private fun macro(name: String) = CategoryMacro.of(name).macro

    @Test
    fun `categories land in the tab their name says`() {
        assertThat(macro("🇮🇹 Top Italia")).isEqualTo(Macro.ITALY)
        assertThat(macro("🇮🇹 Canali Regionali")).isEqualTo(Macro.ITALY)
        assertThat(macro("🇮🇹 Sky Calcio (Hermes)")).isEqualTo(Macro.FOOTBALL)
        assertThat(macro("🇮🇹 Serie A Femminile DVS")).isEqualTo(Macro.FOOTBALL)
        assertThat(macro("🌍 Flow Sports Premier League")).isEqualTo(Macro.FOOTBALL)
        assertThat(macro("🇮🇹 Basket LBA / Serie A (KALY)")).isEqualTo(Macro.SPORT)
        assertThat(macro("🌍 Volleyball Nations League")).isEqualTo(Macro.SPORT)
        assertThat(macro("🇮🇹 DAZN Football Americano")).isEqualTo(Macro.SPORT)
        assertThat(macro("🇮🇹 Sky Sport (SAT)")).isEqualTo(Macro.SPORT)
        assertThat(macro("🌍 NBA Pass (Event)")).isEqualTo(Macro.SPORT)
        assertThat(macro("🇮🇹 Sky Cinema (4K)")).isEqualTo(Macro.CINEMA)
        assertThat(macro("🇮🇹 Tematici Bambini Plus")).isEqualTo(Macro.KIDS)
        assertThat(macro("🇮🇹 Musica Radio")).isEqualTo(Macro.MUSIC)
        assertThat(macro("🇮🇹 Pluto TV")).isEqualTo(Macro.ENTERTAINMENT)
        assertThat(macro("🇮🇹 Rakuten TV+")).isEqualTo(Macro.ENTERTAINMENT)
        assertThat(macro("🇮🇹 Intrattenimento & Cultura (SAT)")).isEqualTo(Macro.ENTERTAINMENT)
        assertThat(macro("🔞 Adulti [VIP]")).isEqualTo(Macro.ADULT)
    }

    @Test
    fun `foreign categories go abroad under their country`() {
        assertThat(CategoryMacro.of("🇩🇪 DE SKY Sport")).isEqualTo(CategoryMacro.Result(Macro.ABROAD, "DE"))
        assertThat(CategoryMacro.of("UK| Movies")).isEqualTo(CategoryMacro.Result(Macro.ABROAD, "GB"))
        assertThat(CategoryMacro.of("🌍 PAESI ARABI Notizie")).isEqualTo(CategoryMacro.Result(Macro.ABROAD, "PAESI ARABI"))
        assertThat(CategoryMacro.countryName("DE")).isEqualTo("Germania")
    }

    @Test
    fun `a broadcaster in front becomes the kicker`() {
        assertThat(CategoryMacro.kicker("Sky Calcio")).isEqualTo("Sky" to "Calcio")
        assertThat(CategoryMacro.kicker("DAZN Serie B")).isEqualTo("DAZN" to "Serie B")
        assertThat(CategoryMacro.kicker("Sky")).isEqualTo(null to "Sky")
        assertThat(CategoryMacro.kicker("Notizie")).isEqualTo(null to "Notizie")
    }

    private fun group(key: String, raw: String) = CategoryGroup(key, raw, listOf(key), raw)

    @Test
    fun `tabs join shelves and countries and gather time-shifted channels`() {
        val groups = listOf(
            group("top", "🇮🇹 Top Italia"),
            group("tophd", "🇮🇹 Top Italia [HI-DEF]"),
            group("shift", "🇮🇹 Canali +2 e +3 ORE"),
            group("de1", "🇩🇪 DE Generali"),
            group("de2", "🇩🇪 DE Sport"),
            group("xxx", "🔞 XXX"),
        )
        val keys = mapOf(
            "top" to setOf("rai1", "rai2", "rai1+1"),
            "tophd" to setOf("rai1", "canale5"),
            "shift" to setOf("rai1+2", "rai1+3", "rai2+2"),
            "de1" to setOf("ard"),
            "de2" to setOf("skyde"),
            "xxx" to setOf("x"),
        )
        val tabs = LiveMacros.build(groups, keys)
        assertThat(tabs.keys).containsExactly(Macro.ITALY, Macro.ABROAD)
        val italy = tabs.getValue(Macro.ITALY)
        // The +2/+3 category has no live channel of its own: only its sections remain.
        assertThat(italy.map { it.key }).containsExactly("m:top", "s:ITALY:1", "s:ITALY:2", "s:ITALY:3").inOrder()
        assertThat(italy.first().count).isEqualTo(3)
        assertThat(italy.first().ids).containsExactly("top", "tophd")
        assertThat(italy.map { it.count }).containsExactly(3, 1, 2, 1).inOrder()
        val abroad = tabs.getValue(Macro.ABROAD).single()
        assertThat(abroad.key).isEqualTo("c:DE")
        assertThat(abroad.name).isEqualTo("Germania")
        assertThat(abroad.count).isEqualTo(2)

        assertThat(LiveMacros.resolve("s:ITALY:2", groups)).isEqualTo(LiveMacros.Pick(listOf("top", "tophd", "shift"), 2, null))
        assertThat(LiveMacros.resolve("m:tophd", groups)?.ids).containsExactly("top", "tophd")
        assertThat(LiveMacros.resolve("top", groups)).isNull()
    }
}
