/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.data.parser.MergeSuggester
import app.opentv.data.parser.MergeSuggester.Group
import app.opentv.data.parser.MergeSuggester.Reason
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Every case is a pair seen on a real Italian list. */
class MergeSuggesterTest {

    private fun g(key: String, name: String, channels: Int = 1, vararg guide: String) =
        Group(key, name, channels, guide.toSet())

    @Test
    fun `same guide id suggests a merge into the bigger row`() {
        val s = MergeSuggester.suggest(listOf(g("milanchannel", "Milan Channel", 2, "milantv.it"), g("milantv", "Milan TV", 5, "MilanTV.it")))
        assertThat(s).hasSize(1)
        assertThat(s[0].from.key).isEqualTo("milanchannel")
        assertThat(s[0].into.key).isEqualTo("milantv")
        assertThat(s[0].reason).isEqualTo(Reason.GUIDE)
    }

    @Test
    fun `a guide id shared by many rows is generic and ignored`() {
        val rows = (1..5).map { g("daznevent$it", "DAZN Event $it", 1, "dazn.it") }
        assertThat(MergeSuggester.suggest(rows)).isEmpty()
    }

    @Test
    fun `the same words in another order`() {
        val s = MergeSuggester.suggest(listOf(g("pescaecaccia", "Pesca e Caccia"), g("cacciaepesca", "Caccia e Pesca", 2)))
        assertThat(s.single().reason).isEqualTo(Reason.WORDS)
        assertThat(s.single().into.key).isEqualTo("cacciaepesca")
    }

    @Test
    fun `TV and Channel are part of the name`() {
        val s = MergeSuggester.suggest(listOf(g("harrypotter", "Harry Potter"), g("harrypotterchannel", "Harry Potter Channel")))
        assertThat(s).isEmpty()
    }

    @Test
    fun `a typo in a long word is a spelling suggestion`() {
        val s = MergeSuggester.suggest(listOf(g("faketaxy", "Fake Taxy"), g("faketaxi", "Fake Taxi", 3)))
        assertThat(s.single().reason).isEqualTo(Reason.SPELLING)
    }

    @Test
    fun `short words and numbers tell channels apart`() {
        val s = MergeSuggester.suggest(
            listOf(
                g("seriea01", "SERIE A 01"), g("serieb01", "SERIE B 01"),
                g("daznnfl1", "DAZN NFL 1"), g("daznefl1", "DAZN EFL 1"),
                g("skysportmix", "Sky Sport Mix"), g("skysportmax", "Sky Sport Max"),
                g("rai1", "Rai 1", 1, "rai.it"), g("rai2", "Rai 2", 1, "rai.it"),
                g("sky1+1", "Sky 1 +1", 1, "sky1"), g("sky1", "Sky 1", 1, "sky1"),
            ),
        )
        assertThat(s).isEmpty()
    }

    @Test
    fun `ignored pairs are not offered again`() {
        val rows = listOf(g("faketaxy", "Fake Taxy"), g("faketaxi", "Fake Taxi"))
        assertThat(MergeSuggester.suggest(rows, ignored = setOf(MergeSuggester.pairId("faketaxi", "faketaxy")))).isEmpty()
    }
}
