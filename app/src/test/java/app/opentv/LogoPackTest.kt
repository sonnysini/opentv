/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.core.LogoPack
import app.opentv.data.parser.ChannelNameNormalizer.groupKeyOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogoPackTest {
    private val index = """
        {"logos": {"Milan": "milan.svg", "Hellas Verona": "verona.svg", "Südtirol": "crests/sudtirol.svg",
                   "Cesena": "https://cdn.example/cesena.svg"}}
    """.trimIndent()

    private val pack = LogoPack(LogoPack.parse("https://example.org/pack/index.json", index))

    @Test fun `names are folded like group keys and paths resolve against the index`() {
        assertEquals("https://example.org/pack/milan.svg", pack.byKey["milan"])
        assertEquals("https://example.org/pack/verona.svg", pack.byKey["hellasverona"])
        assertEquals("https://example.org/pack/crests/sudtirol.svg", pack.byKey["sudtirol"])
        assertEquals("https://cdn.example/cesena.svg", pack.byKey["cesena"])
    }

    @Test fun `bare channel and DAZN team feeds take the crest`() {
        val milan = "https://example.org/pack/milan.svg"
        assertEquals(milan, pack.logoFor(groupKeyOf("Milan")))
        assertEquals(milan, pack.logoFor(groupKeyOf("DAZN Serie A Milan")))
        assertEquals(milan, pack.logoFor(groupKeyOf("DAZN SERIE A 3 MILAN")))
        assertEquals("https://cdn.example/cesena.svg", pack.logoFor(groupKeyOf("DAZN 5 SERIE B WEB CESENA")))
        assertEquals("https://example.org/pack/crests/sudtirol.svg", pack.logoFor(groupKeyOf("Sudtirol")))
    }

    @Test fun `other channels keep their own logo`() {
        assertNull(pack.logoFor(groupKeyOf("Milan TV")))
        assertNull(pack.logoFor(groupKeyOf("DAZN Serie A Women Milan")))
        assertNull(pack.logoFor(groupKeyOf("Rai 1")))
        assertNull(LogoPack.EMPTY.logoFor("milan"))
    }

    @Test fun `a logo picked by hand beats the pack, for that channel only`() {
        val red = "https://provider.example/skycalcio-red.png"
        val picked = LogoPack(pack.byKey, mapOf("milan" to red, "skycalcio1" to red))
        assertEquals(red, picked.logoFor("milan"))
        assertEquals(red, picked.logoFor(groupKeyOf("Sky Calcio 1")))
        assertEquals("https://example.org/pack/milan.svg", picked.logoFor(groupKeyOf("DAZN Serie A Milan")))
        assertNull(picked.logoFor(groupKeyOf("Sky Calcio 2")))
    }
}
