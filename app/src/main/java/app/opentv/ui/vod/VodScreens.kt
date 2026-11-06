/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.vod

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import app.opentv.ui.CARD_GAP
import app.opentv.ui.holdOk
import app.opentv.ui.bottomFade
import app.opentv.data.remote.TmdbLists
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.R
import app.opentv.data.model.Movie
import app.opentv.data.model.Series
import app.opentv.data.model.Source
import app.opentv.data.parser.displayTitle
import app.opentv.ui.VodViewModel
import coil.compose.AsyncImage
import app.opentv.ui.theme.focusLift

/**
 * Movies, in the live page's clothes: a hero turning over the week's most watched films, genre tabs
 * in Oswald, then shelves — Continue Watching, the week's Top 10, Recently added, Popular in Italy,
 * Top rated. The charts are TMDB's ([app.opentv.data.repo.VodHomeRepository]), kept to what the
 * provider really has; with no TMDB key the hero shows what came in lately and the shelves are the
 * catalogue's own. The last tab, All, is the provider's categories, one grid each.
 */
@Composable
fun MoviesScreen(
    onOpenMovie: (Movie) -> Unit,
    onOpenSeries: (Series) -> Unit,
    onResume: (mediaKey: String, url: String, title: String) -> Unit,
    onOpenSearch: () -> Unit,
    hasSources: Boolean,
    isSyncing: Boolean,
    viewModel: VodViewModel = viewModel(),
) {
    val categories by viewModel.movieCategories.collectAsStateWithLifecycle()
    val resume by viewModel.continueWatching.collectAsStateWithLifecycle()
    val recommended by viewModel.recommendedMovies.collectAsStateWithLifecycle()
    val recentlyAdded by viewModel.recentlyAddedMovies.collectAsStateWithLifecycle()
    val genreRows by viewModel.movieGenreRows.collectAsStateWithLifecycle()
    val categoryMovies by viewModel.movies.collectAsStateWithLifecycle()
    val vodLoading by viewModel.vodLoading.collectAsStateWithLifecycle()
    val vodProgress by viewModel.vodProgress.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val selectedSource by viewModel.selectedVodSource.collectAsStateWithLifecycle()
    val tmdbEnabled by viewModel.tmdbEnabled.collectAsStateWithLifecycle()
    val home by viewModel.moviesHome.collectAsStateWithLifecycle()
    val genreHits by viewModel.movieGenreHits.collectAsStateWithLifecycle()

    // Pull the movie library the first time this tab is opened, not at login; refresh the computed
    // home rows (recommended, by-genre) on open too — cheap, and covers a library already on disk.
    LaunchedEffect(Unit) {
        if (hasSources) viewModel.ensureVodLoaded()
        viewModel.loadHomeFeeds()
    }
    // The charts, once there's a library to look them up in (and again when a sync lands).
    LaunchedEffect(vodLoading, tmdbEnabled) {
        if (!vodLoading && tmdbEnabled) viewModel.loadMoviesHome()
    }

    val genres = if (tmdbEnabled) TmdbLists.MOVIE_GENRES.entries.toList() else emptyList()
    val tabLabels = listOf(stringResource(R.string.vod_tab_featured)) + genres.map { it.value } +
        stringResource(R.string.vod_tab_all)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    if (tab > tabLabels.lastIndex) tab = 0
    val allTab = tabLabels.lastIndex
    var browseCategory by remember { mutableStateOf<String?>(null) }

    val chartHero = home?.hero.orEmpty()
    val slides = remember(home, recentlyAdded) {
        val top = home?.top10.orEmpty()
        chartHero.map { h ->
            val rank = top.indexOfFirst { it.movie.id == h.hit.movie.id }.takeIf { it >= 0 }?.plus(1)
            h.hit.toSlide(rank, h.logoUrl, h.genres)
        }.ifEmpty { recentlyAdded.filter { it.backdropUrl != null || it.posterUrl != null }.take(6).map { it.toSlide() } }
    }
    val kicker = stringResource(
        if (chartHero.isNotEmpty()) R.string.vod_hero_trending_movies else R.string.vod_hero_new_movies,
    )
    val play: (Movie) -> Unit = { onResume("movie:${it.id}", it.streamUrl, it.displayTitle) }
    val selectTab: (Int) -> Unit = { i ->
        tab = i
        if (i == allTab && browseCategory == null) {
            categories.firstOrNull()?.let { browseCategory = it.id; viewModel.selectMovieCategory(it.id) }
        }
    }

    if (tab == allTab) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(12.dp))
            VodTabs(tabLabels, tab, selectTab)
            if (sources.size > 1) {
                ProviderChips(
                    sources = sources,
                    selected = selectedSource,
                    onSelectAll = { browseCategory = null; viewModel.selectVodSource(null) },
                    onSelectSource = { id -> browseCategory = null; viewModel.selectVodSource(id) },
                )
            }
            CategoryChips(
                entries = categories.map { it.id to it.name },
                selected = browseCategory,
                onSelectHome = { tab = 0 },
                onSelectCategory = { id -> browseCategory = id; viewModel.selectMovieCategory(id) },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (browseCategory != null) MovieCategoryGrid(categoryMovies, viewModel, onOpenMovie)
            }
        }
        return
    }

    val hasContent = resume.isNotEmpty() || recommended.isNotEmpty() || recentlyAdded.isNotEmpty() ||
        genreRows.isNotEmpty() || home != null
    if (!hasContent && slides.isEmpty()) {
        when {
            vodLoading || isSyncing -> LoadingVod(stringResource(R.string.vod_loading_movies))
            hasSources -> EmptyVod(stringResource(R.string.vod_no_movies), stringResource(R.string.vod_no_movies_provider))
            else -> EmptyVod(stringResource(R.string.vod_no_movies), stringResource(R.string.vod_no_movies_add))
        }
        return
    }

    // The hero lands after the first rows when the charts come in late: bring the page back to
    // the top then, unless someone has already scrolled down.
    val listState = rememberLazyListState()
    val hasHero = slides.isNotEmpty()
    LaunchedEffect(hasHero) {
        if (hasHero && listState.firstVisibleItemIndex <= 1) listState.scrollToItem(0)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val columns = POSTERS_PER_ROW
    val sizes = remember(maxWidth) { vodRowSizes(maxWidth) }
    CompositionLocalProvider(LocalVodRowSizes provides sizes) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().bottomFade(),
    ) {
        if (slides.isNotEmpty()) item(key = "hero") {
            VodHero(slides, kicker, onWatch = { it.movie?.let(play) }, onDetails = { it.movie?.let(onOpenMovie) })
        }
        item(key = "tabs") { VodTabs(tabLabels, tab, selectTab, Modifier.padding(top = if (slides.isEmpty()) 12.dp else 0.dp)) }
        vodProgress?.let { p -> item(key = "sync") { VodSyncStrip(p) } }
        if (tab == 0) {
            if (resume.isNotEmpty()) item(key = "cw") { ContinueWatchingRow(resume, onResume, onOpenSeries, viewModel) }
            home?.let { h ->
                item(key = "top10") { TopTenRow(stringResource(R.string.vod_top10_movies), h.top10) { onOpenMovie(it.movie) } }
            }
            if (recentlyAdded.isNotEmpty()) item(key = "recent") {
                MoviePosterRow(stringResource(R.string.vod_recently_added), recentlyAdded, onOpenMovie)
            }
            home?.let { h ->
                item(key = "popular") { HitRow(stringResource(R.string.vod_popular_italy), h.popular) { onOpenMovie(it.movie) } }
                item(key = "best") { HitRow(stringResource(R.string.vod_top_rated_ever), h.topRated) { onOpenMovie(it.movie) } }
            }
            if (home == null) {
                if (recommended.isNotEmpty()) item(key = "rec") {
                    MoviePosterRow(stringResource(R.string.vod_recommended), recommended, onOpenMovie)
                }
                items(genreRows, key = { "g:${it.genre}" }) { group ->
                    MoviePosterRow(group.genre, group.items, onOpenMovie)
                }
            }
        } else {
            val genreId = genres[tab - 1].key
            val hits = genreHits[genreId]
            item(key = "load:$genreId") { LaunchedEffect(genreId) { if (hits == null) viewModel.loadMovieGenre(genreId) } }
            when {
                hits == null -> item(key = "wait") { GenreNote(stringResource(R.string.vod_genre_loading)) }
                hits.isEmpty() -> item(key = "none") { GenreNote(stringResource(R.string.vod_genre_empty)) }
                else -> items(hits.chunked(columns), key = { "r:${it.first().movie.id}" }) { row ->
                    HitGridRow(row) { onOpenMovie(it.movie) }
                }
            }
        }
    }
    }
    }
}

