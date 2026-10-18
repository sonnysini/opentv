/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.player

import java.util.concurrent.ConcurrentHashMap

/**
 * The quality variant that last played for each logical channel, so zapping back to a channel
 * whose preferred feed is dead starts straight on the one that worked instead of waiting out the
 * fallback again.
 *
 * In memory only and cleared on every fresh launch: a better feed that comes back gets retried.
 * Keyed by every variant id of the channel, so it matches however the channel is reached.
 */
object WorkingVariants {
    private val byVariant = ConcurrentHashMap<Long, Long>()

    fun remember(variantIds: Collection<Long>, working: Long) {
        variantIds.forEach { byVariant[it] = working }
    }

    fun forChannel(id: Long): Long? = byVariant[id]

    fun clear() = byVariant.clear()
}
