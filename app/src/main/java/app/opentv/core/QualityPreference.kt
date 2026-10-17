/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.core

import app.opentv.data.model.Channel

/**
 * Which quality variant of a channel plays first, and in what order the others are tried when it
 * won't start.
 *
 * A resolution choice (FHD/HD/SD) steps *down* from the chosen one, then falls to the
 * resolution-less feeds (RAW/HEVC), and only climbs to higher resolutions last — the user picked a
 * quality to cap bandwidth or to dodge a heavy feed, so a bigger stream is the last resort. A codec
 * choice (RAW/HEVC) tries the matching feeds first, then everything else best-first.
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

    /** Codec/frame-rate extras in a quality label; a plain "FHD" beats "FHD HEVC" at equal rank. */
    private val EXTRAS = Regex("\\b(RAW|HEVC|H\\.?265|AV1|\\d+\\s*FPS)\\b", RegexOption.IGNORE_CASE)

    /** [variants] in the order they should be tried: the first plays, the rest are the fallback. */
    fun order(variants: List<Channel>, preference: PreferredQuality): List<Channel> {
        val best = variants.sortedByDescending { it.qualityRank }
        val rank = preference.rank
        val token = preference.token
        return when {
            rank != null -> best.sortedWith(
                compareBy<Channel>(
                    {
                        when {
                            it.qualityRank == rank -> 0
                            it.qualityRank in 1 until rank -> 1
                            it.qualityRank <= 0 -> 2
                            else -> 3
                        }
                    },
                    // Down the ladder best-first; above the choice, the smallest step up first.
                    { if (it.qualityRank > rank) it.qualityRank else -it.qualityRank },
                    { EXTRAS.containsMatchIn(it.qualityLabel) },
                ),
            )
            token != null -> best.sortedBy { !token.containsMatchIn(it.qualityLabel) }
            else -> best
        }
    }
}
