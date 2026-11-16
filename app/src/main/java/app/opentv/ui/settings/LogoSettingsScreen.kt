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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
    val graph = remember { ServiceLocator.get(context) }
    val settings = graph.settings
    val source by settings.logoSource.collectAsStateWithLifecycle()
    val externalGroups by settings.externalGroupLogos.collectAsStateWithLifecycle()
    val packUrl by settings.logoPackUrl.collectAsStateWithLifecycle()
    val pack by settings.logoPack.collectAsStateWithLifecycle()
    var packWindow by remember { mutableStateOf(false) }

    SettingsPage(stringResource(R.string.settings_logos_title), onBack) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            val sources = listOf(LogoSource.PROVIDER, LogoSource.PROVIDER_THEN_EXTERNAL, LogoSource.EXTERNAL_THEN_PROVIDER)
            // Three long labels: the pills go on a line of their own under the title.
            SetRow(stringResource(R.string.logos_channels_title), stringResource(R.string.logos_source_note), below = true) {
                Pills(
                    listOf(
                        stringResource(R.string.logos_source_provider),
                        stringResource(R.string.logos_source_provider_then_external),
                        stringResource(R.string.logos_source_external_then_provider),
                    ),
                    isOn = { sources[it] == source },
                    first = true,
                ) { settings.setLogoSource(sources[it]) }
            }
            SetSwitchRow(
                stringResource(R.string.logos_groups_external),
                stringResource(R.string.logos_groups_external_desc),
                checked = externalGroups,
                onToggle = settings::setExternalGroupLogos,
            )
            // The pack only matters once outside logos are in play.
            if (source != LogoSource.PROVIDER) {
                SetActionRow(
                    stringResource(R.string.logo_pack_title),
                    stringResource(R.string.logo_pack_desc),
                    value = if (packUrl.isNotEmpty() && pack.byKey.isNotEmpty()) stringResource(R.string.logo_pack_loaded, pack.byKey.size) else null,
                    icon = R.drawable.ic_set_image,
                ) { packWindow = true }
            }
        }
    }

    if (packWindow) LogoPackDialog(onDismiss = { packWindow = false })
}

/** Settings › Logos › Logo pack, in a window: the address, Load, and how the last load went. */
@Composable
private fun LogoPackDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val graph = remember { ServiceLocator.get(context) }
    val saved by graph.settings.logoPackUrl.collectAsStateWithLifecycle()
    val pack by graph.settings.logoPack.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var url by remember(saved) { mutableStateOf(saved) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val load = {
        graph.settings.setLogoPackUrl(url)
        error = null
        if (url.isNotBlank() && !loading) {
            loading = true
            scope.launch {
                runCatching { LogoPack.refresh(graph.settings, graph.httpClient) }
                    .onFailure { error = it.message ?: it.javaClass.simpleName }
                loading = false
            }
        }
    }
    SetDialog(stringResource(R.string.logo_pack_title), onDismiss) {
        SetNote(stringResource(R.string.logo_pack_desc))
        SetTextField(url, { url = it }, stringResource(R.string.logo_pack_url), Modifier.focusRequester(focus), uri = true, onDone = load)
        val status = error?.let { stringResource(R.string.logo_pack_failed, it) }
            ?: if (saved.isNotEmpty() && pack.byKey.isNotEmpty()) stringResource(R.string.logo_pack_loaded, pack.byKey.size) else null
        if (status != null) SetStatus(status, error = error != null)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(stringResource(R.string.logo_pack_load), on = !loading, enabled = !loading) { load() }
            Pill(stringResource(R.string.common_cancel), on = false, onClick = onDismiss)
        }
    }
}
