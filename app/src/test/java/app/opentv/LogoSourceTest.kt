/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.core.LogoPack
import app.opentv.core.LogoSource
import app.opentv.data.model.Channel
import app.opentv.data.model.logo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogoSourceTest {
    private fun ch(key: String, provider: String?, guide: String?) = Channel(
        sourceId = 1, streamId = key, name = key, groupKey = key,
        categoryId = null, logoUrl = provider, guideLogoUrl = guide, epgChannelId = null,
    )

    private val pack = LogoPack(mapOf("milan" to "pack/milan.svg"), mapOf("rai1" to "picked/rai1.png"))

    @Test fun `provider only ignores pack and guide`() {
        assertEquals("p.png", ch("milan", "p.png", "g.png").logo(LogoSource.PROVIDER, pack))
        assertNull(ch("milan", null, "g.png").logo(LogoSource.PROVIDER, pack))
    }

    @Test fun `provider then external fills the gaps, pack before guide`() {
        assertEquals("p.png", ch("milan", "p.png", "g.png").logo(LogoSource.PROVIDER_THEN_EXTERNAL, pack))
        assertEquals("pack/milan.svg", ch("milan", null, "g.png").logo(LogoSource.PROVIDER_THEN_EXTERNAL, pack))
        assertEquals("g.png", ch("inter", "", "g.png").logo(LogoSource.PROVIDER_THEN_EXTERNAL, pack))
    }

    @Test fun `external then provider`() {
        assertEquals("pack/milan.svg", ch("milan", "p.png", "g.png").logo(LogoSource.EXTERNAL_THEN_PROVIDER, pack))
        assertEquals("g.png", ch("inter", "p.png", "g.png").logo(LogoSource.EXTERNAL_THEN_PROVIDER, pack))
        assertEquals("p.png", ch("inter", "p.png", null).logo(LogoSource.EXTERNAL_THEN_PROVIDER, pack))
    }

    @Test fun `a logo picked by hand always wins`() {
        LogoSource.values().forEach { source ->
            assertEquals("picked/rai1.png", ch("rai1", "p.png", "g.png").logo(source, pack))
        }
    }
}