@Composable
private fun GenreNote(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 36.dp, vertical = 24.dp),
    )
}

/**
 * Shows, dressed like Movies: a hero turning over the week's most watched series, TMDB's TV genres as
 * tabs, then Continue Watching, New episodes (shows the profile follows with episodes it hasn't
 * reached), the Top 10, Recently added, Popular in Italy, Top rated. With no TMDB key the hero shows
 * what came in lately and the shelves are the provider's genres. The last tab, All, is the
 * provider's categories. A show opens its detail page, where episodes are fetched on demand.
 */
@Composable
fun SeriesScreen(
    onOpenSeries: (Series) -> Unit,
    onResume: (mediaKey: String, url: String, title: String) -> Unit,
    onOpenSearch: () -> Unit,
    hasSources: Boolean,
    isSyncing: Boolean,
    viewModel: VodViewModel = viewModel(),
) {
    val categories by viewModel.seriesCategories.collectAsStateWithLifecycle()
    val resume by viewModel.continueWatching.collectAsStateWithLifecycle()
    val recentlyAdded by viewModel.recentlyAddedSeries.collectAsStateWithLifecycle()
    val genreRows by viewModel.seriesGenreRows.collectAsStateWithLifecycle()
    val categorySeries by viewModel.series.collectAsStateWithLifecycle()
    val vodLoading by viewModel.vodLoading.collectAsStateWithLifecycle()
    val vodProgress by viewModel.vodProgress.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val selectedSource by viewModel.selectedVodSource.collectAsStateWithLifecycle()
    val tmdbEnabled by viewModel.tmdbEnabled.collectAsStateWithLifecycle()
    val home by viewModel.seriesHome.collectAsStateWithLifecycle()
    val genreHits by viewModel.seriesGenreHits.collectAsStateWithLifecycle()
    val newEpisodes by viewModel.newEpisodes.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (hasSources) viewModel.ensureVodLoaded()
        viewModel.loadHomeFeeds()
        viewModel.loadNewEpisodes()
    }
    LaunchedEffect(vodLoading, tmdbEnabled) {
        if (!vodLoading && tmdbEnabled) viewModel.loadSeriesHome()
    }

    val genres = if (tmdbEnabled) TmdbLists.TV_GENRES.entries.toList() else emptyList()
    val tabLabels = listOf(stringResource(R.string.vod_tab_featured)) + genres.map { it.value } +
        stringResource(R.string.vod_tab_all_shows)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    if (tab > tabLabels.lastIndex) tab = 0
    val allTab = tabLabels.lastIndex
    var browseCategory by remember { mutableStateOf<String?>(null) }

    val chartHero = home?.hero.orEmpty()
    val slides = remember(home, recentlyAdded) {
        val top = home?.top10.orEmpty()
        chartHero.map { h ->
            val rank = top.indexOfFirst { it.key == h.hit.key }.takeIf { it >= 0 }?.plus(1)
            h.hit.toSlide(rank, h.logoUrl, h.genres)
        }.ifEmpty { recentlyAdded.filter { it.backdropUrl != null || it.posterUrl != null }.take(6).map { it.toSlide() } }
    }
    val kicker = stringResource(
        if (chartHero.isNotEmpty()) R.string.vod_hero_trending_shows else R.string.vod_hero_new_shows,
    )
    val selectTab: (Int) -> Unit = { i ->
        tab = i
        if (i == allTab && browseCategory == null) {
            categories.firstOrNull()?.let { browseCategory = it.id; viewModel.selectSeriesCategory(it.id) }
        }
    }

    if (tab == allTab) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(12.dp))
            VodTabs(tabLabels, tab, selectTab)
            if (sources.size > 1) {
                ProviderChips(
                    sources = sources,
                    selected = selectedSource,
                    onSelectAll = { browseCategory = null; viewModel.selectVodSource(null) },
                    onSelectSource = { id -> browseCategory = null; viewModel.selectVodSource(id) },
                )
            }
            CategoryChips(
                entries = categories.map { it.id to it.name },
                selected = browseCategory,
                onSelectHome = { tab = 0 },
                onSelectCategory = { id -> browseCategory = id; viewModel.selectSeriesCategory(id) },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (browseCategory != null) SeriesCategoryGrid(categorySeries, onOpenSeries)
            }
        }
        return
    }

    val hasContent = resume.isNotEmpty() || recentlyAdded.isNotEmpty() || genreRows.isNotEmpty() || home != null
    if (!hasContent && slides.isEmpty()) {
        when {
            vodLoading || isSyncing -> LoadingVod(stringResource(R.string.vod_loading_shows))
            hasSources -> EmptyVod(stringResource(R.string.vod_no_shows), stringResource(R.string.vod_no_shows_provider))
            else -> EmptyVod(stringResource(R.string.vod_no_shows), stringResource(R.string.vod_no_shows_add))
        }
        return
    }

    // As on Movies: the charts may land after the first rows; go back to the hero if nobody scrolled.
    val listState = rememberLazyListState()
    val hasHero = slides.isNotEmpty()
    LaunchedEffect(hasHero) {
        if (hasHero && listState.firstVisibleItemIndex <= 1) listState.scrollToItem(0)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val columns = POSTERS_PER_ROW
    val sizes = remember(maxWidth) { vodRowSizes(maxWidth) }
    CompositionLocalProvider(LocalVodRowSizes provides sizes) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().bottomFade(),
    ) {
        if (slides.isNotEmpty()) item(key = "hero") {
            val open: (HeroSlide) -> Unit = { s -> s.series?.let(onOpenSeries) }
            VodHero(slides, kicker, onWatch = open, onDetails = open)
        }
        item(key = "tabs") { VodTabs(tabLabels, tab, selectTab, Modifier.padding(top = if (slides.isEmpty()) 12.dp else 0.dp)) }
        vodProgress?.let { p -> item(key = "sync") { VodSyncStrip(p) } }
        if (tab == 0) {
            if (resume.isNotEmpty()) item(key = "cw") { ContinueWatchingRow(resume, onResume, onOpenSeries, viewModel) }
            if (newEpisodes.isNotEmpty()) item(key = "new") { NewEpisodesRow(newEpisodes, onOpenSeries) }
            home?.let { h ->
                item(key = "top10") { TopTenRow(stringResource(R.string.vod_top10_shows), h.top10) { onOpenSeries(it.series) } }
            }
            if (recentlyAdded.isNotEmpty()) item(key = "recent") {
                SeriesPosterRow(stringResource(R.string.vod_recently_added), recentlyAdded, onOpenSeries)
            }
            home?.let { h ->
                item(key = "popular") { HitRow(stringResource(R.string.vod_popular_italy), h.popular) { onOpenSeries(it.series) } }
                item(key = "best") { HitRow(stringResource(R.string.vod_top_rated_ever), h.topRated) { onOpenSeries(it.series) } }
            }
            if (home == null) {
                items(genreRows, key = { "g:${it.genre}" }) { group ->
                    SeriesPosterRow(group.genre, group.items, onOpenSeries)
                }
            }
        } else {
            val genreId = genres[tab - 1].key
            val hits = genreHits[genreId]
            item(key = "load:$genreId") { LaunchedEffect(genreId) { if (hits == null) viewModel.loadSeriesGenre(genreId) } }
            when {
                hits == null -> item(key = "wait") { GenreNote(stringResource(R.string.vod_genre_loading_shows)) }
                hits.isEmpty() -> item(key = "none") { GenreNote(stringResource(R.string.vod_genre_empty_shows)) }
                else -> items(hits.chunked(columns), key = { "r:${it.first().key}" }) { row ->
                    HitGridRow(row) { onOpenSeries(it.series) }
                }
            }
        }
    }
    }
    }
}

