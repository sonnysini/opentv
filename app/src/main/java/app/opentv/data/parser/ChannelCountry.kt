/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.parser

import java.util.Locale

/**
 * Which country a channel of the list belongs to.
 *
 * The normaliser strips `DE:` from `DE: Sky Nature`, so before this a German and an Italian
 * Sky Nature shared one row, and the fallback could land on German audio. The signals, strongest
 * first:
 *
 *  1. an ISO code written in the name: `DE: …`, `DAZN DE: …`, `UK| …`
 *  2. the category's flag or code prefix: `🇩🇪 Sport`, `UK| GENERAL`
 *  3. a world-mix category (`🌍 …`): not the home country
 *  4. the suffix of the list's own guide id (`.it`, `.de`) — reliable as a country, often wrong
 *     as a channel, so it comes last
 *  5. otherwise: the home country.
 *
 * Home-country channels keep their key as it is; any other country's get a `cc:` prefix
 * (`de:skynature`), so the two never meet in one row. Keys are lowercase alphanumerics, so the
 * colon can't be anything else.
 */
object ChannelCountry {

    val ISO = setOf(
        "IT", "SM", "VA", "GB", "UK", "US", "USA", "DE", "AT", "CH", "FR", "ES", "PT", "NL", "BE", "PL", "TR",
        "AL", "RO", "BG", "HU", "GR", "RU", "SE", "DK", "NO", "FI", "CZ", "SK", "HR", "RS", "BA", "MK", "SI",
        "CA", "MX", "BR", "AR", "IE", "IL", "AE", "QA", "SA", "IN", "PK", "CN", "TW", "JP", "KR", "AU", "NZ",
        "ZA", "UA", "LV", "LT", "EE", "LA", "EN",
    )

    /** What a foreign channel is filed under when only "not from here" is known (`🌍 World Mix`). */
    const val WORLD = "WORLD"

    /** The countries a home country's own channels may say they are from (San Marino RTV is Italian TV). */
    private val SAME_AS = mapOf("IT" to setOf("IT", "SM", "VA"))

    /** Codes written more than one way (`UK` and `GB`). */
    fun canon(code: String): String = when (val c = code.uppercase()) {
        "UK", "EN" -> "GB"
        "USA" -> "US"
        else -> c
    }

    private val NAME_CC = Regex("""(?:^|[\s|])([A-Z]{2,3})\s*[:|•]\s""")
    private val CATEGORY_CC = Regex(
        """^\W*(UK|USA?|DE|AT|CH|FR|ES|PT|NL|BE|PL|TR|AL|RO|BG|HU|GR|RU|SE|DK|NO|FI|CZ|SK|HR|RS|BA|MK|SI)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val GUIDE_ID_CC = Regex("""\.([a-z]{2})$""", RegexOption.IGNORE_CASE)
    private val KEY_PREFIX = Regex("""^([a-z]{2,5}):""")

    fun ofName(name: String): String? =
        NAME_CC.findAll(name).map { it.groupValues[1] }.firstOrNull { it in ISO }?.let(::canon)

    fun ofCategory(category: String?): String? {
        if (category.isNullOrBlank()) return null
        return CategoryLanguage.firstFlag(category)?.let(::canon)
            ?: CATEGORY_CC.find(category)?.groupValues?.get(1)?.let(::canon)
    }

    fun ofGuideId(epgId: String?): String? {
        if (epgId.isNullOrBlank()) return null
        val code = GUIDE_ID_CC.find(epgId.trim())?.groupValues?.get(1)?.uppercase() ?: return null
        return code.takeIf { it in ISO }?.let(::canon)
    }

    private fun worldCategory(category: String?): Boolean {
        val c = category?.trimStart() ?: return false
        return c.startsWith("🌍") || c.startsWith("🌎") || c.startsWith("🌏")
    }

    /** The channel's country, canonical (`GB`, not `UK`), or [WORLD]; [home] when nothing says. */
    fun of(name: String, category: String?, epgId: String?, home: String): String =
        ofName(name) ?: ofCategory(category) ?: (if (worldCategory(category)) WORLD else null)
            ?: ofGuideId(epgId) ?: home

    fun isHome(country: String, home: String): Boolean =
        country == home || country in SAME_AS[home].orEmpty()

    /** [key] as stored for a channel of [country]: unchanged at home, `de:skynature` abroad. */
    fun keyFor(key: String, country: String, home: String): String =
        if (key.isEmpty() || isHome(country, home)) key else "${country.lowercase()}:$key"

    /** The key without its country: `de:skynature` → `skynature`. */
    fun bare(key: String): String = KEY_PREFIX.find(key)?.let { key.substring(it.range.last + 1) } ?: key

    /** The country a stored key was filed under (`DE`), null for a home channel. */
    fun ofKey(key: String): String? = KEY_PREFIX.find(key)?.groupValues?.get(1)?.uppercase()

    /** The home country when the user hasn't picked one: the device's region, else its language's. */
    fun deviceDefault(locale: Locale = Locale.getDefault()): String {
        locale.country.takeIf { it.length == 2 }?.let { return canon(it) }
        return when (locale.language) {
            "it" -> "IT"; "de" -> "DE"; "fr" -> "FR"; "es" -> "ES"; "pt" -> "PT"; "nl" -> "NL"; "pl" -> "PL"
            "en" -> "GB"; "tr" -> "TR"; "ro" -> "RO"; "el" -> "GR"; "sv" -> "SE"; "da" -> "DK"; "nb" -> "NO"
            "fi" -> "FI"; "cs" -> "CZ"; "sk" -> "SK"; "hr" -> "HR"; "hu" -> "HU"; "bg" -> "BG"; "ru" -> "RU"
            "uk" -> "UA"; "ar" -> "AE"; "ja" -> "JP"; "ko" -> "KR"; "zh" -> "CN"
            else -> "IT"
        }
    }
}
