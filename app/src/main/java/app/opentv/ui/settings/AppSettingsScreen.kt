/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import android.content.Context
import android.widget.Toast
import app.opentv.R
import app.opentv.core.AppSettings
import app.opentv.core.ManualSourceMode
import app.opentv.core.PreferredQuality
import app.opentv.core.SleepTimer
import app.opentv.core.findActivity
import app.opentv.data.work.SyncWorker
import java.text.DecimalFormat

/** Settings › Appearance: style, theme, size, language and how the channel list looks. */
@Composable
fun AppearanceSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { AppSettings.get(context) }
    val themeMode by settings.themeMode.collectAsState()
    val appStyle by settings.appStyle.collectAsState()
    val uiScale by settings.uiScalePercent.collectAsState()
    val channelLayout by settings.channelLayout.collectAsState()
    val previewVideo by settings.guidePreviewVideo.collectAsState()
    val previewSound by settings.guidePreviewSound.collectAsState()
    val language by settings.languageTag.collectAsState()
    var showLanguagePicker by remember { mutableStateOf(false) }

    SettingsPage(stringResource(R.string.settings_appearance), onBack) {
        SettingsSection(stringResource(R.string.settings_style_title)) {
            ThemeOption(stringResource(R.string.settings_style_owntv), appStyle == AppSettings.AppStyle.OWNTV) {
                settings.setAppStyle(AppSettings.AppStyle.OWNTV)
            }
            ThemeOption(stringResource(R.string.settings_style_classic), appStyle == AppSettings.AppStyle.CLASSIC) {
                settings.setAppStyle(AppSettings.AppStyle.CLASSIC)
            }
            Spacer(Modifier.height(8.dp))
            ThemeOption(stringResource(R.string.settings_theme_system), themeMode == AppSettings.ThemeMode.SYSTEM) {
                settings.setThemeMode(AppSettings.ThemeMode.SYSTEM)
            }
            ThemeOption(stringResource(R.string.settings_theme_dark), themeMode == AppSettings.ThemeMode.DARK) {
                settings.setThemeMode(AppSettings.ThemeMode.DARK)
            }
            ThemeOption(stringResource(R.string.settings_theme_light), themeMode == AppSettings.ThemeMode.LIGHT) {
                settings.setThemeMode(AppSettings.ThemeMode.LIGHT)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.settings_theme_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.settings_ui_scale, uiScale),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = { settings.setUiScalePercent(uiScale - AppSettings.UI_SCALE_STEP) },
                    enabled = uiScale > AppSettings.MIN_UI_SCALE,
                ) { Text("−") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { settings.setUiScalePercent(uiScale + AppSettings.UI_SCALE_STEP) },
                    enabled = uiScale < AppSettings.MAX_UI_SCALE,
                ) { Text("+") }
            }
            Text(
                stringResource(R.string.settings_ui_scale_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))

        SettingsSection(stringResource(R.string.settings_section_language)) {
            // One row showing the current language; the full list opens in a dialog so the
            // rest of the settings don't sit below 30-odd radio buttons.
            val systemLabel = stringResource(R.string.settings_language_system)
            val currentName = OpenTvLanguages.firstOrNull { it.first == language }?.second ?: systemLabel
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { showLanguagePicker = true }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(currentName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("›", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.settings_language_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))

        SettingsSection(stringResource(R.string.settings_section_guide)) {
            Text(
                stringResource(R.string.settings_channel_layout_title),
                style = MaterialTheme.typography.titleMedium,
            )
            ThemeOption(
                stringResource(R.string.settings_channel_layout_grid),
                channelLayout == AppSettings.ChannelLayout.GRID,
            ) { settings.setChannelLayout(AppSettings.ChannelLayout.GRID) }
            ThemeOption(
                stringResource(R.string.settings_channel_layout_list),
                channelLayout == AppSettings.ChannelLayout.LIST,
            ) { settings.setChannelLayout(AppSettings.ChannelLayout.LIST) }
            ThemeOption(
                stringResource(R.string.settings_channel_layout_fullscreen),
                channelLayout == AppSettings.ChannelLayout.FULLSCREEN,
            ) { settings.setChannelLayout(AppSettings.ChannelLayout.FULLSCREEN) }
            Spacer(Modifier.height(8.dp))
            ToggleRow(
                title = stringResource(R.string.settings_live_preview_title),
                subtitle = stringResource(R.string.settings_live_preview_subtitle),
                checked = previewVideo,
                onToggle = settings::setGuidePreviewVideo,
            )
            ToggleRow(
                title = stringResource(R.string.settings_preview_sound_title),
                subtitle = stringResource(R.string.settings_preview_sound_subtitle),
                checked = previewSound,
                onToggle = settings::setGuidePreviewSound,
            )
        }
    }

    if (showLanguagePicker) {
        LanguagePickerDialog(
            current = language,
            onPick = { tag ->
                showLanguagePicker = false
                changeLanguage(context, settings, tag)
            },
            onDismiss = { showLanguagePicker = false },
        )
    }
}

