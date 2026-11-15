/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.R
import app.opentv.core.ServiceLocator
import app.opentv.ui.ChannelsViewModel

/**
 * Parental controls: a PIN, and a list of categories to keep out of the guide.
 *
 * Hidden categories vanish from Live TV — rail, All channels, and search — until the session is
 * unlocked here. If a PIN is set, this screen is itself behind the PIN, so a child cannot simply
 * come here and unlock. The PIN is a family lock, not a vault: it's a four-digit code stored as a
 * salted hash, enough to stop casual access, not a serious attacker.
 */
@Composable
fun ParentalControlsScreen(
    onBack: () -> Unit,
    channelsViewModel: ChannelsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val settings = remember { ServiceLocator.get(context).settings }
    val pinIsSet by settings.pinIsSet.collectAsStateWithLifecycle()
    val hidden by settings.hiddenCategories.collectAsStateWithLifecycle()
    val unlocked by settings.hiddenUnlocked.collectAsStateWithLifecycle()
    val categories by channelsViewModel.categoryGroups.collectAsStateWithLifecycle()

    var authed by remember { mutableStateOf(false) }

    if (pinIsSet && !authed) {
        PinGate(
            onCancel = onBack,
            onSubmit = { entered -> settings.verifyPin(entered).also { if (it) authed = true } },
        )
        return
    }

    var pinWindow by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        SetGroup(stringResource(R.string.parental_pin))
        SetRow(
            stringResource(R.string.parental_pin),
            stringResource(if (pinIsSet) R.string.parental_pin_is_set else R.string.parental_pin_set),
            R.drawable.ic_set_lock_simple,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val first = LocalSettingsFirstFocus.current
                Pill(
                    stringResource(if (pinIsSet) R.string.parental_change_pin else R.string.parental_save_pin),
                    on = false,
                    modifier = if (first != null) Modifier.focusRequester(first) else Modifier,
                ) { pinWindow = true }
                if (pinIsSet) Pill(stringResource(R.string.parental_remove_pin), on = false, onClick = settings::clearPin)
            }
        }

        SetGroup(stringResource(R.string.parental_hidden_categories))
        SetNote(stringResource(R.string.parental_hidden_desc))
        SetSwitchRow(
            stringResource(R.string.parental_show_hidden_title),
            stringResource(R.string.parental_show_hidden_subtitle),
            unlocked,
            onToggle = settings::setHiddenUnlocked,
        )
        if (categories.isEmpty()) {
            SetNote(stringResource(R.string.parental_no_categories))
        } else {
            // A plain Column inside the page's scroll: provider categories number in the dozens.
            categories.forEach { group ->
                SwitchRow(group.label, subtitle = null, checked = group.key in hidden, onToggle = { on ->
                    val next = hidden.toMutableSet().apply { if (on) add(group.key) else remove(group.key) }
                    settings.setHiddenCategories(next)
                })
            }
        }
    }

    if (pinWindow) {
        SetDialog(stringResource(R.string.parental_pin), onDismiss = { pinWindow = false }) {
            PinSection(pinIsSet = pinIsSet, onSetPin = { settings.setPin(it); pinWindow = false }, onCancel = { pinWindow = false })
        }
    }
}

@Composable
private fun PinSection(
    pinIsSet: Boolean,
    onSetPin: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val pinLenError = stringResource(R.string.parental_pin_len_error)
    val pinMismatchError = stringResource(R.string.parental_pin_mismatch)
    val firstField = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstField.requestFocus() } }

    SetNote(stringResource(if (pinIsSet) R.string.parental_pin_enter_new else R.string.parental_pin_set))
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PinField(stringResource(R.string.parental_pin_new), pin, Modifier.focusRequester(firstField)) { if (it.length <= 4) pin = it.filter(Char::isDigit) }
        PinField(stringResource(R.string.parental_pin_confirm), confirm) { if (it.length <= 4) confirm = it.filter(Char::isDigit) }
    }
    error?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val ready = pin.length == 4 && confirm.length == 4
        Pill(stringResource(R.string.parental_save_pin), on = ready, enabled = ready) {
            when {
                pin.length != 4 -> error = pinLenError
                pin != confirm -> error = pinMismatchError
                else -> onSetPin(pin)
            }
        }
        Pill(stringResource(R.string.common_cancel), on = false, onClick = onCancel)
    }
}

@Composable
private fun PinField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = modifier.width(160.dp),
    )
}

@Composable
private fun PinGate(onCancel: () -> Unit, onSubmit: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.parental_enter_pin), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            PinField(stringResource(R.string.parental_pin), pin) { if (it.length <= 4) pin = it.filter(Char::isDigit) }
            if (error) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.parental_wrong_pin), color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    enabled = pin.length == 4,
                    onClick = { if (!onSubmit(pin)) { error = true; pin = "" } },
                ) { Text(stringResource(R.string.parental_unlock)) }
                OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.common_back)) }
            }
        }
    }
}
