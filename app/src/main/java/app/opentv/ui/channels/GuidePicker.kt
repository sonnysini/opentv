/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.opentv.R
import app.opentv.data.model.shownName
import app.opentv.data.repo.EpgRepository
import app.opentv.ui.ChannelsViewModel
import kotlinx.coroutines.delay

/**
 * Channel menu › Find guide: searches the enabled guide feeds' channel lists (prefilled with the
 * channel's name) and shows each hit with its feed, programme count and what is on now, so the
 * right one is obvious. The pick goes on every copy of the channel and beats the automatic
 * match; Automatic drops it.
 */
@Composable
fun GuidePickerDialog(
    row: ChannelsViewModel.Row,
    epg: EpgRepository,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val channel = row.primary
    val current = channel.epgOverrideId
    var query by remember(row) { mutableStateOf(channel.shownName) }
    var results by remember { mutableStateOf<List<EpgRepository.GuideHit>?>(null) }
    LaunchedEffect(query) {
        delay(350)
        results = epg.searchGuides(query)
    }
    val fieldFocus = remember { FocusRequester() }
    val firstFocus = remember { FocusRequester() }
    // On a TV the field opens the keyboard as soon as it takes focus, so start on the results.
    var focusedOnce by remember(row) { mutableStateOf(false) }
    LaunchedEffect(results) {
        val hits = results ?: return@LaunchedEffect
        if (focusedOnce) return@LaunchedEffect
        focusedOnce = true
        delay(50)
        runCatching { if (hits.isNotEmpty()) firstFocus.requestFocus() else fieldFocus.requestFocus() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(620.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp),
        ) {
            Text(
                stringResource(R.string.guide_picker_title, channel.shownName),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text(stringResource(R.string.guide_picker_search)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runCatching { firstFocus.requestFocus() } }),
                modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus),
            )
            Spacer(Modifier.height(10.dp))
            val hits = results
            when {
                hits == null -> Hint(stringResource(R.string.guide_picker_searching))
                hits.isEmpty() -> Hint(stringResource(R.string.guide_picker_empty))
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.heightIn(max = 330.dp),
                ) {
                    itemsIndexed(hits, key = { _, hit -> hit.epgId }) { index, hit ->
                        GuideHitRow(
                            hit = hit,
                            selected = hit.epgId == current,
                            modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            onClick = { onPick(hit.epgId) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (current != null) GuideActionRow(stringResource(R.string.guide_picker_automatic)) { onPick(null) }
            GuideActionRow(stringResource(R.string.common_cancel), onClick = onDismiss)
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun GuideHitRow(hit: EpgRepository.GuideHit, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val onColor = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val subColor = if (focused) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLogo(hit.iconUrl, hit.name, Modifier.size(44.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                (if (selected) "✓ " else "") + hit.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = onColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.guide_picker_details, hit.epgId, hit.feedName, hit.programmes),
                style = MaterialTheme.typography.bodySmall,
                color = subColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                hit.nowTitle?.let { stringResource(R.string.guide_now_title, it) }
                    ?: stringResource(R.string.guide_picker_nothing_now),
                style = MaterialTheme.typography.bodySmall,
                color = subColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GuideActionRow(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        color = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
