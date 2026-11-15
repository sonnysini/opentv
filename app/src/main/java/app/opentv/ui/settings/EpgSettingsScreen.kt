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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        SetNote(stringResource(R.string.epg_desc))
        SetActionRow(
            title = stringResource(if (ui.syncing) R.string.epg_updating else R.string.epg_update_now),
            subtitle = ui.statusLine,
            icon = R.drawable.ic_set_arrows_clockwise,
            enabled = !ui.syncing,
        ) { viewModel.refresh() }
        ui.feeds.forEach { feed -> FeedRow(feed, onToggle = { viewModel.setEnabled(feed, it) }, onRemove = { viewModel.remove(feed) }) }
        SetActionRow(stringResource(R.string.epg_add_own), icon = R.drawable.ic_set_plus) { showAdd = true }
    }

    if (showAdd) AddFeedDialog(onDismiss = { showAdd = false }) { name, url -> viewModel.addCustom(name, url); showAdd = false }
}

/** A guide: the switch turns it on or off; one the user added has a Remove pill as well. */
@Composable
private fun FeedRow(feed: EpgFeed, onToggle: (Boolean) -> Unit, onRemove: () -> Unit) {
    // The last sync outcome, verbatim — a guide that fails must say so where the user can see it.
    val sub = feed.lastResult.takeIf { it.isNotEmpty() }
    if (!feed.builtIn && feed.providerSourceId == null) {
        SetRow(feed.name, sub) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill(stringResource(R.string.common_remove), on = false, onClick = onRemove)
                Pill(stringResource(if (feed.enabled) R.string.set_on else R.string.set_off), on = feed.enabled) { onToggle(!feed.enabled) }
            }
        }
    } else {
        SetSwitchRow(feed.name, sub, feed.enabled, onToggle = onToggle)
    }
}

@Composable
private fun AddFeedDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    val focusManager = LocalFocusManager.current
    var newName by remember { mutableStateOf("") }
    var newUrl by remember { mutableStateOf("") }
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { nameFocus.requestFocus() } }
    SetDialog(stringResource(R.string.epg_add_own), onDismiss) {
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
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(stringResource(R.string.epg_add_guide), on = newUrl.isNotBlank(), enabled = newUrl.isNotBlank()) { onAdd(newName, newUrl) }
            Pill(stringResource(R.string.common_cancel), on = false, onClick = onDismiss)
        }
    }
}

/**
 * On a TV remote a text field swallows up/down, so focus can never leave it. Up and down
 * move to the previous/next control instead; left and right still move the cursor.
 */
internal fun Modifier.dpadLeavesField(focusManager: FocusManager): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when (event.key) {
        Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
        Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
        else -> false
    }
}
