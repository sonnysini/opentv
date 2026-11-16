/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.channels

import androidx.activity.compose.BackHandler
import app.opentv.ui.focusVeil
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
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
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.R
import app.opentv.data.model.shownName
import app.opentv.ui.ChannelsViewModel
import app.opentv.ui.settings.Pill
import app.opentv.ui.settings.Pills
import app.opentv.ui.settings.SetCard
import app.opentv.ui.settings.SetDim
import app.opentv.ui.settings.SetFocus
import app.opentv.ui.settings.SetOn
import app.opentv.ui.settings.SetSearchField
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Channel manager: browse your channels by category and, for each, favourite it or hide it from
 * the guide. In the settings style: a search box on top (typed with the system keyboard), the
 * categories on the left, their channels on the right — each a logo, the name and two pills,
 * Favourite and Show. Hidden channels appear here too, greyed, so they can be brought back.
 *
 * Typing two letters or more turns the right side into the search results; with the box empty it
 * is the selected category's channels (a flat list of a 20,000-channel provider is unscrollable
 * on a remote, so browsing is by category by design).
 */
@Composable
fun ChannelManagerScreen(
    onBack: () -> Unit,
    viewModel: ChannelsViewModel = viewModel(),
) {
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val categoryGroups by viewModel.managerCategoryGroups.collectAsStateWithLifecycle()
    val selectedSource by viewModel.managerSelectedSource.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.managerSelectedCategory.collectAsStateWithLifecycle()
    val browseRows by viewModel.managerRows.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    val searchResults by viewModel.managerResults.collectAsStateWithLifecycle()
    LaunchedEffect(query) { viewModel.setManagerQuery(query) }
    val searching = query.isNotBlank()

    // LEFT from a channel's first pill lands back on the selected category.
    val railFocusRequester = remember { FocusRequester() }
    val onExitLeftToRail: () -> Boolean = {
        !searching && runCatching { railFocusRequester.requestFocus() }.isSuccess
    }

    BackHandler { onBack() }

    // Open on the first category so channels show straight away. Re-runs when the category set
    // changes (after switching source, which clears the selection).
    LaunchedEffect(categoryGroups) {
        if (selectedCategory == null && categoryGroups.isNotEmpty()) {
            viewModel.selectManagerCategory(categoryGroups.first().key)
        }
    }
    // One-time: the focus onto the selected category once there is one. Guarded so a later
    // re-emit (a background sync) never yanks the focus out of the channel list.
    var initialFocusDone by remember { mutableStateOf(false) }
    LaunchedEffect(categoryGroups) {
        if (!initialFocusDone && categoryGroups.isNotEmpty()) {
            delay(80)
            runCatching { railFocusRequester.requestFocus() }
            initialFocusDone = true
        }
    }

    val embedded = app.opentv.ui.settings.LocalSettingsEmbedded.current
    Column(Modifier.fillMaxSize().then(if (embedded) Modifier.padding(top = 4.dp) else Modifier.padding(horizontal = 28.dp, vertical = 16.dp))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!embedded) {
                Text(stringResource(R.string.channels_manager_title), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(24.dp))
            }
            SetSearchField(
                value = query,
                onValue = { query = it },
                hint = stringResource(R.string.channels_manager_search_box),
                modifier = Modifier.weight(1f),
            )
            if (!embedded) {
                Spacer(Modifier.width(14.dp))
                Pill(stringResource(R.string.common_done), on = false, onClick = onBack)
            }
        }
        // More than one provider: one of them on its own, or all of them together.
        if (sources.size > 1 && !searching) {
            Spacer(Modifier.height(10.dp))
            val ids = listOf<Long?>(null) + sources.map { it.id }
            Pills(
                listOf(stringResource(R.string.channels_manager_all_sources)) + sources.map { it.name },
                isOn = { ids[it] == selectedSource },
            ) { viewModel.selectManagerSource(ids[it]) }
        }
        Spacer(Modifier.height(10.dp))

        Row(Modifier.weight(1f).fillMaxWidth()) {
            if (!searching) {
                LazyColumn(
                    modifier = Modifier.width(250.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(categoryGroups, key = { "cat-${it.key}" }) { group ->
                        val selected = selectedCategory == group.key
                        RailEntry(
                            label = group.label,
                            selected = selected,
                            onClick = { viewModel.selectManagerCategory(group.key) },
                            modifier = if (selected) Modifier.focusRequester(railFocusRequester) else Modifier,
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                val rows = if (searching) searchResults else browseRows
                when {
                    searching && query.trim().length < 2 -> Hint(stringResource(R.string.common_keep_typing))
                    searching && rows.isEmpty() -> Hint(stringResource(R.string.channels_manager_no_match, query))
                    !searching && categoryGroups.isEmpty() -> Hint(stringResource(R.string.channels_manager_no_channels))
                    !searching && selectedCategory == null -> Hint(stringResource(R.string.channels_manager_pick_category))
                    rows.isEmpty() -> Hint(stringResource(R.string.channels_manager_empty_category))
                    else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(rows, key = { it.key }) { row ->
                            val hidden = row.variants.all { it.hidden }
                            ManagerRow(
                                row = row,
                                hidden = hidden,
                                onToggleHidden = { viewModel.setRowHidden(row, !hidden) },
                                onToggleFavourite = { viewModel.toggleFavourite(row) },
                                leftEdgeModifier = Modifier.onPreviewKeyEvent { e ->
                                    if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) onExitLeftToRail()
                                    else false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RailEntry(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    Text(
        text = label,
        fontSize = 15.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = when {
            focused || selected -> primary
            else -> SetDim
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(11.dp))
            .background(
                when {
                    focused -> SetFocus
                    selected -> primary.copy(alpha = 0.12f)
                    else -> SetCard
                },
            )
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 16.sp, color = SetDim)
    }
}

@Composable
private fun ManagerRow(
    row: ChannelsViewModel.Row,
    hidden: Boolean,
    onToggleHidden: () -> Unit,
    onToggleFavourite: () -> Unit,
    leftEdgeModifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val alpha = if (hidden) 0.5f else 1f
    Row(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.hasFocus }
            .clip(RoundedCornerShape(11.dp))
            .background(if (focused) SetFocus else SetCard)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLogo(
            logoUrl = row.primary.shownLogo(),
            name = row.primary.shownName,
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(
                row.primary.shownName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = (if (focused) MaterialTheme.colorScheme.primary else SetOn).copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(if (hidden) R.string.channels_hidden else R.string.channels_showing),
                fontSize = 13.sp,
                color = SetDim,
            )
        }
        // The first pill carries the LEFT-back-to-the-categories handler.
        Pill(
            "★ " + stringResource(R.string.common_favourite),
            on = row.primary.favourite,
            modifier = leftEdgeModifier,
            onClick = onToggleFavourite,
        )
        Spacer(Modifier.width(8.dp))
        // On = shown in the guide; off = hidden.
        Pill(stringResource(R.string.channels_show), on = !hidden, onClick = onToggleHidden)
    }
}
