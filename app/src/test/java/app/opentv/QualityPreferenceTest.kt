/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.core.PreferredQuality
import app.opentv.core.QualityPreference
import app.opentv.data.model.Channel
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Preferred quality plays first; the fallback steps down, then RAW/HEVC, then up. */
class QualityPreferenceTest {

    private fun ch(id: Long, rank: Int, label: String) = Channel(
        id = id, sourceId = 1, streamId = "$id", name = "Rai 1 $label", groupKey = "rai1",
        qualityRank = rank, qualityLabel = label, categoryId = null, logoUrl = null,
        epgChannelId = null, number = null, streamUrl = "",
    )

    private val uhd = ch(1, 400, "4K")
    private val fhd = ch(2, 300, "FHD")
    private val hd = ch(3, 200, "HD")
    private val sd = ch(4, 100, "SD")
    private val raw = ch(5, 0, "RAW")
    private val hevc = ch(6, 0, "HEVC")
    private val all = listOf(sd, raw, hevc, uhd, hd, fhd)

    private fun labels(p: PreferredQuality, list: List<Channel> = all) =
        QualityPreference.order(list, p).map { it.qualityLabel }

    @Test
    fun bestIsHighestFirst() {
        assertThat(labels(PreferredQuality.BEST)).containsExactly("4K", "FHD", "HD", "SD", "RAW", "HEVC").inOrder()
    }

    @Test
    fun fhdStepsDownThenCodecsThenUp() {
        assertThat(labels(PreferredQuality.FHD)).containsExactly("FHD", "HD", "SD", "RAW", "HEVC", "4K").inOrder()
    }

    @Test
    fun missingChoiceTakesNextLower() {
        assertThat(labels(PreferredQuality.HD, listOf(fhd, sd, raw)).first()).isEqualTo("SD")
        assertThat(labels(PreferredQuality.SD, listOf(fhd, hd)).first()).isEqualTo("HD")
    }

    @Test
    fun codecChoiceFirstThenBest() {
        assertThat(labels(PreferredQuality.HEVC)).containsExactly("HEVC", "4K", "FHD", "HD", "SD", "RAW").inOrder()
    }

    @Test
    fun plainLabelBeatsExtrasAtSameRank() {
        val fhdHevc = ch(7, 300, "FHD HEVC")
        assertThat(labels(PreferredQuality.FHD, listOf(fhdHevc, fhd)).first()).isEqualTo("FHD")
    }
}
