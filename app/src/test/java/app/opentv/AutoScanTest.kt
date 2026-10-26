/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.player.StreamProbe
import app.opentv.ui.football.AutoScan
import app.opentv.ui.football.AutoScan.Verdict
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Ranking a match's feeds: 50 fps first, the right game, live not replays, never an empty list. */
class AutoScanTest {

    @Test
    fun `reads the scoreboards seen on the Fire`() {
        // What ML Kit made of real frames (Sky Calcio, Zona DAZN).
        val sky = AutoScan.parseBoard("73:56 SCF BAY 0 2")!!
        assertThat(sky.clockSeconds).isEqualTo(73 * 60 + 56)
        assertThat(sky.codes).containsExactly("SCF", "BAY")
        assertThat(sky.home to sky.away).isEqualTo(0 to 2)

        val dazn = AutoScan.parseBoard("07:27 NAP 0-0/ROM")!!
        assertThat(dazn.clockSeconds).isEqualTo(7 * 60 + 27)
        assertThat(dazn.codes).containsExactly("NAP", "ROM")
        assertThat(dazn.home to dazn.away).isEqualTo(0 to 0)

        assertThat(AutoScan.parseBoard("72:29 SCF BAY 02")!!.home).isEqualTo(0)
        // Clock glued to the first code, sponsor words after (Fire, Zona DAZN).
        val glued = AutoScan.parseBoard("68:42NAP 2-0 ROM ROGESANA ACQUA OLIGOMUA SORCESA NERA")!!
        assertThat(glued.clockSeconds).isEqualTo(68 * 60 + 42)
        assertThat(glued.codes).containsExactly("NAP", "ROM")
        assertThat(glued.home to glued.away).isEqualTo(2 to 0)
        assertThat(AutoScan.parseBoard("KALI Full Replay")!!.replayWords).isTrue()
        assertThat(AutoScan.parseBoard("sky sport")).isNull()
    }

    @Test
    fun `team codes`() {
        assertThat(AutoScan.fits("JUV", "Juventus")).isTrue()
        assertThat(AutoScan.fits("SCF", "SC Freiburg")).isTrue()
        assertThat(AutoScan.fits("BAY", "Bayern München")).isTrue()
        assertThat(AutoScan.fits("HVE", "Hellas Verona")).isTrue()
        assertThat(AutoScan.fits("ROM", "Roma")).isTrue()
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

    @Test
    fun `score at a feed's own minute, goals' margins ignored`() {
        val goals = listOf(Triple(65 * 60, 0, 1), Triple(91 * 60, 1, 1))
        assertThat(AutoScan.scoreAt(30 * 60, goals)).isEqualTo(0 to 0)
        assertThat(AutoScan.scoreAt(64 * 60 + 30, goals)).isNull()
        assertThat(AutoScan.scoreAt(70 * 60, goals)).isEqualTo(0 to 1)
    }

    @Test
    fun `real clock from the halves`() {
        val first = 1_000_000L
        val second = first + 62 * 60_000L
        assertThat(AutoScan.realClock(first - 1, first, null)).isNull()
        assertThat(AutoScan.realClock(first + 600_000, first, null)).isEqualTo(600)
        assertThat(AutoScan.realClock(second + 60_000, first, second)).isEqualTo(46 * 60)
    }

    private fun probe(height: Int, fps: Int, board: String? = null, at: Long = 0L, millis: Long = 800) =
        AutoScan.Probe(height, fps, 2, millis, at, board?.let { AutoScan.parseBoard(it) })

    @Test
    fun `50 fps beats resolution, dead feeds last`() {
        val ranked = AutoScan.rank(
            listOf("fhd25" to probe(1080, 25), "hd50" to probe(720, 50), "dead" to null),
            "Lecce", "Juventus", { null }, emptyList(),
        )
        assertThat(ranked.map { it.item }).containsExactly("hd50", "fhd25", "dead").inOrder()
        assertThat(ranked.last().verdict).isEqualTo(Verdict.DEAD)
    }

    @Test
    fun `another game and a replay are ruled out, a slightly late feed is not`() {
        val now = 10_000_000L
        val real = { at: Long -> (63 * 60 + (at - now) / 1000).toInt() }
        val ranked = AutoScan.rank(
            listOf(
                "late" to probe(720, 50, "62:20 LEC JUV 0-1", at = now),
                "other" to probe(1080, 50, "62:40 NAP ROM 0-0", at = now),
                "replay" to probe(1080, 50, "42:33 LEC JUV 0-2", at = now),
            ),
            "Lecce", "Juventus", real, listOf(Triple(20 * 60, 0, 1)),
        )
        val v = ranked.associate { it.item to it.verdict }
        assertThat(v["late"]).isEqualTo(Verdict.CONFIRMED)
        assertThat(v["other"]).isEqualTo(Verdict.WRONG_GAME)
        assertThat(v["replay"]).isEqualTo(Verdict.REPLAY)
        assertThat(ranked.first().item).isEqualTo("late")
    }

    @Test
    fun `a feed without a readable scoreboard takes its channel's verdict`() {
        val ranked = AutoScan.rank(
            listOf(
                "zona/hevc" to probe(1080, 50),
                "zona/sat" to probe(720, 50, "72:17 NAP 2-1 ROM"),
                "lazio/fhd" to probe(1080, 25),
            ),
            "Sassuolo", "Lazio", { null }, emptyList(),
            channel = { it.substringBefore('/') },
        )
        val v = ranked.associate { it.item to it.verdict }
        assertThat(v["zona/hevc"]).isEqualTo(Verdict.WRONG_GAME)
        assertThat(ranked.first().item).isEqualTo("lazio/fhd")
    }

    @Test
    fun `a check that rules out everything is ignored`() {
        // Kick-off delayed 15 minutes and FotMob's clock off: every feed "far behind".
        val ranked = AutoScan.rank(
            listOf("a" to probe(1080, 50, "10:00 LEC JUV 0-0"), "b" to probe(720, 25, "10:05 LEC JUV 0-0")),
            "Lecce", "Juventus", { 25 * 60 }, emptyList(),
        )
        assertThat(ranked.map { it.verdict }).containsNoneOf(Verdict.REPLAY, Verdict.WRONG_GAME)
        assertThat(ranked.first().item).isEqualTo("a")
    }

    @Test
    fun `fps from timestamps in decode order`() {
        val fifty = listOf(0L, 40_000, 20_000, 80_000, 60_000, 100_000, 120_000)
        assertThat(StreamProbe.fpsOf(fifty)).isEqualTo(50)
        assertThat(StreamProbe.fpsOf((0..10).map { it * 40_000L })).isEqualTo(25)
    }
}