/** Settings › Playback: what plays first, when to switch streams, the player bar, the sleep timer. */
@Composable
fun PlaybackSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { AppSettings.get(context) }
    val captions by settings.subtitlesEnabled.collectAsState()
    val resumeLast by settings.resumeLastChannel.collectAsState()
    val preferredQuality by settings.preferredQuality.collectAsState()
    val qualityFallback by settings.qualityFallback.collectAsState()
    val fallbackMillis by settings.qualityFallbackMillis.collectAsState()
    val stallFallback by settings.stallFallback.collectAsState()
    val stallMillis by settings.stallFallbackMillis.collectAsState()
    val hiccupLimit by settings.hiccupLimit.collectAsState()
    val liveOffset by settings.liveOffsetSeconds.collectAsState()
    val manualSourceMode by settings.manualSourceMode.collectAsState()
    val qualityButtonCycles by settings.qualityButtonCycles.collectAsState()
    val controlsTimeout by settings.controlsTimeoutSeconds.collectAsState()
    val matchFrameRate by settings.matchFrameRate.collectAsState()

    SettingsPage(stringResource(R.string.settings_section_playback), onBack) {
        SettingsSection(stringResource(R.string.settings_quality_title)) {
            Text(
                stringResource(R.string.settings_quality_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PreferredQuality.values().forEach { quality ->
                val label = if (quality == PreferredQuality.BEST) stringResource(R.string.settings_quality_best) else quality.name
                ThemeOption(label, preferredQuality == quality) { settings.setPreferredQuality(quality) }
            }
            Spacer(Modifier.height(8.dp))
            ToggleRow(
                title = stringResource(R.string.settings_quality_fallback_title),
                subtitle = stringResource(R.string.settings_quality_fallback_subtitle),
                checked = qualityFallback,
                onToggle = settings::setQualityFallback,
            )
            if (qualityFallback) {
                StepSlider(
                    label = stringResource(
                        R.string.settings_quality_timeout,
                        DecimalFormat("0.#").format(fallbackMillis / 1_000.0),
                    ),
                    value = fallbackMillis,
                    min = AppSettings.MIN_FALLBACK_MILLIS,
                    max = AppSettings.MAX_FALLBACK_MILLIS,
                    step = AppSettings.FALLBACK_STEP_MILLIS,
                    onChange = settings::setQualityFallbackMillis,
                )
            }
            Spacer(Modifier.height(8.dp))
            ToggleRow(
                title = stringResource(R.string.settings_stall_fallback_title),
                subtitle = stringResource(R.string.settings_stall_fallback_subtitle),
                checked = stallFallback,
                onToggle = settings::setStallFallback,
            )
            if (stallFallback) {
                StepSlider(
                    label = stringResource(
                        R.string.settings_stall_timeout,
                        DecimalFormat("0.#").format(stallMillis / 1_000.0),
                    ),
                    value = stallMillis,
                    min = AppSettings.MIN_FALLBACK_MILLIS,
                    max = AppSettings.MAX_FALLBACK_MILLIS,
                    step = AppSettings.FALLBACK_STEP_MILLIS,
                    onChange = settings::setStallFallbackMillis,
                )
            }
            Spacer(Modifier.height(8.dp))
            ToggleRow(
                title = stringResource(R.string.settings_hiccup_title),
                subtitle = stringResource(R.string.settings_hiccup_subtitle),
                checked = hiccupLimit > 0,
                onToggle = { on -> settings.setHiccupLimit(if (on) AppSettings.DEFAULT_HICCUP_LIMIT else 0) },
            )
            if (hiccupLimit > 0) {
                StepSlider(
                    label = stringResource(R.string.settings_hiccup_limit, hiccupLimit),
                    value = hiccupLimit,
                    min = MIN_HICCUP_LIMIT,
                    max = AppSettings.MAX_HICCUP_LIMIT,
                    step = 1,
                    onChange = settings::setHiccupLimit,
                )
            }
            Spacer(Modifier.height(8.dp))
            ToggleRow(
                title = stringResource(R.string.settings_match_frame_rate_title),
                subtitle = stringResource(R.string.settings_match_frame_rate_subtitle),
                checked = matchFrameRate,
                onToggle = settings::setMatchFrameRate,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_live_offset_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StepSlider(
                label = if (liveOffset == 0) {
                    stringResource(R.string.settings_live_offset_auto)
                } else {
                    stringResource(R.string.settings_live_offset, liveOffset)
                },
                value = liveOffset,
                min = 0,
                max = AppSettings.MAX_LIVE_OFFSET_SECONDS,
                step = AppSettings.LIVE_OFFSET_STEP_SECONDS,
                onChange = settings::setLiveOffsetSeconds,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_manual_source_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.settings_manual_source_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            listOf(
                ManualSourceMode.STAY to R.string.settings_manual_source_stay,
                ManualSourceMode.STUTTER_ONLY to R.string.settings_manual_source_stutter,
                ManualSourceMode.AUTO to R.string.settings_manual_source_auto,
            ).forEach { (mode, label) ->
                ThemeOption(stringResource(label), manualSourceMode == mode) { settings.setManualSourceMode(mode) }
            }
            Spacer(Modifier.height(8.dp))
            ToggleRow(
                title = stringResource(R.string.settings_quality_button_cycles_title),
                subtitle = stringResource(R.string.settings_quality_button_cycles_subtitle),
                checked = qualityButtonCycles,
                onToggle = settings::setQualityButtonCycles,
            )
        }

        Spacer(Modifier.height(16.dp))

        SettingsSection(stringResource(R.string.settings_section_player)) {
            ToggleRow(
                title = stringResource(R.string.settings_subtitles_title),
                subtitle = stringResource(R.string.settings_subtitles_subtitle),
                checked = captions,
                onToggle = settings::setSubtitlesEnabled,
            )
            ToggleRow(
                title = stringResource(R.string.settings_resume_title),
                subtitle = stringResource(R.string.settings_resume_subtitle),
                checked = resumeLast,
                onToggle = settings::setResumeLastChannel,
            )
            Spacer(Modifier.height(8.dp))
            StepSlider(
                label = stringResource(R.string.settings_controls_timeout, controlsTimeout),
                value = controlsTimeout,
                min = AppSettings.MIN_CONTROLS_TIMEOUT_SECONDS,
                max = AppSettings.MAX_CONTROLS_TIMEOUT_SECONDS,
                step = 1,
                onChange = settings::setControlsTimeoutSeconds,
            )
        }

        Spacer(Modifier.height(16.dp))

        SleepTimerSection()
    }
}

/** Settings › Movies & series (TMDB): the optional key that fills in missing artwork and plots. */
@Composable
fun TmdbSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { AppSettings.get(context) }
    SettingsPage(stringResource(R.string.settings_tmdb_title), onBack) {
        TmdbKeySection(settings)
    }
}

/**
 * Settings › Providers › What to download: live, movies and series on or off, each with a refresh.
 * Less to download makes a sync quicker.
 */
@Composable
internal fun ContentSection() {
    val context = LocalContext.current
    val settings = remember { AppSettings.get(context) }
    val liveEnabled by settings.liveEnabled.collectAsState()
    val moviesEnabled by settings.moviesEnabled.collectAsState()
    val seriesEnabled by settings.seriesEnabled.collectAsState()

    // A refresh next to each content toggle kicks a full catalogue re-sync (which now honours the
    // toggles, so a type just switched on is fetched). Runs as background work so it isn't cut short
    // if you leave this screen; a quick Toast confirms it started.
    val refreshingMessage = stringResource(R.string.settings_content_refreshing)
    val onRefreshContent: () -> Unit = {
        SyncWorker.refreshNow(context)
        Toast.makeText(context, refreshingMessage, Toast.LENGTH_SHORT).show()
    }

    SettingsSection(stringResource(R.string.settings_section_content)) {
        Text(
            stringResource(R.string.settings_content_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        ContentToggleRow(
            title = stringResource(R.string.nav_live_tv),
            subtitle = stringResource(R.string.settings_content_live_subtitle),
            checked = liveEnabled,
            onToggle = settings::setLiveEnabled,
            onRefresh = onRefreshContent,
        )
        ContentToggleRow(
            title = stringResource(R.string.nav_movies),
            subtitle = stringResource(R.string.settings_content_movies_subtitle),
            checked = moviesEnabled,
            onToggle = settings::setMoviesEnabled,
            onRefresh = onRefreshContent,
        )
        ContentToggleRow(
            title = stringResource(R.string.nav_shows),
            subtitle = stringResource(R.string.settings_content_series_subtitle),
            checked = seriesEnabled,
            onToggle = settings::setSeriesEnabled,
            onRefresh = onRefreshContent,
        )
    }
}

/** A scrolling settings page: title and Done on top, [content] below. */
@Composable
internal fun SettingsPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.common_done)) }
        }
        Spacer(Modifier.height(20.dp))
        content()
    }
}

