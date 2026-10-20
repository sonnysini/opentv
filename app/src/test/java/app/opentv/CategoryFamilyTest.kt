/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.data.parser.CategoryFamily
import app.opentv.data.parser.ChannelNameNormalizer
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** Category names shaped like a real 25k-channel list; the restream sources are made up. */
class CategoryFamilyTest {

    private val sources = setOf("ALPHA", "BRAVO", "DELTA", "GAMMA", "ECHO")

    private fun of(name: String) = CategoryFamily.of(name, sources)

    private fun displayName(name: String) = CategoryFamily.displayName(name, sources)

    private fun assertSameFamily(vararg names: String) {
        val key = of(names.first()).key
        assertThat(key).isNotEmpty()
        for (name in names) assertWithMessage(name).that(of(name).key).isEqualTo(key)
    }

    private fun assertDifferent(a: String, b: String) {
        assertWithMessage("$a vs $b").that(of(a).key).isNotEqualTo(of(b).key)
    }

    @Test
    fun `source splits fold into one family`() {
        assertSameFamily("🇮🇹 Top Italia", "🇮🇹 Top Italia (HI-DEF)")
        assertSameFamily(
            "⚽ Sky Sport (4K)", "⚽ Sky Sport (Alpha)", "⚽ Sky Sport (BRAVO)",
            "⚽ Sky Sport (Gamma)", "⚽ Sky Sport (Delta)",
        )
        assertSameFamily(
            "⚽ Sky Calcio BRAVO", "⚽ Sky Calcio (RAW / Power)", "⚽ Sky Calcio (Alpha)",
            "⚽ Sky Calcio (ECHO)", "⚽ Sky Calcio (Gamma)", "⚽ Sky Calcio (Delta)",
        )
        assertSameFamily("🍸Sky Sport Business BRAVO (Only Event)", "🍸Sky Sport Business Gamma")
        assertSameFamily(
            "⚡ DAZN ECHO", "🔥 DAZN ALPHA", "📺 DAZN BRAVO", "⭐ DAZN GAMMA",
            "💎 DAZN High Quality", "🎛️ DAZN DELTA", "🎬 DAZN RAW",
        )
        assertSameFamily("🔴 Sky Primafila Live", "📺 Sky Primafila 4K")
        assertSameFamily("🌍 Discovery Live Echo", "⚽ Discovery LIVE")
        assertSameFamily("🌍 Disney Plus Quality", "🌍 Disney Plus LIVE BRAVO")
        assertSameFamily("🏀 Basket LNP | BRAVO", "🏀 Basket LNP| GAMMA", "🏀 Basket LNP| QUALITY")
    }

    @Test
    fun `hand-made aliases`() {
        assertSameFamily(
            "🎭 Intratt. & Cultura (BRAVO)", "🎭 Intratt. & Cultura (Delta)",
            "🎭 Intrattenimento (RAW)", "🎭 Cultura (RAW)",
        )
        assertSameFamily("🏀 Basket LBA | Serie A (ECHO)", "🏀 Basket LBA | Serie A (Alpha)", "🏀  Basket LBA | Gamma")
    }

    @Test
    fun `different shelves stay apart`() {
        assertDifferent("⭐ Primafila Premiere", "🔴 Sky Primafila Live")
        assertDifferent("🏀 Basket LNP | Serie A (Echo)", "🏀 Basket LNP | Serie A2 (Alpha)")
        assertDifferent("🏀 Basket LNP | Serie B (Alpha)", "🏀 Basket LNP| GAMMA")
        assertDifferent("🎾 Eurosport ITA Evento", "🎾 EuroSport")
        assertDifferent("⚽ DAZN SERIE B DELTA", "🎛️ DAZN DELTA")
        assertDifferent("🇩🇪 SKY Sport (RAW)", "⚽ Sky Sport (BRAVO)")
    }

    @Test
    fun `source label and clean name`() {
        assertThat(of("⚽ Sky Sport (Alpha)").source).isEqualTo("Alpha")
        assertThat(of("⚽ Sky Sport (Alpha)").name).isEqualTo("⚽ Sky Sport")
        assertThat(of("⚽ Sky Calcio (RAW / Power)").source).isEqualTo("Power")
        assertThat(of("🍸Sky Sport Business BRAVO (Only Event)").source).isEqualTo("Bravo")
        assertThat(of("⚡ DAZN ECHO").source).isEqualTo("ECHO")
        assertThat(of("🇮🇹 Top Italia").source).isNull()
        assertThat(CategoryFamily.of("⚽ Sky Sport (Alpha)").source).isNull() // not a built-in tag
    }

    @Test
    fun `lone group name drops its source but keeps quality and timing`() {
        assertThat(displayName("🧸 Bambini (Alpha)")).isEqualTo("🧸 Bambini")
        assertThat(displayName("🏀 Basket LNP | Serie A2 (Alpha)")).isEqualTo("🏀 Basket LNP | Serie A2")
        assertThat(displayName("🏀 Basket LNP| GAMMA")).isEqualTo("🏀 Basket LNP")
        assertThat(displayName("🇮🇹 Top Italia (HI-DEF)")).isEqualTo("🇮🇹 Top Italia")
        assertThat(displayName("🏐 Volley Pass Echo")).isEqualTo("🏐 Volley Pass")
        assertThat(CategoryFamily.displayName("🏎️ F1 LIVE")).isNull()
        assertThat(CategoryFamily.displayName("📺 Sky Primafila 4K")).isNull()
        assertThat(CategoryFamily.displayName("🎬 DAZN RAW")).isNull()
        assertThat(CategoryFamily.displayName("🇮🇹 Top Italia")).isNull()
    }

    @Test
    fun `learns a source used across groups, not a one-off bracket`() {
        val tags = CategoryFamily.learnSourceTags(
            listOf("⚽ Sky Sport (Zeus)", "🧸 Bambini (Zeus)", "⚽ Sky Calcio (Only Event)", "🎬 Cinema (4K)", "📺 Serie (4K)", "🌍 Nfl (Pay-Per-View)"),
        )
        assertThat(tags).contains("ZEUS")
        assertThat(tags).containsAtLeastElementsIn(ChannelNameNormalizer.BUILTIN_SOURCE_TAGS)
        assertThat(tags).doesNotContain("4K")
        assertThat(tags).doesNotContain("PAY-PER-VIEW")
    }
}
