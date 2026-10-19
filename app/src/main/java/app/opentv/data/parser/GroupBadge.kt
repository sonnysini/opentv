/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.parser

import java.text.Normalizer

/**
 * What to show beside a channel group's name. Providers send no category artwork (Xtream has
 * only id, name and parent), so it is worked out from the name alone: a broadcaster's logo when
 * the name starts with one, else the country flag, else the emoji the provider put in front.
 * Nothing is bundled — logos and flags load from public repositories like any channel logo.
 */
object GroupBadge {

    sealed interface Badge
    data class Image(val url: String) : Badge
    data class Emoji(val text: String) : Badge

    data class Result(
        /** The group's name without the leading emoji, which the badge now shows. */
        val label: String,
        val badge: Badge?,
    )

    private const val TV_LOGOS = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/"

    /** Name prefix (lowercase, accents and symbols gone) → tv-logos file. Longest prefix first. */
    private val BRANDS = listOf(
        "sky sport business" to "italy/sky-sport-bar-it.png",
        "sky sport" to "italy/sky-sport-it.png",
        "sky calcio" to "italy/sky-sport-calcio-it.png",
        "sky cinema" to "italy/sky-cinema-it.png",
        "sky primafila" to "italy/sky-primafila-it.png",
        "primafila" to "italy/sky-primafila-it.png",
        "dazn" to "international/dazn-int.png",
        "discovery" to "italy/discovery-channel-it.png",
        "disney plus" to "international/disney-plus-int.png",
        "eleven sport" to "international/eleven-sports-int.png",
        "f1" to "international/f1-tv-int.png",
    ).sortedByDescending { it.first.length }

    fun of(rawName: String): Result {
        val name = rawName.trim()
        val lead = leadingSymbols(name)
        val label = name.substring(lead.length).trim().ifEmpty { name }
        val key = brandKey(label)
        BRANDS.firstOrNull { (prefix, _) -> key == prefix || key.startsWith("$prefix ") }?.let {
            return Result(label, Image(TV_LOGOS + it.second))
        }
        flagCountry(lead)?.let { return Result(label, Image("https://flagcdn.com/w160/$it.png")) }
        val emoji = lead.trim()
        return if (emoji.isNotEmpty() && emoji.any { it.code > 0x2000 }) {
            Result(label, Emoji(emoji))
        } else {
            Result(name, null)
        }
    }

    /** Everything before the first letter or digit: `❤️‍🔥 `, `🇮🇹  `, `⚽ `. */
    private fun leadingSymbols(name: String): String {
        val first = name.indexOfFirst { it.isLetterOrDigit() }
        return if (first < 0) "" else name.substring(0, first)
    }

    /** `🇮🇹` → "it": two regional-indicator letters in a row. */
    internal fun flagCountry(text: String): String? {
        val points = text.codePoints().toArray()
        for (i in 0 until points.size - 1) {
            val a = points[i] - 0x1F1E6
            val b = points[i + 1] - 0x1F1E6
            if (a in 0..25 && b in 0..25) return "${'a' + a}${'a' + b}"
        }
        return null
    }

    internal fun brandKey(label: String): String =
        Normalizer.normalize(label, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace('+', ' ')
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
}
