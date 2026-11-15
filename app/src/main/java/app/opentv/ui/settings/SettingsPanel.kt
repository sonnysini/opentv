/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentv.R
import app.opentv.ui.channels.ChannelManagerScreen
import app.opentv.ui.focusOnEnter
import kotlinx.coroutines.delay

/**
 * The settings, as entries of the side menu: five on top, three at the bottom after the separator
 * (as Search and Settings in the app's menu), Account last.
 */
enum class SettingsSection(
    @StringRes val label: Int,
    @StringRes val desc: Int,
    @DrawableRes val icon: Int,
    @DrawableRes val iconFill: Int,
    val bottom: Boolean = false,
) {
    SOURCES(R.string.settings_hub_sources, R.string.set_sources_desc, R.drawable.ic_set_hard_drives, R.drawable.ic_set_hard_drives_fill),
    CHANNELS(R.string.settings_hub_channels, R.string.set_channels_desc, R.drawable.ic_set_squares_four, R.drawable.ic_set_squares_four_fill),
    APPEARANCE(R.string.settings_appearance, R.string.settings_appearance_subtitle, R.drawable.ic_set_palette, R.drawable.ic_set_palette_fill),
    PLAYBACK(R.string.settings_section_playback, R.string.settings_playback_subtitle, R.drawable.ic_set_play_circle, R.drawable.ic_set_play_circle_fill),
    RECORDINGS(R.string.nav_recordings, R.string.settings_recording_subtitle, R.drawable.ic_nav_recordings, R.drawable.ic_nav_recordings_fill),
    PARENTAL(R.string.set_rail_parental, R.string.settings_parental_subtitle, R.drawable.ic_set_lock_simple, R.drawable.ic_set_lock_simple_fill, bottom = true),
    SYSTEM(R.string.settings_hub_system, R.string.set_system_desc, R.drawable.ic_set_info, R.drawable.ic_set_info_fill, bottom = true),
    ACCOUNT(R.string.settings_account_title, R.string.set_account_desc, R.drawable.ic_set_user_circle, R.drawable.ic_set_user_circle_fill, bottom = true),
}

/**
 * What the settings page needs from the app's navigation: the providers list works on the app's
 * own sources view model and opens the add/edit form, the account list on the profiles one.
 */
class SettingsSlots(
    val providers: @Composable (onBack: () -> Unit) -> Unit,
    val account: @Composable (onBack: () -> Unit) -> Unit,
)

private class Entry(
    val key: String,
    val title: String,
    val subtitle: String,
    @DrawableRes val icon: Int,
    // A long list (categories, channels): the drop-down takes the page's height and scrolls inside.
    val tall: Boolean = false,
    // Opens in a window over the page instead of a drop-down (the phone QR).
    val window: Boolean = false,
    val body: @Composable (close: () -> Unit) -> Unit,
)

/**
 * The page right of the settings menu: kicker, big title, a line, then the section's rows. While the
 * focus is in the menu it is a dimmed preview of the section the focus is on. Rows with more inside
 * open in place as drop-downs ([SetAccordion]), one at a time — no second-level pages. Back closes
 * the open drop-down; with none open it goes back to the menu ([onBackToRail]).
 */
