/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.core

/**
 * The user's own layout of the live lists — group order, channel order inside a group and groups
 * folded into another — applied on top of what the provider sends. Kept free of Android so the
 * rules can be unit-tested; the phone page sets them, the TV lists follow.
 */
object LiveLayout {

    /** Longest chain of hand merges followed (a → b → c); anything longer is a loop. */
    private const val MAX_HOPS = 8

    /**
     * [items] in the saved [order]: the ones it names first, in its order, then the rest in their
     * own order — so a group or channel that shows up after the user arranged things lands at the
     * end instead of shuffling the list. An item answers to any of [keysOf] (a folded group to
     * each of its members), and takes the earliest place among them.
     */
    fun <T> ordered(items: List<T>, order: List<String>, keysOf: (T) -> Collection<String>): List<T> {
        if (order.isEmpty() || items.size < 2) return items
        val rank = HashMap<String, Int>(order.size * 2)
        order.forEachIndexed { i, key -> rank.putIfAbsent(key, i) }
        val placed = ArrayList<Pair<Int, T>>()
        val rest = ArrayList<T>()
        for (item in items) {
            val r = keysOf(item).mapNotNull { rank[it] }.minOrNull()
            if (r == null) rest += item else placed += r to item
        }
        if (placed.isEmpty()) return items
        return placed.sortedBy { it.first }.map { it.second } + rest
    }

    /**
     * Where [key] ends up after the user's group [merges] (a → b → c), or null when it isn't merged.
     * A loop stops where it started repeating.
     */
    fun mergeTarget(key: String, merges: Map<String, String>): String? {
        var current = merges[key] ?: return null
        val seen = hashSetOf(key, current)
        repeat(MAX_HOPS) {
            val next = merges[current] ?: return current
            if (!seen.add(next)) return current
            current = next
        }
        return current
    }

    /**
     * Folds groups into the ones the user merged them into. [keysOf] are the keys a group answers
     * to; [absorb] makes one group out of a target and what joins it, target first so it keeps its
     * name. A merge whose target isn't in [groups] (switched off, another provider) is left alone.
     */
    fun <T> mergeGroups(
        groups: List<T>,
        merges: Map<String, String>,
        keysOf: (T) -> Collection<String>,
        absorb: (target: T, joining: List<T>) -> T,
    ): List<T> {
        if (merges.isEmpty() || groups.size < 2) return groups
        val indexByKey = HashMap<String, Int>()
        groups.forEachIndexed { i, g -> keysOf(g).forEach { indexByKey.putIfAbsent(it, i) } }

        // Each group's final home: follow its merge chain to a group that exists here.
        fun home(i: Int): Int {
            var at = i
            repeat(MAX_HOPS) {
                val next = keysOf(groups[at]).firstNotNullOfOrNull { k ->
                    mergeTarget(k, merges)?.let(indexByKey::get)?.takeIf { it != at }
                } ?: return at
                if (next == i) return i // loop back to the start: stay put
                at = next
            }
            return at
        }

        val homes = IntArray(groups.size) { home(it) }
        // A home that itself moves (only possible inside a loop) would drop the group: keep it put.
        val settled = homes.copyOf()
        homes.indices.forEach { i -> if (homes[homes[i]] != homes[i]) settled[i] = i }
        settled.copyInto(homes)
        val joining = HashMap<Int, MutableList<T>>()
        groups.indices.forEach { i -> if (homes[i] != i) joining.getOrPut(homes[i]) { ArrayList() } += groups[i] }
        if (joining.isEmpty()) return groups
        return groups.indices.mapNotNull { i ->
            when {
                homes[i] != i -> null
                else -> joining[i]?.let { absorb(groups[i], it) } ?: groups[i]
            }
        }
    }
}
