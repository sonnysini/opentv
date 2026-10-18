/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.parser

/**
 * Finds channel rows that are probably one channel under two names, for the user to confirm.
 *
 * [ChannelNameNormalizer] only folds what it can prove (`Sky MTV` next to `MTV`). The rest —
 * `Milan TV` vs `Milan Channel`, `Caccia e Pesca` vs `Pesca e Caccia`, a typo in one list — is
 * a guess, so it is offered as a suggestion instead: the user says Merge or Ignore, once.
 *
 * Three signals, strongest first:
 *  - **Same guide**: two rows whose channels point at the same guide id.
 *  - **Same words**: the same words in another order.
 *  - **Spelling**: one letter apart, on names long enough for that to mean a typo, or a
 *    timeshift that lost its plus sign (`Sky Cinema Uno 24` next to `Sky Cinema Uno +24`).
 *
 * Pure and fast on a 25k-channel list: no pairwise scan, everything goes through hash buckets.
 */
object MergeSuggester {

    enum class Reason { GUIDE, WORDS, SPELLING }

    /** One row of the list: its group key, the name it shows, its channel count and guide ids. */
    data class Group(val key: String, val name: String, val channels: Int, val guideIds: Set<String>)

    /** [from] would fold into [into]. */
    data class Suggestion(val from: Group, val into: Group, val reason: Reason) {
        val pairId: String get() = pairId(from.key, into.key)
    }

    /** Order-free id of a pair, as stored for "Ignore". */
    fun pairId(a: String, b: String): String = if (a < b) "$a|$b" else "$b|$a"

    /** A guide id shared by more rows than this is a generic one (`DAZN` for 30 event feeds). */
    private const val MAX_ROWS_PER_GUIDE_ID = 3
    private const val MIN_SPELLING_LENGTH = 7
    // `TV` / `Channel` are NOT filler: `Harry Potter` and `Harry Potter Channel` are two channels.
    private val FILLER_WORDS = setOf("e", "and", "&")
    /** A spelling edit inside a word this short is a different channel (`Serie A`/`B`, `NFL`/`EFL`, `Mix`/`Max`). */
    private const val MAX_SHORT_WORD = 3

    fun suggest(groups: Collection<Group>, ignored: Set<String> = emptySet(), limit: Int = 200): List<Suggestion> {
        val byKey = groups.filter { it.key.isNotEmpty() }.associateBy { it.key }
        val seen = HashSet<String>()
        val out = ArrayList<Suggestion>()
        /** [intoB]: [b] keeps its name whatever the sizes. */
        fun offer(a: String, b: String, reason: Reason, checked: Boolean = true, intoB: Boolean = false) {
            if (a == b || (checked && !compatible(a, b))) return
            val id = pairId(a, b)
            if (id in ignored || !seen.add(id)) return
            val ga = byKey[a] ?: return
            val gb = byKey[b] ?: return
            // The bigger row keeps its name; a tie goes to the shorter, plainer key.
            val aWins = !intoB && (ga.channels > gb.channels || (ga.channels == gb.channels && a.length <= b.length))
            out += if (aWins) Suggestion(gb, ga, reason) else Suggestion(ga, gb, reason)
        }

        val byGuide = HashMap<String, MutableSet<String>>()
        for (g in byKey.values) for (id in g.guideIds) byGuide.getOrPut(id.lowercase()) { HashSet() } += g.key
        for (keys in byGuide.values) {
            if (keys.size !in 2..MAX_ROWS_PER_GUIDE_ID) continue
            val sorted = keys.sorted()
            for (i in sorted.indices) for (j in i + 1 until sorted.size) offer(sorted[i], sorted[j], Reason.GUIDE)
        }

        val byWords = HashMap<String, MutableList<String>>()
        for (g in byKey.values) {
            val words = wordsOf(g.name)
            if (words.size < 2) continue
            byWords.getOrPut(words.sorted().joinToString(" ")) { ArrayList() } += g.key
        }
        for (keys in byWords.values) if (keys.size == 2) offer(keys[0], keys[1], Reason.WORDS)

        // One edit apart ⇔ the two keys share a one-letter deletion (or one IS the other's deletion).
        val byDeletion = HashMap<String, MutableList<String>>()
        for (key in byKey.keys) {
            if (key.length < MIN_SPELLING_LENGTH || '+' in key) continue
            byDeletion.getOrPut(key) { ArrayList() } += key
            for (i in key.indices) byDeletion.getOrPut(key.removeRange(i, i + 1)) { ArrayList() } += key
        }
        for (keys in byDeletion.values) {
            if (keys.size !in 2..3) continue
            val distinct = keys.distinct()
            for (i in distinct.indices) for (j in i + 1 until distinct.size) {
                val a = byKey.getValue(distinct[i])
                val b = byKey.getValue(distinct[j])
                if (typoInLongWord(a.name, b.name)) offer(a.key, b.key, Reason.SPELLING)
            }
        }

        // `skycinemauno24` is `skycinemauno+24` with the plus lost. Two-digit shifts only:
        // `Rai 1` is not `Rai +1`.
        for (key in byKey.keys) {
            val shift = key.substringAfter('+', "")
            if (shift.length < 2 || !shift.all(Char::isDigit)) continue
            val lost = key.replace("+", "")
            if (lost in byKey) offer(lost, key, Reason.SPELLING, checked = false, intoB = true)
        }

        return out.sortedBy { it.reason.ordinal }.take(limit)
    }

    /**
     * Pairs that can never be one channel: different numbers (`Rai 1`/`Rai 2`, `Box 01`/`Box 10`)
     * or different timeshift (`+1`).
     */
    private fun compatible(a: String, b: String): Boolean =
        a.substringAfter('+', "") == b.substringAfter('+', "") && a.filter(Char::isDigit) == b.filter(Char::isDigit)

    /** The words the two names don't share are all long ones: `Fake Taxy`/`Taxi`, not `Serie A`/`B`. */
    private fun typoInLongWord(a: String, b: String): Boolean {
        val wa = a.lowercase().split(NON_WORD).filter { it.isNotEmpty() }.toSet()
        val wb = b.lowercase().split(NON_WORD).filter { it.isNotEmpty() }.toSet()
        val differing = (wa - wb) + (wb - wa)
        return differing.isNotEmpty() && differing.all { it.length > MAX_SHORT_WORD }
    }

    private fun wordsOf(name: String): List<String> =
        name.lowercase().split(NON_WORD).filter { it.isNotEmpty() && it !in FILLER_WORDS }

    private val NON_WORD = Regex("""[^\p{L}\p{Nd}&]+""")
}
