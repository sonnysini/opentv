/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage

/**
 * A channel's logo, or its initials on a tile when there is none (or it fails to load).
 * Callers size and clip it through [modifier], exactly as they did the bare image.
 */
@Composable
fun ChannelLogo(logoUrl: String?, name: String, modifier: Modifier = Modifier) {
    var failed by remember(logoUrl) { mutableStateOf(false) }
    if (logoUrl.isNullOrBlank() || failed) {
        Box(
            modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                channelInitials(name),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    } else {
        AsyncImage(
            model = logoUrl,
            contentDescription = null,
            onError = { failed = true },
            modifier = modifier,
        )
    }
}

/** `Rai 1` → "R1", `Sky Cinema Uno +2` → "SC", `Focus` → "FO". */
internal fun channelInitials(name: String): String {
    val words = name.split(' ', '-', '_', '|')
        .map { w -> w.filter { it.isLetterOrDigit() } }
        .filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "TV"
        words.size == 1 -> words[0].take(2)
        else -> "${words[0].first()}${words[1].first()}"
    }.uppercase()
}
