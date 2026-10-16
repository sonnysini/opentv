/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.core

import androidx.compose.runtime.Immutable
import app.opentv.data.model.Category
import app.opentv.data.model.StreamKind
import app.opentv.data.parser.CategoryLanguage
import app.opentv.data.repo.CatalogRepository

/**
 * Whether a category is switched off: by hand ([disabled]) or, for live categories, by a language
 * rule — [italianOnly] drops foreign-language groups, [foreignSportOff] the international sport
 * ones. Rules apply to categories the provider adds later too. [forcedOn] holds the live keys the
 * user switched back on by hand, which beats a rule.
 */
@Immutable
data class CategoryRules(
    val disabled: Set<String> = emptySet(),
    val forcedOn: Set<String> = emptySet(),
    val italianOnly: Boolean = false,
    val foreignSportOff: Boolean = false,
) {
    val isEmpty: Boolean get() = disabled.isEmpty() && !italianOnly && !foreignSportOff

    fun isOff(category: Category): Boolean =
        isOff(CatalogRepository.categoryKey(category), category.kind, category.name)

    fun isOff(key: String, kind: StreamKind, name: String): Boolean {
        if (key in disabled) return true
        if (kind != StreamKind.LIVE || key in forcedOn) return false
        return ruleHides(name)
    }

    /** Whether a language rule (ignoring hand-made choices) switches off the live category [name]. */
    fun ruleHides(name: String): Boolean =
        (italianOnly && CategoryLanguage.isForeign(name)) ||
            (foreignSportOff && CategoryLanguage.isForeignSport(name))
}
