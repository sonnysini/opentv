/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.player.ScoreboardMask
import app.opentv.player.StreamProbe
import app.opentv.ui.football.AutoScan
import app.opentv.ui.football.AutoScan.Verdict
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Ranking a match's feeds: 50 fps first, the right game, never an empty list. */
class AutoScanTest {

    @Test
    fun `reads the scoreboards seen on the Fire`() {
        // What Tesseract made of real frames on the Fire (Sky Bundesliga, Zona DAZN, Sky Serie C).
        assertThat(AutoScan.parseBoard("7229 SCF BAY")!!.codes).containsExactly("SCF", "BAY")
        assertThat(AutoScan.parseBoard("73:56 SCF BAY")!!.codes).containsExactly("SCF", "BAY")
        assertThat(AutoScan.parseBoard("NAP ROM")!!.codes).containsExactly("NAP", "ROM")
        assertThat(AutoScan.parseBoard("XN AZP00 SAL MN")!!.codes).containsExactly("AZP", "SAL")
        // Glued together.
        assertThat(AutoScan.parseBoard("CRO00CSR")!!.codes).containsExactly("CRO", "CSR")
        assertThat(AutoScan.parseBoard("7356SCFBAY")!!.codes).containsExactly("SCF", "BAY")
        // No scoreboard: other text, logo crumbs.
        assertThat(AutoScan.parseBoard("RACE2 MN")).isNull()
        assertThat(AutoScan.parseBoard("PULSETV")).isNull()
        assertThat(AutoScan.parseBoard("PI")).isNull()
        assertThat(AutoScan.parseBoard("")).isNull()
    }

    @Test
    fun `team codes`() {
        assertThat(AutoScan.fits("JUV", "Juventus")).isTrue()
        assertThat(AutoScan.fits("SCF", "SC Freiburg")).isTrue()
        assertThat(AutoScan.fits("BAY", "Bayern München")).isTrue()
        assertThat(AutoScan.fits("HVE", "Hellas Verona")).isTrue()
        assertThat(AutoScan.fits("ROM", "Roma")).isTrue()
        assertThat(AutoScan.fits("AZP", "AZ Picerno")).isTrue()
        assertThat(AutoScan.fits("CSR", "Casarano")).isTrue()
        assertThat(AutoScan.fits("NAP", "Roma")).isFalse()
        assertThat(AutoScan.fits("INT", "Juventus")).isFalse()
    }

    @Test
    fun `game check`() {
        val board = AutoScan.parseBoard("07:27 NAP 0-0 ROM")!!
        assertThat(AutoScan.gameCheck(board, "Napoli", "Roma")).isEqualTo(Verdict.CONFIRMED)
        assertThat(AutoScan.gameCheck(board, "Lecce", "Juventus")).isEqualTo(Verdict.WRONG_GAME)
        // One code only: can't tell.
        assertThat(AutoScan.gameCheck(AutoScan.parseBoard("07:27 NAP")!!, "Lecce", "Juventus")).isNull()
    }

    private fun probe(height: Int, fps: Int, board: String? = null, millis: Long = 800) =
        AutoScan.Probe(height, fps, 2, millis, board?.let { AutoScan.parseBoard(it) })

    @Test
    fun `50 fps beats resolution, dead feeds last`() {
        val ranked = AutoScan.rank(
            listOf("fhd25" to probe(1080, 25), "hd50" to probe(720, 50), "dead" to null),
            "Lecce", "Juventus",
        )
        assertThat(ranked.map { it.item }).containsExactly("hd50", "fhd25", "dead").inOrder()
        assertThat(ranked.last().verdict).isEqualTo(Verdict.DEAD)
    }

    @Test
    fun `another game is ruled out, the right one comes first`() {
        val ranked = AutoScan.rank(
            listOf(
                "other" to probe(1080, 50, "NAP ROM"),
                "right" to probe(720, 25, "LEC JUV"),
                "unread" to probe(720, 25),
            ),
            "Lecce", "Juventus",
        )
        val v = ranked.associate { it.item to it.verdict }
        assertThat(v["other"]).isEqualTo(Verdict.WRONG_GAME)
        assertThat(v["right"]).isEqualTo(Verdict.CONFIRMED)
        assertThat(ranked.map { it.item }).containsExactly("right", "unread", "other").inOrder()
    }

