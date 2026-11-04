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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalShowTest {
    private val h1830 = 1_761_672_600_000L
    private val h2045 = h1830 + 135 * 60_000L

    private fun game(id: Long, kickoff: Long, phase: Phase) =
        Match(id, kickoff, Team(id, "Home$id"), Team(id + 100, "Away$id"), null, null, phase, null, "9")

    @Test fun noGamesTogether() {
        assertNull(FootballRepository.goalShow(listOf(game(1, h1830, Phase.LIVE), game(2, h2045, Phase.SCHEDULED))))
    }

    @Test fun beforeKickoff() {
        val g = FootballRepository.goalShow(listOf(game(1, h2045, Phase.SCHEDULED), game(2, h2045, Phase.SCHEDULED)))!!
        assertTrue(g.goals)
        assertEquals(Phase.SCHEDULED, g.phase)
        assertEquals(h2045, g.kickoffUtcMillis)
        assertTrue(g.id < 0)
    }

    @Test fun liveWhileAnyIs() {
        val g = FootballRepository.goalShow(listOf(game(1, h2045, Phase.FINISHED), game(2, h2045, Phase.LIVE), game(3, h2045, Phase.LIVE)))!!
        assertEquals(Phase.LIVE, g.phase)
    }

    @Test fun overMovesToTheNextSlot() {
        val today = listOf(
            game(1, h1830, Phase.FINISHED), game(2, h1830, Phase.FINISHED),
            game(3, h2045, Phase.SCHEDULED), game(4, h2045, Phase.SCHEDULED), game(5, h2045, Phase.OFF),
        )
        assertEquals(h2045, FootballRepository.goalShow(today)!!.kickoffUtcMillis)
    }

    @Test fun postponedGamesDontCount() {
        assertNull(FootballRepository.goalShow(listOf(game(1, h2045, Phase.LIVE), game(2, h2045, Phase.OFF))))
    }

    private fun cup(id: Long, league: Int, kickoff: Long, phase: Phase = Phase.SCHEDULED) =
        game(id, kickoff, phase).copy(league = league)

    @Test fun oneShowPerCupEuropaAndConferenceShared() {
        val shows = FootballRepository.goalShows(
            listOf(
                cup(1, FootballRepository.CHAMPIONS, h2045), cup(2, FootballRepository.CHAMPIONS, h2045),
                cup(3, FootballRepository.EUROPA, h2045), cup(4, FootballRepository.CONFERENCE, h2045),
                game(5, h2045, Phase.SCHEDULED),
            ),
        )
        assertEquals(listOf(FootballRepository.CHAMPIONS, FootballRepository.EUROPA), shows.map { it.league })
        assertEquals(2, shows.map { it.id }.distinct().size)
        assertTrue(shows.all { it.goals && it.home.id < 0 })
    }

    @Test fun conferenceAloneIsConferences() {
        val show = FootballRepository.goalShows(
            listOf(cup(1, FootballRepository.CONFERENCE, h1830), cup(2, FootballRepository.CONFERENCE, h1830)),
        ).single()
        assertEquals(FootballRepository.CONFERENCE, show.league)
        assertEquals("Conference League", show.away.name)
    }

    @Test fun serieAShowKeepsItsId() {
        val g = FootballRepository.goalShow(listOf(game(1, h2045, Phase.SCHEDULED), game(2, h2045, Phase.SCHEDULED)))!!
        assertEquals(-(h2045 / 60_000L), g.id)
    }

    @Test fun cupShowTitlesFromTheGuide() {
        assertTrue(FootballRepository.isCupGoalShow("Diretta Gol Ucl 04/11/2026 (Diretta)", FootballRepository.CHAMPIONS))
        assertTrue(FootballRepository.isCupGoalShow("Diretta Gol Uel/ Uecl... (Diretta)", FootballRepository.EUROPA))
        assertTrue(FootballRepository.isCupGoalShow("Diretta Gol Uel/ Uecl... (Diretta)", FootballRepository.CONFERENCE))
        assertTrue(!FootballRepository.isCupGoalShow("Diretta Gol Uel/ Uecl... (Diretta)", FootballRepository.CHAMPIONS))
        assertTrue(!FootballRepository.isCupGoalShow("Diretta Gol Serie A", FootballRepository.CHAMPIONS))
        assertTrue(!FootballRepository.isCupGoalShow("Next Game: Champions League Magazine", FootballRepository.CHAMPIONS))
    }

    @Test fun aShowPerKickoff() {
        val shows = FootballRepository.goalShows(
            listOf(
                cup(1, FootballRepository.CHAMPIONS, h1830), cup(2, FootballRepository.CHAMPIONS, h1830),
                cup(3, FootballRepository.CHAMPIONS, h2045), cup(4, FootballRepository.CHAMPIONS, h2045),
            ),
        )
        assertEquals(listOf(h1830, h2045), shows.map { it.kickoffUtcMillis })
        assertEquals(2, shows.map { it.id }.distinct().size)
    }
}
