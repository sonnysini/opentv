/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The bottom of a page of rows melting into the page: rows aren't all the same height, so whatever
 * is cut off at the bottom of the screen fades out instead of ending on a hard edge.
 */
fun Modifier.bottomFade(height: Dp = BOTTOM_FADE_HEIGHT): Modifier = composed {
    val page = MaterialTheme.colorScheme.background
    drawWithContent {
        drawContent()
        val h = height.toPx().coerceAtMost(size.height)
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, page), startY = size.height - h, endY = size.height),
            topLeft = Offset(0f, size.height - h),
            size = Size(size.width, h),
        )
    }
}

val BOTTOM_FADE_HEIGHT = 68.dp

/** The gap between cards in a row of the VOD and football pages. */
val CARD_GAP = 12.dp

/**
 * How wide each of [count] cards is so that exactly that many fill the row, with [margin] on both
 * sides and [gap] between them: no sliver of a next card at the right edge.
 */
fun BoxWithConstraintsScope.cardWidth(count: Int, margin: Dp, gap: Dp = CARD_GAP): Dp =
    exactCardWidth(maxWidth, count, margin, gap)

fun exactCardWidth(width: Dp, count: Int, margin: Dp, gap: Dp = CARD_GAP): Dp =
    ((width - margin * 2 - gap * (count - 1)) / count).coerceAtLeast(48.dp)
