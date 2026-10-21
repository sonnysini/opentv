/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.opentv.R
import app.opentv.core.LogoPack
import app.opentv.core.LogoSource
import app.opentv.core.ServiceLocator
import kotlinx.coroutines.launch

/**
 * Settings › Logos: every logo choice in one place. Channels take the provider's logos alone by
 * default, or add the outside ones (the user's logo pack, then the free guides' icons) after or
 * before them. Groups get the provider's emoji, or broadcaster logos and flags first. A logo
 * picked by hand for one channel (channel menu › Change logo) beats all of it.
 */
@Composable
fun LogoSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { ServiceLocator.get(context).settings }
    val source by settings.logoSource.collectAsState()
    val externalGroups by settings.externalGroupLogos.collectAsState()

    SettingsPage(stringResource(R.string.settings_logos_title), onBack) {
        SettingsSection(stringResource(R.string.logos_channels_title)) {
            ThemeOption(stringResource(R.string.logos_source_provider), source == LogoSource.PROVIDER) {
                settings.setLogoSource(LogoSource.PROVIDER)
            }
            ThemeOption(stringResource(R.string.logos_source_provider_then_external), source == LogoSource.PROVIDER_THEN_EXTERNAL) {
                settings.setLogoSource(LogoSource.PROVIDER_THEN_EXTERNAL)
            }
            ThemeOption(stringResource(R.string.logos_source_external_then_provider), source == LogoSource.EXTERNAL_THEN_PROVIDER) {
                settings.setLogoSource(LogoSource.EXTERNAL_THEN_PROVIDER)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.logos_source_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))
        SettingsSection(stringResource(R.string.logos_groups_title)) {
            SwitchRow(
                stringResource(R.string.logos_groups_external),
                stringResource(R.string.logos_groups_external_desc),
                checked = externalGroups,
                onToggle = settings::setExternalGroupLogos,
            )
        }

        // The pack only matters once outside logos are in play. It goes last: on Fire TV a text
        // field opens the keyboard as soon as it takes focus, so nothing should sit below it.
        if (source != LogoSource.PROVIDER) {
            Spacer(Modifier.height(16.dp))
            LogoPackRow()
        }
    }
}

/** Settings › Logos › Logo pack: the address, a Load button and how the last load went. */
@Composable
private fun LogoPackRow() {
    val context = LocalContext.current
    val graph = remember { ServiceLocator.get(context) }
    val saved by graph.settings.logoPackUrl.collectAsState()
    val pack by graph.settings.logoPack.collectAsState()
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var url by remember(saved) { mutableStateOf(saved) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    Card {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(stringResource(R.string.logo_pack_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.logo_pack_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.logo_pack_url)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.moveFocus(FocusDirection.Right) }),
                    modifier = Modifier.weight(1f).dpadLeavesField(focusManager),
                )
                Spacer(Modifier.width(12.dp))
                Button(
                    enabled = !loading,
                    onClick = {
                        graph.settings.setLogoPackUrl(url)
                        error = null
                        if (url.isBlank()) return@Button
                        loading = true
                        scope.launch {
                            runCatching { LogoPack.refresh(graph.settings, graph.httpClient) }
                                .onFailure { error = it.message ?: it.javaClass.simpleName }
                            loading = false
                        }
                    },
                ) { Text(stringResource(R.string.logo_pack_load)) }
            }
            val status = error?.let { stringResource(R.string.logo_pack_failed, it) }
                ?: if (saved.isNotEmpty() && pack.byKey.isNotEmpty()) stringResource(R.string.logo_pack_loaded, pack.byKey.size) else null
            if (status != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
