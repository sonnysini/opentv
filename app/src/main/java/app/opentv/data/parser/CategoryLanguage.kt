/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.parser

/**
 * Guesses whether a live category is in a foreign language from its provider name — the basis of
 * the "Italian only" switch. Providers label foreign groups with a country flag ("🇩🇪 SKY Cinema",
 * "🇬🇧 UK| Movies") and, where there is no flag, with a country prefix or a region word
 * ("UK General", "🌍 EX YU - SPORT", "🕌 Arabic News"). Anything unrecognised counts as Italian:
 * a wrongly kept category is one switch away, a wrongly dropped one would just silently vanish.
 */
object CategoryLanguage {

    /** Flags that mean Italian-language TV: Italy, San Marino, Vatican City. */
    private val italianFlags = setOf("IT", "SM", "VA")

    /** Country prefix with a separator: "DE: …", "FR| …", "PT - …". */
    private val countryPrefix = Regex(
        "^(UK|USA?|DE|AT|CH|FR|ES|PT|NL|BE|PL|TR|AL|RO|BG|HU|GR|RU|SE|DK|NO|FI|CZ|SK|HR|RS|BA|MK|SI)\\s*[|:-]",
        RegexOption.IGNORE_CASE,
    )

    /** UK/US groups often skip the separator: "UK General", "USA News". */
    private val englishPrefix = Regex("^(UK|USA?)\\b", RegexOption.IGNORE_CASE)

    /** Region words providers use for flagless foreign groups. */
    private val regionWords = Regex(
        "\\b(EX[ -]?YU|ARABIC|LATINO|KURDISH|BALTICI?)\\b",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Flagless international sport groups (US leagues, pay-per-view, foreign rights holders):
     * events Italian viewers do watch, with foreign commentary — a separate switch, on by default.
     */
    private val foreignSportWords = Regex(
        "\\b(ESPN|NBA PASS|NHL|MLB|MILB|NFL SPORT|NCAA[FB]?|PPV|PAY-PER-VIEW|CBC|FLOW SPORTS|" +
            "BEIN SPORT|OPTUS|HK NOW|RUGBY PASS|HOCKEY LEAGUE|WORLD MIX)\\b",
        RegexOption.IGNORE_CASE,
    )

    /** True when [name] is a foreign-language category (flag or name says so). */
    fun isForeign(name: String): Boolean {
        val flag = firstFlag(name)
        if (flag != null) return flag !in italianFlags
        val text = stripDecoration(name)
        return countryPrefix.containsMatchIn(text) ||
            englishPrefix.containsMatchIn(text) ||
            regionWords.containsMatchIn(text)
    }

    /** True when [name] is an international sport group with foreign commentary. */
    fun isForeignSport(name: String): Boolean {
        val flag = firstFlag(name)
        if (flag != null && flag in italianFlags) return false
        return !isForeign(name) && foreignSportWords.containsMatchIn(name)
    }

    /** The first flag emoji in [name] as an ISO country code ("🇩🇪" → "DE"), or null. */
    internal fun firstFlag(name: String): String? {
        var previous = -1
        var i = 0
        while (i < name.length) {
            val cp = name.codePointAt(i)
            if (cp in REGIONAL_A..REGIONAL_Z) {
                if (previous >= 0) {
                    return "${'A' + (previous - REGIONAL_A)}${'A' + (cp - REGIONAL_A)}"
                }
                previous = cp
            } else {
                previous = -1
            }
            i += Character.charCount(cp)
        }
        return null
    }

    /** Drops leading emoji/symbols so prefixes match: "🌍 EX YU" → "EX YU". */
    private fun stripDecoration(name: String): String =
        name.trimStart { !it.isLetterOrDigit() }

    private const val REGIONAL_A = 0x1F1E6
    private const val REGIONAL_Z = 0x1F1FF
}
