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
import java.time.Instant
import java.time.ZoneId

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
            )
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
            )
        )
        assertEquals(listOf(FootballRepository.EUROPA, FootballRepository.SERIE_A), order(rows))
    }

    @Test fun italianFirstAtTheSameTimeAndCarouselGamesKept() {
        val rows = FootballViewModel.leagueRows(
            listOf(
                game(1, FootballRepository.EUROPA, tue2100, relevant = false),
                game(2, FootballRepository.EUROPA, tue2100),
                game(3, FootballRepository.EUROPA, tue2100 - 2 * hour, relevant = false),
                game(4, FootballRepository.EUROPA, tue2100 - 3 * hour, Phase.LIVE),
                game(5, FootballRepository.EUROPA, tue2100 - 4 * hour),
            ),
        )
        assertEquals(listOf(4L, 5L, 3L, 2L, 1L), rows.single().items.map { it.match.id })
    }

    @Test fun postponedLeftOut() {
        val rows = FootballViewModel.leagueRows(listOf(game(1, FootballRepository.SERIE_A, tue2100, Phase.OFF)))
        assertEquals(emptyList<Int>(), order(rows))
    }

    private val tuesday = Instant.ofEpochMilli(tue2100).atZone(ZoneId.systemDefault()).toLocalDate()

    @Test fun carouselIsTodaysGamesThatMatterLiveFirst() {
        val goals = Item(
            Match(-9, tue2100, Team(-42, "Diretta Gol"), Team(-42, "Champions League"), null, null, Phase.SCHEDULED, null, null, goals = true, league = FootballRepository.CHAMPIONS),
        )
        val all = listOf(
            game(1, FootballRepository.CHAMPIONS, tue2100),
            game(2, FootballRepository.CHAMPIONS, tue2100, relevant = false),
            goals,
            game(3, FootballRepository.CHAMPIONS, tue2100 - 2 * hour, Phase.LIVE),
            game(4, FootballRepository.CHAMPIONS, tue2100 - 3 * hour, Phase.FINISHED),
            game(5, FootballRepository.SERIE_A, tue2100 + 24 * hour),
        )
        assertEquals(listOf(3L, 1L, -9L), FootballViewModel.carousel(all, tuesday).map { it.match.id })
    }

    @Test fun carouselWithNothingTodayIsTheNextGame() {
        val all = listOf(
            game(1, FootballRepository.EUROPA, tue2100 + 46 * hour, relevant = false),
            game(2, FootballRepository.SERIE_A, tue2100 + 72 * hour),
        )
        assertEquals(listOf(2L), FootballViewModel.carousel(all, tuesday).map { it.match.id })
    }

    @Test fun sameDayGoesByCompetitionNotKickoff() {
        val rows = FootballViewModel.leagueRows(
            listOf(
                game(1, FootballRepository.CHAMPIONS, tue2100),
                game(2, FootballRepository.EUROPA, tue2100 - 3 * hour),
            ),
        )
        assertEquals(listOf(FootballRepository.CHAMPIONS, FootballRepository.EUROPA), order(rows))
    }
}
