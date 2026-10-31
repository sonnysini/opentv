/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.football

import app.opentv.data.repo.FootballRepository
import app.opentv.data.repo.FootballRepository.Match
import app.opentv.data.repo.FootballRepository.Phase
import app.opentv.data.repo.FootballRepository.Team
import app.opentv.ui.football.FootballViewModel.Item
import org.junit.Assert.assertEquals
import org.junit.Test

class LeagueRowsTest {
    private val tue2100 = 1_793_736_000_000L
    private val hour = 3_600_000L

    private fun game(id: Long, league: Int, kickoff: Long, phase: Phase = Phase.SCHEDULED, relevant: Boolean = true) =
        Item(Match(id, kickoff, Team(id, "Home$id"), Team(id + 100, "Away$id"), null, null, phase, null, "4", league = league), relevant = relevant)

    private fun order(rows: List<FootballViewModel.LeagueRow>) = rows.map { it.league }

    @Test fun competitionPlayingNextFirst() {
        val rows = FootballViewModel.leagueRows(
            listOf(
                game(1, FootballRepository.SERIE_A, tue2100 + 72 * hour),
                game(2, FootballRepository.CONFERENCE, tue2100 + 46 * hour),
                game(3, FootballRepository.CHAMPIONS, tue2100),
                game(4, FootballRepository.EUROPA, tue2100 + 46 * hour),
            ),
            emptySet(),
        )
        // Thursday's two cups start together: Europa before Conference.
        assertEquals(
            listOf(FootballRepository.CHAMPIONS, FootballRepository.EUROPA, FootballRepository.CONFERENCE, FootballRepository.SERIE_A),
            order(rows),
        )
    }

    @Test fun matchdayOverGoesLast() {
        val rows = FootballViewModel.leagueRows(
            listOf(
                game(1, FootballRepository.SERIE_A, tue2100 - 48 * hour, Phase.FINISHED),
                game(2, FootballRepository.EUROPA, tue2100 + 46 * hour),
            ),
            emptySet(),
        )
        assertEquals(listOf(FootballRepository.EUROPA, FootballRepository.SERIE_A), order(rows))
    }

    @Test fun italianFirstAtTheSameTimeAndBigCardLeftOut() {
        val rows = FootballViewModel.leagueRows(
            listOf(
                game(1, FootballRepository.EUROPA, tue2100, relevant = false),
                game(2, FootballRepository.EUROPA, tue2100),
                game(3, FootballRepository.EUROPA, tue2100 - 2 * hour, relevant = false),
                game(4, FootballRepository.EUROPA, tue2100 - 3 * hour, Phase.LIVE),
                game(5, FootballRepository.EUROPA, tue2100 - 4 * hour),
            ),
            setOf(5L),
        )
        assertEquals(listOf(4L, 3L, 2L, 1L), rows.single().items.map { it.match.id })
    }

    @Test fun postponedLeftOut() {
        val rows = FootballViewModel.leagueRows(listOf(game(1, FootballRepository.SERIE_A, tue2100, Phase.OFF)), emptySet())
        assertEquals(emptyList<Int>(), order(rows))
    }
}
