/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.R
import app.opentv.data.parser.ChannelCountry
import app.opentv.data.parser.MergeSuggester
import app.opentv.ui.focusVeil
import app.opentv.ui.theme.focusLift
import androidx.compose.foundation.clickable
import androidx.compose.ui.focus.onFocusChanged
import app.opentv.ui.GroupingViewModel

/**
 * Settings › Channel grouping. The normaliser folds what it can prove; this screen is for the
 * rest — rows that look like one channel under two names. Merge or Ignore once, and the choice
 * holds through every sync. Merges made here are listed below and can be undone.
 */
@Composable
fun GroupingScreen(
    onBack: () -> Unit,
    viewModel: GroupingViewModel = viewModel(),
) {
    val mergeSources by viewModel.mergeSources.collectAsStateWithLifecycle()
    val stripSources by viewModel.stripSources.collectAsStateWithLifecycle()
    val showRecentGroups by viewModel.showRecentGroups.collectAsStateWithLifecycle()
    val homeCountry by viewModel.homeCountry.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val merges by viewModel.merges.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    // A row that is answered disappears; focus moves to the row that takes its place, so the
    // d-pad never lands on nothing.
    val requesters = remember { HashMap<String, FocusRequester>() }
    var refocusIndex by remember { mutableStateOf<Int?>(null) }
    val doneFocus = remember { FocusRequester() }
    LaunchedEffect(suggestions, refocusIndex) {
        val index = refocusIndex ?: return@LaunchedEffect
        val list = suggestions ?: return@LaunchedEffect
        val target = list.getOrNull(index.coerceAtMost(list.lastIndex))
        runCatching { (target?.let { requesters[it.pairId] } ?: doneFocus).requestFocus() }
        refocusIndex = null
    }

    val embedded = LocalSettingsEmbedded.current
    Column(Modifier.settingsRoot(embedded)) {
        if (!embedded) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_grouping_title), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.weight(1f))
                HubTextButton(stringResource(R.string.common_done), Modifier.focusRequester(doneFocus), onBack)
            }
            Spacer(Modifier.height(8.dp))
        }
        SetNote(stringResource(R.string.grouping_desc))
        if (busy) SetStatus(stringResource(R.string.grouping_applying))
        Spacer(Modifier.height(6.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
            item(key = "home_country") {
                val auto = homeCountry.isBlank()
                val code = if (auto) ChannelCountry.deviceDefault() else homeCountry
                val country = countryName(code)
                SetActionRow(
                    stringResource(R.string.grouping_home_country),
                    stringResource(R.string.grouping_home_country_desc),
                    value = if (auto) stringResource(R.string.grouping_home_country_auto, country) else country,
                    modifier = if (embedded) Modifier.focusRequester(doneFocus) else Modifier,
                ) {
                    // Automatic, then the countries one by one, then back to automatic.
                    val i = HOME_COUNTRIES.indexOf(homeCountry)
                    viewModel.setHomeCountry(HOME_COUNTRIES[(i + 1) % HOME_COUNTRIES.size])
                }
            }
            item(key = "merge_sources") {
                SetSwitchRow(
                    stringResource(R.string.categories_merge_sources),
                    stringResource(R.string.categories_merge_sources_desc),
                    checked = mergeSources,
                    onToggle = viewModel::setMergeSources,
                )
            }
            item(key = "strip_sources") {
                SetSwitchRow(
                    stringResource(R.string.categories_strip_sources),
                    stringResource(R.string.categories_strip_sources_desc),
                    checked = stripSources,
                    onToggle = viewModel::setStripSources,
                )
            }
            item(key = "recent_groups") {
                SetSwitchRow(
                    stringResource(R.string.categories_recent_groups),
                    stringResource(R.string.categories_recent_groups_desc),
                    checked = showRecentGroups,
                    onToggle = viewModel::setShowRecentGroups,
                )
            }

            val list = suggestions
            item(key = "suggestions_header") {
                SetGroup(
                    if (list == null) stringResource(R.string.grouping_suggestions_loading)
                    else stringResource(R.string.grouping_suggestions_header, list.size),
                )
            }
            if (list != null && list.isEmpty()) {
                item(key = "suggestions_none") { SetNote(stringResource(R.string.grouping_suggestions_none)) }
            }
            if (list != null) {
                items(list, key = { "s:" + it.pairId }) { s ->
                    val index = list.indexOf(s)
                    SetRow(
                        "${s.from.name}  →  ${s.into.name}",
                        stringResource(
                            R.string.grouping_channel_count,
                            stringResource(reasonLabel(s.reason)),
                            s.from.channels,
                            s.into.channels,
                        ),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill(
                                stringResource(R.string.grouping_merge),
                                on = false,
                                modifier = Modifier.focusRequester(requesters.getOrPut(s.pairId) { FocusRequester() }),
                            ) {
                                refocusIndex = index
                                viewModel.merge(s)
                            }
                            Pill(stringResource(R.string.grouping_ignore), on = false) {
                                refocusIndex = index
                                viewModel.ignore(s)
                            }
                        }
                    }
                }
            }

            if (merges.isNotEmpty()) {
                item(key = "merges_header") { SetGroup(stringResource(R.string.grouping_merged_header, merges.size)) }
                items(merges, key = { "m:" + it.fromKey }) { m ->
                    SetRow("${m.fromName}  →  ${m.intoName}") {
                        Pill(stringResource(R.string.grouping_undo), on = false) { viewModel.undo(m) }
                    }
                }
            }
        }
    }
}

private fun reasonLabel(reason: MergeSuggester.Reason): Int = when (reason) {
    MergeSuggester.Reason.GUIDE -> R.string.grouping_reason_guide
    MergeSuggester.Reason.WORDS -> R.string.grouping_reason_words
    MergeSuggester.Reason.SPELLING -> R.string.grouping_reason_spelling
}

/** Automatic first, then the countries the app knows how to read a list for. */
private val HOME_COUNTRIES = listOf(
    "", "IT", "GB", "DE", "AT", "CH", "FR", "BE", "NL", "ES", "PT", "PL", "RO", "GR", "AL", "TR", "US", "CA",
)

private fun countryName(code: String): String =
    java.util.Locale("", code).getDisplayCountry(java.util.Locale.getDefault()).ifBlank { code }
