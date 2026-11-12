/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.core.VodShelves
import app.opentv.data.model.Category
import app.opentv.data.model.StreamKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VodShelvesTest {
    private var next = 0
    private fun cat(name: String) = Category(id = "${next++}", sourceId = 1, name = name, kind = StreamKind.MOVIE, sortIndex = next)

    private fun plan(cats: List<Category>, people: Set<String> = emptySet(), italianOnly: Boolean = true, counts: Map<String, Int> = emptyMap()) =
        VodShelves.plan(cats, counts, people, italianOnly, "Attori e registi")

    private fun VodShelves.Shelf.name() = when (this) {
        is VodShelves.Row -> label
        is VodShelves.Collection -> "[$label: ${members.joinToString { it.label }}]"
    }

    @Test
    fun `one row per category in the provider's order, flags dropped`() {
        val shelves = plan(listOf(cat("🇮🇹 Film della Settimana"), cat("🇮🇹 Il meglio del 2026"), cat("🇮🇹 4K UHD")))
        assertThat(shelves.map { it.name() }).containsExactly("Film della Settimana", "Il meglio del 2026", "4K UHD").inOrder()
    }

    @Test
    fun `sagas fold into one collection where the first one was`() {
        val shelves = plan(
            listOf(
                cat("🇮🇹 Netflix"), cat("🇮🇹 Saga: Rocky"), cat("🇮🇹 Saga: Alien"), cat("🇮🇹 Cinepanettone"),
                cat("🇮🇹 Saga: Matrix"),
            ),
        )
        assertThat(shelves.map { it.name() })
            .containsExactly("Netflix", "[Saghe: Rocky, Alien, Matrix]", "Cinepanettone").inOrder()
    }

    @Test
    fun `two of a prefix are not a family`() {
        val shelves = plan(listOf(cat("Saga: Rocky"), cat("Saga: Alien")))
        assertThat(shelves.map { it.name() }).containsExactly("Saga: Rocky", "Saga: Alien").inOrder()
    }

    @Test
    fun `people fold into their own collection`() {
        val shelves = plan(
            listOf(cat("🇮🇹 3D"), cat("🇮🇹 Alberto Sordi"), cat("🇮🇹 Toto"), cat("🇮🇹 Western"), cat("🇮🇹 Carlo Verdone")),
            people = setOf("alberto sordi", "toto", "carlo verdone"),
        )
        assertThat(shelves.map { it.name() })
            .containsExactly("3D", "[Attori e registi: Alberto Sordi, Toto, Carlo Verdone]", "Western").inOrder()
    }

    @Test
    fun `never adult, foreign or empty`() {
        val cats = listOf(cat("🇮🇹 Azione"), cat("XXX | Hot"), cat("🇩🇪 DE Horror"), cat("🇮🇹 Vuota"))
        val counts = mapOf("1:${cats[0].id}" to 5, "1:${cats[1].id}" to 5, "1:${cats[2].id}" to 5)
        assertThat(plan(cats, counts = counts).map { it.name() }).containsExactly("Azione")
        assertThat(plan(cats, italianOnly = false, counts = counts).map { it.name() }).containsExactly("Azione")
    }

    @Test
    fun `who might be a person`() {
        assertThat(VodShelves.mightBePerson("Alberto Sordi")).isTrue()
        assertThat(VodShelves.mightBePerson("Toto")).isTrue()
        assertThat(VodShelves.mightBePerson("Il meglio del 2026")).isFalse()
        assertThat(VodShelves.mightBePerson("Disney+")).isFalse()
        assertThat(VodShelves.mightBePerson("Saga: Rocky")).isFalse()
        assertThat(VodShelves.mightBePerson("Gemme da scoprire")).isFalse()
    }
}

class PersonNameTest {
    private fun same(a: String, b: String) = app.opentv.data.remote.TmdbLists.sameName(a, b)

    @Test
    fun `provider spellings match TMDB's`() {
        assertThat(same("danny devito", "danny de vito")).isTrue()
        assertThat(same("alessandro gassmann", "alessandro gassman")).isTrue()
        assertThat(same("toto", "toto")).isTrue()
        assertThat(same("johnny depp", "jhonny deep")).isFalse()
        assertThat(same("tom hanks", "tom hardy")).isFalse()
    }
}
