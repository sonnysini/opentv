/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.repo

import app.opentv.data.repo.FootballRepository.Companion.namesAny
import app.opentv.data.repo.FootballRepository.Companion.namesBoth
import app.opentv.data.repo.FootballRepository.Companion.teamKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** FotMob's names against the titles Italian guides really use. */
class TeamKeysTest {
    private fun shows(title: String, home: String, away: String) = namesBoth(title, teamKeys(home), teamKeys(away))

    @Test fun shortenedNames() {
        assertTrue(shows("Atletico M. - Bayern M.", "Atlético Madrid", "Bayern München"))
    }

    @Test fun italianNames() {
        assertTrue(shows("FC Barcellona - Aston Villa", "Barcelona", "Aston Villa"))
        assertTrue(shows("RB Lipsia - Manchester City", "RB Leipzig", "Manchester City"))
        assertTrue(shows("Galatasaray - VfB Stoccarda", "Galatasaray", "VfB Stuttgart"))
        assertTrue(shows("AEK Atene - Real Madrid UEFA Champions League", "AEK Athens", "Real Madrid"))
        assertTrue(shows("Slavia Praga - Arsenal", "Slavia Prague", "Arsenal"))
        assertTrue(shows("PSV Eindhoven - Club Bruges", "PSV Eindhoven", "Club Brugge"))
        assertTrue(shows("Bayer Leverkusen - Olympique Marsiglia", "Bayer Leverkusen", "Marseille"))
        assertTrue(shows("Sunderland - Dinamo Zagabria", "Sunderland", "Dinamo Zagreb"))
        assertTrue(shows("Rennes - Olympiakos", "Rennes", "Olympiacos"))
    }

    @Test fun shortClubNames() {
        assertTrue(shows("AZ - Juventus", "AZ Alkmaar", "Juventus"))
    }

    @Test fun lettersWithoutAccents() {
        assertTrue(shows("Bodo/Glimt - Lille", "Bodø/Glimt", "Lille"))
        assertTrue(shows("Celtic - Besiktas", "Celtic", "Beşiktaş"))
        assertTrue(shows("Milan - Ferencváros", "Milan", "Ferencváros"))
    }

    @Test fun stillNeedsBothTeams() {
        assertFalse(shows("Manchester United - Aston Villa", "Manchester United", "Roma"))
        assertFalse(shows("FC Bayern Monaco - Borussia Dortmund", "Atlético Madrid", "Bayern München"))
    }

    @Test fun wholeWordsOnly() {
        assertFalse(shows("Interviste - Romanzo", "Inter", "Roma"))
    }

    @Test fun clubWordsAlone() {
        assertEquals(listOf("madrid"), teamKeys("Real Madrid"))
        assertEquals(listOf("manchester"), teamKeys("Manchester City"))
    }

    @Test fun clubChannel() {
        assertTrue(namesAny("Inter TV HD", teamKeys("Inter")))
        assertFalse(namesAny("Sky Sport Uno", teamKeys("Inter")))
    }
}
