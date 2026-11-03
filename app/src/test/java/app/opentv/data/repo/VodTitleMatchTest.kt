/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.repo

import app.opentv.data.remote.TmdbTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VodTitleMatchTest {
    private fun t(title: String, year: Int?, original: String? = null, id: String = "1") = TmdbTitle(
        id = id, title = title, originalTitle = original, year = year, overview = null,
        posterUrl = null, backdropUrl = null, rating = null, genreIds = emptyList(),
    )

    @Test fun `provider junk year and quality fold away`() {
        assertEquals(vodTitleKey("Iron Man"), vodTitleKey("Iron Man (2008) FHD"))
        assertEquals(vodTitleKey("Il cavaliere oscuro"), vodTitleKey("IT - Il Cavaliere Oscuro 2008 4K"))
    }

    @Test fun `same name and year matches`() {
        assertTrue(matchesTitle("Iron Man (2008) FHD", null, null, t("Iron Man", 2008)))
        assertTrue(matchesTitle("Inception HD", 2010, "", t("Inception", 2010)))
    }

    @Test fun `original title matches too`() {
        assertTrue(matchesTitle("The Dark Knight (2008)", null, null, t("Il cavaliere oscuro", 2008, "The Dark Knight")))
    }

    @Test fun `a year one off is the same film, further is not`() {
        assertTrue(matchesTitle("Dune (2021)", null, null, t("Dune", 2020)))
        assertFalse(matchesTitle("Dune (1984)", null, null, t("Dune", 2021)))
    }

    @Test fun `a longer title is another film`() {
        assertFalse(matchesTitle("Iron Man 2 (2010)", null, null, t("Iron Man", 2008)))
    }

    @Test fun `a copy tied to another tmdb id is never taken`() {
        assertFalse(matchesTitle("Iron Man (2008)", null, "999", t("Iron Man", 2008, id = "1726")))
        assertTrue(matchesTitle("Iron Man (2008)", null, "1726", t("Iron Man", 2008, id = "1726")))
    }
}
