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
import androidx.compose.runtime.collectAsState
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
import app.opentv.data.parser.MergeSuggester
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
    val mergeSources by viewModel.mergeSources.collectAsState()
    val stripSources by viewModel.stripSources.collectAsState()
    val groupBadges by viewModel.groupBadges.collectAsState()
    val showRecentGroups by viewModel.showRecentGroups.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val merges by viewModel.merges.collectAsState()
    val busy by viewModel.busy.collectAsState()

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

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_grouping_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            if (busy) {
                Text(
                    stringResource(R.string.grouping_applying),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(16.dp))
            }
            HubTextButton(stringResource(R.string.common_done), Modifier.focusRequester(doneFocus), onBack)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.grouping_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            item(key = "merge_sources") {
                SwitchRow(
                    stringResource(R.string.categories_merge_sources),
                    stringResource(R.string.categories_merge_sources_desc),
                    checked = mergeSources,
                    onToggle = viewModel::setMergeSources,
                    emphasised = true,
                )
            }
            item(key = "strip_sources") {
                SwitchRow(
                    stringResource(R.string.categories_strip_sources),
                    stringResource(R.string.categories_strip_sources_desc),
                    checked = stripSources,
                    onToggle = viewModel::setStripSources,
                    emphasised = true,
                )
            }
            item(key = "group_badges") {
                SwitchRow(
                    stringResource(R.string.categories_group_badges),
                    stringResource(R.string.categories_group_badges_desc),
                    checked = groupBadges,
                    onToggle = viewModel::setGroupBadges,
                    emphasised = true,
                )
            }
            item(key = "recent_groups") {
                SwitchRow(
                    stringResource(R.string.categories_recent_groups),
                    stringResource(R.string.categories_recent_groups_desc),
                    checked = showRecentGroups,
                    onToggle = viewModel::setShowRecentGroups,
                    emphasised = true,
                )
            }

            val list = suggestions
            item(key = "suggestions_header") {
                SectionHeader(
                    if (list == null) stringResource(R.string.grouping_suggestions_loading)
                    else stringResource(R.string.grouping_suggestions_header, list.size),
                )
            }
            if (list != null && list.isEmpty()) {
                item(key = "suggestions_none") { Note(stringResource(R.string.grouping_suggestions_none)) }
            }
            if (list != null) {
                items(list, key = { "s:" + it.pairId }) { s ->
                    val index = list.indexOf(s)
                    PairRow(
                        title = "${s.from.name}  →  ${s.into.name}",
                        subtitle = stringResource(
                            R.string.grouping_channel_count,
                            stringResource(reasonLabel(s.reason)),
                            s.from.channels,
                            s.into.channels,
                        ),
                    ) {
                        HubTextButton(
                            stringResource(R.string.grouping_merge),
                            Modifier.focusRequester(requesters.getOrPut(s.pairId) { FocusRequester() }),
                        ) {
                            refocusIndex = index
                            viewModel.merge(s)
                        }
                        Spacer(Modifier.width(10.dp))
                        HubTextButton(stringResource(R.string.grouping_ignore)) {
                            refocusIndex = index
                            viewModel.ignore(s)
                        }
                    }
                }
            }

            if (merges.isNotEmpty()) {
                item(key = "merges_header") { SectionHeader(stringResource(R.string.grouping_merged_header, merges.size)) }
                items(merges, key = { "m:" + it.fromKey }) { m ->
                    PairRow(title = "${m.fromName}  →  ${m.intoName}", subtitle = null) {
                        HubTextButton(stringResource(R.string.grouping_undo)) { viewModel.undo(m) }
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

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The row itself is not a focus target: only its buttons are, one d-pad stop each. */
@Composable
private fun PairRow(title: String, subtitle: String?, buttons: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        buttons()
    }
}
