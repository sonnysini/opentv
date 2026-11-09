/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.repo

import app.opentv.data.repo.FootballRepository.Match
import app.opentv.data.repo.FootballRepository.Phase
import app.opentv.data.repo.FootballRepository.Team
import org.junit.Assert.assertEquals
import org.junit.Test

class NextMatchdaysTest {
    private fun game(id: Long, round: String?, phase: Phase = Phase.SCHEDULED, league: Int = FootballRepository.SERIE_A) =
        Match(id, id * 3_600_000L, Team(id, "Home$id"), Team(id + 100, "Away$id"), null, null, phase, null, round, league = league)

    @Test fun nextMatchdayWaits() {
        val today = listOf(game(1, "10", Phase.FINISHED), game(2, "10"))
        val week = listOf(game(3, "11"), game(4, "11"))
        assertEquals(emptyList<Match>(), FootballRepository.nextMatchdays(today, week))
    }

    @Test fun cupsStay() {
        val today = listOf(game(1, "10"))
        val week = listOf(game(2, null, league = FootballRepository.CHAMPIONS), game(3, "11"))
        assertEquals(listOf(2L), FootballRepository.nextMatchdays(today, week).map { it.id })
    }

    @Test fun todaysOverThenTheNextShows() {
        val today = listOf(game(1, "10", Phase.FINISHED))
        val week = listOf(game(2, "11"), game(3, "11"), game(4, "12"))
        assertEquals(listOf(2L, 3L), FootballRepository.nextMatchdays(today, week).map { it.id })
    }

    @Test fun restOfTheMatchdayLaterInTheWeek() {
        val week = listOf(game(1, "10"), game(2, "10"), game(3, "11"))
        assertEquals(listOf(1L, 2L), FootballRepository.nextMatchdays(emptyList(), week).map { it.id })
    }

    @Test fun oneMatchdayPerCup() {
        val cl = FootballRepository.CHAMPIONS
        val el = FootballRepository.EUROPA
        val ahead = listOf(game(1, "12"), game(2, "5", league = cl), game(3, "5", league = el),
            game(4, "13"), game(5, "6", league = cl), game(6, "6", league = el))
        assertEquals(listOf(1L, 2L, 3L), FootballRepository.nextMatchdays(emptyList(), ahead).map { it.id })
    }

    @Test fun cupWithoutRoundKeepsItsDays() {
        val cl = FootballRepository.CHAMPIONS
        val first = game(1, null, league = cl)
        val sameWeek = first.copy(id = 2, kickoffUtcMillis = first.kickoffUtcMillis + 24 * 3_600_000L)
        val later = first.copy(id = 3, kickoffUtcMillis = first.kickoffUtcMillis + 14 * 24 * 3_600_000L)
        assertEquals(listOf(1L, 2L), FootballRepository.nextMatchdays(emptyList(), listOf(first, sameWeek, later)).map { it.id })
    }
}
