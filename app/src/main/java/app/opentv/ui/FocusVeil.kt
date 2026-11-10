/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * The focus on anything made of words (menu entries, tabs, rows, chips, text buttons): a faint veil
 * of the accent over the ground the item has unfocused, with its text and icon in the accent —
 * no solid pill, no frame. Posters and cards keep their own focus.
 */
@Composable
@ReadOnlyComposable
fun focusVeil(ground: Color = Color.Transparent): Color =
    MaterialTheme.colorScheme.primary.copy(alpha = FOCUS_VEIL_ALPHA).compositeOver(ground)

const val FOCUS_VEIL_ALPHA = 0.24f