    @Test
    fun `a feed without a readable scoreboard takes its channel's verdict`() {
        val ranked = AutoScan.rank(
            listOf(
                "zona/hevc" to probe(1080, 50),
                "zona/sat" to probe(720, 50, "NAP ROM"),
                "lazio/fhd" to probe(1080, 25),
            ),
            "Sassuolo", "Lazio",
            channel = { it.substringBefore('/') },
        )
        val v = ranked.associate { it.item to it.verdict }
        assertThat(v["zona/hevc"]).isEqualTo(Verdict.WRONG_GAME)
        assertThat(ranked.first().item).isEqualTo("lazio/fhd")
    }

    @Test
    fun `a check that rules out everything is ignored`() {
        val ranked = AutoScan.rank(
            listOf("a" to probe(1080, 50, "NAP ROM"), "b" to probe(720, 25, "NAP ROM")),
            "Lecce", "Juventus",
        )
        assertThat(ranked.map { it.verdict }).doesNotContain(Verdict.WRONG_GAME)
        assertThat(ranked.first().item).isEqualTo("a")
    }

    @Test
    fun `mask keeps letters in a row, drops boxes and lone blobs`() {
        // A 1080p frame's corner: three 24-line "letters" in a row, a big light box, a lone blob.
        val w = 400
        val h = 160
        val px = IntArray(w * h) { DARK }
        fun fill(x0: Int, y0: Int, x1: Int, y1: Int) {
            for (y in y0 until y1) for (x in x0 until x1) px[y * w + x] = LIGHT
        }
        fill(20, 60, 36, 84); fill(42, 60, 58, 84); fill(64, 60, 80, 84)
        fill(150, 40, 300, 120)
        fill(350, 10, 366, 34)
        val ink = ScoreboardMask.letters(px, w, h, frameHeight = 1080)!!
        assertThat(ink[70 * w + 25].toInt()).isEqualTo(1)
        assertThat(ink[70 * w + 70].toInt()).isEqualTo(1)
        assertThat(ink[80 * w + 200].toInt()).isEqualTo(0)
        assertThat(ink[20 * w + 355].toInt()).isEqualTo(0)
        // Nothing letter-like at all.
        assertThat(ScoreboardMask.letters(IntArray(w * h) { DARK }, w, h, 1080)).isNull()
    }

    @Test
    fun `fps from timestamps in decode order`() {
        val fifty = listOf(0L, 40_000, 20_000, 80_000, 60_000, 100_000, 120_000)
        assertThat(StreamProbe.fpsOf(fifty)).isEqualTo(50)
        assertThat(StreamProbe.fpsOf((0..10).map { it * 40_000L })).isEqualTo(25)
    }

    private companion object {
        const val DARK = 0xFF102040.toInt()
        const val LIGHT = 0xFFF0F0F0.toInt()
    }

    @Test
    fun `bitrate breaks ties between equal feeds, never beats a step in lines or 50 fps`() {
        fun p(h: Int, fps: Int, kbps: Int?) = AutoScan.Probe(h, fps, 2, 500, null, kbps = kbps)
        val heavy = AutoScan.quality(p(1080, 50, 8000))
        val light = AutoScan.quality(p(1080, 50, 4000))
        assertThat(heavy).isGreaterThan(light)
        assertThat(AutoScan.quality(p(1080, 50, null))).isLessThan(light)
        assertThat(AutoScan.quality(p(720, 50, 30000))).isLessThan(AutoScan.quality(p(1080, 50, 500)))
        assertThat(AutoScan.quality(p(1080, 25, 30000))).isLessThan(AutoScan.quality(p(720, 50, 500)))
    }

    @Test
    fun `bitrate from the samples fetched`() {
        // 50 frames at 50 fps (1 s of video), 1 MB in all: 8 Mbps.
        val times = (0 until 50).map { it * 20_000L }
        assertThat(StreamProbe.kbpsOf(1_000_000, times)).isEqualTo(8000)
        // Decode order doesn't matter.
        assertThat(StreamProbe.kbpsOf(1_000_000, times.reversed())).isEqualTo(8000)
        // Under half a second: no figure.
        assertThat(StreamProbe.kbpsOf(1_000_000, times.take(10))).isNull()
    }
}