/** "New episodes": the show's poster, the first episode not yet reached, and how many there are. */
@Composable
private fun NewEpisodesRow(items: List<app.opentv.data.repo.CatalogRepository.NewEpisodes>, onOpenSeries: (Series) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.vod_new_episodes))
        VodRow(LocalVodRowSizes.current.poster) {
            items(items, key = { it.series.id }) { item ->
                val next = "S${item.next.season} E${item.next.episodeNumber}"
                PosterCard(
                    title = item.series.displayTitle,
                    posterUrl = item.series.posterUrl,
                    subtitle = if (item.count > 1) stringResource(R.string.vod_new_episodes_more, next, item.count - 1) else next,
                    qualityBadge = stringResource(R.string.vod_new_episodes_badge),
                    onClick = { onOpenSeries(item.series) },
                    modifier = Modifier.width(LocalVodRowSizes.current.poster),
                )
            }
        }
    }
}

// ---- Whole-category browse grids ---------------------------------------------------------------

/** One category's films as a poster grid. Quality variants collapse to one card, badged. */
@Composable
internal fun MovieCategoryGrid(movies: List<Movie>, viewModel: VodViewModel, onOpenMovie: (Movie) -> Unit) {
    if (movies.isEmpty()) { LoadingVod(stringResource(R.string.vod_loading_movies)); return }
    val groups = remember(movies) { viewModel.collapseVariants(movies) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        gridItems(groups, key = { it.primary.id }) { group ->
            val quality = group.variants.firstOrNull()?.qualityLabel?.takeIf { it.isNotBlank() }
            val badge = quality
                ?: if (group.hasMultipleQualities) {
                    stringResource(R.string.guide_qualities_count, group.variants.size)
                } else {
                    null
                }
            PosterCard(
                title = group.primary.displayTitle,
                posterUrl = group.primary.posterUrl,
                subtitle = group.primary.year?.toString(),
                rating = group.primary.rating,
                qualityBadge = badge,
                onClick = { onOpenMovie(group.primary) },
            )
        }
    }
}

