/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.parser

/**
 * Restream source tags seen in real lists, kept in this one place so the rest of the code (and
 * the tests) can talk about sources in general. Matching is case-insensitive everywhere.
 */
object SourceTags {

    /** Tags written into channel names. The list grows per provider with learned tags. */
    val CHANNEL = setOf(
        "STEL", "KALI", "KALY", "DVS", "SAT", "STELLINA", "HERMES", "QUALITY",
        "SKYNET", "SKYNET+", "STEL-DVS",
    )

    /** Tags that also lead a name without brackets. Short ones don't, they read as words. */
    val LEADING = setOf("STEL", "KALI", "KALY")

    /** Tags that split live categories, as regex alternatives. */
    val CATEGORY = listOf("stel-dvs", "hermes", "stellina", "skynet\\+?", "kali", "kaly", "dvs", "sat")

    /** Spellings that title case gets wrong, by lowercase tag. */
    val LABELS = mapOf("skynet" to "SkyNet", "skynet+" to "SkyNet", "stel-dvs" to "Stel-DVS")
}
