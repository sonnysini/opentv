/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
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

/**
 * A row of whole cards draws only between its margins (plus [slack], room for the focused card to
 * grow, smaller than the gap): the next card waiting beyond the edge stays out of sight instead of
 * peeking into the margin.
 */
fun Modifier.wholeCardsWindow(margin: Dp, slack: Dp = CARD_GAP - 1.dp): Modifier = drawWithContent {
    val edge = (margin - slack).toPx().coerceAtLeast(0f)
    clipRect(left = edge, top = 0f, right = size.width - edge, bottom = size.height) {
        this@drawWithContent.drawContent()
    }
}

/**
 * Scrolls just enough to show the focused item whole, as on a phone, with [margin] to spare. In a
 * row of whole cards, moving past the last one shifts the row by exactly one card.
 */
@OptIn(ExperimentalFoundationApi::class)
class JustEnough(private val margin: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val start = offset - margin
        val end = offset + size + margin
        return when {
            start >= 0f && end <= containerSize -> 0f
            start < 0f && end > containerSize -> 0f
            kotlin.math.abs(start) < kotlin.math.abs(end - containerSize) -> start
            else -> end - containerSize
        }
    }
}

/**
 * [JustEnough] for a row of cards [itemWidth] wide whose focused part may be inset or grown (padding
 * inside the card, the focus lift): the card's edges are worked out from the centre of what asked
 * to be shown, which neither changes. [centreToEnd] is how far the card's right edge is from that
 * centre (half the card, unless the focused part sits elsewhere in it, as the Top 10's poster).
 */
@OptIn(ExperimentalFoundationApi::class)
class WholeCards(
    private val margin: Float,
    private val itemWidth: Float,
    private val centreToEnd: Float = itemWidth / 2f,
) : BringIntoViewSpec {
    private val inner = JustEnough(margin)
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val end = offset + size / 2f + centreToEnd
        return inner.calculateScrollDistance(end - itemWidth, itemWidth, containerSize)
    }
}
