/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.player

/**
 * Names for a channel's quality variants that tell same-quality feeds apart: with two FHD feeds
 * the Quality button reads `FHD 1` / `FHD 2`, and the picker and the switch notice say
 * `FHD 1 · Hermes` / `FHD 2 · SAT`. A quality with a single feed keeps its plain name (`HD`).
 *
 * The quality is the label's first word (`FHD` of `FHD HEVC · Hermes`); feeds are numbered in
 * the order given, which is the channel's variant order.
 */
object QualityNames {

    data class Name(
        /** Short text for the Quality button: `FHD 2`. Empty when the label says nothing. */
        val badge: String,
        /** The full label with the number in: `FHD 2 · SAT`. Empty when the label is empty. */
        val full: String,
    )

    fun of(labels: List<Pair<Long, String>>): Map<Long, Name> {
        val quality = labels.associate { (id, label) -> id to label.trim().substringBefore(' ') }
        val counts = quality.values.filter { it.isNotEmpty() }.groupingBy { it.uppercase() }.eachCount()
        val seen = HashMap<String, Int>()
        return labels.associate { (id, label) ->
            val q = quality.getValue(id)
            val key = q.uppercase()
            val name = if (q.isEmpty() || (counts[key] ?: 0) < 2) {
                Name(q, label.trim())
            } else {
                val n = (seen[key] ?: 0) + 1
                seen[key] = n
                Name("$q $n", "$q $n" + label.trim().removePrefix(q))
            }
            id to name
        }
    }
}
