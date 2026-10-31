/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.channels

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentv.R
import app.opentv.data.repo.TvHighlightsRepository
import app.opentv.data.repo.TvHighlightsRepository.Highlight
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The top of the live TV page: tonight's big programmes, one at a time, turning over by themselves,
 * dots underneath to say how many there are and which one this is. Shown at start and whenever
 * focus goes up to the tabs or onto it; folded away once the channels have it.
 *
 * "Watch on …" is its one stop for the d-pad: OK tunes the programme's channel (on now or not), LEFT
 * and RIGHT step through the programmes, LEFT on the first hands over to the navigation menu.
 */
@Composable
fun TvHero(
    items: List<Highlight>,
    expanded: Boolean,
    nowMillis: Long,
    onWatch: (Highlight) -> Unit,
    onExitLeft: () -> Unit,
    // The button's handle, and where DOWN from it goes (the tabs).
    buttonFocus: FocusRequester? = null,
    below: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    var index by remember { mutableIntStateOf(0) }
    if (index >= items.size) index = 0
    val item = items[index]
    // Folds away to nothing while the channels have focus; the button can't take focus meanwhile.
    val height by animateDpAsState(if (expanded) EXPANDED_HEIGHT else 0.dp, label = "heroHeight")

    // Turns over every few seconds; any step by hand starts the count again. Held while the button
    // has focus, so OK plays the programme on screen, not the one that slid in meanwhile.
    var held by remember { mutableStateOf(false) }
    val turn = remember { Animatable(0f) }
    val counted = remember { intArrayOf(-1) }
    LaunchedEffect(index, items.size, held) {
        if (counted[0] != index) {
            counted[0] = index
            turn.snapTo(0f)
        }
        if (held || items.size < 2) return@LaunchedEffect
        val left = ((1f - turn.value) * TURN_MILLIS).toInt().coerceAtLeast(1)
        turn.animateTo(1f, tween(left, easing = LinearEasing))
        index = (index + 1) % items.size
    }

    Box(modifier.fillMaxWidth().height(height).clipToBounds()) {
        // Laid out at full height whatever the fold, so nothing reflows as it opens and closes.
        Box(Modifier.fillMaxWidth().requiredHeight(EXPANDED_HEIGHT).align(Alignment.TopStart)) {
            Backdrop(item, Modifier.align(Alignment.TopEnd))
            Column(Modifier.padding(start = 32.dp, top = 26.dp, end = 32.dp)) {
                Kicker(item, nowMillis, small = false)
                Spacer(Modifier.height(8.dp))
                Text(
                    item.title.uppercase(),
                    fontFamily = Oswald,
                    fontWeight = FontWeight.Bold,
                    fontSize = 44.sp,
                    lineHeight = 48.sp,
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.62f),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    meta(item),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.synopsis.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        item.synopsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.5f),
                    )
                }
            }
            WatchButton(
                text = stringResource(R.string.tv_hero_watch, item.network),
                small = false,
                onClick = { onWatch(item) },
                onLeft = { if (index > 0) index-- else onExitLeft() },
                onRight = { index = (index + 1) % items.size },
                onFocus = { held = it },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 32.dp, bottom = 40.dp)
                    .then(if (buttonFocus != null) Modifier.focusRequester(buttonFocus) else Modifier)
                    .focusProperties {
                        canFocus = expanded
                        below?.let { down = it }
                    },
            )
            Dots(items.size, index, turn.value, Modifier.align(Alignment.BottomStart).padding(start = 34.dp, bottom = 18.dp))
        }
    }
}

/** The programme's still over the right of the screen, melting into the page left and below. */
@Composable
private fun Backdrop(item: Highlight, modifier: Modifier) {
    val page = MaterialTheme.colorScheme.background
    val context = LocalContext.current
    Crossfade(item, animationSpec = tween(600), label = "heroBackdrop", modifier = modifier.fillMaxHeight().aspectRatio(16f / 9f)) { shown ->
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    drawRect(Brush.horizontalGradient(0f to page, 0.3f to page.copy(alpha = 0.55f), 0.65f to Color.Transparent))
                    drawRect(Brush.verticalGradient(0.55f to Color.Transparent, 1f to page))
                },
        ) {
            val url = shown.imageUrl
            // Some stills are refused or missing: the fallback below then, not a blank.
            var failed by remember(url) { mutableStateOf(false) }
            if (url != null && !failed) {
                AsyncImage(
                    onError = { failed = true },
                    model = remember(url) {
                        ImageRequest.Builder(context).data(url).apply {
                            TvHighlightsRepository.IMAGE_HEADERS.forEach { (k, v) -> addHeader(k, v) }
                        }.crossfade(true).build()
                    },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // No still: the network's logo, faint, on a soft wash.
                Box(
                    Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF16302B), Color(0xFF0E1513)))),
                    contentAlignment = Alignment.Center,
                ) {
                    shown.logoUrl?.let {
                        AsyncImage(
                            model = it,
                            contentDescription = null,
                            modifier = Modifier.size(160.dp).graphicsLayer { alpha = 0.25f },
                        )
                    }
                }
            }
        }
    }
}

