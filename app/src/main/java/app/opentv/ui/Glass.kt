package app.opentv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The frosted dark glass of the side menu capsule. No real blur: RenderEffect needs API 31 and
 * the Fire TV sticks run Android 9/11, so it is a dense tint plus a faint white edge.
 */
val Glass = Color(0xD90E1513)

/** The glass's thin white edge. */
val GlassEdge = Color.White.copy(alpha = 0.09f)

/**
 * A floating glass capsule behind a group of controls (the player's buttons). It doesn't clip,
 * so a focused button's name can still float above the capsule.
 */
fun Modifier.glass(radius: Dp = 19.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this.background(Glass, shape).border(1.dp, GlassEdge, shape)
}
