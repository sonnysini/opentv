/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.core

import app.opentv.data.model.Category
import app.opentv.data.parser.CategoryLanguage
import app.opentv.data.parser.CategoryMacro

/**
 * The Movies/Shows home below the charts: one shelf of posters per provider category, in the
 * provider's order. Big families of small categories don't each get a shelf — that would be a
 * hundred and fifty rows of sagas — but fold into one shelf of wide collection tiles, placed where
 * the family's first category was:
 *  - categories named "Prefix: name" sharing a prefix ("Saga: Rocky", "Saga: Alien" → Sagas);
 *  - categories named after a person (actors, directors), when TMDB says so ([people]).
 * Adult and foreign-language categories never get a shelf (they stay under All).
 */
object VodShelves {

    sealed interface Shelf {
        val key: String
    }

    /** One category's own row of posters. */
    data class Row(val category: Category, val label: String) : Shelf {
        override val key: String get() = "c:${category.sourceId}:${category.id}"
    }

    /** A family of categories, one wide tile each; OK on a tile opens that category. */
    data class Collection(override val key: String, val label: String, val members: List<Row>) : Shelf

    /**
     * [categories] in the provider's order; [counts] = titles per "sourceId:categoryId" (empty =
     * not known yet: keep everything); [people] = names (as [label] gives them) TMDB knows as a
     * person; [peopleLabel] = what the people's shelf is called.
     */
    fun plan(
        categories: List<Category>,
        counts: Map<String, Int>,
        people: Set<String>,
        italianOnly: Boolean,
        peopleLabel: String,
    ): List<Shelf> {
        val rows = categories
            .filter { couldHaveShelf(it) }
            .filter { counts.isEmpty() || (counts[countKey(it)] ?: 0) > 0 }
            .map { Row(it, label(it.name)) }

        // Which family each row belongs to, if any.
        val prefixed = rows.groupBy { prefixOf(it.label)?.first?.lowercase() }
            .filterKeys { it != null }
            .filterValues { it.size >= MIN_FAMILY }
        val familyOf = HashMap<String, String>()
        prefixed.forEach { (prefix, members) -> members.forEach { familyOf[it.key] = "p:$prefix" } }
        val persons = rows.filter { it.key !in familyOf && personKey(it.label) in people }
        if (persons.size >= MIN_FAMILY) persons.forEach { familyOf[it.key] = PEOPLE }

        val out = ArrayList<Shelf>()
        val placed = HashSet<String>()
        for (row in rows) {
            val family = familyOf[row.key]
            if (family == null) {
                out += row
                continue
            }
            if (!placed.add(family)) continue
            val members = rows.filter { familyOf[it.key] == family }
            out += if (family == PEOPLE) {
                Collection(family, peopleLabel, members)
            } else {
                val prefix = prefixOf(members.first().label)!!.first
                Collection(family, plural(prefix), members.map { it.copy(label = prefixOf(it.label)!!.second) })
            }
        }
        return out
    }

    /** Neither adult nor in a foreign language: the categories the home may show. */
    fun couldHaveShelf(category: Category): Boolean =
        CategoryMacro.of(category.name).macro != CategoryMacro.Macro.ADULT && !CategoryLanguage.isForeign(category.name)

    /** "🇮🇹 Il meglio del 2026" → "Il meglio del 2026": flags and symbols in front dropped. */
    fun label(name: String): String = name.trim().trimStart { !it.isLetterOrDigit() }.trim().ifEmpty { name.trim() }

    /** "sourceId:categoryId", the key of [plan]'s counts. */
    fun countKey(category: Category): String = "${category.sourceId}:${category.id}"

    /** The form a person's name is looked up and remembered in. */
    fun personKey(label: String): String = label.trim().lowercase()

    /**
     * Whether [label] could be a person's name, so it's worth asking TMDB: two to four words, no
     * digits, starting with capitals — or one word like "Totò". Cheap filter, TMDB decides.
     */
    fun mightBePerson(label: String): Boolean {
        if (label.any { it.isDigit() } || label.contains(':') || label.contains('+')) return false
        val words = label.split(' ').filter { it.isNotBlank() }
        if (words.isEmpty() || words.size > 5) return false
        return words.first().first().isUpperCase() && words.last().first().isUpperCase()
    }

    /** "Saga: Rocky" → ("Saga", "Rocky"). */
    private fun prefixOf(label: String): Pair<String, String>? {
        val i = label.indexOf(':')
        if (i < 2 || i > MAX_PREFIX) return null
        val prefix = label.substring(0, i).trim()
        val rest = label.substring(i + 1).trim()
        if (prefix.isEmpty() || rest.isEmpty()) return null
        return prefix to rest
    }

    private fun plural(prefix: String): String = PLURALS[prefix.lowercase()] ?: prefix

    private const val MIN_FAMILY = 3
    private const val MAX_PREFIX = 24
    private const val PEOPLE = "people"
    private val PLURALS = mapOf("saga" to "Saghe", "collezione" to "Collezioni", "collection" to "Collections")
}