/**
 * The language list as a dialog: "System default" first, then each language in its own name
 * (endonym), the convention users expect. Focus starts on the current choice so a d-pad user
 * doesn't scroll from the top.
 */
@Composable
private fun LanguagePickerDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val options = listOf("" to stringResource(R.string.settings_language_system)) + OpenTvLanguages
    val selectedIndex = options.indexOfFirst { it.first == current }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 3).coerceAtLeast(0))
    val selectedFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { selectedFocus.requestFocus() } }

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(Modifier.padding(vertical = 16.dp).widthIn(min = 320.dp, max = 480.dp)) {
                Text(
                    stringResource(R.string.settings_section_language),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                LazyColumn(state = listState, modifier = Modifier.heightIn(max = 420.dp)) {
                    itemsIndexed(options) { index, (tag, name) ->
                        val focus = if (index == selectedIndex) Modifier.focusRequester(selectedFocus) else Modifier
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .then(focus)
                                .selectable(selected = tag == current, onClick = { onPick(tag) })
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = tag == current, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(name, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Optional TMDB back-fill. The user pastes their own free key; it is stored on-device in
 * [AppSettings] and used to fill posters/backdrops/synopsis/cast a provider left blank. Empty by
 * default, so the feature is off until opted into.
 */
@Composable
private fun TmdbKeySection(settings: AppSettings) {
    val context = LocalContext.current
    val savedKey by settings.tmdbApiKey.collectAsState()
    var field by remember(savedKey) { mutableStateOf(savedKey) }
    val savedMessage = stringResource(R.string.settings_tmdb_saved)

    SettingsSection(stringResource(R.string.settings_section_metadata)) {
        Text(
            stringResource(R.string.settings_tmdb_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = field,
            onValueChange = { field = it.trim() },
            singleLine = true,
            label = { Text(stringResource(R.string.settings_tmdb_key_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                settings.setTmdbApiKey(field)
                Toast.makeText(context, savedMessage, Toast.LENGTH_SHORT).show()
            }) { Text(stringResource(R.string.settings_tmdb_save)) }
            if (savedKey.isNotBlank()) {
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.settings_tmdb_active),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * The UI languages OpenTV ships translations for, each labelled with its own endonym. The tag is
 * both the stored language tag and the resource qualifier (values-<tag>), so adding a language is
 * just a new `values-xx/strings.xml` plus a line here.
 */
private val OpenTvLanguages: List<Pair<String, String>> = listOf(
    "en" to "English",
    "es" to "Español",
    "fr" to "Français",
    "de" to "Deutsch",
    "it" to "Italiano",
    "pt" to "Português",
    "nl" to "Nederlands",
    "pl" to "Polski",
    "ru" to "Русский",
    "tr" to "Türkçe",
    "ar" to "العربية",
    "zh" to "中文",
    "ja" to "日本語",
    "ko" to "한국어",
    "hi" to "हिन्दी",
    "sv" to "Svenska",
    "da" to "Dansk",
    "fi" to "Suomi",
    "nb" to "Norsk",
    "cs" to "Čeština",
    "el" to "Ελληνικά",
    "ro" to "Română",
    "hu" to "Magyar",
    "uk" to "Українська",
    "id" to "Bahasa Indonesia",
    "th" to "ไทย",
    "vi" to "Tiếng Việt",
    "bg" to "Български",
    "sk" to "Slovenčina",
    "hr" to "Hrvatski",
    "fa" to "فارسی",
)

/** Persist the chosen language and recreate the activity so the whole UI reloads translated. */
private fun changeLanguage(context: Context, settings: AppSettings, tag: String) {
    if (settings.languageTag.value == tag) return
    settings.setLanguageTag(tag)
    context.findActivity()?.recreate()
}

@Composable
private fun SleepTimerSection() {
    val deadline by SleepTimer.deadline.collectAsState()
    val remaining = deadline?.let {
        val left = it - System.currentTimeMillis()
        if (left <= 0L) 0 else ((left + 59_999L) / 60_000L).toInt()
    }

    Text(
        stringResource(R.string.settings_sleep_timer),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(8.dp))
    Card {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                if (remaining == null) stringResource(R.string.settings_sleep_off_desc)
                else stringResource(R.string.settings_sleep_on_desc, remaining),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            SleepOption(stringResource(R.string.settings_sleep_off), selected = remaining == null) { SleepTimer.clear() }
            SleepTimer.presets.forEach { mins ->
                SleepOption(stringResource(R.string.settings_sleep_minutes, mins), selected = false) { SleepTimer.armMinutes(mins) }
            }
        }
    }
}

@Composable
private fun SleepOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
internal fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(8.dp))
    Card {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) { content() }
    }
}

@Composable
internal fun ThemeOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * A d-pad slider: the whole row takes focus, ←/→ move [value] by [step] within [min]..[max].
 * Replaces the old −/+ buttons, which lost focus when one of them became disabled at an end.
 */
@Composable
internal fun StepSlider(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int,
    onChange: (Int) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .border(2.dp, if (focused) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
            .onPreviewKeyEvent { event ->
                val delta = when (event.key) {
                    Key.DirectionLeft -> -step
                    Key.DirectionRight -> step
                    else -> return@onPreviewKeyEvent false
                }
                if (event.type == KeyEventType.KeyDown) onChange((value + delta).coerceIn(min, max))
                true
            }
            .focusable()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (focused) {
                Text(
                    "◀  ▶",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        val fraction = if (max > min) (value - min).toFloat() / (max - min) else 1f
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).widthIn(max = 640.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

/**
 * A [ToggleRow] with a refresh button in front of the switch: toggle the content type on/off, or
 * tap refresh to re-sync it now. Only the label column toggles on tap, so the refresh button and
 * switch stay independently focusable for d-pad users.
 */
@Composable
private fun ContentToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .widthIn(max = 640.dp)
                .clickable { onToggle(!checked) }
                .padding(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onRefresh) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = stringResource(R.string.settings_content_refresh),
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

/** Fewer stutters than this in a minute is just a live stream being a live stream. */
private const val MIN_HICCUP_LIMIT = 2
