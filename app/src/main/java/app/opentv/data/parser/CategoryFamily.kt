/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.parser

/**
 * Folds live categories that are the same package from different restream sources.
 *
 * Italian resellers split one bouquet by where the feed comes from — `⚽ Sky Sport (Hermes)`,
 * `⚽ Sky Sport (SAT)`, `⚽ Sky Sport (Stel-DVS)`, `⚡ DAZN KALI`, `🏀 Basket LNP| STELLINA` — the way
 * channels are split by FHD/HD/SD. The source tag is stripped to find the family; the channels
 * inside then fold by their own groupKey as usual, so "Sky Sport Uno" appears once.
 *
 * Tags only count as a suffix (bracketed, after `|`, or the trailing words), so a category that is
 * *named* after one of them is left alone. The country stays part of the key: a German
 * `🇩🇪 SKY Sport (RAW)` never folds into the Italian Sky Sport.
 */
object CategoryFamily {

    /** Restream source tags, longest first so `RAW / Power` wins over `RAW`. */
    private const val TAG =
        "raw\\s*/\\s*power|stel-dvs|high quality|only event|hermes|stellina|skynet\\+?|" +
            "quality|hi-def|kali|kaly|dvs|sat|raw|4k|live"

    private val BRACKETED = Regex("""\(\s*($TAG)\s*\)""", RegexOption.IGNORE_CASE)
    private val TRAILING = Regex("""(?:\s|\|)+($TAG)\s*$""", RegexOption.IGNORE_CASE)
    private val TRAILING_PIPE = Regex("""\s*\|\s*$""")

    /**
     * Families the tag rule can't see, by stripped groupKey: the RAW split of "Intratt. & Cultura"
     * is two shelves with their own names, and LBA's Stellina feed drops the "Serie A".
     */
    private val ALIASES = mapOf(
        "intrattenimento" to "intrattcultura",
        "cultura" to "intrattcultura",
        "basketlba" to "basketlbaseriea",
    )

    data class Family(
        /** Join key across sources; empty when the name has nothing left after stripping. */
        val key: String,
        /** The source tag that was stripped (`Hermes`, `SAT`…), or null for a plain category. */
        val source: String?,
        /** The name without its source tag, emoji intact: `⚽ Sky Sport`. */
        val name: String,
    )

    fun of(name: String): Family {
        val folded = ChannelNameNormalizer.foldSuperscripts(name.trim())
        var source: String? = null
        var working = BRACKETED.replace(folded) { m ->
            if (source == null) source = sourceLabel(m.groupValues[1])
            ""
        }.trim()
        // Up to two trailing tags: "Sky Sport Business SAT (Only Event)", "Discovery Live Kali".
        repeat(2) {
            val m = TRAILING.find(working) ?: return@repeat
            if (source == null) source = sourceLabel(m.groupValues[1])
            working = working.removeRange(m.range).trim()
        }
        working = TRAILING_PIPE.replace(working, "")
        val stripped = ChannelNameNormalizer.groupKeyOf(working)
        val base = ALIASES[stripped] ?: stripped
        val country = if (CategoryLanguage.isForeign(name)) {
            CategoryLanguage.firstFlag(name) ?: ChannelNameNormalizer.normalize(name).region ?: "xx"
        } else {
            ""
        }
        return Family(if (base.isEmpty()) "" else "$country|$base", source, working.trim())
    }

    /** How a source reads next to a quality: `FHD · Hermes`. "RAW / Power" reads as "Power" so the
     * RAW quality preference doesn't mistake a whole feed for raw streams; "Only Event" says
     * nothing about the source and yields null so the next tag can speak. */
    private fun sourceLabel(tag: String): String? {
        val t = tag.trim().lowercase()
        return when {
            t == "only event" -> null
            t.contains('/') -> "Power"
            t == "skynet" || t == "skynet+" -> "SkyNet"
            t == "hi-def" -> "HI-DEF"
            t.length <= 4 -> t.uppercase()
            else -> t.replaceFirstChar { it.uppercase() }
        }
    }
}
