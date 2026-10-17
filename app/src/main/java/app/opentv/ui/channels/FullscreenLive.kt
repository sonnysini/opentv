/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.channels

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.opentv.R
import app.opentv.data.model.shownName
import app.opentv.ui.ChannelsViewModel
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Live TV as one full-screen list, TiviMate-style: no preview, no rail, no menu — just the channels
 * of the current group with what's on now (and next) against each one. ☰ (the remote's menu key)
 * swaps the list for the categories in the same style, with the provider's own names — flags and
 * emoji included; OK on one goes back to its channels. OK on a channel plays it full-screen.
 * LEFT on a row of either list asks the host to reveal the app's navigation menu.
 */
@Composable
fun FullscreenLive(
    rows: List<ChannelsViewModel.Row>,
    categories: List<ChannelsViewModel.CategoryGroup>,
    selectedCategory: String?,
    favouritesOnly: Boolean,
    highlightedKey: Any?,
    // The guide has been built at least once (and no sync is running), so an empty list is final.
    rowsLoaded: Boolean,
    // Where to start when the highlight isn't in the list yet (rows only just loaded): the last
    // watched channel, so returning from the player lands back on it.
    fallbackKey: (List<ChannelsViewModel.Row>) -> Any?,
    nowMillis: Long,
    onSelectCategory: (String?) -> Unit,
    onSelectFavourites: () -> Unit,
    onFocusRow: (ChannelsViewModel.Row) -> Unit,
    onPlay: (ChannelsViewModel.Row) -> Unit,
    onToggleFavourite: (ChannelsViewModel.Row) -> Unit,
    onExitLeft: () -> Unit,
    emptyContent: @Composable () -> Unit,
) {
    var showCategories by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showCategories) { showCategories = false }

    // After picking another category the old group's rows are still on hand for a moment; hold the
    // list back until the new ones arrive, so focus lands on the new group's first channel instead
    // of on whatever sat at the old position. The timeout covers a group that comes back identical
    // (two empty groups), which a StateFlow wouldn't re-emit.
    var awaitingRows by remember { mutableStateOf<List<ChannelsViewModel.Row>?>(null) }
    LaunchedEffect(rows) { if (awaitingRows != null && rows !== awaitingRows) awaitingRows = null }
    LaunchedEffect(awaitingRows) {
        if (awaitingRows != null) {
            delay(3_000)
            awaitingRows = null
        }
    }

    // A group that turns out empty (all its channels gone, filtered or hidden) would otherwise sit
    // on the loading screen for good. Once the guide is known to be built, give it a moment, then
    // go back to the categories so the user can pick another.
    val groupEmpty = !showCategories && awaitingRows == null && rows.isEmpty() && rowsLoaded
    LaunchedEffect(groupEmpty) {
        if (groupEmpty) {
            delay(1_500)
            showCategories = true
        }
    }

    // When nothing in the list can hold focus (loading, empty), park it on the screen itself —
    // with focus nowhere, the remote's keys (☰ included) would go nowhere either.
    val holderFocus = remember { FocusRequester() }
    val needsHolder = !showCategories && (awaitingRows != null || rows.isEmpty())
    LaunchedEffect(needsHolder) {
        if (needsHolder) {
            delay(50)
            runCatching { holderFocus.requestFocus() }
        }
    }

    val favouritesLabel = stringResource(R.string.guide_favourites)
    val allLabel = stringResource(R.string.guide_all_channels)
    val title = when {
        favouritesOnly -> favouritesLabel
        selectedCategory == null -> allLabel
        else -> categories.firstOrNull { selectedCategory in it.memberKeys }?.rawName ?: ""
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.Menu -> { showCategories = !showCategories; true }
                    Key.DirectionLeft -> if (needsHolder) { onExitLeft(); true } else false
                    else -> false
                }
            }
            .padding(horizontal = 32.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (showCategories) stringResource(R.string.live_categories_title) else title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!showCategories && rows.isNotEmpty()) {
                Text(
                    "${rows.size}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(24.dp))
            }
            Text(
                stringResource(if (showCategories) R.string.live_menu_hint_categories else R.string.live_menu_hint),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))

        if (showCategories) {
            data class Entry(val key: String, val label: String, val selected: Boolean, val pick: () -> Unit)
            val entries = buildList {
                add(Entry("fav", favouritesLabel, favouritesOnly) { onSelectFavourites() })
                add(Entry("all", allLabel, !favouritesOnly && selectedCategory == null) { onSelectCategory(null) })
                categories.forEach { g ->
                    add(Entry("cat-${g.key}", g.rawName, !favouritesOnly && selectedCategory in g.memberKeys) { onSelectCategory(g.key) })
                }
            }
            val startIndex = entries.indexOfFirst { it.selected }.coerceAtLeast(0)
            FocusedList(count = entries.size, startIndex = startIndex) { index, requester ->
                val entry = entries[index]
                CategoryEntry(
                    label = entry.label,
                    selected = entry.selected,
                    modifier = requester,
                    onLeft = onExitLeft,
                    onClick = {
                        if (!entry.selected) {
                            awaitingRows = rows
                            entry.pick()
                        }
                        showCategories = false
                    },
                )
            }
        } else if (awaitingRows != null) {
            Spacer(Modifier.weight(1f).fillMaxWidth().focusRequester(holderFocus).focusable())
        } else if (rows.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().focusRequester(holderFocus).focusable()) {
                if (groupEmpty) {
                    Text(
                        stringResource(R.string.live_group_empty),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    emptyContent()
                }
            }
        } else {
            val startIndex = rows.indexOfFirst { it.key == highlightedKey }.takeIf { it >= 0 }
                ?: fallbackKey(rows)?.let { key -> rows.indexOfFirst { it.key == key } }?.coerceAtLeast(0)
                ?: 0
            // Keyed on the group so switching category re-focuses the new list's first row.
            androidx.compose.runtime.key(favouritesOnly, selectedCategory) {
                FocusedList(count = rows.size, startIndex = startIndex) { index, requester ->
                    val row = rows[index]
                    FullscreenChannelRow(
                        row = row,
                        nowMillis = nowMillis,
                        modifier = requester,
                        onFocus = { onFocusRow(row) },
                        onPlay = { onPlay(row) },
                        onToggleFavourite = { onToggleFavourite(row) },
                        onLeft = onExitLeft,
                    )
                }
            }
        }
    }
}