@Composable
fun SettingsPanel(
    section: SettingsSection,
    dimmed: Boolean,
    slots: SettingsSlots,
    onBackToRail: () -> Unit,
    // Bumped to move the focus into the page (OK or ▶ in the menu).
    enterKey: Int,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(if (dimmed) 0.55f else 1f, tween(220), label = "settingsDim")
    val firsts = remember { SettingsSection.values().associateWith { FocusRequester() } }
    Crossfade(
        targetState = section,
        animationSpec = tween(180),
        label = "settingsSection",
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .focusOnEnter(key = enterKey, enabled = enterKey > 0, enter = { runCatching { firsts.getValue(section).requestFocus() }.isSuccess }),
    ) { s ->
      CompositionLocalProvider(LocalSettingsFirstFocus provides firsts.getValue(s)) {
        Column(Modifier.fillMaxSize().padding(start = 44.dp, end = 36.dp, top = 30.dp)) {
            Text(
                stringResource(R.string.nav_settings).uppercase(),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                color = SetDim,
            )
            Text(
                stringResource(s.label).uppercase(),
                fontFamily = SetOswald,
                fontSize = 50.sp,
                letterSpacing = 0.5.sp,
                color = SetOn,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(stringResource(s.desc), fontSize = 15.sp, color = SetDim, modifier = Modifier.padding(top = 2.dp, bottom = 14.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                SectionBody(s, slots, onBackToRail)
            }
        }
      }
    }
}

@Composable
private fun SectionBody(section: SettingsSection, slots: SettingsSlots, onBackToRail: () -> Unit) {
    when (section) {
        SettingsSection.APPEARANCE -> ScrollingRows { AppearanceRows() }
        SettingsSection.PLAYBACK -> ScrollingRows { PlaybackRows() }
        // One screen each: it is the section, shown right on the page (it scrolls by itself).
        SettingsSection.RECORDINGS -> Embedded { RecordingSettingsScreen(onBack = onBackToRail) }
        SettingsSection.PARENTAL -> Embedded { ParentalControlsScreen(onBack = onBackToRail) }
        SettingsSection.ACCOUNT -> Embedded { slots.account(onBackToRail) }
        SettingsSection.SOURCES -> Accordions(
            listOf(
                Entry("providers", stringResource(R.string.settings_providers_title), stringResource(R.string.settings_providers_subtitle), R.drawable.ic_set_hard_drives) { slots.providers(it) },
                Entry("addons", stringResource(R.string.settings_addons_title), stringResource(R.string.settings_addons_subtitle), R.drawable.ic_set_puzzle_piece) { StremioAddonsScreen(onBack = it) },
                Entry("guide", stringResource(R.string.settings_guide_title), stringResource(R.string.settings_guide_subtitle), R.drawable.ic_set_television_simple) { EpgSettingsScreen(onBack = it) },
                Entry("tmdb", stringResource(R.string.settings_tmdb_title), stringResource(R.string.settings_tmdb_subtitle), R.drawable.ic_set_film_slate) { TmdbSettingsScreen(onBack = it) },
            ),
        )
        SettingsSection.CHANNELS -> Accordions(
            listOf(
                Entry("categories", stringResource(R.string.settings_categories_title), stringResource(R.string.settings_categories_subtitle), R.drawable.ic_set_squares_four, tall = true) { CategoriesScreen(onBack = it) },
                Entry("channels", stringResource(R.string.common_channels), stringResource(R.string.settings_channels_subtitle), R.drawable.ic_set_list_bullets, tall = true) { ChannelManagerScreen(onBack = it) },
                Entry("grouping", stringResource(R.string.settings_grouping_title), stringResource(R.string.settings_grouping_subtitle), R.drawable.ic_set_link_simple) { GroupingScreen(onBack = it) },
                Entry("logos", stringResource(R.string.settings_logos_title), stringResource(R.string.settings_logos_subtitle), R.drawable.ic_set_image) { LogoSettingsScreen(onBack = it) },
                Entry("web", stringResource(R.string.settings_webmanager_title), stringResource(R.string.settings_webmanager_subtitle), R.drawable.ic_set_device_mobile, window = true) { WebManagerScreen(onBack = it) },
            ),
        )
        SettingsSection.SYSTEM -> Accordions(
            listOf(
                Entry("sync", stringResource(R.string.settings_sync_title), stringResource(R.string.settings_sync_subtitle), R.drawable.ic_set_arrows_clockwise) { SyncScreen(onBack = it) },
                Entry("about", stringResource(R.string.settings_about_title), stringResource(R.string.settings_about_subtitle), R.drawable.ic_set_info) { AboutScreen(onBack = it) },
            ),
        )
    }
}

@Composable
private fun ScrollingRows(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) { content() }
}

@Composable
private fun Embedded(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSettingsEmbedded provides true) {
        Box(Modifier.fillMaxSize()) { content() }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Accordions(entries: List<Entry>) {
    var open by remember { mutableStateOf<String?>(null) }
    var window by remember { mutableStateOf<Entry?>(null) }
    val headers = remember { HashMap<String, FocusRequester>() }
    val first = LocalSettingsFirstFocus.current
    val close: () -> Unit = {
        val was = open
        open = null
        was?.let { k -> runCatching { headers[k]?.requestFocus() } }
    }
    // Back with a drop-down open closes it (and leaves the focus on its row). Composed after the
    // shell's own Back, so it is asked first; a screen inside one with its own Back is asked before.
    BackHandler(enabled = open != null) { close() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // An open drop-down fits under its row on screen: the page's height less a row.
        val bodyMax = (maxHeight - 72.dp).coerceAtLeast(160.dp)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            entries.forEach { e ->
                if (e.window) {
                    SetActionRow(
                        e.title, e.subtitle, icon = e.icon,
                        modifier = Modifier
                            .focusRequester(headers.getOrPut(e.key) { FocusRequester() })
                            .then(if (e === entries.first() && first != null) Modifier.focusRequester(first) else Modifier),
                    ) { open = null; window = e }
                    return@forEach
                }
                val isOpen = open == e.key
                val bring = remember { BringIntoViewRequester() }
                LaunchedEffect(isOpen) {
                    if (isOpen) {
                        delay(260)
                        runCatching { bring.bringIntoView() }
                    }
                }
                Box(Modifier.bringIntoViewRequester(bring)) {
                    SetAccordion(
                        title = e.title,
                        subtitle = e.subtitle,
                        icon = e.icon,
                        open = isOpen,
                        onToggle = { if (isOpen) close() else open = e.key },
                        headerModifier = Modifier
                            .focusRequester(headers.getOrPut(e.key) { FocusRequester() })
                            .then(if (e === entries.first() && first != null) Modifier.focusRequester(first) else Modifier),
                    ) {
                        Box(if (e.tall) Modifier.fillMaxWidth().height(bodyMax) else Modifier.fillMaxWidth().heightIn(max = bodyMax)) {
                            e.body(close)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    window?.let { e ->
        val shut = { window = null; runCatching { headers[e.key]?.requestFocus() }; Unit }
        SetDialog(e.title, onDismiss = shut, wide = true) {
            CompositionLocalProvider(LocalSettingsEmbedded provides true) { e.body(shut) }
        }
    }
}
