/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.R
import app.opentv.core.AppSettings
import app.opentv.data.model.LiveStreamFormat
import app.opentv.data.model.Source
import app.opentv.data.model.SourceKind
import app.opentv.data.work.SyncWorker
import app.opentv.ui.SourcesViewModel
import app.opentv.ui.onboarding.AddSourceScreen

/**
 * The provider list as settings rows: one row per provider with its actions as pills (edit, on/off,
 * remove — remove asks once more, it takes the channels and guide with it), the stream format for
 * Xtream ones, then what to download. Adding or editing a provider opens a window over the page.
 */
@Composable
fun ProvidersScreen(
    onAddSource: () -> Unit,
    onEditSource: (Source) -> Unit,
    onBack: () -> Unit,
    viewModel: SourcesViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var pendingRemove by remember { mutableStateOf<Long?>(null) }
    // The add/edit window: null closed, 0 a new provider, else the id being edited.
    var form by remember { mutableStateOf<Long?>(null) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (ui.sources.isEmpty()) SetNote(stringResource(R.string.providers_empty))
        ui.sources.forEach { source ->
            ProviderRow(
                source = source,
                confirming = pendingRemove == source.id,
                onEdit = { form = source.id },
                onAskRemove = { pendingRemove = source.id },
                onCancelRemove = { pendingRemove = null },
                onConfirmRemove = { viewModel.delete(source); pendingRemove = null },
                onToggleEnabled = { viewModel.setEnabled(source, !source.enabled) },
            )
            // Stream format is an Xtream-panel concept — M3U sources carry their own URLs.
            if (source.kind == SourceKind.XTREAM && source.enabled) {
                val formats = listOf(LiveStreamFormat.HLS, LiveStreamFormat.MPEG_TS)
                SetPillsRow(
                    stringResource(R.string.provider_stream_format),
                    stringResource(R.string.provider_stream_format_hint),
                    listOf(stringResource(R.string.provider_stream_format_hls), stringResource(R.string.provider_stream_format_ts)),
                    formats.indexOf(source.liveFormat),
                ) { viewModel.setLiveFormat(source, formats[it]) }
            }
        }
        SetActionRow(stringResource(R.string.providers_add), icon = R.drawable.ic_set_plus) { form = 0L }

        ContentRows()
    }

    form?.let { id ->
        SetDialog(
            title = stringResource(if (id == 0L) R.string.providers_add else R.string.common_edit),
            onDismiss = { form = null },
            wide = true,
        ) {
            Box(Modifier.fillMaxWidth().weight(1f, fill = false)) {
              androidx.compose.runtime.CompositionLocalProvider(LocalSettingsEmbedded provides true) {
                AddSourceScreen(
                    viewModel = viewModel,
                    editingSourceId = id.takeIf { it != 0L },
                    onFinished = { form = null },
                )
              }
            }
        }
    }
}

@Composable
private fun ProviderRow(
    source: Source,
    confirming: Boolean,
    onEdit: () -> Unit,
    onAskRemove: () -> Unit,
    onCancelRemove: () -> Unit,
    onConfirmRemove: () -> Unit,
    onToggleEnabled: () -> Unit,
) {
    val sub = if (source.enabled) "${source.kind.name} · ${hostOf(source.url)}"
    else "${stringResource(R.string.providers_disabled)} · ${hostOf(source.url)}"
    SetRow(source.name, sub, R.drawable.ic_set_hard_drives, modifier = Modifier.alpha(if (source.enabled) 1f else 0.7f)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (confirming) {
                Pill(stringResource(R.string.providers_yes_remove), on = false, onClick = onConfirmRemove)
                Pill(stringResource(R.string.common_cancel), on = false, onClick = onCancelRemove)
            } else {
                Pill(stringResource(R.string.common_edit), on = false, onClick = onEdit)
                Pill(stringResource(if (source.enabled) R.string.providers_disable else R.string.providers_enable), on = false, onClick = onToggleEnabled)
                Pill(stringResource(R.string.common_remove), on = false, onClick = onAskRemove)
            }
        }
    }
}

/** What to download (live, films, series), and a refresh that re-reads the catalogue now. */
@Composable
private fun ContentRows() {
    val context = LocalContext.current
    val settings = remember { AppSettings.get(context) }
    val liveEnabled by settings.liveEnabled.collectAsStateWithLifecycle()
    val moviesEnabled by settings.moviesEnabled.collectAsStateWithLifecycle()
    val seriesEnabled by settings.seriesEnabled.collectAsStateWithLifecycle()
    val refreshing = stringResource(R.string.settings_content_refreshing)

    SetGroup(stringResource(R.string.settings_section_content))
    SetMultiPillsRow(
        stringResource(R.string.set_content_title),
        stringResource(R.string.settings_content_note),
        listOf(stringResource(R.string.nav_live_tv), stringResource(R.string.nav_movies), stringResource(R.string.nav_shows)),
        isOn = { listOf(liveEnabled, moviesEnabled, seriesEnabled)[it] },
    ) {
        when (it) {
            0 -> settings.setLiveEnabled(!liveEnabled)
            1 -> settings.setMoviesEnabled(!moviesEnabled)
            else -> settings.setSeriesEnabled(!seriesEnabled)
        }
    }
    SetActionRow(stringResource(R.string.set_content_refresh), stringResource(R.string.set_content_refresh_desc), icon = R.drawable.ic_set_arrows_clockwise) {
        SyncWorker.refreshNow(context)
        Toast.makeText(context, refreshing, Toast.LENGTH_SHORT).show()
    }
}

/** Host only — the full URL carries the login and has no place on a settings list. */
private fun hostOf(url: String): String =
    runCatching { java.net.URI(url).host ?: url }.getOrDefault(url)
