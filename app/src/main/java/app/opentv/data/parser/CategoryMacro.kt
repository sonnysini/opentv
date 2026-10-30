/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.parser

import java.util.Locale

/**
 * Sorts a live category into one of the tabs across the top of the groups (Italy, Football,
 * Sport…) from its provider name alone. A foreign category goes abroad, filed under its country;
 * the rest go by the first word list they match, in a fixed order, so "Basket LBA / Serie A" is
 * sport and "Serie A Femminile" is football. What matches nothing is entertainment.
 */
object CategoryMacro {

    enum class Macro { ITALY, FOOTBALL, SPORT, ENTERTAINMENT, CINEMA, KIDS, MUSIC, ABROAD, ADULT }

    data class Result(
        val macro: Macro,
        /** For [Macro.ABROAD]: the country code ("DE") or the region in capitals ("PAESI ARABI"). */
        val country: String? = null,
    )

    private fun words(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

    private val adult = words("🔞|\\bxxx\\b|\\badult[io]?\\b|\\bhot\\b|\\bporn")

    /** Flagless foreign groups named after a region rather than a country. */
    private val region = words(
        "\\b(paesi arabi|arabic|ex[ -]?jugoslavia|ex[ -]?yu|africa|america latina|latino|caraibi|" +
            "scandinavia|paesi baltici|baltici|kurdish)\\b",
    )

    private val countryPrefix = Regex(
        "^(UK|USA?|DE|AT|CH|FR|ES|PT|NL|BE|PL|TR|AL|RO|BG|HU|GR|RU|SE|DK|NO|FI|CZ|SK|HR|RS|BA|MK|SI)\\s*[|:-]",
        RegexOption.IGNORE_CASE,
    )

    /** Free channels that are mostly films and shows, whatever else their names say. */
    private val entertainment = words("\\b(pluto|rakuten)\\b")

    /** Sports whose leagues borrow football words ("Basket LBA / Serie A", "Volleyball Nations League"). */
    private val otherSport = words(
        "\\b(basket\\w*|volley\\w*|rugby|nfl|football americano|pallamano|hockey|nba|nhl|mlb|milb|ncaa\\w*)\\b",
    )

    private val ordered = listOf(
        Macro.FOOTBALL to words(
            "\\b(calcio|serie [abc]|champions|liga|ligue|premier league|bundesliga|buli|football|fa cup|efl|" +
                "nations league|fifa|uefa|squadre|amichevoli|big match|championship|coppa italia|" +
                "europa league|conference league)\\b",
        ),
        Macro.SPORT to words(
            "\\b(sports?|f1|formula 1|moto ?gp|dazn|eurosport|tennis|ufc|box|boxe|boxing|wrestling|darts|" +
                "ciclismo|motori|golf|biliardo|scacchi|espn|bein|optus|cbc|ppv|pay-per-view|eleven|multisport|" +
                "helbiz|olimpiadi)\\b",
        ),
        Macro.CINEMA to words("\\b(cinema|primafila|film|movies?|attori|serie tv|peliculas|filmes|eagle)\\b"),
        Macro.KIDS to words("\\b(bambini|kids|cartoni|ragazzi|kinder|enfants|ninos|infantil)\\b"),
        Macro.MUSIC to words("\\b(musica|music|musik|musique|radio)\\b"),
        Macro.ITALY to words(
            "\\b(top italia|italia|regional[ei]|notizie|news|rai|mediaset|grande fratello|generalist[ei])\\b|\\+\\d+\\s*ore\\b",
        ),
    )

    fun of(rawName: String): Result {
        if (adult.containsMatchIn(rawName)) return Result(Macro.ADULT)
        val text = rawName.trimStart { !it.isLetterOrDigit() }
        val flag = CategoryLanguage.firstFlag(rawName)
        if (flag != null && flag !in ITALIAN_FLAGS) return Result(Macro.ABROAD, flag)
        if (flag == null) {
            region.find(text)?.let { return Result(Macro.ABROAD, it.value.uppercase(Locale.ROOT)) }
            countryPrefix.find(text)?.let {
                val code = it.groupValues[1].uppercase(Locale.ROOT)
                return Result(Macro.ABROAD, if (code == "UK") "GB" else if (code == "USA") "US" else code)
            }
        }
        if (entertainment.containsMatchIn(text)) return Result(Macro.ENTERTAINMENT)
        if (otherSport.containsMatchIn(text)) return Result(Macro.SPORT)
        return Result(ordered.firstOrNull { (_, words) -> words.containsMatchIn(text) }?.first ?: Macro.ENTERTAINMENT)
    }

    /** A country code's name in Italian ("DE" → "Germania"); a region comes back as it is. */
    fun countryName(country: String): String {
        if (country.length != 2) return country.lowercase(Locale.ITALIAN).replaceFirstChar { it.titlecase(Locale.ITALIAN) }
        val name = Locale("", country).getDisplayCountry(Locale.ITALIAN)
        return name.takeIf { it.isNotBlank() && !it.equals(country, ignoreCase = true) } ?: country
    }

    /**
     * A group name split into the broadcaster in front and the rest: "Sky Calcio" → ("Sky", "Calcio").
     * No known broadcaster, or nothing after it, and the whole name is the rest.
     */
    fun kicker(label: String): Pair<String?, String> {
        val trimmed = label.trim()
        val brand = BRANDS.firstOrNull { trimmed.startsWith("$it ", ignoreCase = true) }
            ?: return null to trimmed
        val rest = trimmed.substring(brand.length).trim().trimStart('-', '|', ':', '/').trim()
        return if (rest.isEmpty()) null to trimmed else trimmed.substring(0, brand.length) to rest
    }

    /** "Top Italia [HI-DEF]" and "Sky Cinema (4K)" are the shelf "Top Italia" and "Sky Cinema". */
    fun shelfKey(label: String): String =
        label.replace(TAGS, " ").replace(SPACES, " ").trim().lowercase(Locale.ROOT)

    private val TAGS = Regex("\\[[^]]*]|\\([^)]*\\)")
    private val SPACES = Regex("\\s+")
    private val ITALIAN_FLAGS = setOf("IT", "SM", "VA")
    private val BRANDS = listOf("Disney Plus", "Prime Video", "Sky", "DAZN", "Rai", "Mediaset", "Discovery", "Eurosport", "Amazon")
}
