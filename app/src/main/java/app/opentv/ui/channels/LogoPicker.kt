/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.opentv.R
import app.opentv.core.LogoPack
import app.opentv.data.model.logo
import app.opentv.data.repo.CatalogRepository
import app.opentv.ui.ChannelsViewModel

/**
 * Channel menu › Change logo: every logo this channel could wear — each merged source's, the
 * guide's, the logo pack's, then those of channels named like it — as a grid. The pick is kept
 * per group key and beats everything else (see [LogoPack.custom]); Automatic drops it.
 */
@Composable
fun LogoPickerDialog(
    row: ChannelsViewModel.Row,
    catalog: CatalogRepository,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val pack = LocalLogoPack.current
    val preferGuide = LocalPreferGuideLogos.current
    val groupKey = row.primary.groupKey
    val current = row.primary.logo(preferGuide, pack)
    val similar by produceState(emptyList<String>(), groupKey) { value = catalog.similarLogos(groupKey) }
    val choices = remember(row, pack, similar) {
        buildList {
            pack.custom[groupKey]?.let(::add)
            row.variants.forEach { add(it.logoUrl); add(it.guideLogoUrl) }
            add(LogoPack(pack.byKey).logoFor(groupKey))
            addAll(similar)
        }.filterNotNull().filter { it.isNotBlank() }.distinct()
    }
    val firstFocus = remember { FocusRequester() }
    val currentIndex = choices.indexOf(current).coerceAtLeast(0)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(560.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp),
        ) {
            Text(
                stringResource(R.string.logo_picker_title, row.primary.shownName),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))
            if (choices.isEmpty()) {
                Text(
                    stringResource(R.string.logo_picker_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 340.dp),
                ) {
                    itemsIndexed(choices, key = { _, url -> url }) { index, url ->
                        LogoTile(
                            url = url,
                            name = row.primary.shownName,
                            selected = url == current,
                            modifier = if (index == currentIndex) Modifier.focusRequester(firstFocus) else Modifier,
                            onClick = { onPick(url) },
                        )
                    }
                }
                LaunchedEffect(choices.isNotEmpty()) { runCatching { firstFocus.requestFocus() } }
            }
            Spacer(Modifier.height(12.dp))
            if (groupKey in pack.custom) PickerActionRow(stringResource(R.string.logo_picker_automatic)) { onPick(null) }
            PickerActionRow(stringResource(R.string.common_cancel), onClick = onDismiss)
        }
    }
}

@Composable
private fun LogoTile(url: String, name: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val border = when {
        focused -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        else -> Color.Transparent
    }
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
            .border(3.dp, border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        ChannelLogo(url, name, Modifier.fillMaxSize())
        if (selected) {
            Text(
                "✓",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 6.dp),
            )
        }
    }
}

@Composable
private fun PickerActionRow(label: String, onClick: () -> Unit) {
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
