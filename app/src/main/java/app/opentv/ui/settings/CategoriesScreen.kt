/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import androidx.compose.foundation.background
import app.opentv.ui.focusVeil
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.R
import app.opentv.data.model.StreamKind
import app.opentv.ui.CategoriesViewModel
import app.opentv.ui.channels.OnScreenKeyboard
import app.opentv.ui.theme.focusLift

/**
 * Switch whole provider categories off. A switched-off category is never downloaded — its
 * channels or titles aren't stored at all — which is what keeps a 25k-channel, 70k-film provider
 * usable on a low-RAM stick: turn off the countries and shelves you never watch.
 *
 * The keyboard filters the list, and "switch all off/on" acts on exactly what the filter shows,
 * so "XXX → all off" or "SPAIN → all off" is a handful of key presses instead of 400 toggles.
 */
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    viewModel: CategoriesViewModel = viewModel(),
) {
    val kind by viewModel.kind.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val offCount by viewModel.offCount.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()
    val italianOnly by viewModel.italianOnly.collectAsStateWithLifecycle()
    val foreignSportOff by viewModel.foreignSportOff.collectAsStateWithLifecycle()

    val embedded = LocalSettingsEmbedded.current
    Column(Modifier.settingsRoot(embedded, 28.dp, 20.dp).then(if (embedded) Modifier.fillMaxSize() else Modifier)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!embedded) {
                Text(stringResource(R.string.settings_categories_title), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(24.dp))
            }
            val kinds = listOf(StreamKind.LIVE, StreamKind.MOVIE, StreamKind.SERIES)
            Pills(
                listOf(stringResource(R.string.nav_live_tv), stringResource(R.string.nav_movies), stringResource(R.string.nav_shows)),
                isOn = { kinds[it] == kind },
            ) { viewModel.selectKind(kinds[it]) }
            Spacer(Modifier.weight(1f))
            if (!embedded) HubTextButton(stringResource(R.string.common_done), onBack)
        }

        Spacer(Modifier.height(6.dp))
        SetNote(stringResource(R.string.categories_desc))
        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxSize()) {
            OnScreenKeyboard(
                onKey = { if (query.length < 40) viewModel.query.value = query + it },
                onSpace = { if (query.length < 40) viewModel.query.value = "$query " },
                onBackspace = { viewModel.query.value = query.dropLast(1) },
                onClear = { viewModel.query.value = "" },
            )

            Spacer(Modifier.width(28.dp))

            Column(Modifier.weight(1f).fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        query.ifEmpty { stringResource(R.string.categories_filter_hint) },
                        fontSize = 20.sp,
                        color = if (query.isEmpty()) SetDim
                        else MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.categories_off_count, offCount, totalCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (kind == StreamKind.LIVE && query.isEmpty()) {
                    RuleRow(
                        stringResource(R.string.categories_italian_only),
                        stringResource(R.string.categories_italian_only_desc),
                        checked = italianOnly,
                        onToggle = viewModel::setItalianOnly,
                    )
                    Spacer(Modifier.height(6.dp))
                    // Worded as "keep", so on = channels kept, like every other switch here.
                    RuleRow(
                        stringResource(R.string.categories_foreign_sport),
                        stringResource(R.string.categories_foreign_sport_desc),
                        checked = !foreignSportOff,
                        onToggle = { keep -> viewModel.setForeignSportOff(!keep) },
                    )
                    Spacer(Modifier.height(10.dp))
                }
                // Only the bulk action that would change something is offered.
                val shownOn = entries.count { it.enabled }
                val shownOff = entries.size - shownOn
                if (shownOn > 0 || shownOff > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (shownOn > 0) {
                            Pill(stringResource(R.string.categories_all_off, shownOn), on = false) {
                                viewModel.setEnabled(entries.filter { it.enabled }.map { it.key }, enabled = false)
                            }
                        }
                        if (shownOff > 0) {
                            Pill(stringResource(R.string.categories_all_on, shownOff), on = false) {
                                viewModel.setEnabled(entries.filter { !it.enabled }.map { it.key }, enabled = true)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                if (entries.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(
                                if (totalCount == 0) R.string.categories_none else R.string.categories_no_match,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(entries, key = { it.key }) { entry ->
                            CategoryRow(entry) { on -> viewModel.setEnabled(listOf(entry.key), on) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(entry: CategoriesViewModel.Entry, onToggle: (Boolean) -> Unit) =
    SwitchRow(entry.label, subtitle = null, checked = entry.enabled, onToggle = onToggle)

@Composable
private fun RuleRow(label: String, subtitle: String, checked: Boolean, onToggle: (Boolean) -> Unit) =
    SwitchRow(label, subtitle, checked, onToggle, emphasised = true)

@Composable
internal fun SwitchRow(
    label: String,
    subtitle: String?,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    emphasised: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(11.dp)
    // The whole row is the toggle, so the d-pad lands on one target per category; the switch only
    // mirrors the state. Lower than a settings row: these lists run to hundreds.
    Row(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(if (focused) SetFocus else SetCard)
            .toggleable(interactionSource = null, indication = null, value = checked, role = Role.Switch, onValueChange = onToggle)
            .padding(horizontal = 18.dp, vertical = if (emphasised) 10.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                label,
                fontSize = if (emphasised) 17.sp else 15.5.sp,
                fontWeight = FontWeight.Medium,
                color = when {
                    focused -> MaterialTheme.colorScheme.primary
                    checked || emphasised -> SetOn
                    else -> SetDim
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(subtitle, fontSize = 13.sp, color = SetDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        SetSwitch(checked, small = !emphasised)
    }
}
