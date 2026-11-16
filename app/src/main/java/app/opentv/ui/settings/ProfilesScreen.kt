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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.R
import app.opentv.ui.ProfilesViewModel
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

/**
 * Who's watching. Add, rename, remove and switch between local profiles; the active one decides
 * whose continue-watching and watched state is in play across the app.
 */
@Composable
fun ProfilesScreen(
    onBack: () -> Unit,
    viewModel: ProfilesViewModel = viewModel(),
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeId by viewModel.activeProfileId.collectAsStateWithLifecycle()

    // null = no window; NEW = adding; a positive id = renaming that profile.
    var editing by remember { mutableStateOf<Long?>(null) }

    val embedded = LocalSettingsEmbedded.current
    Column(
        Modifier.settingsRoot(embedded).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (!embedded) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.profiles_title), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.weight(1f))
                HubTextButton(stringResource(R.string.common_done), onBack)
            }
            Spacer(Modifier.height(12.dp))
        }
        val first = LocalSettingsFirstFocus.current
        profiles.forEachIndexed { i, profile ->
            val active = profile.id == activeId
            SetRow(
                profile.name,
                if (active) stringResource(R.string.profiles_active) else null,
                icon = R.drawable.ic_set_user_circle,
                titleColor = if (active) MaterialTheme.colorScheme.primary else null,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val firstPill = if (i == 0 && first != null) Modifier.focusRequester(first) else Modifier
                    if (!active) Pill(stringResource(R.string.profiles_use), on = false, modifier = firstPill) { viewModel.select(profile.id) }
                    Pill(stringResource(R.string.profiles_rename), on = false, modifier = if (active) firstPill else Modifier) { editing = profile.id }
                    if (profile.id != 1L) Pill(stringResource(R.string.common_remove), on = false) { viewModel.remove(profile.id) }
                }
            }
        }
        SetActionRow(stringResource(R.string.profiles_add), icon = R.drawable.ic_set_plus) { editing = NEW }
    }

    editing?.let { target ->
        NameDialog(
            initial = if (target == NEW) "" else profiles.firstOrNull { it.id == target }?.name.orEmpty(),
            heading = if (target == NEW) stringResource(R.string.profiles_new_heading) else stringResource(R.string.profiles_rename_heading),
            onCancel = { editing = null },
            onSave = { name ->
                if (target == NEW) viewModel.addProfile(name) else viewModel.rename(target, name)
                editing = null
            },
        )
    }
}

/** The profile's name in a window, typed with the system keyboard. */
@Composable
private fun NameDialog(
    initial: String,
    heading: String,
    onCancel: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val ok = name.isNotBlank()
    SetDialog(heading, onDismiss = onCancel) {
        SetTextField(
            value = name,
            onValue = { name = it.take(24) },
            label = stringResource(R.string.profiles_name_label),
            modifier = Modifier.focusRequester(focus),
            onDone = { if (ok) onSave(name.trim()) },
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(stringResource(R.string.common_save), on = ok, enabled = ok) { onSave(name.trim()) }
            Pill(stringResource(R.string.common_cancel), on = false, onClick = onCancel)
        }
    }
}

private const val NEW = -1L
