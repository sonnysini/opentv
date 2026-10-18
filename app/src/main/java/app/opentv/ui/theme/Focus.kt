/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** True while the OwnTV style is on; the Classic style keeps the original focus visuals. */
val LocalOwnTvStyle = staticCompositionLocalOf { false }

/**
 * OwnTV-style lift for an element that draws its own focus: grows it slightly while focused,
 * about 12 dp wider whatever its size (≈1% on a full-width row, capped at 5% on a small key or
 * chip). Put it before the element's clip/background so those scale with the content.
 * Does nothing in the Classic style.
 */
@Composable
fun Modifier.focusLift(focused: Boolean): Modifier {
    if (!LocalOwnTvStyle.current) return this
    val progress by animateFloatAsState(if (focused) 1f else 0f, tween(FOCUS_ANIM_MILLIS), label = "focusLift")
    return graphicsLayer {
        val grow = if (size.width > 0f) (12.dp.toPx() / size.width).coerceAtMost(0.05f) else 0f
        val scale = 1f + grow * progress
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Replaces Material's grey focus/press overlay on plain clickable rows in the OwnTV style: a
 * tonal fill and an accent ring around the focused element, fading in and out.
 */
data class OwnTvFocusIndication(val ring: Color, val fill: Color, val press: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        OwnTvFocusNode(interactionSource, ring, fill, press)
}

private class OwnTvFocusNode(
    private val interactionSource: InteractionSource,
    private val ring: Color,
    private val fill: Color,
    private val press: Color,
) : Modifier.Node(), DrawModifierNode {
    private val progress = Animatable(0f)
    private var pressed = false

    override fun onAttach() {
        coroutineScope.launch {
            var focusCount = 0
            var pressCount = 0
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is FocusInteraction.Focus -> focusCount++
                    is FocusInteraction.Unfocus -> focusCount--
                    is PressInteraction.Press -> pressCount++
                    is PressInteraction.Release, is PressInteraction.Cancel -> pressCount--
                }
                pressed = pressCount > 0
                val target = if (focusCount > 0) 1f else 0f
                if (progress.targetValue != target) {
                    launch {
                        progress.animateTo(target, tween(FOCUS_ANIM_MILLIS)) { invalidateDraw() }
                    }
                }
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val p = progress.value
        val radius = CornerRadius(10.dp.toPx())
        if (p > 0f) drawRoundRect(fill.copy(alpha = fill.alpha * p), cornerRadius = radius)
        drawContent()
        if (pressed) drawRoundRect(press, cornerRadius = radius)
        if (p > 0f) {
            val stroke = 2.dp.toPx()
            drawRoundRect(
                ring.copy(alpha = ring.alpha * p),
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = radius,
                style = Stroke(stroke),
            )
        }
    }
}

private const val FOCUS_ANIM_MILLIS = 120
