/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.repo

import app.opentv.data.parser.ChannelNameNormalizer

/**
 * Joins provider channels to guide channels when nobody gave us a join key.
 *
 * The provider says `UK| BBC ONE FHD`. The guide says `BBC One` (id `bbc1.uk`). The only
 * bridge between them is the name, so both sides are pushed through
 * [ChannelNameNormalizer.groupKeyOf] and matched on the result.
 *
 * ## The design rule: prefer no match to a wrong match
 *
 * A missing guide entry reads as "provider doesn't do EPG" and can be fixed with a manual
 * override. A *wrong* one shows EastEnders against a sports channel, and the user reasonably
 * concludes the whole guide is broken. So the fuzzy tier only accepts a prefix match when it
 * is **unambiguous** — exactly one guide channel fits. `bbcone` will claim `BBC One` when
 * that is the only candidate, but if the guide carries `BBC One London` *and* `BBC One
 * Wales` and nothing plain, we refuse to guess between regions.
 */
object EpgMatcher {

    /** One guide channel, pre-normalised. */
    data class Alias(
        val epgId: String,
        /** Normalised display-name or id: `bbcone`, `bbc1uk`. */
        val key: String,
        /** Which guide it came from. Two ids for one name are only ambiguous inside one guide. */
        val feedId: Long = 0,
        /** How much guide the id carries (programme count): the tie-break between guides. */
        val weight: Int = 0,
    ) {
        /** [key] split once at build time: the matcher compares these for every channel. */
        val base: String
        val shift: String
        init {
            val (b, sh) = splitShift(key)
            base = b
            shift = sh
        }
    }

    /** One `<channel>` of one guide, as the index is built from. */
    data class GuideChannel(
        val epgId: String,
        val name: String,
        val feedId: Long = 0,
        val weight: Int = 0,
    )

    /** How a match was found: an exact name says more than a prefix or a typo-tolerant one. */
    enum class Tier { EXACT, PREFIX, FUZZY }

    data class Match(val epgId: String, val tier: Tier)

    class Index internal constructor(
        private val byKey: Map<String, List<Alias>>,
        private val sortedKeys: List<Alias>,
    ) {
        private val keysById: Map<String, Set<String>> =
            sortedKeys.groupBy({ it.epgId }, { it.key }).mapValues { it.value.toSet() }

        /** Typo candidates by first letter: a typo rarely hits it, and it keeps the scan short. */
        private val byFirstLetter: Map<Char, List<Alias>> =
            sortedKeys.filter { it.base.length >= MIN_FUZZY_LENGTH }.groupBy { it.base[0] }

        /** Returns the epg id for a provider channel's group key, or null. */
        fun match(groupKey: String): String? = find(groupKey)?.epgId

        /** Only an exact name: no prefix, no typo tolerance. */
        fun exact(groupKey: String): String? =
            if (groupKey.length < MIN_KEY_LENGTH) null else byKey[groupKey]?.let(::pickPerGuide)

        /** The normalised keys a guide id is known by (`skyuno`, `skyunoit`). */
        fun keysOf(epgId: String): Set<String> = keysById[epgId].orEmpty()

        fun find(groupKey: String): Match? {
            if (groupKey.length < MIN_KEY_LENGTH) return null

            exact(groupKey)?.let { return Match(it, Tier.EXACT) }

            // Prefix tier: the provider name extends the guide name or vice versa
            // ("skysportsmainevent" vs "skysportsmainevt"; "bbcone" vs "bbconelondon").
            // Only accepted when no single guide offers two different channels for it.
            // A timeshift only meets the same timeshift: `rai1+1` extends `rai1`, but its guide
            // is an hour late, and `+2` is a prefix of `+24` without being the same channel.
            val (base, shift) = splitShift(groupKey)
            val hits = sortedKeys.filter { alias ->
                alias.key.length >= MIN_KEY_LENGTH && alias.shift == shift &&
                    (alias.base.startsWith(base) || base.startsWith(alias.base))
            }
            pickStrict(hits)?.let { return Match(it, Tier.PREFIX) }

            // Typo tier: one or two letters off on a long name (`suspence`, `documentaris`),
            // never across numbers, and only when every candidate is the same name.
            if (base.length >= MIN_FUZZY_LENGTH) {
                val near = byFirstLetter[base[0]].orEmpty().filter { alias ->
                    alias.shift == shift && alias.base != base && closeSpelling(alias.base, base)
                }
                if (near.map { it.key }.distinct().size == 1) {
                    pickPerGuide(near)?.let { return Match(it, Tier.FUZZY) }
                }
            }
            return null
        }
    }

