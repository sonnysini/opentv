/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.core

import app.opentv.data.model.Channel
import app.opentv.data.parser.ChannelNameNormalizer

/**
 * Which quality variant of a channel plays first, and in what order the others are tried when it
 * won't start.
 *
 * A resolution choice (FHD/HD/SD) steps *down* from the chosen one (HEVC, then RAW, then plain
 * inside each resolution), then falls to the resolution-less feeds, and only climbs to higher
 * resolutions last — the user picked a quality to cap bandwidth or to dodge a heavy feed, so a
 * bigger stream is the last resort. A codec
 * choice (RAW/HEVC) tries the matching feeds first, then everything else best-first.
 *
 * Preferred restream sources come before all of that: labels say little about what a feed really
 * is (an "HEVC" may be 720p H.264), while one source can be reliably smoother than another, so
 * the user's source order wins and the quality rule only orders variants inside each source.
 */
enum class PreferredQuality(internal val rank: Int?, internal val token: Regex?) {
    BEST(null, null),
    FHD(300, null),
    HD(200, null),
    SD(100, null),
    RAW(null, Regex("\\bRAW\\b", RegexOption.IGNORE_CASE)),
    HEVC(null, Regex("\\b(HEVC|H\\.?265)\\b", RegexOption.IGNORE_CASE)),
}

object QualityPreference {

    /**
     * [variants] in the order they should be tried: the first plays, the rest are the fallback.
     * [preferredSources] go first in their order; variants from other sources follow.
     */
    fun order(
        variants: List<Channel>,
        preference: PreferredQuality,
        preferredSources: List<String> = emptyList(),
    ): List<Channel> {
        val byQuality = orderByQuality(variants, preference)
        if (preferredSources.isEmpty()) return byQuality
        val rankOf = preferredSources.withIndex().associate { (i, name) -> name.lowercase() to i }
        return byQuality.sortedBy { rankOf[sourceOf(it.qualityLabel)?.lowercase()] ?: Int.MAX_VALUE }
    }

    /** The restream source in a variant label: `FHD · Src` → `Src`; a label without a quality is
     * the source itself (`Src`), so a lone `FHD` simply matches no source. */
    fun sourceOf(label: String): String? =
        label.substringAfterLast(SOURCE_SEPARATOR).trim().ifEmpty { null }

    private const val SOURCE_SEPARATOR = " · "

    private fun orderByQuality(variants: List<Channel>, preference: PreferredQuality): List<Channel> {
        // qualityRank already orders HEVC > RAW > plain inside a resolution tier.
        val best = variants.sortedByDescending { it.qualityRank }
        val rank = preference.rank
        val token = preference.token
        return when {
            rank != null -> best.sortedWith(
                compareBy<Channel>(
                    {
                        val tier = ChannelNameNormalizer.resolutionTier(it.qualityRank)
                        when {
                            tier == rank -> 0
                            tier in 1 until rank -> 1
                            tier <= 0 -> 2
                            else -> 3
                        }
                    },
                    // Down the ladder best-first; above the choice, the smallest step up first.
                    {
                        val tier = ChannelNameNormalizer.resolutionTier(it.qualityRank)
                        if (tier > rank) tier else -tier
                    },
                    { -it.qualityRank },
                ),
            )
            token != null -> best.sortedBy { !token.containsMatchIn(it.qualityLabel) }
            else -> best
        }
    }
}
