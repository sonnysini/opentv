/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.data.parser.CategoryFamily
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** Names taken from a real 25k-channel Italian provider list. */
class CategoryFamilyTest {

    private fun assertSameFamily(vararg names: String) {
        val key = CategoryFamily.of(names.first()).key
        assertThat(key).isNotEmpty()
        for (name in names) assertWithMessage(name).that(CategoryFamily.of(name).key).isEqualTo(key)
    }

    private fun assertDifferent(a: String, b: String) {
        assertWithMessage("$a vs $b").that(CategoryFamily.of(a).key).isNotEqualTo(CategoryFamily.of(b).key)
    }

    @Test
    fun `source splits fold into one family`() {
        assertSameFamily("🇮🇹 Top Italia", "🇮🇹 Top Italia (HI-DEF)")
        assertSameFamily(
            "⚽ Sky Sport (4K)", "⚽ Sky Sport (Hermes)", "⚽ Sky Sport (SAT)",
            "⚽ Sky Sport (Stel-DVS)", "⚽ Sky Sport (SkyNet+)",
        )
        assertSameFamily(
            "⚽ Sky Calcio SAT", "⚽ Sky Calcio (RAW / Power)", "⚽ Sky Calcio (Hermes)",
            "⚽ Sky Calcio (DVS)", "⚽ Sky Calcio (Stellina)", "⚽ Sky Calcio (SkyNet+)",
        )
        assertSameFamily("🍸Sky Sport Business SAT (Only Event)", "🍸Sky Sport Business Stellina")
        assertSameFamily(
            "⚡ DAZN KALI", "🔥 DAZN HERMES", "📺 DAZN SAT", "⭐ DAZN STELLINA",
            "💎 DAZN High Quality", "🎛️ DAZN DVS", "🎬 DAZN RAW",
        )
        assertSameFamily("🔴 Sky Primafila Live", "📺 Sky Primafila 4K")
        assertSameFamily("🌍 Discovery Live Kali", "⚽ Discovery LIVE")
        assertSameFamily("🌍 Disney Plus Quality", "🌍 Disney Plus LIVE SAT")
        assertSameFamily("🏀 Basket LNP | SAT", "🏀 Basket LNP| STELLINA", "🏀 Basket LNP| QUALITY")
    }

    @Test
    fun `hand-made aliases`() {
        assertSameFamily(
            "🎭 Intratt. & Cultura (SAT)", "🎭 Intratt. & Cultura (SkyNet)",
            "🎭 Intrattenimento (RAW)", "🎭 Cultura (RAW)",
        )
        assertSameFamily("🏀 Basket LBA | Serie A (KALY)", "🏀 Basket LBA | Serie A (Hermes)", "🏀  Basket LBA | Stellina")
    }

    @Test
    fun `different shelves stay apart`() {
        assertDifferent("⭐ Primafila Premiere", "🔴 Sky Primafila Live")
        assertDifferent("🏀 Basket LNP | Serie A (Kaly)", "🏀 Basket LNP | Serie A2 (Hermes)")
        assertDifferent("🏀 Basket LNP | Serie B (Hermes)", "🏀 Basket LNP| STELLINA")
        assertDifferent("🎾 Eurosport ITA Evento", "🎾 EuroSport")
        assertDifferent("⚽ DAZN SERIE B DVS", "🎛️ DAZN DVS")
        assertDifferent("🇩🇪 SKY Sport (RAW)", "⚽ Sky Sport (SAT)")
    }

    @Test
    fun `source label and clean name`() {
        assertThat(CategoryFamily.of("⚽ Sky Sport (Hermes)").source).isEqualTo("Hermes")
        assertThat(CategoryFamily.of("⚽ Sky Sport (Hermes)").name).isEqualTo("⚽ Sky Sport")
        assertThat(CategoryFamily.of("⚽ Sky Calcio (RAW / Power)").source).isEqualTo("Power")
        assertThat(CategoryFamily.of("🍸Sky Sport Business SAT (Only Event)").source).isEqualTo("SAT")
        assertThat(CategoryFamily.of("⚡ DAZN KALI").source).isEqualTo("KALI")
        assertThat(CategoryFamily.of("🇮🇹 Top Italia").source).isNull()
    }

    @Test
    fun `learns a source used across groups, not a one-off bracket`() {
        val tags = CategoryFamily.learnSourceTags(
            listOf("⚽ Sky Sport (Zeus)", "🧸 Bambini (Zeus)", "⚽ Sky Calcio (Only Event)", "🎬 Cinema (4K)", "📺 Serie (4K)", "🌍 Nfl (Pay-Per-View)"),
        )
        assertThat(tags).contains("ZEUS")
        assertThat(tags).contains("STELLINA") // built-in
        assertThat(tags).doesNotContain("4K")
        assertThat(tags).doesNotContain("PAY-PER-VIEW")
    }
}
