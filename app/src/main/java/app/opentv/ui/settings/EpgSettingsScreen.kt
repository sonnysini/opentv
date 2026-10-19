/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.R
import app.opentv.data.model.EpgFeed
import app.opentv.ui.EpgViewModel

/**
 * Guide settings: where guide data comes from.
 *
 * Three kinds of feed share the list — the provider's own guide, the curated free sources
 * shipped with the app (off by default; enabling one is one click), and any XMLTV URL the
 * user adds. Every enabled feed merges into a single guide, and each row shows its last
 * sync outcome, because "the guide silently didn't update" is the failure this whole app
 * exists to avoid.
 */
@Composable
fun EpgSettingsScreen(
    onBack: () -> Unit,
    viewModel: EpgViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    var newName by remember { mutableStateOf("") }
    var newUrl by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_guide_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { viewModel.refresh() }, enabled = !ui.syncing) {
                Text(if (ui.syncing) stringResource(R.string.epg_updating) else stringResource(R.string.epg_update_now))
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.common_done)) }
        }

        ui.statusLine?.let { line ->
            Spacer(Modifier.height(8.dp))
            Text(
                line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.epg_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ui.feeds, key = { it.id }) { feed ->
                FeedRow(
                    feed = feed,
                    onToggle = { enabled -> viewModel.setEnabled(feed, enabled) },
                    onRemove = { viewModel.remove(feed) },
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                if (!showAdd) {
                    OutlinedButton(onClick = { showAdd = true }) { Text(stringResource(R.string.epg_add_own)) }
                } else {
                    // Opening the form puts focus in it — not back on the top bar.
                    val nameFocus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { runCatching { nameFocus.requestFocus() } }
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            OutlinedTextField(
                                value = newName,
                                onValueChange = { newName = it },
                                label = { Text(stringResource(R.string.epg_name_optional)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                                modifier = Modifier.fillMaxWidth().focusRequester(nameFocus).dpadLeavesField(focusManager),
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = newUrl,
                                onValueChange = { newUrl = it },
                                label = { Text(stringResource(R.string.epg_xmltv_url)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.moveFocus(FocusDirection.Down) }),
                                modifier = Modifier.fillMaxWidth().dpadLeavesField(focusManager),
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        viewModel.addCustom(newName, newUrl)
                                        newName = ""; newUrl = ""; showAdd = false
                                    },
                                    enabled = newUrl.isNotBlank(),
                                ) { Text(stringResource(R.string.epg_add_guide)) }
                                TextButton(onClick = { newName = ""; newUrl = ""; showAdd = false }) {
                                    Text(stringResource(R.string.common_cancel))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedRow(
    feed: EpgFeed,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    Card {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(feed.name, style = MaterialTheme.typography.titleMedium)
                // The last sync outcome, verbatim — success counts or the actual error.
                // A guide that fails must say so where the user can see it.
                if (feed.lastResult.isNotEmpty()) {
                    Text(
                        feed.lastResult,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // User-added feeds can be removed; provider and built-in ones only toggled.
            if (!feed.builtIn && feed.providerSourceId == null) {
                TextButton(onClick = onRemove) { Text(stringResource(R.string.common_remove)) }
                Spacer(Modifier.width(4.dp))
            }
            Switch(checked = feed.enabled, onCheckedChange = onToggle)
        }
    }
}

/**
 * On a TV remote a text field swallows up/down, so focus can never leave it. Up and down
 * move to the previous/next control instead; left and right still move the cursor.
 */
private fun Modifier.dpadLeavesField(focusManager: FocusManager): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when (event.key) {
        Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
        Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
        else -> false
    }
}
