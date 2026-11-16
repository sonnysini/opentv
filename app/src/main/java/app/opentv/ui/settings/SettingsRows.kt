/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentv.R
import app.opentv.ui.focusOnEnter

/**
 * True inside the settings page's drop-downs: a settings screen shown there leaves out its own
 * title and Done (the drop-down's row is its title, Back closes it) and only takes the height its
 * content needs.
 */
val LocalSettingsEmbedded = staticCompositionLocalOf { false }

/**
 * Where the focus lands coming into the settings page: put on the page's first row (or its first
 * pill). Without it the page picks the focusable nearest its left edge — a whole-row switch half
 * way down rather than the pills at the top.
 */
val LocalSettingsFirstFocus = staticCompositionLocalOf<androidx.compose.ui.focus.FocusRequester?> { null }

/** The root of a settings screen: the whole screen on its own, just its width in a drop-down. */
internal fun Modifier.settingsRoot(embedded: Boolean, horizontal: androidx.compose.ui.unit.Dp = 32.dp, vertical: androidx.compose.ui.unit.Dp = 24.dp): Modifier =
    if (embedded) fillMaxWidth().padding(top = 4.dp) else fillMaxSize().padding(horizontal = horizontal, vertical = vertical)

// Colours and sizes from the approved mock (1920×1080 at about 1.45 px per dp), dark like the menu.
internal val SetCard = Color(0xFF1D2422)
internal val SetSubs = Color(0xFF151B19)
internal val SetFocus = Color(0xFF1E443E)
internal val SetOn = Color(0xFFDEE4E1)
internal val SetDim = Color(0xFF9FA9A4)
internal val SetChipEdge = Color(0xFF333B39)
internal val SetOnAccent = Color(0xFF003730)
internal val SetSwitchOff = Color(0xFF3A4341)
internal val SetKnobOff = Color(0xFF8D9893)
internal val SetOswald = FontFamily(Font(R.font.oswald_regular, FontWeight.Normal), Font(R.font.oswald_bold, FontWeight.Bold))

private val RowShape = RoundedCornerShape(11.dp)

/** A small accent heading over a run of rows ("STILE", "PLAYER"). */
@Composable
internal fun SetGroup(text: String) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.8.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 8.dp),
    )
}

/**
 * One row: a card with an optional icon, the title and a line under it, and [trailing] on the right.
 * [onClick] makes the whole row one focus stop; without it the row is a frame for focusable things
 * inside ([trailing] pills) and lights up while one of them has the focus.
 */
@Composable
internal fun SetRow(
    title: String,
    subtitle: String? = null,
    @DrawableRes icon: Int? = null,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RowShape,
    onClick: (() -> Unit)? = null,
    // Many pills (six qualities) would squeeze the title to a sliver: they go on a line of their own.
    below: Boolean = false,
    titleColor: Color? = null,
    trailing: @Composable (focused: Boolean) -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val head: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        if (icon != null) {
            Icon(ImageVector.vectorResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp))
            Spacer(Modifier.width(15.dp))
        }
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = if (focused) MaterialTheme.colorScheme.primary else titleColor ?: SetOn,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, fontSize = 13.sp, color = SetDim, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
    val frame = modifier
        .fillMaxWidth()
        .heightIn(min = 58.dp)
        .onFocusChanged { focused = if (onClick != null) it.isFocused else it.hasFocus }
        .clip(shape)
        .background(if (focused) SetFocus else SetCard)
        .then(if (onClick != null) Modifier.clickable(interactionSource = null, indication = null, onClick = onClick) else Modifier)
        .padding(horizontal = 18.dp, vertical = 10.dp)
    if (below) {
        Column(frame) {
            Row(verticalAlignment = Alignment.CenterVertically, content = head)
            Box(Modifier.padding(top = 10.dp)) { trailing(focused) }
        }
    } else {
        Row(frame, verticalAlignment = Alignment.CenterVertically) {
            head()
            trailing(focused)
        }
    }
}

/** A row that does one thing on OK (opens a window, starts a sync): a value or label on the right and a caret. */
@Composable
internal fun SetActionRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    @DrawableRes icon: Int? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    SetRow(title, subtitle, icon, modifier = modifier, onClick = { if (enabled) onClick() }) { focused ->
        if (!value.isNullOrBlank()) {
            Text(
                value, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (focused) MaterialTheme.colorScheme.primary else SetDim,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Icon(
            ImageVector.vectorResource(R.drawable.ic_set_caret_down),
            contentDescription = null,
            tint = if (focused) MaterialTheme.colorScheme.primary else SetDim,
            modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = -90f },
        )
    }
}

/**
 * A window over the settings page (adding a provider, the phone QR, syncing): dark card in the
 * middle, the page dimmed behind. Back closes it.
 */
