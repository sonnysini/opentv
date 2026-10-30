/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui

import app.opentv.data.model.TimeShift
import app.opentv.data.parser.CategoryFamily
import app.opentv.data.parser.CategoryMacro
import app.opentv.data.parser.CategoryMacro.Macro
import app.opentv.data.parser.GroupBadge

/**
 * The live groups sorted under tabs (Italy, Football, Sport…): each tab lists its groups, the
 * same shelf from several feeds as one ("Top Italia" + "Top Italia [HI-DEF]"), foreign groups as
 * one entry per country, and — at the bottom — the channels running an hour or more behind as
 * "+1 hour", "+2 hours"… The groups keep the order (and merges) the user set from the phone.
 *
 * A tab's entries are picked by key, kept apart from plain group keys by a prefix:
 * `m:<group key>` (a group, its live channels only), `c:<country>` (a country, live channels
 * only) and `s:<MACRO>:<hours>` (a tab's channels shifted by that many hours).
 */
object LiveMacros {

    /** The tabs in the order they show, between Favourites and All. */
    val TABS = listOf(
        Macro.ITALY, Macro.FOOTBALL, Macro.SPORT, Macro.ENTERTAINMENT, Macro.CINEMA,
        Macro.KIDS, Macro.MUSIC, Macro.ABROAD,
    )

    data class Entry(
        val key: String,
        /** The broadcaster or country code above the name, small; null for none. */
        val kicker: String?,
        val name: String,
        val ids: List<String>,
        /** Group keys this entry covers, so "is the current group in here" holds after merges. */
        val memberKeys: Set<String>,
        /** Hours behind for a "+N" entry; 0 for the live channels of a group or country. */
        val shift: Int,
        /** Channels it lists (one per channel, as the list counts them); null when not known yet. */
        val count: Int? = null,
    )

    /** What a picked key means for the channel list: which categories, and which timeshift. */
    data class Pick(val ids: List<String>, val shift: Int, val groupKey: String?)

    /**
     * Every tab's entries. [channelKeys] (category id → channel keys in it) gives the counts and the
     * "+N" sections; without it the groups come with no counts and no sections. Empty entries drop out.
     */
    fun build(
        groups: List<ChannelsViewModel.CategoryGroup>,
        channelKeys: Map<String, Set<String>>? = null,
    ): Map<Macro, List<Entry>> {
        val out = LinkedHashMap<Macro, MutableList<Entry>>()
        val tabIds = HashMap<Macro, MutableList<String>>()
        // Shelves already placed, per tab: a second feed of one joins the first.
        val shelves = HashMap<Pair<Macro, String>, Int>()
        for (g in groups) {
            val sorted = CategoryMacro.of(g.rawName)
            val macro = sorted.macro
            if (macro == Macro.ADULT) continue
            tabIds.getOrPut(macro) { mutableListOf() } += g.ids
            val list = out.getOrPut(macro) { mutableListOf() }
            val label = GroupBadge.of(g.rawName, external = false).label
            // One shelf per name once feed tags are gone: "Sky Calcio SAT" is "Sky Calcio (Hermes)".
            val shelf = if (macro == Macro.ABROAD) "c:" + sorted.country
            else CategoryMacro.shelfKey(label).let { plain -> CategoryFamily.of(plain).key.ifEmpty { plain } }
            val at = shelves[macro to shelf]
            if (at != null) {
                val joined = list[at]
                list[at] = joined.copy(ids = joined.ids + g.ids, memberKeys = joined.memberKeys + g.memberKeys)
                continue
            }
            shelves[macro to shelf] = list.size
            list += if (macro == Macro.ABROAD) {
                val country = sorted.country.orEmpty()
                Entry("c:$country", country.takeIf { it.length == 2 }, CategoryMacro.countryName(country), g.ids, g.memberKeys, 0)
            } else {
                val clean = label.replace(TAGS, " ").replace(SPACES, " ").trim().ifEmpty { label }
                val (kicker, name) = CategoryMacro.kicker(clean)
                Entry("m:" + g.key, kicker, name, g.ids, g.memberKeys, 0)
            }
        }
        if (channelKeys == null) return out
        // Counts, and the tab's shifted channels gathered by hours.
        val result = LinkedHashMap<Macro, List<Entry>>()
        for ((macro, entries) in out) {
            val shifted = java.util.TreeMap<Int, MutableSet<String>>()
            val counted = entries.map { e ->
                val live = HashSet<String>()
                for (id in e.ids) for (k in channelKeys[id].orEmpty()) {
                    val hours = shiftOf(k)
                    if (hours == 0) live += k else shifted.getOrPut(hours) { HashSet() } += k
                }
                e.copy(count = live.size)
            }.filter { (it.count ?: 0) > 0 }
                // Countries biggest first; groups keep the provider's (or the user's) order.
                .let { list -> if (macro == Macro.ABROAD) list.sortedByDescending { it.count } else list }
            val ids = tabIds[macro].orEmpty()
            val sections = shifted.map { (hours, keys) ->
                Entry("s:${macro.name}:$hours", null, "+$hours", ids, emptySet(), hours, keys.size)
            }
            val all = counted + sections
            if (all.isNotEmpty()) result[macro] = all
        }
        return result
    }

    /** What [key] picks among [groups], or null when it isn't a tab entry's key. */
    fun resolve(key: String, groups: List<ChannelsViewModel.CategoryGroup>): Pick? {
        if (!(key.startsWith("m:") || key.startsWith("c:") || key.startsWith("s:"))) return null
        val tabs = build(groups)
        if (key.startsWith("s:")) {
            val parts = key.split(':')
            val macro = runCatching { Macro.valueOf(parts[1]) }.getOrNull() ?: return null
            val hours = parts.getOrNull(2)?.toIntOrNull() ?: return null
            return Pick(tabs[macro].orEmpty().flatMap { it.ids }, hours, null)
        }
        val entry = tabs.values.asSequence().flatten().firstOrNull { it.key == key }
            ?: if (key.startsWith("m:")) {
                // The group has moved tab or merged since: find it by any of its keys.
                val inner = key.removePrefix("m:")
                tabs.values.asSequence().flatten().firstOrNull { inner in it.memberKeys }
            } else null
        entry ?: return null
        return Pick(entry.ids, 0, if (key.startsWith("m:")) key.removePrefix("m:") else null)
    }

    /** The plain group key behind a tab entry's key, for the "Recent" list; null for countries and sections. */
    fun plainKey(key: String): String? = when {
        key.startsWith("m:") -> key.removePrefix("m:")
        key.startsWith("c:") || key.startsWith("s:") -> null
        else -> key
    }

    /** Hours behind a channel key says it runs ("rai1+1" → 1), 0 for a live one. */
    fun shiftOf(channelKey: String): Int = TimeShift.splitKey(channelKey)?.second ?: 0

    private val TAGS = Regex("\\[[^]]*]|\\([^)]*\\)")
    private val SPACES = Regex("\\s+")
}
