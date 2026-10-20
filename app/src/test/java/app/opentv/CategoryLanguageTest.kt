/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.core.CategoryRules
import app.opentv.data.model.Category
import app.opentv.data.model.StreamKind
import app.opentv.data.parser.CategoryLanguage
import app.opentv.data.repo.CatalogRepository
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** Names taken from a real 25k-channel Italian provider list. */
class CategoryLanguageTest {

    @Test
    fun `flags decide when present`() {
        assertThat(CategoryLanguage.firstFlag("🇩🇪 SKY Cinema")).isEqualTo("DE")
        assertThat(CategoryLanguage.isForeign("🇩🇪 SKY Cinema")).isTrue()
        assertThat(CategoryLanguage.isForeign("🇬🇧 UK| Movies")).isTrue()
        assertThat(CategoryLanguage.isForeign("🇨🇭Svizzera")).isTrue()
        assertThat(CategoryLanguage.isForeign("🇮🇹 Top Italia")).isFalse()
        assertThat(CategoryLanguage.isForeign("🇮🇹  Canali +2 e +3 ORE")).isFalse()
    }

    @Test
    fun `flagless foreign groups are caught by prefix or region word`() {
        listOf("UK General", "UK Live Football", "🌍 EX YU - SPORT", "🕌 Arabic News", "🌎 Latino",
            "🌍 Kurdish", "🌍 Baltici", "DE: HD+ SD").forEach {
            assertWithMessage(it).that(CategoryLanguage.isForeign(it)).isTrue()
        }
    }

    @Test
    fun `italian groups without a flag stay`() {
        listOf("⚽ DAZN LA LIGA DELTA", "⚽ DAZN NFL ALPHA", "🌍 DAZN VARI", "🏀 Basket LNP | Serie B (ECHO)",
            "💠 Pluto TV", "🎾 EuroSport", "📡 Canali Regionali", "🧸 Bambini (GAMMA)",
            "🏈 DAZN FOOTBALL AMERICANO").forEach {
            assertWithMessage(it).that(CategoryLanguage.isForeign(it)).isFalse()
            assertWithMessage(it).that(CategoryLanguage.isForeignSport(it)).isFalse()
        }
    }

    @Test
    fun `international sport is its own group`() {
        listOf("🌍 ESPN+ Sport", "🏀 Nba Pass (ONLY EVENT)", "🏒 NHL Pass", "⚾ MLB Sport", "🏈 NFL Sport",
            "🌍 PPV (Pay-Per-View) Events", "🌍 Bein Sport Channel", "🌎 DAZN WORLD MIX").forEach {
            assertWithMessage(it).that(CategoryLanguage.isForeignSport(it)).isTrue()
            assertWithMessage(it).that(CategoryLanguage.isForeign(it)).isFalse()
        }
        // Already foreign by flag/name: counted once, under the language rule.
        assertThat(CategoryLanguage.isForeignSport("🕌 Arabic Bein Sports")).isFalse()
    }

    @Test
    fun `rules switch off foreign live categories unless forced back on`() {
        val de = Category(id = "1", sourceId = 1, name = "🇩🇪 SKY Cinema", kind = StreamKind.LIVE)
        val ita = Category(id = "2", sourceId = 1, name = "🇮🇹 Top Italia", kind = StreamKind.LIVE)
        val espn = Category(id = "3", sourceId = 1, name = "🌍 ESPN+ Sport", kind = StreamKind.LIVE)
        val film = Category(id = "4", sourceId = 1, name = "🇩🇪 Filme", kind = StreamKind.MOVIE)

        val rules = CategoryRules(italianOnly = true)
        assertThat(rules.isOff(de)).isTrue()
        assertThat(rules.isOff(ita)).isFalse()
        assertThat(rules.isOff(espn)).isFalse()
        assertThat(rules.isOff(film)).isFalse()

        assertThat(rules.copy(foreignSportOff = true).isOff(espn)).isTrue()
        assertThat(rules.copy(forcedOn = setOf(CatalogRepository.categoryKey(de))).isOff(de)).isFalse()
    }
}