@Composable
internal fun SetDialog(
    title: String,
    onDismiss: () -> Unit,
    wide: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth(if (wide) 0.86f else 0.62f)
                // Never taller than the screen (a 1080p Fire is 540 dp high).
                .heightIn(max = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.9f).dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF0E1513))
                .border(1.dp, SetChipEdge, RoundedCornerShape(22.dp))
                .padding(horizontal = 32.dp, vertical = 26.dp)
                // Into the window with the d-pad: on its first control unless it put the focus somewhere itself.
                .focusOnEnter(),
        ) {
            Text(title.uppercase(), fontFamily = SetOswald, fontSize = 30.sp, color = SetOn, modifier = Modifier.padding(bottom = 14.dp))
            content()
        }
    }
}

/** An on/off row: OK anywhere on it flips the switch. */
@Composable
internal fun SetSwitchRow(title: String, subtitle: String? = null, checked: Boolean, @DrawableRes icon: Int? = null, modifier: Modifier = Modifier, onToggle: (Boolean) -> Unit) {
    SetRow(title, subtitle, icon, modifier = modifier, onClick = { onToggle(!checked) }) { SetSwitch(checked) }
}

/** The switch as drawn in the mock: a teal track with a dark knob on, grey off. */
@Composable
internal fun SetSwitch(checked: Boolean, small: Boolean = false) {
    val k by animateFloatAsState(if (checked) 1f else 0f, tween(160), label = "switch")
    val w = if (small) 38.dp else 44.dp
    val h = if (small) 21.dp else 25.dp
    val knob = h - 6.dp
    Box(
        Modifier
            .size(w, h)
            .clip(CircleShape)
            .background(if (checked) MaterialTheme.colorScheme.primary else SetSwitchOff),
    ) {
        Box(
            Modifier
                .offset(x = 3.dp + (w - knob - 6.dp) * k, y = 3.dp)
                .size(knob)
                .clip(CircleShape)
                .background(if (checked) SetOnAccent else SetKnobOff),
        )
    }
}

/** A value on the right with a caret: OK opens a picker (dialog). */
@Composable
internal fun SetValueRow(title: String, subtitle: String? = null, value: String, @DrawableRes icon: Int? = null, onClick: () -> Unit) {
    SetRow(title, subtitle, icon, onClick = onClick) { focused ->
        Text(value, fontSize = 16.sp, color = if (focused) MaterialTheme.colorScheme.primary else SetDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("  ›", fontSize = 20.sp, color = if (focused) MaterialTheme.colorScheme.primary else SetDim)
    }
}

/** One choice among a few, as pills on the row: ◀/▶ move between them, OK picks. */
@Composable
internal fun SetPillsRow(title: String, subtitle: String? = null, options: List<String>, selected: Int, first: Boolean = false, @DrawableRes icon: Int? = null, onSelect: (Int) -> Unit) {
    SetRow(title, subtitle, icon, below = options.size > 4) {
        Pills(options, isOn = { it == selected }, first = first, onClick = onSelect)
    }
}

/** Several choices that can each be on (multi-select pills). */
@Composable
internal fun SetMultiPillsRow(title: String, subtitle: String? = null, options: List<String>, isOn: (Int) -> Boolean, onClick: (Int) -> Unit) {
    SetRow(title, subtitle, below = options.size > 4) { Pills(options, isOn, onClick = onClick) }
}

/** A number stepped with − and +: "− 100% +" with the value between, as in the mock. */
@Composable
internal fun SetStepRow(title: String, subtitle: String? = null, value: String, canDown: Boolean, canUp: Boolean, onDown: () -> Unit, onUp: () -> Unit) {
    SetRow(title, subtitle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill("−", on = false, enabled = canDown, onClick = onDown)
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SetOn, modifier = Modifier.padding(horizontal = 12.dp))
            Pill("+", on = false, enabled = canUp, onClick = onUp)
        }
    }
}

@Composable
internal fun Pills(options: List<String>, isOn: (Int) -> Boolean, first: Boolean = false, onClick: (Int) -> Unit) {
    val firstFocus = if (first) LocalSettingsFirstFocus.current else null
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { i, label ->
            Pill(label, on = isOn(i), modifier = if (i == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier) { onClick(i) }
        }
    }
}

@Composable
internal fun Pill(label: String, on: Boolean, enabled: Boolean = true, small: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    val shape = CircleShape
    Text(
        label,
        fontSize = if (small) 13.sp else 14.5.sp,
        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
        color = when {
            on -> SetOnAccent
            focused -> primary
            !enabled -> SetDim.copy(alpha = 0.4f)
            else -> SetDim
        },
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer { val s = if (focused) 1.06f else 1f; scaleX = s; scaleY = s }
            .clip(shape)
            .background(if (on) primary else if (focused) primary.copy(alpha = 0.24f) else Color.Transparent)
            // The chosen pill with the focus on it gets a white edge, so the focus shows on it too.
            .border(if (on && focused) 2.dp else 1.5.dp, if (on && focused) Color.White else if (on || focused) primary else SetChipEdge, shape)
            .clickable(interactionSource = null, indication = null) { if (enabled) onClick() }
            .padding(horizontal = if (small) 12.dp else 16.dp, vertical = if (small) 5.dp else 8.dp),
    )
}

/**
 * A row that opens in place: its body slides down under it (the caret turns), the rows below move
 * down. Only one is open at a time — the page keeps which one ([open]).
 */
