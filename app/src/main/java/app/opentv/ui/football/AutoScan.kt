/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.football

import java.text.Normalizer

/**
 * "Watch automatically": every feed of a match looked at for a second, then ranked. The pieces
 * here are plain functions so they can be tested off the device.
 *
 * - Quality: real 50 fps first (football at 25 is half the motion), then resolution, 5.1 audio,
 *   a quick start.
 * - Right game: the team codes on the scoreboard in the corner ("SCF BAY") are read; a feed
 *   naming two other teams is out. Clock and score aren't looked at: the same game replayed
 *   while it's on live doesn't happen.
 * - Nothing here ever empties the list: a check that rules out every feed is the broken one.
 */
object AutoScan {
    /** What the scoreboard says: three-letter team codes as read, "SCF", "BAY". */
    data class Board(val codes: List<String>)

    enum class Verdict { OK, CONFIRMED, WRONG_GAME, DEAD }

    data class Probe(
        val height: Int,
        val fps: Int?,
        val audioChannels: Int?,
        val millis: Long,
        val board: Board?,
        /** E-AC3 (Dolby Digital Plus) audio: rare on these feeds, a little better than plain AC3/AAC. */
        val audioHd: Boolean = false,
        /** Video bitrate actually received, when measured (the scan server does): more is a better picture. */
        val kbps: Int? = null,
    )

    data class Ranked<T>(val item: T, val probe: Probe?, val verdict: Verdict, val score: Int)

    private val LETTERS = Regex("[A-Z]+")
    /** Three-letter words on screen that aren't teams. */
    private val NOT_CODES = setOf("SKY", "UNO", "FHD", "UHD", "HDR", "TIM", "VAR", "GOL", "END", "AMP", "ORA", "EUR")

    /**
     * Reads what OCR made of the scoreboard corner: runs of three capitals are codes, six are two
     * codes glued together ("CRO00CSR" reads as CRO, CSR; "SCFBAY" as SCF, BAY). Null when none.
     */
    fun parseBoard(text: String): Board? {
        val codes = LETTERS.findAll(text).flatMap { m ->
            when (m.value.length) {
                3 -> sequenceOf(m.value)
                6 -> sequenceOf(m.value.substring(0, 3), m.value.substring(3))
                else -> emptySequence()
            }
        }.filter { it !in NOT_CODES }.distinct().toList()
        return if (codes.isEmpty()) null else Board(codes)
    }

    /**
     * Whether [code] could be how a broadcaster shortens [team]: its letters in order, the first
     * starting a word. "JUV" Juventus, "SCF" SC Freiburg, "HVE" Hellas Verona, "ACM" AC Milan.
     */
    fun fits(code: String, team: String): Boolean {
        val words = fold(team).uppercase().split(Regex("[^A-Z]+")).filter { it.isNotEmpty() }
        val joined = words.joinToString("")
        var start = 0
        for (w in words) {
            if (w[0] == code[0] && subsequence(code, joined.substring(start))) return true
            start += w.length
        }
        return false
    }

    private fun subsequence(code: String, s: String): Boolean {
        var i = 0
        for (c in s) if (i < code.length && c == code[i]) i++
        return i == code.length
    }

    /** Right game when both teams' codes are there; wrong when two codes are read and neither team's is. */
    fun gameCheck(board: Board, home: String, away: String): Verdict? {
        val h = board.codes.any { fits(it, home) }
        val a = board.codes.any { fits(it, away) }
        return when {
            h && a -> Verdict.CONFIRMED
            !h && !a && board.codes.size >= 2 -> Verdict.WRONG_GAME
            else -> null
        }
    }

    /** Ranks [probes] (null = didn't start), best first. */
    fun <T> rank(
        probes: List<Pair<T, Probe?>>,
        home: String,
        away: String,
        /** The logical channel of an item: its feeds show the same thing, so a game check carries over. */
        channel: (T) -> String = { it.toString() },
    ): List<Ranked<T>> {
        val judged = probes.map { (item, p) ->
            if (p == null) return@map Ranked(item, null, Verdict.DEAD, Int.MIN_VALUE)
            val verdict = p.board?.let { gameCheck(it, home, away) } ?: Verdict.OK
            Ranked(item, p, verdict, quality(p) + if (verdict == Verdict.CONFIRMED) CONFIRMED_BONUS else 0)
        }
        // A feed whose scoreboard couldn't be read takes its channel's verdict: same pictures.
        val byChannel = judged.groupBy { channel(it.item) }
        val judgedAll = judged.map { r ->
            if (r.verdict != Verdict.OK) return@map r
            val siblings = byChannel.getValue(channel(r.item)).map { it.verdict }
            when {
                Verdict.CONFIRMED in siblings -> r.copy(verdict = Verdict.CONFIRMED, score = r.score + CONFIRMED_BONUS)
                Verdict.WRONG_GAME in siblings -> r.copy(verdict = Verdict.WRONG_GAME)
                else -> r
            }
        }
        // Never rule out every feed: a check that does is wrong itself.
        val usable = judgedAll.filter { it.verdict == Verdict.OK || it.verdict == Verdict.CONFIRMED }
        val out = if (usable.isEmpty()) {
            judgedAll.map { if (it.verdict == Verdict.WRONG_GAME) it.copy(verdict = Verdict.OK) else it }
        } else {
            judgedAll
        }
        return out.sortedWith(
            compareBy<Ranked<T>> { if (it.verdict == Verdict.OK || it.verdict == Verdict.CONFIRMED) 0 else if (it.verdict == Verdict.DEAD) 2 else 1 }
                .thenByDescending { it.score },
        )
    }

    /**
     * The quality part of the score: 50 fps above all, then lines, then bitrate (15 a Mbps, never
     * enough to beat a step in lines), 5.1, E-AC3, start-up time.
     */
    fun quality(p: Probe): Int =
        (if ((p.fps ?: 0) >= 48) FPS50_BONUS else 0) + p.height / 2 +
            ((p.kbps ?: 0) / 1000).coerceAtMost(MAX_MBPS) * PER_MBPS +
            (if ((p.audioChannels ?: 0) >= 6) SURROUND_BONUS else 0) +
            (if (p.audioHd) AUDIO_HD_BONUS else 0) - (p.millis / 50).toInt()

    private fun fold(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    private const val FPS50_BONUS = 1000
    private const val SURROUND_BONUS = 50
    private const val AUDIO_HD_BONUS = 20
    private const val CONFIRMED_BONUS = 200
    private const val PER_MBPS = 15
    private const val MAX_MBPS = 10
}
