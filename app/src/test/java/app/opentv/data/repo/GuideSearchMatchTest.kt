/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.repo

import app.opentv.ui.search.SearchViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuideSearchMatchTest {
    @Test fun everyWordIsAPrefix() {
        assertEquals("juve*", GuideSearchIndex.matchExpression("Juve"))
        assertEquals("milan* inter*", GuideSearchIndex.matchExpression("  Milan-Inter "))
    }

    @Test fun quotesAndOperatorsDontReachFts() {
        assertEquals("rai* 1*", GuideSearchIndex.matchExpression("\"rai\" 1*"))
        assertNull(GuideSearchIndex.matchExpression("-- * ()"))
    }

    @Test fun accentsAndCaseFold() {
        assertEquals("bodo/glimt", SearchViewModel.fold("Bodø/Glimt"))
        assertEquals("citta", SearchViewModel.fold("Città"))
    }
}
