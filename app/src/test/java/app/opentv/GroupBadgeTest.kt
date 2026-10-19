/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.data.parser.GroupBadge
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GroupBadgeTest {

    @Test
    fun brandLogoWinsAndEmojiLeavesTheName() {
        val r = GroupBadge.of("⚽ Sky Sport")
        assertThat(r.label).isEqualTo("Sky Sport")
        assertThat((r.badge as GroupBadge.Image).url).endsWith("italy/sky-sport-it.png")
        assertThat((GroupBadge.of("🍸Sky Sport Business").badge as GroupBadge.Image).url).endsWith("sky-sport-bar-it.png")
        assertThat((GroupBadge.of("🏀 DAZN BASKETBALL").badge as GroupBadge.Image).url).endsWith("dazn-int.png")
        assertThat((GroupBadge.of("🏎️ F1 LIVE").badge as GroupBadge.Image).url).endsWith("f1-tv-int.png")
    }

    @Test
    fun brandMustBeAWholeWord() {
        assertThat(GroupBadge.of("⚽ F1rst League").badge).isEqualTo(GroupBadge.Emoji("⚽"))
        assertThat(GroupBadge.of("🌍 Tematici Discovery+ Plus").badge).isEqualTo(GroupBadge.Emoji("🌍"))
    }

    @Test
    fun flagBecomesAFlagImage() {
        val r = GroupBadge.of("🇮🇹  Top Italia")
        assertThat(r.label).isEqualTo("Top Italia")
        assertThat((r.badge as GroupBadge.Image).url).isEqualTo("https://flagcdn.com/w160/it.png")
    }

    @Test
    fun closingEmojiGoesToo() {
        assertThat(GroupBadge.of("⭐ Serie A Replay ⭐")).isEqualTo(GroupBadge.Result("Serie A Replay", GroupBadge.Emoji("⭐")))
        assertThat(GroupBadge.of("★ Preferiti")).isEqualTo(GroupBadge.Result("Preferiti", GroupBadge.Emoji("★")))
    }

    @Test
    fun plainNamesStayAsTheyAre() {
        assertThat(GroupBadge.of("Notizie")).isEqualTo(GroupBadge.Result("Notizie", null))
        assertThat(GroupBadge.of("?")).isEqualTo(GroupBadge.Result("?", null))
        assertThat(GroupBadge.of("-- Sport --").badge).isNull()
    }
}