@Composable
internal fun SetAccordion(
    title: String,
    subtitle: String?,
    @DrawableRes icon: Int?,
    open: Boolean,
    onToggle: () -> Unit,
    headerModifier: Modifier = Modifier,
    body: @Composable () -> Unit,
) {
    val turn by animateFloatAsState(if (open) 180f else 0f, tween(220), label = "caret")
    Column(Modifier.fillMaxWidth()) {
        SetRow(
            title, subtitle, icon,
            modifier = headerModifier,
            shape = if (open) RoundedCornerShape(topStart = 11.dp, topEnd = 11.dp) else RowShape,
            onClick = onToggle,
        ) { focused ->
            Icon(
                ImageVector.vectorResource(R.drawable.ic_set_caret_down),
                contentDescription = null,
                tint = if (focused) MaterialTheme.colorScheme.primary else SetDim,
                modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = turn },
            )
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = open,
            enter = androidx.compose.animation.expandVertically(tween(240)) + androidx.compose.animation.fadeIn(tween(200)),
            exit = androidx.compose.animation.shrinkVertically(tween(200)) + androidx.compose.animation.fadeOut(tween(150)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 11.dp, bottomEnd = 11.dp))
                    .background(SetSubs)
                    .padding(start = 24.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
            ) {
                androidx.compose.runtime.CompositionLocalProvider(LocalSettingsEmbedded provides true) { body() }
            }
        }
    }
}

/** Plain explanatory text between rows. */
@Composable
internal fun SetNote(text: String) {
    Text(text, fontSize = 13.sp, color = SetDim, modifier = Modifier.padding(start = 6.dp, top = 2.dp, bottom = 6.dp))
}

/** A text button in the settings style: an outlined pill. */
@Composable
internal fun HubTextButton(label: String, modifier: Modifier, onClick: () -> Unit) =
    Pill(label, on = false, modifier = modifier, onClick = onClick)

@Composable
internal fun HubTextButton(label: String, onClick: () -> Unit) = HubTextButton(label, Modifier, onClick)

/**
 * A search box in the settings style, typed with the system keyboard (Fire TV's own): OK opens the
 * keyboard, its Search/Next closes it, ▲/▼ leave the box — a text field otherwise keeps the d-pad.
 */
@Composable
internal fun SetSearchField(
    value: String,
    onValue: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val primary = MaterialTheme.colorScheme.primary
    val shape = CircleShape
    val close = { keyboard?.hide(); Unit }
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = { onValue(it.take(40)) },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, color = SetOn),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(primary),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = androidx.compose.ui.text.input.ImeAction.Search,
            autoCorrectEnabled = false,
        ),
        // Fire TV's "Next" key closes its keyboard with ImeAction.Next whatever was asked.
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onSearch = { close() }, onDone = { close() }, onGo = { close() }, onNext = { close() },
        ),
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                when (e.key) {
                    androidx.compose.ui.input.key.Key.DirectionDown, androidx.compose.ui.input.key.Key.DirectionUp -> {
                        if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                            focusManager.moveFocus(
                                if (e.key == androidx.compose.ui.input.key.Key.DirectionDown) androidx.compose.ui.focus.FocusDirection.Down
                                else androidx.compose.ui.focus.FocusDirection.Up,
                            )
                        }
                        true
                    }
                    androidx.compose.ui.input.key.Key.DirectionCenter, androidx.compose.ui.input.key.Key.Enter, androidx.compose.ui.input.key.Key.NumPadEnter -> {
                        if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyUp) keyboard?.show()
                        true
                    }
                    else -> false
                }
            },
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(if (focused) primary.copy(alpha = 0.16f) else SetCard)
                    .border(1.5.dp, if (focused) primary else SetChipEdge, shape)
                    .padding(horizontal = 18.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    ImageVector.vectorResource(R.drawable.ic_nav_search),
                    contentDescription = null,
                    tint = if (focused) primary else SetDim,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(hint, fontSize = 16.sp, color = SetDim, maxLines = 1)
                    inner()
                }
                if (focused) {
                    Text(androidx.compose.ui.res.stringResource(R.string.search_ok_to_type), fontSize = 13.sp, color = SetDim, maxLines = 1)
                }
            }
        },
    )
}

/**
 * A text field for the settings windows, typed with the system keyboard. ▲/▼ leave it (a text
 * field otherwise keeps the d-pad); the keyboard's Done/Next runs [onDone].
 */
@Composable
internal fun SetTextField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    uri: Boolean = false,
    onDone: () -> Unit = {},
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = if (uri) androidx.compose.ui.text.input.KeyboardType.Uri else androidx.compose.ui.text.input.KeyboardType.Text,
            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            autoCorrectEnabled = false,
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onDone = { onDone() }, onNext = { onDone() }, onGo = { onDone() },
        ),
        modifier = modifier.fillMaxWidth().dpadLeavesField(focusManager),
    )
}

/** A short status line under a control: teal when it went well, red when it did not. */
@Composable
internal fun SetStatus(text: String, error: Boolean = false) {
    Text(
        text,
        fontSize = 13.sp,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp, top = 4.dp),
    )
}
