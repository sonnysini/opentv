/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.player.QualityNames
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Same-quality feeds get numbered; a quality with one feed keeps its plain name. */
class QualityNamesTest {

    @Test
    fun `numbers only repeated qualities`() {
        val names = QualityNames.of(
            listOf(
                1L to "4K · SkyNet",
                2L to "FHD · Hermes",
                3L to "FHD · SAT",
                4L to "HD",
            ),
        )
        assertThat(names.getValue(1)).isEqualTo(QualityNames.Name("4K", "4K · SkyNet"))
        assertThat(names.getValue(2)).isEqualTo(QualityNames.Name("FHD 1", "FHD 1 · Hermes"))
        assertThat(names.getValue(3)).isEqualTo(QualityNames.Name("FHD 2", "FHD 2 · SAT"))
        assertThat(names.getValue(4)).isEqualTo(QualityNames.Name("HD", "HD"))
    }

    @Test
    fun `quality is the first word of the label`() {
        val names = QualityNames.of(listOf(1L to "FHD", 2L to "FHD HEVC", 3L to "RAW 60fps"))
        assertThat(names.getValue(1).full).isEqualTo("FHD 1")
        assertThat(names.getValue(2)).isEqualTo(QualityNames.Name("FHD 2", "FHD 2 HEVC"))
        assertThat(names.getValue(3)).isEqualTo(QualityNames.Name("RAW", "RAW 60fps"))
    }

    @Test
    fun `empty labels stay empty`() {
        val names = QualityNames.of(listOf(1L to "", 2L to ""))
        assertThat(names.getValue(1)).isEqualTo(QualityNames.Name("", ""))
        assertThat(names.getValue(2)).isEqualTo(QualityNames.Name("", ""))
    }
}