/** Network logo, "TONIGHT · 21:40" (or "ON NOW · 21:40 – 00:05"), the premiere/live tag. */
@Composable
private fun Kicker(item: Highlight, nowMillis: Long, small: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        item.logoUrl?.let {
            AsyncImage(
                model = it,
                contentDescription = item.network,
                modifier = Modifier.heightIn(max = if (small) 16.dp else 24.dp).widthIn(max = 64.dp),
            )
        }
        val onNow = item.startUtcMillis <= nowMillis
        val time = if (onNow) "${clock(item.startUtcMillis)} – ${clock(item.endUtcMillis)}" else clock(item.startUtcMillis)
        Text(
            (stringResource(if (onNow) R.string.tv_hero_on_now else R.string.tv_hero_tonight) + " · " + time).uppercase(),
            style = if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp,
            color = Color.White.copy(alpha = 0.8f),
            maxLines = 1,
        )
        val badge = when (item.badge) {
            TvHighlightsRepository.Badge.PREMIERE -> R.string.tv_badge_premiere
            TvHighlightsRepository.Badge.LIVE -> R.string.tv_badge_live
            TvHighlightsRepository.Badge.FINALE -> R.string.tv_badge_finale
            TvHighlightsRepository.Badge.NONE -> null
        }
        if (badge != null) {
            val live = item.badge == TvHighlightsRepository.Badge.LIVE
            Text(
                stringResource(badge).uppercase(),
                fontSize = if (small) 10.sp else 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                color = if (live) Color.White else MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (live) LIVE_RED else MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

/** "Show · Season 13 · Ep. 10 · 21:40 – 00:05". */
@Composable
private fun meta(item: Highlight): String = listOfNotNull(
    item.genre,
    item.season?.let { stringResource(R.string.tv_hero_season, it) },
    item.episode?.let { stringResource(R.string.tv_hero_episode, it) },
    "${clock(item.startUtcMillis)} – ${clock(item.endUtcMillis)}",
).joinToString(" · ")

/** Streaming-app button, as on the football page: translucent grey, white with black text in focus. */
@Composable
private fun WatchButton(
    text: String,
    small: Boolean,
    onClick: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onFocus: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale = if (focused) 1.05f else 1f
    Text(
        "▶  $text",
        fontSize = if (small) 13.sp else 17.sp,
        fontWeight = FontWeight.Bold,
        color = if (focused) Color.Black else Color.White,
        maxLines = 1,
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color.White else Color(0x736E7672))
            .onFocusChanged {
                focused = it.isFocused
                onFocus(it.isFocused)
            }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { onLeft(); true }
                    Key.DirectionRight -> { onRight(); true }
                    else -> false
                }
            }
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(horizontal = if (small) 14.dp else 24.dp, vertical = if (small) 5.dp else 11.dp),
    )
}

/** One dot per programme; this one's is a pill filling up until the next turns over. */
@Composable
private fun Dots(count: Int, current: Int, turned: Float, modifier: Modifier = Modifier) {
    if (count < 2) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val dim = Color.White.copy(alpha = 0.28f)
            if (i == current) {
                Box(Modifier.width(28.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(dim)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(turned.coerceIn(0.05f, 1f)).clip(RoundedCornerShape(3.dp)).background(Color.White))
                }
            } else {
                Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(dim))
            }
        }
    }
}

private fun clock(utcMillis: Long): String = CLOCK.format(Date(utcMillis))

private val CLOCK = SimpleDateFormat("HH:mm", Locale.getDefault())
private val Oswald = FontFamily(Font(R.font.oswald_bold, FontWeight.Bold))
private val LIVE_RED = Color(0xFFE53935)
private val EXPANDED_HEIGHT = 292.dp
private const val TURN_MILLIS = 8_000
