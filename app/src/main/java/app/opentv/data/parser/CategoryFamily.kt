/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.parser

/**
 * Folds live categories that are the same package from different restream sources.
 *
 * Some lists split one bouquet by where the feed comes from — `⚽ Sky Sport (A)`,
 * `⚽ Sky Sport (B)`, `⚡ DAZN C`, `🏀 Basket LNP| D` — the way channels are split by FHD/HD/SD. The source tag is stripped to find the family; the channels
 * inside then fold by their own groupKey as usual, so "Sky Sport Uno" appears once.
 *
 * Tags only count as a suffix (bracketed, after `|`, or the trailing words), so a category that is
 * *named* after one of them is left alone. The country stays part of the key: a German
 * `🇩🇪 SKY Sport (RAW)` never folds into the Italian Sky Sport.
 */
object CategoryFamily {

    /** Tags that describe the feed rather than name a source; [SourceTags.CATEGORY] adds the rest. */
    private val GENERIC_TAGS = listOf(
        "raw\\s*/\\s*power", "high quality", "only event", "quality", "hi-def", "raw", "4k", "live",
    )

    /** Source tags as one alternation, longest first so `RAW / Power` wins over `RAW`. */
    private fun tagPattern(extraTags: Collection<String>): String =
        (GENERIC_TAGS + SourceTags.CATEGORY + extraTags.map { Regex.escape(it.lowercase()) })
            .sortedByDescending { it.length }
            .joinToString("|")

    private class Matchers(tag: String) {
        val bracketed = Regex("""\(\s*($tag)\s*\)""", RegexOption.IGNORE_CASE)
        val trailing = Regex("""(?:\s|\|)+($tag)\s*$""", RegexOption.IGNORE_CASE)
    }

    private val BUILTIN = Matchers(tagPattern(emptyList()))
    private val TRAILING_PIPE = Regex("""\s*\|\s*$""")

    /**
     * Families the tag rule can't see, by stripped groupKey: the RAW split of "Intratt. & Cultura"
     * is two shelves with their own names, and one of LBA's feeds drops the "Serie A".
     */
    private val ALIASES = mapOf(
        "intrattenimento" to "intrattcultura",
        "cultura" to "intrattcultura",
        "basketlba" to "basketlbaseriea",
    )

    data class Family(
        /** Join key across sources; empty when the name has nothing left after stripping. */
        val key: String,
        /** The source tag that was stripped, or null for a plain category. */
        val source: String?,
        /** The name without its source tag, emoji intact: `⚽ Sky Sport`. */
        val name: String,
    )

    /** [extraTags] are sources on top of the built-in ones (`ALPHA`…). */
    fun of(name: String, extraTags: Set<String> = emptySet()): Family {
        val matchers = if (extraTags.isEmpty()) BUILTIN else Matchers(tagPattern(extraTags))
        val folded = ChannelNameNormalizer.foldSuperscripts(name.trim())
        var source: String? = null
        var working = matchers.bracketed.replace(folded) { m ->
            if (source == null) source = sourceLabel(m.groupValues[1])
            ""
        }.trim()
        // Up to two trailing tags: "Sky Sport Business SRC (Only Event)", "Discovery Live Src".
        repeat(2) {
            val m = matchers.trailing.find(working) ?: return@repeat
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

    /** Tags that say how a feed looks or when it airs, not who restreams it — kept in a lone name. */
    private val KEEP_IN_NAME = setOf("4K", "RAW")

    /**
     * A lone category's name without its restream source — `🧸 Bambini (Src)` → `🧸 Bambini`,
     * `🏀 Basket LNP | Serie A2 (Src)` → `🏀 Basket LNP | Serie A2` — or null when there is no
     * source to drop. `F1 LIVE` and `Sky Primafila 4K` stay as they are.
     */
    fun displayName(name: String, extraTags: Set<String> = emptySet()): String? {
        val family = of(name, extraTags)
        val source = family.source ?: return null
        if (source.uppercase() in KEEP_IN_NAME || family.key.isEmpty()) return null
        return family.name.ifEmpty { null }
    }

    private val PAREN_TAG = Regex("""\(\s*([A-Za-z][A-Za-z+\-]{1,11})\s*\)""")

    /** Bracketed words that describe a group's content or timing, never its source. */
    private val NOT_SOURCES = setOf("LIVE", "EVENT", "EVENTS", "BAR", "BACKUP", "TV", "HD", "ITA", "IT")

    /**
     * Source tags this provider uses, learned from its own category names: a bracketed word that
     * follows two or more different groups (`Sky Sport (Src)`, `Bambini (Src)`) is a
     * restream source, not a topic. Returned uppercase, merged with the built-in list, so a
     * provider that invents `(Zeus)` tomorrow gets `Sky Sport F1 Zeus` folded too — no update.
     */
    fun learnSourceTags(categoryNames: Collection<String>): Set<String> {
        val stems = HashMap<String, MutableSet<String>>()
        for (name in categoryNames) {
            val folded = ChannelNameNormalizer.foldSuperscripts(name)
            for (m in PAREN_TAG.findAll(folded)) {
                val tag = m.groupValues[1].uppercase()
                val stem = ChannelNameNormalizer.groupKeyOf(folded.removeRange(m.range))
                if (stem.isNotEmpty()) stems.getOrPut(tag) { HashSet() } += stem
            }
        }
        val learned = stems.filter { (tag, s) ->
            s.size >= 2 && tag !in NOT_SOURCES &&
                ChannelNameNormalizer.qualityRankOfToken(tag) == null &&
                !ChannelNameNormalizer.isStreamMarker(tag)
        }.keys
        return ChannelNameNormalizer.BUILTIN_SOURCE_TAGS + learned
    }

    /** How a source reads next to a quality: `FHD · Src`. "RAW / Power" reads as "Power" so the
     * RAW quality preference doesn't mistake a whole feed for raw streams; "Only Event" says
     * nothing about the source and yields null so the next tag can speak. */
    internal fun sourceLabel(tag: String): String? {
        val t = tag.trim().lowercase()
        return when {
            t == "only event" || t == "live" -> null
            t.contains('/') -> "Power"
            t in SourceTags.LABELS -> SourceTags.LABELS.getValue(t)
            t == "hi-def" -> "HI-DEF"
            t.length <= 4 -> t.uppercase()
            else -> t.replaceFirstChar { it.uppercase() }
        }
    }
}
