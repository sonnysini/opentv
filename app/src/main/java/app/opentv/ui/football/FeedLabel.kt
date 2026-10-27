/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.football

import app.opentv.data.parser.ChannelNameNormalizer

/**
 * A match feed's name split for the big card's list: the channel ("Lazio", "Zona DAZN SA1"), the
 * quality the provider claims ("FHD", shown only until the feed is looked at) and the restream
 * source ("DVS", "SAT · HEVC"), small at the end. The channel name is already cleaned by
 * [ChannelNameNormalizer]; this only shortens what repeats on every feed and shouts.
 */
object FeedLabel {
    data class Parts(val name: String, val claimed: String, val source: String)

    /** "Serie A 1" → "SA1": every DAZN zone feed says Serie A, the number is what tells them apart. */
    private val SERIE_A_NUMBER = Regex("""\bserie\s+a\s*(\d)\b""", RegexOption.IGNORE_CASE)
    /** A country tag left at the end: "Sky Sport 251 IT". */
    private val TRAILING_COUNTRY = Regex("""\s+(?:IT|ITA)$""", RegexOption.IGNORE_CASE)
    private val SPACES = Regex("""\s+""")
    /** Words kept in capitals when an all-caps name is put in title case. */
    private val ACRONYMS = setOf("DAZN", "TV", "TIM", "SA1", "SA2", "SA3", "SA4", "UHD", "RAI")

    fun split(name: String, qualityLabel: String): Parts {
        var n = SERIE_A_NUMBER.replace(name.trim()) { "SA" + it.groupValues[1] }
        n = TRAILING_COUNTRY.replace(n, "")
        n = titleCase(SPACES.replace(n, " ").trim())
        val claimed = ArrayList<String>()
        val source = ArrayList<String>()
        // "FHD HEVC · High Quality": resolution words are the claim, the rest is the source.
        for (segment in qualityLabel.split('·')) {
            val rest = ArrayList<String>()
            for (token in segment.trim().split(' ').filter { it.isNotEmpty() }) {
                if (ChannelNameNormalizer.qualityRankOfToken(token) != null) claimed += token.uppercase() else rest += token
            }
            if (rest.isNotEmpty()) source += shortSource(rest)
        }
        return Parts(
            name = n.ifEmpty { name },
            claimed = claimed.distinct().joinToString(" "),
            source = source.distinct().joinToString(" · "),
        )
    }

    /** A source of several words by its initials ("High Quality" → "HQ"); one word in capitals. */
    private fun shortSource(words: List<String>): String =
        if (words.size == 1) words[0].uppercase() else words.joinToString("") { it.take(1).uppercase() }

    /** "ZONA DAZN SA1" → "Zona DAZN SA1"; names already in mixed case are left as they are. */
    private fun titleCase(s: String): String {
        val letters = s.filter { it.isLetter() }
        if (letters.length < 4 || letters.any { it.isLowerCase() }) return s
        return s.split(' ').joinToString(" ") { w ->
            if (w.uppercase() in ACRONYMS || w.any { it.isDigit() }) w
            else w.lowercase().replaceFirstChar { it.titlecase() }
        }
    }

    /** The resolution a look measured, as on a TV box: "4K", "1080p", "720p". */
    fun resolution(height: Int): String = when {
        height >= 2000 -> "4K"
        height >= 1000 -> "1080p"
        height >= 700 -> "720p"
        else -> "${height}p"
    }

    /** "50 FPS" / "60 FPS" for the smooth ones; null for the usual 25/30. */
    fun smooth(fps: Int?): String? = when {
        fps == null || fps < 48 -> null
        fps >= 56 -> "60 FPS"
        else -> "50 FPS"
    }
}
