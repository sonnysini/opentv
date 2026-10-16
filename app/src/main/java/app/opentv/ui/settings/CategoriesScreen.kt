/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.R
import app.opentv.data.model.StreamKind
import app.opentv.ui.CategoriesViewModel
import app.opentv.ui.channels.OnScreenKeyboard

/**
 * Switch whole provider categories off. A switched-off category is never downloaded — its
 * channels or titles aren't stored at all — which is what keeps a 25k-channel, 70k-film provider
 * usable on a low-RAM stick: turn off the countries and shelves you never watch.
 *
 * The keyboard filters the list, and "switch all off/on" acts on exactly what the filter shows,
 * so "XXX → all off" or "SPAIN → all off" is a handful of key presses instead of 400 toggles.
 */
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    viewModel: CategoriesViewModel = viewModel(),
) {
    val kind by viewModel.kind.collectAsState()
    val query by viewModel.query.collectAsState()
    val entries by viewModel.entries.collectAsState()
    val offCount by viewModel.offCount.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_categories_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(24.dp))
            KindChip(stringResource(R.string.nav_live_tv), kind == StreamKind.LIVE) { viewModel.selectKind(StreamKind.LIVE) }
            Spacer(Modifier.width(8.dp))
            KindChip(stringResource(R.string.nav_movies), kind == StreamKind.MOVIE) { viewModel.selectKind(StreamKind.MOVIE) }
            Spacer(Modifier.width(8.dp))
            KindChip(stringResource(R.string.nav_shows), kind == StreamKind.SERIES) { viewModel.selectKind(StreamKind.SERIES) }
            Spacer(Modifier.weight(1f))
            HubTextButton(stringResource(R.string.common_done), onBack)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.categories_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxSize()) {
            OnScreenKeyboard(
                onKey = { if (query.length < 40) viewModel.query.value = query + it },
                onSpace = { if (query.length < 40) viewModel.query.value = "$query " },
                onBackspace = { viewModel.query.value = query.dropLast(1) },
                onClear = { viewModel.query.value = "" },
            )

            Spacer(Modifier.width(28.dp))

            Column(Modifier.weight(1f).fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        query.ifEmpty { stringResource(R.string.categories_filter_hint) },
                        style = MaterialTheme.typography.titleLarge,
                        color = if (query.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.categories_off_count, offCount, totalCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val shown = entries
                    HubTextButton(stringResource(R.string.categories_all_off, shown.count { it.enabled })) {
                        viewModel.setEnabled(shown.filter { it.enabled }.map { it.key }, enabled = false)
                    }
                    HubTextButton(stringResource(R.string.categories_all_on, shown.count { !it.enabled })) {
                        viewModel.setEnabled(shown.filter { !it.enabled }.map { it.key }, enabled = true)
                    }
                }
                Spacer(Modifier.height(12.dp))

                if (entries.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(
                                if (totalCount == 0) R.string.categories_none else R.string.categories_no_match,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(entries, key = { it.key }) { entry ->
                            CategoryRow(entry) { on -> viewModel.setEnabled(listOf(entry.key), on) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KindChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        color = when {
            focused -> MaterialTheme.colorScheme.onPrimary
            selected -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(
                when {
                    focused -> MaterialTheme.colorScheme.primary
                    selected -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .toggleable(value = selected, role = Role.Tab, onValueChange = { onClick() })
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun CategoryRow(entry: CategoriesViewModel.Entry, onToggle: (Boolean) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    // The whole row is the toggle, so the d-pad lands on one target per category; the Switch
    // only mirrors the state.
    Row(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(
                if (focused) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .then(if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .toggleable(value = entry.enabled, role = Role.Switch, onValueChange = onToggle)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            entry.label,
            style = MaterialTheme.typography.titleMedium,
            color = when {
                focused -> MaterialTheme.colorScheme.onPrimaryContainer
                entry.enabled -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = entry.enabled, onCheckedChange = null)
    }
}
