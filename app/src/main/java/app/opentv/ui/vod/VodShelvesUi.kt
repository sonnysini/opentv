/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.vod

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentv.R
import app.opentv.core.VodShelves
import app.opentv.data.model.Movie
import app.opentv.data.model.Series
import app.opentv.data.parser.displayTitle
import app.opentv.ui.VodViewModel
import app.opentv.ui.focusOnEnter
import coil.compose.AsyncImage

/*
 * The provider's own shelves under the Movies/Shows charts (see [VodShelves]): a row of posters per
 * category ending in a "See all N" card, and families of categories (sagas, actors) as a row of
 * wide collection tiles. "See all" and a tile open [ShelfPage], one category as a grid.
 */

/** Which category page is open, and what it was opened from (the family, for the kicker). */
internal data class OpenShelf(val row: VodShelves.Row, val family: String?, val returnKey: String)

/** One movie category's shelf. Its titles load when the row first composes. */
@Composable
internal fun MovieShelfRow(
    row: VodShelves.Row,
    count: Int?,
    version: Int,
    viewModel: VodViewModel,
    focusKey: String?,
    onOpenMovie: (Movie) -> Unit,
    onSeeAll: (OpenShelf) -> Unit,
) {
    val movies by produceState<List<Movie>?>(null, row.key, version) { value = viewModel.shelfMovies(row.category) }
    val groups = remember(movies) { movies?.let { viewModel.collapseVariants(it) } }
    ShelfFrame(row.label, count, R.string.vod_shelf_count_movies, loading = groups == null, empty = groups?.isEmpty() == true) {
        val poster = LocalVodRowSizes.current.poster
        VodRow(poster) {
            items(groups.orEmpty(), key = { it.primary.id }) { group ->
                val badge = group.variants.firstOrNull()?.qualityLabel?.takeIf { it.isNotBlank() }
                PosterCard(
                    title = group.primary.displayTitle,
                    posterUrl = group.primary.posterUrl,
                    subtitle = group.primary.year?.toString(),
                    rating = group.primary.rating,
                    qualityBadge = badge,
                    onClick = { onOpenMovie(group.primary) },
                    modifier = Modifier.width(poster),
                )
            }
            if ((count ?: 0) > (movies?.size ?: 0)) item(key = "all") {
                SeeAllCard(count ?: 0, R.string.vod_shelf_count_movies, poster, focus = focusKey == row.key) {
                    onSeeAll(OpenShelf(row, null, row.key))
                }
            }
        }
    }
}

/** One show category's shelf. */
@Composable
internal fun SeriesShelfRow(
    row: VodShelves.Row,
    count: Int?,
    version: Int,
    viewModel: VodViewModel,
    focusKey: String?,
    onOpenSeries: (Series) -> Unit,
    onSeeAll: (OpenShelf) -> Unit,
) {
    val series by produceState<List<Series>?>(null, row.key, version) { value = viewModel.shelfSeries(row.category) }
    ShelfFrame(row.label, count, R.string.vod_shelf_count_shows, loading = series == null, empty = series?.isEmpty() == true) {
        val poster = LocalVodRowSizes.current.poster
        VodRow(poster) {
            items(series.orEmpty(), key = { it.id }) { item ->
                PosterCard(
                    title = item.displayTitle,
                    posterUrl = item.posterUrl,
                    subtitle = item.year?.toString(),
                    rating = item.rating,
                    onClick = { onOpenSeries(item) },
                    modifier = Modifier.width(poster),
                )
            }
            if ((count ?: 0) > (series?.size ?: 0)) item(key = "all") {
                SeeAllCard(count ?: 0, R.string.vod_shelf_count_shows, poster, focus = focusKey == row.key) {
                    onSeeAll(OpenShelf(row, null, row.key))
                }
            }
        }
    }
}

