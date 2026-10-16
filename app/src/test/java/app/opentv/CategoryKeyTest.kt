/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.data.model.Category
import app.opentv.data.model.StreamKind
import app.opentv.data.repo.CatalogRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The on/off switch must fold exactly like the rail shows, and never across kinds. */
class CategoryKeyTest {

    private fun cat(name: String, kind: StreamKind, id: String = name) =
        Category(id = id, sourceId = 1, name = name, kind = kind)

    @Test
    fun `codec-split live categories share one switch`() {
        val hd = CatalogRepository.categoryKey(cat("UK| GENERAL HD/RAW", StreamKind.LIVE, "1"))
        val hevc = CatalogRepository.categoryKey(cat("UK| GENERAL hevc", StreamKind.LIVE, "2"))
        assertThat(hd).isEqualTo(hevc)
    }

    @Test
    fun `vod shelves that differ only by quality stay separate`() {
        val uhd = CatalogRepository.categoryKey(cat("◈ Film 4k UHD ◈", StreamKind.MOVIE))
        val fhd = CatalogRepository.categoryKey(cat("◈ Film FHD 1080p ◈", StreamKind.MOVIE))
        assertThat(uhd).isNotEqualTo(fhd)
    }

    @Test
    fun `same name in live and movies are different switches`() {
        val live = CatalogRepository.categoryKey(cat("Disney+", StreamKind.LIVE))
        val movie = CatalogRepository.categoryKey(cat("Disney+", StreamKind.MOVIE))
        assertThat(live).isNotEqualTo(movie)
    }
}