/** One category's shows as a poster grid. */
@Composable
private fun SeriesCategoryGrid(series: List<Series>, onOpenSeries: (Series) -> Unit) {
    if (series.isEmpty()) { LoadingVod(stringResource(R.string.vod_loading_shows)); return }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        gridItems(series, key = { it.id }) { item ->
            PosterCard(
                title = item.displayTitle,
                posterUrl = item.posterUrl,
                subtitle = item.year?.toString(),
                rating = item.rating,
                onClick = { onOpenSeries(item) },
            )
        }
    }
}

// ---- Shared shelves ----------------------------------------------------------------------------

/** A titled horizontal shelf of movie poster cards. Shared by the home and the detail's "more like this". */
@Composable
internal fun MoviePosterRow(title: String, movies: List<Movie>, onOpenMovie: (Movie) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(title)
        VodRow(LocalVodRowSizes.current.poster) {
            items(movies, key = { it.id }) { movie ->
                PosterCard(
                    title = movie.displayTitle,
                    posterUrl = movie.posterUrl,
                    subtitle = movie.year?.toString(),
                    rating = movie.rating,
                    onClick = { onOpenMovie(movie) },
                    modifier = Modifier.width(LocalVodRowSizes.current.poster),
                )
            }
        }
    }
}

/** A titled horizontal shelf of series poster cards. */
@Composable
internal fun SeriesPosterRow(title: String, series: List<Series>, onOpenSeries: (Series) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(title)
        VodRow(LocalVodRowSizes.current.poster) {
            items(series, key = { it.id }) { item ->
                PosterCard(
                    title = item.displayTitle,
                    posterUrl = item.posterUrl,
                    subtitle = item.year?.toString(),
                    rating = item.rating,
                    onClick = { onOpenSeries(item) },
                    modifier = Modifier.width(LocalVodRowSizes.current.poster),
                )
            }
        }
    }
}

