/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.core.LiveLayout
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The user's group order, channel order and group merges set from the phone page. */
class LiveLayoutTest {

    private data class G(val key: String, val members: Set<String> = setOf(key), val ids: List<String> = listOf(key))

    private fun keys(list: List<G>) = list.map { it.key }

    private fun merge(groups: List<G>, merges: Map<String, String>) =
        LiveLayout.mergeGroups(groups, merges, { it.members }) { target, joining ->
            target.copy(members = target.members + joining.flatMap { it.members }, ids = target.ids + joining.flatMap { it.ids })
        }

    @Test
    fun `saved order first, new items keep their order at the end`() {
        val items = listOf(G("a"), G("b"), G("c"), G("d"))
        assertThat(keys(LiveLayout.ordered(items, listOf("c", "a")) { it.members })).containsExactly("c", "a", "b", "d").inOrder()
    }

    @Test
    fun `a folded group takes the place of its earliest member`() {
        val items = listOf(G("a"), G("b", setOf("b", "x")), G("c"))
        assertThat(keys(LiveLayout.ordered(items, listOf("x", "c", "a", "b")) { it.members })).containsExactly("b", "c", "a").inOrder()
    }

    @Test
    fun `unknown keys in the order change nothing`() {
        val items = listOf(G("a"), G("b"))
        assertThat(keys(LiveLayout.ordered(items, listOf("zz")) { it.members })).containsExactly("a", "b").inOrder()
    }

    @Test
    fun `merged group joins its target, which keeps its place and name`() {
        val out = merge(listOf(G("a"), G("b"), G("c")), mapOf("c" to "a"))
        assertThat(keys(out)).containsExactly("a", "b").inOrder()
        assertThat(out[0].ids).containsExactly("a", "c").inOrder()
        assertThat(out[0].members).containsExactly("a", "c")
    }

    @Test
    fun `chains are followed`() {
        val out = merge(listOf(G("a"), G("b"), G("c")), mapOf("a" to "b", "b" to "c"))
        assertThat(keys(out)).containsExactly("c")
        assertThat(out[0].ids).containsExactly("c", "a", "b")
    }

    @Test
    fun `a merge into a missing group is left alone`() {
        val out = merge(listOf(G("a"), G("b")), mapOf("a" to "gone"))
        assertThat(keys(out)).containsExactly("a", "b").inOrder()
    }

    @Test
    fun `a loop loses no group`() {
        val out = merge(listOf(G("a"), G("b"), G("c")), mapOf("a" to "b", "b" to "a"))
        assertThat(out.flatMap { it.ids }).containsExactly("a", "b", "c")
    }

    @Test
    fun `merge target follows the chain`() {
        assertThat(LiveLayout.mergeTarget("a", mapOf("a" to "b", "b" to "c"))).isEqualTo("c")
        assertThat(LiveLayout.mergeTarget("z", mapOf("a" to "b"))).isNull()
    }
}
