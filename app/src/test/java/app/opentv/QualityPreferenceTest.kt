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

/** Preferred quality plays first (HEVC > RAW > plain within it); the fallback steps down, then up. */
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
    private val raw = ch(5, 310, "RAW")
    private val hevc = ch(6, 320, "HEVC")
    private val all = listOf(sd, raw, hevc, uhd, hd, fhd)

    private fun labels(p: PreferredQuality, list: List<Channel> = all) =
        QualityPreference.order(list, p).map { it.qualityLabel }

    @Test
    fun bestIsHighestFirst() {
        assertThat(labels(PreferredQuality.BEST)).containsExactly("4K", "HEVC", "RAW", "FHD", "HD", "SD").inOrder()
    }

    @Test
    fun fhdPrefersHevcRawThenStepsDownThenUp() {
        assertThat(labels(PreferredQuality.FHD)).containsExactly("HEVC", "RAW", "FHD", "HD", "SD", "4K").inOrder()
    }

    @Test
    fun missingChoiceTakesNextLower() {
        assertThat(labels(PreferredQuality.HD, listOf(fhd, sd, raw)).first()).isEqualTo("SD")
        assertThat(labels(PreferredQuality.SD, listOf(fhd, hd)).first()).isEqualTo("HD")
    }

    @Test
    fun codecChoiceFirstThenBest() {
        assertThat(labels(PreferredQuality.HEVC)).containsExactly("HEVC", "4K", "RAW", "FHD", "HD", "SD").inOrder()
    }

    @Test
    fun unlabelledFeedsComeAfterTheLadder() {
        val plain = ch(7, 0, "")
        assertThat(labels(PreferredQuality.FHD, listOf(plain, uhd, sd)))
            .containsExactly("SD", "", "4K").inOrder()
    }

    @Test
    fun preferredSourcesGoFirstInTheirOrder() {
        val aHevc = ch(10, 320, "HEVC · Alpha")
        val aFhd = ch(11, 300, "FHD · Alpha")
        val bFhd = ch(12, 300, "FHD · Beta")
        val cHevc = ch(13, 320, "HEVC · Gamma")
        val list = listOf(aHevc, aFhd, bFhd, cHevc)
        val order = QualityPreference.order(list, PreferredQuality.FHD, listOf("beta", "Alpha"))
            .map { it.qualityLabel }
        // Beta, then Alpha (quality order inside it), then the unlisted Gamma.
        assertThat(order).containsExactly("FHD · Beta", "HEVC · Alpha", "FHD · Alpha", "HEVC · Gamma").inOrder()
    }

    @Test
    fun noPreferredSourcesKeepsTheQualityOrder() {
        assertThat(QualityPreference.order(all, PreferredQuality.FHD, emptyList()))
            .isEqualTo(QualityPreference.order(all, PreferredQuality.FHD))
    }

    @Test
    fun sourceOfReadsTheLastPart() {
        assertThat(QualityPreference.sourceOf("FHD · Alpha")).isEqualTo("Alpha")
        assertThat(QualityPreference.sourceOf("Alpha")).isEqualTo("Alpha")
        assertThat(QualityPreference.sourceOf("")).isNull()
    }
}