/** A family of categories as wide tiles: three of its posters side by side, the name under it. */
@Composable
internal fun CollectionShelfRow(
    collection: VodShelves.Collection,
    counts: Map<String, Int>,
    countRes: Int,
    version: Int,
    focusKey: String?,
    posters: suspend (VodShelves.Row) -> List<String?>,
    onOpen: (OpenShelf) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        ShelfHeader(collection.label, stringResource(R.string.vod_shelf_groups, collection.members.size))
        val tile = LocalVodRowSizes.current.resume
        VodRow(tile) {
            items(collection.members, key = { it.key }) { member ->
                CollectionTile(
                    member = member,
                    count = counts[VodShelves.countKey(member.category)],
                    countRes = countRes,
                    width = tile,
                    version = version,
                    focus = focusKey == member.key,
                    posters = posters,
                    onClick = { onOpen(OpenShelf(member, collection.label, member.key)) },
                )
            }
        }
    }
}

@Composable
private fun CollectionTile(
    member: VodShelves.Row,
    count: Int?,
    countRes: Int,
    width: Dp,
    version: Int,
    focus: Boolean,
    posters: suspend (VodShelves.Row) -> List<String?>,
    onClick: () -> Unit,
) {
    val art by produceState<List<String?>>(emptyList(), member.key, version) { value = posters(member).take(3) }
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    if (focus) LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    Column(
        Modifier
            .width(width)
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (focused) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    else Modifier,
                ),
        ) {
            // The first poster, darkened, as the tile's backdrop (no blur on the Fire's Android).
            art.firstOrNull()?.let {
                AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)))
            }
            Row(
                Modifier.fillMaxSize().padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                art.forEach { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxHeight().aspectRatio(2f / 3f).clip(RoundedCornerShape(5.dp)),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            member.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        count?.let {
            Text(
                stringResource(countRes, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** The last card of a shelf: "›  See all · N films", the size of a poster. */
@Composable
private fun SeeAllCard(count: Int, countRes: Int, width: Dp, focus: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    if (focus) LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    Column(
        Modifier
            .width(width)
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(4.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .border(
                    if (focused) 3.dp else 1.dp,
                    if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(8.dp),
                ),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("›", color = MaterialTheme.colorScheme.primary, fontSize = 40.sp, lineHeight = 40.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.vod_shelf_see_all) + "\n" + stringResource(countRes, count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Header + content, or a placeholder of the row's height while it loads; nothing when empty. */
@Composable
private fun ShelfFrame(
    title: String,
    count: Int?,
    countRes: Int,
    loading: Boolean,
    empty: Boolean,
    content: @Composable () -> Unit,
) {
    if (empty) return
    Column(Modifier.fillMaxWidth()) {
        ShelfHeader(title, count?.let { stringResource(countRes, it) })
        if (loading) {
            val poster = LocalVodRowSizes.current.poster
            Spacer(Modifier.height((poster - 8.dp) * 1.5f + 70.dp))
        } else {
            content()
        }
    }
}

@Composable
private fun ShelfHeader(title: String, note: String?) {
    Row(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        note?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/**
 * One category as a page: "Movies › Sagas" kicker, the name big in Oswald, the count, then the
 * grid. Back returns to the shelves.
 */
@Composable
internal fun ShelfPage(
    open: OpenShelf,
    sectionLabel: String,
    count: Int?,
    countRes: Int,
    onBack: () -> Unit,
    grid: @Composable () -> Unit,
) {
    BackHandler { onBack() }
    Column(Modifier.fillMaxSize().focusOnEnter(open.row.key)) {
        Text(
            listOfNotNull(sectionLabel, open.family).joinToString("  ›  "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, top = 18.dp),
        )
        Text(
            open.row.label.uppercase(),
            fontFamily = ShelfOswald,
            fontWeight = FontWeight.Bold,
            fontSize = 40.sp,
            letterSpacing = 0.5.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 20.dp, top = 2.dp, end = 20.dp),
        )
        count?.let {
            Text(
                stringResource(countRes, it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, bottom = 4.dp),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { grid() }
    }
}

private val ShelfOswald = FontFamily(Font(R.font.oswald_bold, FontWeight.Bold))
