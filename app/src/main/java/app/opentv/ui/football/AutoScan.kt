/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.football

import java.text.Normalizer
import kotlin.math.abs

/**
 * "Watch automatically": every feed of a match looked at for a second, then ranked. The pieces
 * here are plain functions so they can be tested off the device.
 *
 * - Quality: real 50 fps first (football at 25 is half the motion), then resolution, 5.1 audio,
 *   a quick start.
 * - Right game: the scoreboard in the corner ("73:56 SCF BAY 0-2") is read; a feed naming two
 *   other teams is out.
 * - Live, not a replay: the scoreboard clock against the real one (FotMob's half start times) and
 *   against the other feeds; a feed minutes behind is a replay. Its score is checked against the
 *   score at *its* minute, not the live one — every feed is some seconds late — and a goal's
 *   couple of minutes either side never count.
 * - Nothing here ever empties the list: a check that rules out every feed is the broken one.
 */
object AutoScan {
    /** What the scoreboard says; any part may be missing. */
    data class Board(
        val clockSeconds: Int?,
        val home: Int?,
        val away: Int?,
        /** Three-letter team codes as read: "SCF", "BAY". */
        val codes: List<String>,
        /** "Replay", "Differita"… written on screen. */
        val replayWords: Boolean,
    )

    enum class Verdict { OK, CONFIRMED, WRONG_GAME, REPLAY, DEAD }

    data class Probe(
        val height: Int,
        val fps: Int?,
        val audioChannels: Int?,
        val millis: Long,
        val fetchedAt: Long,
        val board: Board?,
    )

    data class Ranked<T>(val item: T, val probe: Probe?, val verdict: Verdict, val score: Int, val lagSeconds: Int?)

    private val CLOCK = Regex("(?<!\\d)(\\d{1,3})\\s?[:.]\\s?(\\d{2})(?!\\d)")
    private val SCORE = Regex("(?<![\\d:.])(\\d{1,2})\\s*[-–—/]?\\s*(\\d{1,2})(?![\\d:.])")
    // Not \b: OCR glues the clock to the code ("68:42NAP").
    private val CODE = Regex("(?<![A-Za-z])[A-Z]{3}(?![A-Za-z])")
    private val REPLAY = Regex("\\b(replay|differita|highlights?|sintesi|full match|rewind)\\b", RegexOption.IGNORE_CASE)
    /** Three-letter words on screen that aren't teams. */
    private val NOT_CODES = setOf("SKY", "DAZN", "UNO", "FHD", "UHD", "HDR", "LIVE", "TIM", "VAR", "GOL", "END", "AMP", "ORA", "EUR")

