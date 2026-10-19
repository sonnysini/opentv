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
    )

    /** One `<channel>` of one guide, as the index is built from. */
    data class GuideChannel(
        val epgId: String,
        val name: String,
        val feedId: Long = 0,
        val weight: Int = 0,
    )

    class Index internal constructor(
        private val exact: Map<String, String>,
        private val sortedKeys: List<Alias>,
    ) {
        /** Returns the epg id for a provider channel's group key, or null. */
        fun match(groupKey: String): String? {
            if (groupKey.length < MIN_KEY_LENGTH) return null

            exact[groupKey]?.let { return it }

            // Prefix tier: the provider name extends the guide name or vice versa
            // ("skysportsmainevent" vs "skysportsmainevt"; "bbcone" vs "bbconelondon").
            // Only accepted when no single guide offers two different channels for it.
            // Never across a timeshift: `rai1+1` extends `rai1`, but its guide is an hour late.
            val shifted = '+' in groupKey
            val hits = sortedKeys.filter { alias ->
                alias.key.length >= MIN_KEY_LENGTH &&
                    ('+' in alias.key) == shifted &&
                    (alias.key.startsWith(groupKey) || groupKey.startsWith(alias.key))
            }
            return pick(hits)
        }
    }

    /**
     * Chooses among the guide channels that fit one name.
     *
     * Inside one guide, two different ids for a name are two different channels (regions,
     * `CNN` US vs international) — refuse to guess. Across guides, they are the same channel
     * published twice (`Rai3.it` here, `Rai.3.HD.it` there): take the one with more guide.
     */
    private fun pick(candidates: List<Alias>): String? {
        if (candidates.isEmpty()) return null
        val ambiguous = candidates.groupBy { it.feedId }.values.any { perFeed ->
            perFeed.map { it.epgId }.distinct().size > 1
        }
        if (ambiguous) return null
        return candidates.maxBy { it.weight }.epgId
    }

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
        // apart; pick() drops the key rather than choose a side.
        val exact = HashMap<String, String>()
        for ((key, aliases) in byKey) pick(aliases)?.let { exact[key] = it }

        return Index(exact, all)
    }

    /**
     * Below this, keys are too generic to mean anything — `e4` is real, but `tv`, `hd` and
     * single letters match everything. 2 keeps `e4`/`5usa`-style names alive.
     */
    private const val MIN_KEY_LENGTH = 2
}