@Composable
internal fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
    )
}

/**
 * The reusable poster card: art, title and an optional year, with a rating chip, a quality badge and
 * a resume progress bar drawn over the art where the data is there. The focused card scales up and
 * gains a primary border — the app's established focus cue — and, being focusable, the lazy row
 * brings it into view on its own.
 */
@Composable
internal fun PosterCard(
    title: String,
    posterUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    rating: Double? = null,
    qualityBadge: String? = null,
    progress: Float? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .width(POSTER_WIDTH)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (focused) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    else Modifier,
                ),
        ) {
            AsyncImage(
                model = posterUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // A flat 10.0 is a provider placeholder, not a score.
            rating?.takeIf { it > 0.0 && it < 10.0 }?.let {
                Badge(
                    text = "★ ${formatRating(it)}",
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                )
            }
            qualityBadge?.let {
                Badge(
                    text = it,
                    highlight = true,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                )
            }
            progress?.let {
                LinearProgressIndicator(
                    progress = { it },
                    modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** A small rounded chip drawn over poster art — a rating or a quality label. */
@Composable
private fun Badge(text: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    val bg = if (highlight) MaterialTheme.colorScheme.primary
    else androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.66f)
    val fg = if (highlight) MaterialTheme.colorScheme.onPrimary else androidx.compose.ui.graphics.Color.White
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = fg,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

// ---- Continue watching -------------------------------------------------------------------------

@Composable
internal fun ContinueWatchingRow(
    items: List<VodViewModel.ResumeItem>,
    onResume: (mediaKey: String, url: String, title: String) -> Unit,
    onOpenSeries: (Series) -> Unit,
    viewModel: VodViewModel,
) {
    // Hold OK on a card: its panel; "Choose the source" swaps it for the list of copies.
    var held by remember { mutableStateOf<VodViewModel.ResumeItem?>(null) }
    var picking by remember { mutableStateOf<Pair<VodViewModel.ResumeItem, List<VodViewModel.ResumeSource>>?>(null) }
    // A card taken off the shelf (removed, or watched): the focus stays in the row, on the card
    // that takes its place, instead of jumping to the top of the page.
    var focusedAt by remember { mutableIntStateOf(0) }
    var refocusPending by remember { mutableStateOf(false) }
    val refocus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(items) {
        if (!refocusPending) return@LaunchedEffect
        refocusPending = false
        kotlinx.coroutines.delay(50)
        runCatching { refocus.requestFocus() }
    }
    val target = focusedAt.coerceAtMost(items.lastIndex)
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.vod_continue_watching))
        VodRow(LocalVodRowSizes.current.resume) {
            itemsIndexed(items, key = { _, it -> it.mediaKey }) { i, item ->
                Box(
                    (if (i == target) Modifier.focusRequester(refocus) else Modifier)
                        .onFocusChanged { if (it.hasFocus) focusedAt = i },
                ) {
                    ResumeCard(item, onHold = { held = item }) { onResume(item.mediaKey, item.streamUrl, item.title) }
                }
            }
        }
    }
    held?.let { item ->
        ResumePanel(
            item = item,
            viewModel = viewModel,
            onResume = onResume,
            onOpenSeries = onOpenSeries,
            onPickSource = { sources ->
                held = null
                picking = item to sources
            },
            onLeaveShelf = { refocusPending = true },
            onClose = { held = null },
        )
    }
    picking?.let { (item, sources) ->
        ResumeSourcePanel(item, sources, onResume) { picking = null }
    }
}

/** A landscape resume thumbnail with a progress fill — a movie or an episode part-way through. */
@Composable
private fun ResumeCard(item: VodViewModel.ResumeItem, onHold: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .width(LocalVodRowSizes.current.resume)
            .onFocusChanged { focused = it.isFocused }
            .holdOk({ focused }, onHold)
            .clip(RoundedCornerShape(8.dp))
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (focused) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                    else Modifier,
                ),
        ) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            LinearProgressIndicator(
                progress = { item.progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomStart),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---- Category chips ----------------------------------------------------------------------------

/**
 * A horizontal chip strip along the top of the home: an "All" chip returns to the curated rows, and
 * each following chip opens that category's full grid. Keeps whole-category browsing reachable on a
 * d-pad without a permanent side rail taking the width.
 */
@Composable
internal fun CategoryChips(
    entries: List<Pair<String, String>>,
    selected: String?,
    onSelectHome: () -> Unit,
    onSelectCategory: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "label") {
            Text(
                stringResource(R.string.vod_categories),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        item(key = "all") { Chip(stringResource(R.string.vod_all), selected == null, onSelectHome) }
        items(entries, key = { it.first }) { (id, name) ->
            Chip(name, selected == id) { onSelectCategory(id) }
        }
    }
}

/**
 * A provider filter above the category chips, shown only when more than one source is configured —
 * pick a provider to browse just its Movies/Shows categories, or "All sources" to fold them.
 */
@Composable
internal fun ProviderChips(
    sources: List<Source>,
    selected: Long?,
    onSelectAll: () -> Unit,
    onSelectSource: (Long) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "plabel") {
            Text(
                stringResource(R.string.channels_manager_source_header),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        item(key = "pall") {
            Chip(stringResource(R.string.channels_manager_all_sources), selected == null, onSelectAll)
        }
        items(sources, key = { it.id }) { source ->
            Chip(source.name, selected == source.id) { onSelectSource(source.id) }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when {
        focused -> MaterialTheme.colorScheme.onPrimary
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = fg,
        maxLines = 1,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .focusLift(focused)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

// ---- Search / loading / empty ------------------------------------------------------------------

@Composable
internal fun EmptyVod(title: String, body: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun LoadingVod(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.vod_large_provider_note),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A thin "still loading" line while a background VOD sync runs: what is on screen is usable, more
 * is on its way. Counts are this run's titles, so they climb from zero on a refresh too.
 */
@Composable
internal fun VodSyncStrip(progress: app.opentv.data.repo.CatalogRepository.VodProgress) {
    val format = remember { java.text.NumberFormat.getIntegerInstance() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.width(14.dp).height(14.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.vod_sync_progress, format.format(progress.movies), format.format(progress.series)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Poster shelf card width; the grid uses an adaptive min size close to this. */
private val POSTER_WIDTH = 140.dp

/** The focused card grows this much, on top of its teal ring. */

/** Rating to one decimal place, locale-independent (the "★" is drawn beside it). */
internal fun formatRating(rating: Double): String = String.format(java.util.Locale.US, "%.1f", rating)
