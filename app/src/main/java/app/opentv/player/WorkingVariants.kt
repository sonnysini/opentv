/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.player

import app.opentv.data.model.Channel
import java.util.concurrent.ConcurrentHashMap

/**
 * The quality variant that last played for each logical channel, so zapping back to a channel
 * whose preferred feed is dead starts straight on the one that worked instead of waiting out the
 * fallback again.
 *
 * In memory only and cleared on every fresh launch: a better feed that comes back gets retried.
 * Keyed by the logical channel (its quality group), so it matches whichever copy the list holds.
 */
object WorkingVariants {
    private val byChannel = ConcurrentHashMap<String, Long>()

    private fun key(channel: Channel) = channel.groupKey.ifEmpty { "id:${channel.id}" }

    fun remember(channel: Channel, workingVariantId: Long) {
        byChannel[key(channel)] = workingVariantId
    }

    fun forChannel(channel: Channel): Long? = byChannel[key(channel)]

    fun clear() = byChannel.clear()
}