/**
 * A lazy list that, on first composition, scrolls to [startIndex] and puts focus on that item —
 * so opening the categories lands on the current one and coming back lands on the channel you
 * were on. [item] gets a modifier to attach to its focusable root.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FocusedList(
    count: Int,
    startIndex: Int,
    item: @Composable (index: Int, requester: Modifier) -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (startIndex - 3).coerceAtLeast(0))
    val focusRequester = remember { FocusRequester() }
    var listHasFocus by remember { mutableStateOf(false) }
    // The requester follows focus, so coming back into the list (RIGHT from the navigation menu)
    // returns to the row you left rather than to whichever row sits nearest the menu.
    var focusedIndex by remember { mutableIntStateOf(startIndex) }
    LaunchedEffect(Unit) {
        // The item has to be laid out — and, at app start, the window focused — before it can take
        // focus, and a request made too early is silently dropped. Keep asking until it sticks.
        repeat(40) {
            delay(75)
            if (listHasFocus) return@LaunchedEffect
            runCatching { focusRequester.requestFocus() }
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .focusProperties { enter = { focusRequester } }
            .onFocusChanged { listHasFocus = it.hasFocus },
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(List(count) { it }, key = { i, _ -> i }) { index, _ ->
            val tracked = Modifier.onFocusChanged { if (it.isFocused) focusedIndex = index }
            item(index, if (index == focusedIndex) tracked.focusRequester(focusRequester) else tracked)
        }
    }
}

@Composable
private fun CategoryEntry(label: String, selected: Boolean, modifier: Modifier, onLeft: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .leftReveals({ focused }, onLeft)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    focused -> MaterialTheme.colorScheme.primary
                    selected -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surface
                },
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleLarge,
            color = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One channel across the full width: number, logo and name, with the programme on now and its
 * progress underneath, and what's next on the right where a full-screen row has room for it.
 */
@Composable
private fun FullscreenChannelRow(
    row: ChannelsViewModel.Row,
    nowMillis: Long,
    modifier: Modifier,
    onFocus: () -> Unit,
    onPlay: () -> Unit,
    onToggleFavourite: () -> Unit,
    onLeft: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .leftReveals({ focused }, onLeft)
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .then(if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            row.primary.number?.toString() ?: "",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.width(48.dp),
        )
        AsyncImage(
            model = row.primary.logoUrl,
            contentDescription = null,
            modifier = Modifier.size(width = 72.dp, height = 48.dp).clip(RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(0.6f)) {
            Text(
                row.primary.shownName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val nowProg = row.now
            Spacer(Modifier.height(2.dp))
            Text(
                text = nowProg?.let {
                    "${clock.format(Date(it.startUtcMillis))}–${clock.format(Date(it.endUtcMillis))}  ${it.title}"
                } ?: stringResource(R.string.guide_no_info),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (nowProg != null) {
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(nowProg.progressAt(nowMillis).coerceIn(0f, 1f))
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
        Spacer(Modifier.width(24.dp))
        Text(
            row.next?.let { stringResource(R.string.guide_next_prefix, clock.format(Date(it.startUtcMillis)), it.title) } ?: "",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.4f),
        )
        IconButton(onClick = onToggleFavourite, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector = if (row.primary.favourite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = if (row.primary.favourite) stringResource(R.string.common_remove_favourite)
                else stringResource(R.string.common_favourite),
                tint = if (row.primary.favourite) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * LEFT on the row itself (not on a child like the star button, which LEFT should just leave)
 * calls [onLeft] — the host's "reveal the navigation menu".
 */
private fun Modifier.leftReveals(isRowFocused: () -> Boolean, onLeft: () -> Unit): Modifier =
    onPreviewKeyEvent { e ->
        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft && isRowFocused()) {
            onLeft()
            true
        } else {
            false
        }
    }

private val clock = SimpleDateFormat("HH:mm", Locale.getDefault())
