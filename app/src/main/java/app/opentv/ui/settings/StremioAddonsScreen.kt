/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import android.app.Application
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.R
import app.opentv.core.ServiceLocator
import app.opentv.data.model.StremioAddon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StremioAddonsViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = ServiceLocator.get(app)

    val addons: StateFlow<List<StremioAddon>> = graph.settings.stremioAddons

    sealed interface Status {
        data object Idle : Status
        data object Checking : Status
        data class Added(val name: String) : Status
        data object Invalid : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Validate a pasted manifest URL against the live add-on, and store it if it checks out. */
    fun add(rawUrl: String) {
        val url = rawUrl.trim()
        if (url.isBlank()) return
        viewModelScope.launch {
            _status.value = Status.Checking
            val name = withContext(Dispatchers.IO) { graph.stremioClient.fetchManifest(url) }
            _status.value = if (name != null) {
                graph.settings.addStremioAddon(StremioAddon(manifestUrl = url, name = name))
                Status.Added(name)
            } else {
                Status.Invalid
            }
        }
    }

    fun remove(addon: StremioAddon) = graph.settings.removeStremioAddon(addon.manifestUrl)

    fun clearStatus() { _status.value = Status.Idle }
}

/**
 * Manage Stremio add-ons: paste a manifest URL, it's validated against the live add-on and stored
 * on-device. OpenTV bundles none and points at none — the user brings their own. A short note makes
 * the debrid model explicit: the key lives in the add-on's own personalised URL, not in OpenTV.
 * In the settings style: one row per add-on with Remove, and "Add add-on" opens a window.
 */
@Composable
fun StremioAddonsScreen(onBack: () -> Unit) {
    val viewModel: StremioAddonsViewModel = viewModel()
    val addons by viewModel.addons.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }

    SettingsPage(stringResource(R.string.settings_addons_title), onBack) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            SetNote(stringResource(R.string.addons_note))
            if (addons.isEmpty()) SetNote(stringResource(R.string.addons_empty))
            addons.forEach { addon ->
                SetRow(addon.name, hostOf(addon.manifestUrl), icon = R.drawable.ic_set_puzzle_piece) {
                    Pill(stringResource(R.string.common_remove), on = false) { viewModel.remove(addon) }
                }
            }
            SetActionRow(stringResource(R.string.addons_add_row), icon = R.drawable.ic_set_plus) {
                viewModel.clearStatus()
                adding = true
            }
        }
    }

    if (adding) AddAddonDialog(viewModel, onDismiss = { adding = false; viewModel.clearStatus() })
}

@Composable
private fun AddAddonDialog(viewModel: StremioAddonsViewModel, onDismiss: () -> Unit) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // Validated and stored: the window closes by itself.
    LaunchedEffect(status) { if (status is StremioAddonsViewModel.Status.Added) onDismiss() }
    val checking = status is StremioAddonsViewModel.Status.Checking
    val canAdd = input.isNotBlank() && !checking
    SetDialog(stringResource(R.string.addons_add_row), onDismiss, wide = true) {
        SetTextField(
            value = input,
            onValue = {
                input = it
                if (status !is StremioAddonsViewModel.Status.Idle) viewModel.clearStatus()
            },
            label = stringResource(R.string.addons_url_label),
            modifier = Modifier.focusRequester(focus),
            uri = true,
            onDone = { if (canAdd) viewModel.add(input) },
        )
        when (status) {
            is StremioAddonsViewModel.Status.Checking -> SetStatus(stringResource(R.string.addons_checking))
            is StremioAddonsViewModel.Status.Invalid -> SetStatus(stringResource(R.string.addons_invalid), error = true)
            else -> {}
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(stringResource(R.string.addons_add), on = canAdd, enabled = canAdd) { viewModel.add(input) }
            Pill(stringResource(R.string.common_cancel), on = false, onClick = onDismiss)
        }
    }
}

/** Host only — a personalised manifest URL can embed a key, which has no place on a settings list. */
private fun hostOf(url: String): String =
    runCatching { java.net.URI(url).host ?: url }.getOrDefault(url)
