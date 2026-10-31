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
import org.junit.Assert.assertNull
import org.junit.Test

class ByClockTest {
    private val kickoff = 1_793_736_000_000L
    private val minute = 60_000L

    private fun match(league: Int = FootballRepository.SERIE_A, phase: Phase = Phase.SCHEDULED, minuteText: String? = null) =
        Match(1, kickoff, Team(1, "Home"), Team(2, "Away"), null, null, phase, minuteText, "4", league = league)

    private fun phaseAt(m: Match, offset: Long) = FootballViewModel.byClock(m, kickoff + offset).phase

    @Test fun serieALiveForTwoHours() {
        val m = match()
        assertEquals(Phase.SCHEDULED, phaseAt(m, -minute))
        assertEquals(Phase.LIVE, phaseAt(m, 0))
        assertEquals(Phase.LIVE, phaseAt(m, 119 * minute))
        assertEquals(Phase.FINISHED, phaseAt(m, 120 * minute))
    }

    @Test fun cupLiveForTwoAndAHalfHours() {
        val m = match(league = FootballRepository.CHAMPIONS)
        assertEquals(Phase.LIVE, phaseAt(m, 149 * minute))
        assertEquals(Phase.FINISHED, phaseAt(m, 150 * minute))
    }

    @Test fun minuteDroppedOverAndOffKept() {
        assertNull(FootballViewModel.byClock(match(phase = Phase.LIVE, minuteText = "54'"), kickoff + 54 * minute).minute)
        assertEquals(Phase.FINISHED, phaseAt(match(phase = Phase.FINISHED), 100 * minute))
        assertEquals(Phase.OFF, phaseAt(match(phase = Phase.OFF), 30 * minute))
    }

    @Test fun nextChangeIsKickoffThenEnd() {
        val items = listOf(Item(match()))
        assertEquals(kickoff, FootballViewModel.nextChange(items, kickoff - minute))
        assertEquals(kickoff + 120 * minute, FootballViewModel.nextChange(items, kickoff))
        assertNull(FootballViewModel.nextChange(listOf(Item(match(phase = Phase.FINISHED))), kickoff))
    }
}