    /** Reads what OCR made of the scoreboard corner. */
    fun parseBoard(text: String): Board? {
        val clock = CLOCK.find(text)
        val clockSeconds = clock?.let {
            val m = it.groupValues[1].toInt()
            val s = it.groupValues[2].toInt()
            if (s < 60 && m <= 130) m * 60 + s else null
        }
        // The score is looked for in what's left once the clock is out ("73:56 SCF BAY 0-2").
        val rest = if (clock != null) text.removeRange(clock.range) else text
        val score = SCORE.findAll(rest).firstOrNull { m ->
            // Single digits each side: "0-2", "0 2", "02". Two-digit runs are other numbers.
            val a = m.groupValues[1]
            val b = m.groupValues[2]
            a.length == 1 && b.length == 1
        }
        val codes = CODE.findAll(text).map { it.value }.filter { it !in NOT_CODES }.distinct().toList()
        val replay = REPLAY.containsMatchIn(text)
        if (clockSeconds == null && score == null && codes.isEmpty() && !replay) return null
        return Board(
            clockSeconds = clockSeconds,
            home = score?.groupValues?.get(1)?.toInt(),
            away = score?.groupValues?.get(2)?.toInt(),
            codes = codes,
            replayWords = replay,
        )
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

    /** The score at [clockSeconds] from the goals ([Triple] of clock, home, away), or null near a goal. */
    fun scoreAt(clockSeconds: Int, goals: List<Triple<Int, Int, Int>>): Pair<Int, Int>? {
        if (goals.any { abs(it.first - clockSeconds) < GOAL_MARGIN_SECONDS }) return null
        val last = goals.filter { it.first <= clockSeconds }.maxByOrNull { it.first }
        return last?.let { it.second to it.third } ?: (0 to 0)
    }

    /** The real match clock (seconds) at [at], from the halves' start times; null before or in the break. */
    fun realClock(at: Long, firstHalf: Long?, secondHalf: Long?): Int? = when {
        secondHalf != null && at >= secondHalf -> (HALF_SECONDS + (at - secondHalf) / 1000).toInt()
        firstHalf != null && at >= firstHalf -> ((at - firstHalf) / 1000).toInt()
        else -> null
    }

    /**
     * Ranks [probes] (null = didn't start), best first. [realClock] gives the true match clock at
     * a moment, when known; [goals] the goals so far.
     */
    fun <T> rank(
        probes: List<Pair<T, Probe?>>,
        home: String,
        away: String,
        realClock: (Long) -> Int?,
        goals: List<Triple<Int, Int, Int>>,
        /** The logical channel of an item: its feeds show the same thing, so a game check carries over. */
        channel: (T) -> String = { it.toString() },
    ): List<Ranked<T>> {
        val alive = probes.filter { it.second != null }
        // The clock every feed would show at one shared moment: the consensus among them.
        val now = alive.maxOfOrNull { it.second!!.fetchedAt } ?: 0L
        fun aligned(p: Probe): Int? = p.board?.clockSeconds?.let { it + ((now - p.fetchedAt) / 1000).toInt() }
        val consensus = alive.mapNotNull { aligned(it.second!!) }.sorted().let { if (it.size >= 3) it[it.size / 2] else null }

        fun lagOf(p: Probe): Int? {
            val clock = p.board?.clockSeconds ?: return null
            // A clock parked at 45:00 or 90:00 in added time says nothing about delay.
            if (clock % HALF_SECONDS == 0) return null
            realClock(p.fetchedAt)?.let { return it - clock }
            return consensus?.let { c -> c - aligned(p)!! }
        }

        val judged = probes.map { (item, p) ->
            if (p == null) return@map Ranked(item, null, Verdict.DEAD, Int.MIN_VALUE, null)
            val board = p.board
            val lag = lagOf(p)
            var verdict = board?.let { gameCheck(it, home, away) } ?: Verdict.OK
            if (verdict != Verdict.WRONG_GAME) {
                val far = lag != null && (lag > REPLAY_LAG_SECONDS || lag < -AHEAD_SECONDS)
                val expected = board?.clockSeconds?.let { scoreAt(it, goals) }
                val wrongScore = expected != null && board.home != null && board.away != null &&
                    (board.home to board.away) != expected
                // A wrong score alone may be a misread digit; with the clock far off or replay
                // written on screen it's a replay.
                if (far || (board?.replayWords == true) || (wrongScore && lag != null && lag > SUSPECT_LAG_SECONDS)) {
                    verdict = Verdict.REPLAY
                }
            }
            Ranked(item, p, verdict, quality(p) - (lag?.coerceIn(0, MAX_LAG_PENALTY) ?: 0) +
                if (verdict == Verdict.CONFIRMED) CONFIRMED_BONUS else 0, lag)
        }
        // A feed whose scoreboard couldn't be read takes its channel's verdict: same pictures.
        val byChannel = judged.groupBy { channel(it.item) }
        val judgedAll = judged.map { r ->
            if (r.verdict != Verdict.OK || r.probe?.board?.let { gameCheck(it, home, away) } != null) return@map r
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
            judgedAll.map { if (it.verdict == Verdict.WRONG_GAME || it.verdict == Verdict.REPLAY) it.copy(verdict = Verdict.OK) else it }
        } else {
            judgedAll
        }
        return out.sortedWith(
            compareBy<Ranked<T>> { if (it.verdict == Verdict.OK || it.verdict == Verdict.CONFIRMED) 0 else if (it.verdict == Verdict.DEAD) 2 else 1 }
                .thenByDescending { it.score },
        )
    }

    /** The quality part of the score: 50 fps above all, then lines, 5.1, start-up time. */
    fun quality(p: Probe): Int =
        (if ((p.fps ?: 0) >= 48) FPS50_BONUS else 0) + p.height / 2 +
            (if ((p.audioChannels ?: 0) >= 6) SURROUND_BONUS else 0) - (p.millis / 50).toInt()

    private fun fold(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    const val HALF_SECONDS = 45 * 60
    private const val GOAL_MARGIN_SECONDS = 120
    private const val REPLAY_LAG_SECONDS = 300
    private const val SUSPECT_LAG_SECONDS = 120
    private const val AHEAD_SECONDS = 120
    private const val MAX_LAG_PENALTY = 300
    private const val FPS50_BONUS = 1000
    private const val SURROUND_BONUS = 50
    private const val CONFIRMED_BONUS = 200
}
