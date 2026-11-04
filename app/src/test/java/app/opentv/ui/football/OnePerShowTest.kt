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

class OnePerShowTest {
    private val h1845 = 1_793_727_900_000L
    private val h2100 = h1845 + 135 * 60_000L

    private fun show(id: Long, league: Int, kickoff: Long, phase: Phase) =
        Item(Match(id, kickoff, Team(-league.toLong(), "Diretta Gol"), Team(-league.toLong(), "x"), null, null, phase, null, "4", goals = true, league = league))

    private fun game(id: Long, league: Int, kickoff: Long) =
        Item(Match(id, kickoff, Team(id, "H"), Team(id + 100, "A"), null, null, Phase.SCHEDULED, null, "4", league = league))

    private fun ids(items: List<Item>) = items.map { it.match.id }

    @Test fun beforeEitherTheFirst() {
        val all = listOf(
            game(1, FootballRepository.CHAMPIONS, h1845),
            show(-1, FootballRepository.CHAMPIONS, h1845, Phase.SCHEDULED),
            show(-2, FootballRepository.CHAMPIONS, h2100, Phase.SCHEDULED),
        )
        assertEquals(listOf(1L, -1L), ids(FootballViewModel.onePerShow(all)))
    }

    @Test fun theOneOnNow() {
        val all = listOf(
            show(-1, FootballRepository.CHAMPIONS, h1845, Phase.LIVE),
            show(-2, FootballRepository.CHAMPIONS, h2100, Phase.SCHEDULED),
        )
        assertEquals(listOf(-1L), ids(FootballViewModel.onePerShow(all)))
    }

    @Test fun overlappingTheLaterStarted() {
        val all = listOf(
            show(-1, FootballRepository.CHAMPIONS, h1845, Phase.LIVE),
            show(-2, FootballRepository.CHAMPIONS, h2100, Phase.LIVE),
        )
        assertEquals(listOf(-2L), ids(FootballViewModel.onePerShow(all)))
    }

    @Test fun onePerCompetitionEuropaAndConferenceShared() {
        val all = listOf(
            show(-1, FootballRepository.CHAMPIONS, h2100, Phase.SCHEDULED),
            show(-2, FootballRepository.CONFERENCE, h1845, Phase.SCHEDULED),
            show(-3, FootballRepository.EUROPA, h2100, Phase.SCHEDULED),
            show(-4, FootballRepository.SERIE_A, h2100, Phase.SCHEDULED),
        )
        assertEquals(listOf(-1L, -2L, -4L), ids(FootballViewModel.onePerShow(all)))
    }
}