    /**
     * Inside one guide, two different ids for a name are two different channels (regions,
     * `CNN` US vs international): that guide can't say which. Across guides, they are the same
     * channel published twice (`Rai3.it` here, `Rai.3.HD.it` there). A guide in doubt is left
     * out and the others decide; the one with more guide wins. Every guide in doubt: no match.
     */
    private fun pickPerGuide(candidates: List<Alias>): String? =
        candidates.groupBy { it.feedId }.values
            .filter { perFeed -> perFeed.map { it.epgId }.distinct().size == 1 }
            .flatten()
            .maxByOrNull { it.weight }
            ?.epgId

    /** The prefix tier is looser, so one guide in doubt is enough to refuse. */
    private fun pickStrict(candidates: List<Alias>): String? {
        if (candidates.isEmpty()) return null
        val ambiguous = candidates.groupBy { it.feedId }.values.any { perFeed ->
            perFeed.map { it.epgId }.distinct().size > 1
        }
        if (ambiguous) return null
        return candidates.maxBy { it.weight }.epgId
    }

    /** `rai1+1` → (`rai1`, `+1`); `rai1` → (`rai1`, ``). */
    fun splitShift(key: String): Pair<String, String> {
        val m = SHIFT.matchEntire(key) ?: return key to ""
        return m.groupValues[1] to "+" + m.groupValues[2]
    }

    /**
     * Whether two keys are the same name spelled a little differently: equal, one extending
     * the other, or one letter off (two on long names). Numbers are identity — `Rai Radio 1` is
     * not `Rai Radio 2`, `27 Mediaset` is not `20 Mediaset` — and so is the timeshift.
     */
    fun similar(a: String, b: String): Boolean {
        if (a.length < MIN_KEY_LENGTH || b.length < MIN_KEY_LENGTH) return false
        val (aBase, aShift) = splitShift(a)
        val (bBase, bShift) = splitShift(b)
        if (aShift != bShift) return false
        if (aBase.startsWith(bBase) || bBase.startsWith(aBase)) return true
        return closeSpelling(aBase, bBase)
    }

    private fun closeSpelling(a: String, b: String): Boolean {
        val shorter = minOf(a.length, b.length)
        if (shorter < MIN_FUZZY_LENGTH) return false
        val allowed = if (shorter >= LONG_FUZZY_LENGTH) 2 else 1
        // Cheapest checks first: this runs for every channel the exact and prefix tiers missed.
        if (kotlin.math.abs(a.length - b.length) > allowed) return false
        if (editDistance(a, b, allowed) > allowed) return false
        return numbersOf(a) == numbersOf(b)
    }

    private fun numbersOf(key: String): List<String> = DIGITS.findAll(key).map { it.value }.toList()

    /** Levenshtein distance; gives up (returns `limit + 1`) as soon as it can't stay within [limit]. */
    fun editDistance(a: String, b: String, limit: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            var rowMin = current[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                rowMin = minOf(rowMin, current[j])
            }
            if (rowMin > limit) return limit + 1
            previous = current
        }
        return previous[b.length]
    }

    private val SHIFT = Regex("""(.+)\+(\d{1,2})""")
    private val DIGITS = Regex("""\d+""")

    /**
     * Builds the lookup from every `<channel>` element seen across every enabled guide.
     *
     * Both the display-name (`BBC One`) and the raw id (`bbc1.uk`) are indexed — providers
     * that *do* fill in `epg_channel_id` often use the id form, and it costs nothing to
     * accept either.
     */
    fun buildIndex(aliases: Iterable<Pair<String, String>>): Index =
        buildIndexOf(aliases.map { (id, name) -> GuideChannel(id, name) })

    fun buildIndexOf(channels: Iterable<GuideChannel>): Index {
        val byKey = HashMap<String, MutableList<Alias>>()
        val all = ArrayList<Alias>()

        for (channel in channels) {
            for (candidate in listOf(channel.name, channel.epgId)) {
                // Full normalise, not bare groupKeyOf: free guides append 'HD' to display
                // names ('BBC One East HD'), which would otherwise never key-match a
                // provider's 'BBC ONE EAST'. normalize() strips the quality token.
                val key = ChannelNameNormalizer.normalize(candidate).groupKey
                if (key.length < MIN_KEY_LENGTH) continue
                val alias = Alias(channel.epgId, key, channel.feedId, channel.weight)
                all += alias
                byKey.getOrPut(key) { mutableListOf() } += alias
            }
        }
        // Two different channels of one guide normalising to the same key can't be told
        // apart by that guide; pickPerGuide() lets the other guides decide.
        return Index(byKey, all)
    }

    /**
     * Below this, keys are too generic to mean anything — `e4` is real, but `tv`, `hd` and
     * single letters match everything. 2 keeps `e4`/`5usa`-style names alive.
     */
    private const val MIN_KEY_LENGTH = 2

    /** Typos are only forgiven on names this long: short names differ by one letter for real. */
    private const val MIN_FUZZY_LENGTH = 8
    private const val LONG_FUZZY_LENGTH = 12
}
