package app.opentv.ui

import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import app.opentv.R

val ACTION_PANEL_WIDTH = 440.dp

/** Red of the "record" dot, as on the recordings' badges. */
val RecordRed = Color(0xFFFF5A4E)

private val PanelOswald = FontFamily(Font(R.font.oswald_bold, FontWeight.Bold))

/**
 * Hold OK on something: what you can do with it, in a panel down the right; the page darkens
 * behind. A small kicker over a big Oswald title, maybe a thin progress bar, then the entries.
 * Back closes it (there's no "Cancel").
 *
 * [onDismiss] given = the panel opens in a window of its own over the whole app (the side menu
 * too), and Back reaches [onDismiss]; null = drawn in place, the caller handles Back.
 * [content] gets the entry the focus starts on: put it on one of them (see [ActionRow]'s modifier).
 */
@Composable
fun ActionPanel(
    kicker: String?,
    title: String,
    onDismiss: (() -> Unit)?,
    progress: Float? = null,
    progressLabel: String? = null,
    width: Dp = ACTION_PANEL_WIDTH,
    content: LazyListScope.(first: FocusRequester) -> Unit,
) {
    if (onDismiss == null) {
        PanelBody(kicker, title, progress, progressLabel, width, content)
        return
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        LaunchedEffect(window) {
            window?.setWindowAnimations(0)
            window?.setDimAmount(0f)
            window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        PanelBody(kicker, title, progress, progressLabel, width, content)
    }
}

@Composable
private fun PanelBody(
    kicker: String?,
    title: String,
    progress: Float?,
    progressLabel: String?,
    width: Dp,
    content: LazyListScope.(first: FocusRequester) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val surface = scheme.surface
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    // Opened by holding OK, the same press keeps repeating into the panel and its release would
    // tap the first entry. Ignore OK until a fresh press.
    var armed by remember { mutableStateOf(false) }
    val shown = remember { MutableTransitionState(false) }.apply { targetState = true }
    Box(
        Modifier.fillMaxSize().background(
            Brush.horizontalGradient(0f to Color(0xFF08090A).copy(alpha = 0.55f), 0.6f to Color(0xFF08090A).copy(alpha = 0.8f)),
        ),
    ) {
        AnimatedVisibility(
            visibleState = shown,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            enter = slideInHorizontally(tween(220)) { it } + fadeIn(tween(220)),
        ) {
            Row(Modifier.fillMaxHeight()) {
                // The panel's edge melts into the darkened page.
                Box(Modifier.width(60.dp).fillMaxHeight().background(Brush.horizontalGradient(listOf(Color.Transparent, surface))))
                Column(
                    Modifier
                        .width(width)
                        .fillMaxHeight()
                        .background(surface)
                        .onPreviewKeyEvent { e ->
                            val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                            when {
                                armed || !ok -> false
                                e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0 -> { armed = true; false }
                                e.type == KeyEventType.KeyUp -> { armed = true; true }
                                else -> true
                            }
                        }
                        .padding(start = 28.dp, end = 36.dp, top = 32.dp),
                ) {
                    if (!kicker.isNullOrBlank()) Text(
                        kicker.uppercase(),
                        color = scheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        title.uppercase(),
                        Modifier.padding(top = 4.dp, bottom = if (progress != null) 10.dp else 16.dp),
                        fontFamily = PanelOswald,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        lineHeight = 28.sp,
                        color = scheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (progress != null) ProgressLine(progress, progressLabel)
                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        content(first)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressLine(progress: Float, label: String?) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(scheme.surfaceVariant)) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(2.dp)).background(scheme.primary),
            )
        }
        if (label != null) Text(label, color = scheme.onSurfaceVariant, fontSize = 10.sp, maxLines = 1)
    }
}

/** "PIÙ TARDI", "CANALE": small spaced capitals over a part of the panel. */
@Composable
fun ActionLabel(text: String) {
    Text(
        text.uppercase(),
        Modifier.padding(start = 11.dp, top = 9.dp, bottom = 4.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        fontSize = 8.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.5.sp,
    )
}

/**
 * One entry: an icon (or a time, for a later programme) then the label. Focused = the theme's
 * pill, as everywhere else. [iconTint] colours the icon while not focused (the red record dot).
 */
@Composable
fun ActionRow(
    label: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    iconTint: Color? = null,
    time: String? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val fg = if (focused) scheme.onPrimary else scheme.onSurface
    val faint = if (focused) scheme.onPrimary else scheme.onSurfaceVariant
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(if (focused) scheme.primary else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        when {
            time != null -> Text(
                time,
                Modifier.width(38.dp),
                color = faint,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            icon != null -> Icon(
                painterResource(icon),
                contentDescription = null,
                tint = if (focused) scheme.onPrimary else iconTint ?: scheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            label,
            Modifier.weight(1f),
            color = fg,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A row of small choices (the qualities to record in): outlined, the chosen one filled. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActionChips(content: @Composable () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(start = 11.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
fun ActionChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val (bg, fg, edge) = when {
        focused -> Triple(scheme.primary, scheme.onPrimary, scheme.primary)
        selected -> Triple(scheme.onSurface, scheme.surface, scheme.onSurface)
        else -> Triple(Color.Transparent, Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0.45f))
    }
    Text(
        label,
        color = fg,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .border(1.dp, edge, RoundedCornerShape(5.dp))
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/**
 * The centred card for a question that needs a straight answer (watching live would stop a
 * recording): kicker, Oswald title, a line of text, then entries as in [ActionPanel].
 */
@Composable
fun ActionCard(
    kicker: String,
    title: String,
    text: String,
    onDismiss: () -> Unit,
    content: @Composable (first: FocusRequester) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        val scheme = MaterialTheme.colorScheme
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
        Column(
            Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(scheme.surface)
                .padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(kicker.uppercase(), color = scheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
            Text(
                title.uppercase(),
                Modifier.padding(top = 4.dp, bottom = 8.dp),
                fontFamily = PanelOswald,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                lineHeight = 28.sp,
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text, Modifier.padding(bottom = 14.dp), color = scheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
            content(first)
        }
    }
}

/** Puts the panel's starting focus on this entry. */
fun Modifier.startFocus(first: FocusRequester, on: Boolean = true): Modifier =
    if (on) focusRequester(first) else this

/**
 * Holding OK on this (while [isFocused]) calls [onHold] once; the rest of that press — repeats and
 * the release that would also click it — is swallowed.
 */
fun Modifier.holdOk(isFocused: () -> Boolean, onHold: () -> Unit): Modifier = composed {
    var held by remember { mutableStateOf(false) }
    onPreviewKeyEvent { e ->
        val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
        when {
            !ok -> false
            // A fresh press: the last one may have ended in a panel that took its release.
            e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0 -> { held = false; false }
            e.type == KeyEventType.KeyDown && e.nativeKeyEvent.isLongPress && isFocused() -> {
                held = true
                onHold()
                true
            }
            held && e.type == KeyEventType.KeyUp -> { held = false; true }
            else -> held
        }
    }
}
