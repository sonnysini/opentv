/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.football

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Names as the provider's list really has them (after ChannelNameNormalizer). */
class FeedLabelTest {
    private fun check(name: String, label: String, want: FeedLabel.Parts) =
        assertEquals(want, FeedLabel.split(name, label))

    @Test fun teamChannelWithSource() = check("Lazio", "FHD · DVS", FeedLabel.Parts("Lazio", "FHD", "DVS"))

    @Test fun teamChannelPlain() = check("Sassuolo", "HD", FeedLabel.Parts("Sassuolo", "HD", ""))

    @Test fun rawIsASource() = check("Lazio", "RAW", FeedLabel.Parts("Lazio", "", "RAW"))

    @Test fun zoneNumberShortened() =
        check("ZONA DAZN SERIE A 1", "HD", FeedLabel.Parts("Zona DAZN SA1", "HD", ""))

    @Test fun zoneWithoutNumberKeepsSerieA() =
        check("ZONA DAZN SERIE A", "FHD", FeedLabel.Parts("Zona DAZN Serie A", "FHD", ""))

    @Test fun codecGoesWithTheSource() =
        check("Zona Dazn", "FHD HEVC · SAT", FeedLabel.Parts("Zona Dazn", "FHD", "HEVC · SAT"))

    @Test fun skyKeepsItsNumber() =
        check("Sky Sport 251 IT", "FHD · Alt", FeedLabel.Parts("Sky Sport 251", "FHD", "ALT"))

    @Test fun mixedCaseLeftAlone() = check("Zona Serie A 2", "SD", FeedLabel.Parts("Zona SA2", "SD", ""))

    @Test fun resolutions() {
        assertEquals("1080p", FeedLabel.resolution(1080))
        assertEquals("720p", FeedLabel.resolution(720))
        assertEquals("4K", FeedLabel.resolution(2160))
        assertEquals("576p", FeedLabel.resolution(576))
    }

    @Test fun smoothOnlyWhenSmooth() {
        assertNull(FeedLabel.smooth(25))
        assertNull(FeedLabel.smooth(null))
        assertEquals("50 FPS", FeedLabel.smooth(50))
        assertEquals("60 FPS", FeedLabel.smooth(60))
    }
}
