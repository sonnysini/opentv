/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.football

import androidx.compose.ui.graphics.Color
import app.opentv.data.repo.FootballRepository.Team

/**
 * A team's main colour, for the two halves of a match tile. Toned down from the kit so white
 * text and the crests read on it. Teams not listed get a dark colour of their own from the id.
 */
object TeamColors {
    private val byName = listOf(
        "atalanta" to 0xFF1C4E8F,
        "bologna" to 0xFF1A2F48,
        "cagliari" to 0xFF8C1A2B,
        "como" to 0xFF1B3C8C,
        "cremonese" to 0xFF8E1B2C,
        "empoli" to 0xFF0E4FA0,
        "fiorentina" to 0xFF4B2A85,
        "frosinone" to 0xFF1D4C9C,
        "genoa" to 0xFF7A1424,
        "verona" to 0xFF113A78,
        "inter" to 0xFF00487A,
        "juventus" to 0xFF1E1E1E,
        "lazio" to 0xFF4C87B9,
        "lecce" to 0xFF9C8A10,
        "milan" to 0xFFB0101A,
        "monza" to 0xFFB5121B,
        "napoli" to 0xFF0E6E9E,
        "palermo" to 0xFFB5527A,
        "parma" to 0xFF1E3D7B,
        "pisa" to 0xFF1A2B5C,
        "roma" to 0xFF8E1F2F,
        "salernitana" to 0xFF6E1B1F,
        "sampdoria" to 0xFF1B4A9E,
        "sassuolo" to 0xFF0F6B3A,
        "spezia" to 0xFF2A2A2A,
        "torino" to 0xFF7A1A08,
        "udinese" to 0xFF2B2B2B,
        "venezia" to 0xFF0B5A3C,
    )

    fun of(team: Team): Color {
        // The goal show: the league's own.
        if (team.id < 0) return Color(0xFF3A1452)
        val name = team.name.lowercase()
        byName.firstOrNull { name.contains(it.first) }?.let { return Color(it.second) }
        val hue = ((team.id * 47) % 360).toFloat()
        return Color.hsv(hue, 0.55f, 0.38f)
    }
}
