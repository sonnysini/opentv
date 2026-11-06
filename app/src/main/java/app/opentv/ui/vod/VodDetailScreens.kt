/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.vod

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import app.opentv.data.model.PlaybackPosition
import app.opentv.data.repo.MovieVariant
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.R
import app.opentv.data.model.Episode
import app.opentv.data.model.Movie
import app.opentv.data.model.Series
import app.opentv.data.model.StremioStream
import app.opentv.data.parser.displayTitle
import app.opentv.ui.VodViewModel
import coil.compose.AsyncImage
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.opentv.ui.theme.focusLift

/**
 * A film's page, in the home hero's clothes: the backdrop melting into the page, the film's own
 * lettering, year · genres · running time · rating and the copies on offer, the plot, then Watch (or
 * Resume), one button per other copy (FHD, HD…), favourite and add-on sources. Under it, cast and
 * director and "more like this". Opening it enriches the row from the provider (backdrop, cast,
 * genre) if those fields are still bare.
 */
@Composable
fun MovieDetailScreen(
    movieId: Long,
    onPlay: (Movie) -> Unit,
    onPlayUrl: (key: String, url: String, title: String) -> Unit,
    onOpenMovie: (Movie) -> Unit,
    onOpenPerson: (String) -> Unit,
    viewModel: VodViewModel = viewModel(),
) {
    var movie by remember(movieId) { mutableStateOf<Movie?>(null) }
    var moreLike by remember(movieId) { mutableStateOf<List<Movie>>(emptyList()) }
    var resumeExists by remember(movieId) { mutableStateOf(false) }
    var variants by remember(movieId) { mutableStateOf<List<MovieVariant>>(emptyList()) }
    var logo by remember(movieId) { mutableStateOf<String?>(null) }
    val playFocus = remember { FocusRequester() }
    val hasAddons by viewModel.hasAddons.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // null = the add-on picker is closed; a (possibly empty) list = show it. Separate flag for the spinner.
    var addonStreams by remember(movieId) { mutableStateOf<List<StremioStream>?>(null) }
    var addonLoading by remember(movieId) { mutableStateOf(false) }

    LaunchedEffect(movieId) {
        val loaded = viewModel.movieDetail(movieId)
        movie = loaded
        if (loaded != null) {
            resumeExists = viewModel.resumePosition("movie:${loaded.id}")?.let { !it.isFinished } == true
            variants = viewModel.movieVariants(loaded)
            logo = viewModel.titleLogo(isMovie = true, tmdbId = loaded.tmdbId)
            moreLike = viewModel.moreLikeThis(loaded)
        }
    }

    val m = movie ?: run { LoadingDetail(); return }

    // Land focus on the primary action once the page is up, so a d-pad has somewhere to be.
    LaunchedEffect(m.id) {
        delay(60)
        runCatching { playFocus.requestFocus() }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "header") {
            DetailHero(
                kicker = stringResource(R.string.vod_kicker_movie),
                title = m.displayTitle,
                logoUrl = logo,
                backdropUrl = m.backdropUrl,
                posterUrl = m.posterUrl,
                meta = listOfNotNull(
                    m.year?.toString(),
                    genreLine(m.genre),
                    m.durationSeconds?.takeIf { it > 0 }?.let { formatRuntime(it) },
                ),
                rating = m.rating,
                qualities = variants.map { it.qualityLabel }.filter { it.isNotBlank() }.distinct(),
                overview = m.plot,
            ) {
                HeroButton(
                    text = "▶  " + stringResource(if (resumeExists) R.string.vod_resume else R.string.vod_watch_now),
                    onClick = { onPlay(m) },
                    modifier = Modifier.focusRequester(playFocus),
                )
                variants.filter { it.movie.id != m.id && it.qualityLabel.isNotBlank() }.forEach { v ->
                    HeroButton(text = v.qualityLabel, onClick = { onPlay(v.movie) })
                }
                HeroButton(
                    text = (if (m.favourite) "★  " else "☆  ") +
                        stringResource(if (m.favourite) R.string.common_remove_favourite else R.string.common_favourite),
                    onClick = {
                        viewModel.toggleMovieFavourite(m)
                        movie = m.copy(favourite = !m.favourite)
                    },
                )
                // Only when the user has add-ons set up — queries them for this film's streams.
                if (hasAddons) {
                    HeroButton(
                        text = stringResource(R.string.vod_addon_sources),
                        onClick = {
                            addonLoading = true
                            scope.launch {
                                addonStreams = viewModel.addonStreams(m)
                                addonLoading = false
                            }
                        },
                    )
                }
            }
        }
        item(key = "info") {
            DetailInfo(plot = null, cast = m.cast, director = m.director, genre = null, onOpenPerson = onOpenPerson)
        }
        if (moreLike.isNotEmpty()) {
            item(key = "more") {
                MoviePosterRow(stringResource(R.string.vod_more_like_this), moreLike, onOpenMovie)
            }
        }
    }

    if (addonLoading || addonStreams != null) {
        AddonStreamPicker(
            loading = addonLoading,
            streams = addonStreams.orEmpty(),
            onPick = { stream ->
                addonStreams = null
                onPlayUrl("movie:${m.id}", stream.url, m.displayTitle)
            },
            onDismiss = { addonStreams = null; addonLoading = false },
        )
    }
}

/**
 * The picker shown when the user taps "Add-on sources" on a film: a spinner while streams are
 * fetched, then the list each add-on returned (already filtered to directly-playable URLs). Picking
 * one plays it through the normal VOD player.
 */
@Composable
private fun AddonStreamPicker(
    loading: Boolean,
    streams: List<StremioStream>,
    onPick: (StremioStream) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(560.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp),
        ) {
            Text(
                stringResource(R.string.vod_addon_pick_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(12.dp))
            when {
                loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.height(22.dp).width(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.vod_addon_loading), style = MaterialTheme.typography.bodyMedium)
                }
                streams.isEmpty() -> Text(
                    stringResource(R.string.vod_addon_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.height(360.dp),
                ) {
                    items(streams, key = { it.url }) { stream -> AddonStreamRow(stream, onPick) }
                }
            }
        }
    }
}

@Composable
private fun AddonStreamRow(stream: StremioStream, onPick: (StremioStream) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusLift(focused)
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(interactionSource = null, indication = null) { onPick(stream) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stream.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stream.addonName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** A tab on a show's page: one season, the cast, or similar shows. */
private sealed interface ShowTab {
    data class Season(val number: Int) : ShowTab
    data object Cast : ShowTab
    data object Similar : ShowTab
}

/**
 * A show's page: the same hero as a film's, with Resume (or Watch) on the episode to go on with,
 * then tabs — one per season, Cast, Similar. A season is a row of episode cards: the still, a bar
 * of how far you got, "✓ Seen" or the minutes left. It opens on the season of the episode to go on
 * with. Episodes are fetched on demand — pulling every episode of every series up front is what
 * makes a first sync take twenty minutes on a large provider.
 */
@Composable
fun SeriesDetailScreen(
    seriesId: Long,
    onPlayEpisode: (mediaKey: String, url: String, title: String) -> Unit,
    onOpenSeries: (Series) -> Unit,
    onOpenPerson: (String) -> Unit,
    viewModel: VodViewModel = viewModel(),
) {
    var series by remember(seriesId) { mutableStateOf<Series?>(null) }
    var moreLike by remember(seriesId) { mutableStateOf<List<Series>>(emptyList()) }
    var logo by remember(seriesId) { mutableStateOf<String?>(null) }
    val favFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }

    LaunchedEffect(seriesId) {
        val loaded = viewModel.seriesDetail(seriesId)
        series = loaded
        if (loaded != null) {
            viewModel.loadEpisodes(loaded)
            logo = viewModel.titleLogo(isMovie = false, tmdbId = loaded.tmdbId)
            moreLike = viewModel.moreLikeThisSeries(loaded)
        }
    }

    val s = series
    val episodes by (s?.let { viewModel.episodes(it) } ?: viewModel.noEpisodes)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val positions by produceState(emptyMap<String, PlaybackPosition>(), episodes) {
        value = if (episodes.isEmpty()) emptyMap() else viewModel.positions(episodes.map { "ep:${it.id}" })
    }

    if (s == null) { LoadingDetail(); return }

    val seasons = remember(episodes) {
        episodes.groupBy { it.season }
            .mapValues { (_, eps) -> eps.sortedBy { it.episodeNumber } }
            .toSortedMap()
    }
    // The episode to go on with: the last one played if unfinished, else the one after it, else the first.
    val next: Pair<Episode, PlaybackPosition?>? = remember(seasons, positions) {
        val ordered = seasons.values.flatten()
        val last = ordered.mapNotNull { e -> positions["ep:${e.id}"]?.let { e to it } }
            .maxByOrNull { it.second.updatedAtMillis }
        when {
            last == null -> ordered.firstOrNull()?.let { it to null }
            !last.second.isFinished -> last
            else -> (ordered.getOrNull(ordered.indexOf(last.first) + 1) ?: last.first) to null
        }
    }

    // Focus Resume/Watch once there's an episode to go on with, the favourite until then.
    val hasNext = next != null
    LaunchedEffect(s.id, hasNext) {
        delay(60)
        runCatching { (if (hasNext) playFocus else favFocus).requestFocus() }
    }

    val castList = remember(s.cast) { splitNames(s.cast, ',') }
    val tabs = buildList {
        seasons.keys.forEach { add(ShowTab.Season(it)) }
        if (castList.isNotEmpty()) add(ShowTab.Cast)
        if (moreLike.isNotEmpty()) add(ShowTab.Similar)
    }
    val labels = tabs.map {
        when (it) {
            is ShowTab.Season -> stringResource(R.string.vod_season, it.number)
            ShowTab.Cast -> stringResource(R.string.vod_cast)
            ShowTab.Similar -> stringResource(R.string.vod_similar)
        }
    }
    var picked by remember(seriesId) { mutableStateOf<ShowTab?>(null) }
    val tab = picked?.takeIf { it in tabs }
        ?: next?.first?.season?.let { ShowTab.Season(it) }?.takeIf { it in tabs }
        ?: tabs.firstOrNull()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "header") {
            DetailHero(
                kicker = stringResource(R.string.vod_kicker_series),
                title = s.displayTitle,
                logoUrl = logo,
                backdropUrl = s.backdropUrl,
                posterUrl = s.posterUrl,
                meta = listOfNotNull(
                    s.year?.toString(),
                    genreLine(s.genre),
                    seasons.size.takeIf { it > 0 }?.let {
                        if (it == 1) stringResource(R.string.vod_one_season) else stringResource(R.string.vod_seasons, it)
                    },
                ),
                rating = s.rating,
                qualities = emptyList(),
                overview = s.plot,
            ) {
                next?.let { (ep, pos) ->
                    val left = pos?.let { ((it.durationMillis - it.positionMillis) / 60_000).toInt().coerceAtLeast(1) }
                    HeroButton(
                        text = "▶  " + if (left != null) {
                            stringResource(R.string.vod_resume_episode, ep.season, ep.episodeNumber, left)
                        } else {
                            stringResource(R.string.vod_watch_episode, ep.season, ep.episodeNumber)
                        },
                        onClick = { onPlayEpisode("ep:${ep.id}", ep.streamUrl, episodePlayerTitle(ep)) },
                        modifier = Modifier.focusRequester(playFocus),
                    )
                }
                HeroButton(
                    text = (if (s.favourite) "★  " else "☆  ") +
                        stringResource(if (s.favourite) R.string.common_remove_favourite else R.string.common_favourite),
                    onClick = {
                        viewModel.toggleSeriesFavourite(s)
                        series = s.copy(favourite = !s.favourite)
                    },
                    modifier = Modifier.focusRequester(favFocus),
                )
            }
        }
        if (tabs.isNotEmpty()) item(key = "tabs") {
            VodTabs(labels, tabs.indexOf(tab), { picked = tabs[it] }, Modifier.padding(top = 4.dp))
        }
        when (tab) {
            is ShowTab.Season -> {
                val eps = seasons[tab.number].orEmpty()
                item(key = "season:${tab.number}") {
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        SectionHeader(stringResource(R.string.vod_season_episodes, tab.number, eps.size))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(eps, key = { it.id }) { ep ->
                                EpisodeCard(ep, s.backdropUrl, positions["ep:${ep.id}"]) {
                                    onPlayEpisode("ep:${ep.id}", ep.streamUrl, episodePlayerTitle(ep))
                                }
                            }
                        }
                    }
                }
            }
            ShowTab.Cast -> item(key = "cast") {
                DetailInfo(plot = null, cast = s.cast, director = null, genre = null, onOpenPerson = onOpenPerson)
            }
            ShowTab.Similar -> item(key = "more") {
                SeriesPosterRow(stringResource(R.string.vod_more_like_this), moreLike, onOpenSeries)
            }
            null -> item(key = "eploading") {
                Text(
                    stringResource(R.string.vod_loading_episodes),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/**
 * One episode as a landscape card: its still (the show's backdrop when it has none), a teal bar of
 * how far it was watched, "2. Chapter 2" and "44 min · 31 min left" or "✓ Seen".
 */
@Composable
private fun EpisodeCard(ep: Episode, fallbackArt: String?, position: PlaybackPosition?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val minutes = ep.durationSeconds?.takeIf { it > 0 }?.div(60)
        ?: position?.durationMillis?.takeIf { it > 0 }?.div(60_000)?.toInt()
    val progress = position?.let {
        if (it.isFinished) 1f else if (it.durationMillis > 0) (it.positionMillis.toFloat() / it.durationMillis).coerceIn(0f, 1f) else null
    }
    Column(
        Modifier
            .width(EPISODE_WIDTH)
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
            val art = remember(ep.stillUrl, fallbackArt) { listOfNotNull(ep.stillUrl, fallbackArt).distinct() }
            var artIndex by remember(art) { mutableStateOf(0) }
            art.getOrNull(artIndex)?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = { if (artIndex < art.lastIndex) artIndex++ },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            progress?.let {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.Black.copy(alpha = 0.5f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(it).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${ep.episodeNumber}. " + (episodeName(ep) ?: stringResource(R.string.vod_episode_n, ep.episodeNumber)),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val parts = listOfNotNull(
            minutes?.let { stringResource(R.string.vod_minutes, it) },
            when {
                position == null -> null
                position.isFinished -> stringResource(R.string.vod_ep_watched)
                position.durationMillis > 0 -> stringResource(
                    R.string.vod_ep_left,
                    ((position.durationMillis - position.positionMillis) / 60_000).toInt().coerceAtLeast(1),
                )
                else -> null
            },
        )
        if (parts.isNotEmpty()) {
            Text(
                parts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** Provider episode titles repeat the show and the code ("Bleach (2004) - S01E03 - Title"): keep the title. */
private val EPISODE_PREFIX = Regex("""^.*?\bS\d{1,3}\s*E\d{1,4}\b[\s\-–:·.]*""", RegexOption.IGNORE_CASE)

private fun episodeName(ep: Episode): String? = EPISODE_PREFIX.replace(ep.title, "").trim().takeIf { it.isNotEmpty() }

private fun episodePlayerTitle(ep: Episode): String = "S${ep.season}E${ep.episodeNumber} · ${episodeName(ep) ?: ep.title}"

/** The first two of a provider genre string ("Crime, Dramma"), or null. */
private fun genreLine(raw: String?): String? =
    splitNames(raw, ',', '|', '/').take(2).joinToString(", ").takeIf { it.isNotEmpty() }

private val EPISODE_WIDTH = 220.dp

// ---- Shared detail pieces ----------------------------------------------------------------------

/**
 * The body under the hero: a genre chip row, the synopsis, and Plex-style **clickable** Cast and
 * Director rows. Tapping a person opens the Person screen ([onOpenPerson]) — everything else in the
 * library featuring them. Each block appears only when the provider gave us something for it.
 *
 * Rows bleed to the screen edge (their own 24dp content padding) rather than sitting inside a padded
 * Column, so a long cast list scrolls cleanly to the edge on a d-pad instead of stopping short.
 */
@Composable
private fun DetailInfo(
    plot: String?,
    cast: String?,
    director: String?,
    genre: String?,
    onOpenPerson: (String) -> Unit,
) {
    val genres = remember(genre) { splitNames(genre, ',', '|', '/') }
    val castList = remember(cast) { splitNames(cast, ',') }
    val directors = remember(director) { splitNames(director, ',') }

    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        if (genres.isNotEmpty()) {
            ChipRow(genres.size, key = { genres[it] }) { GenreChip(genres[it]) }
            Spacer(Modifier.height(16.dp))
        }
        plot?.takeIf { it.isNotBlank() }?.let { LabeledBlock(stringResource(R.string.vod_synopsis), it) }
        if (castList.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            SectionLabel(stringResource(R.string.vod_cast))
            Spacer(Modifier.height(8.dp))
            ChipRow(castList.size, key = { castList[it] }) {
                PersonChip(castList[it]) { onOpenPerson(castList[it]) }
            }
        }
        if (directors.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            SectionLabel(stringResource(R.string.vod_director))
            Spacer(Modifier.height(8.dp))
            ChipRow(directors.size, key = { directors[it] }) {
                PersonChip(directors[it]) { onOpenPerson(directors[it]) }
            }
        }
    }
}

/** Splits a provider multi-value string (comma/pipe/slash) into trimmed, de-duplicated, non-blank names. */
private fun splitNames(raw: String?, vararg delimiters: Char): List<String> =
    raw?.split(*delimiters)
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.distinct()
        .orEmpty()

/** A section label ("Cast", "Director") aligned with the page's 24dp gutter. */
@Composable
private fun SectionLabel(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
}

@Composable
private fun LabeledBlock(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A horizontal, edge-bleeding shelf of chips — genres or people. */
@Composable
private fun ChipRow(count: Int, key: (Int) -> Any, chip: @Composable (Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(count, key = key) { chip(it) }
    }
}

/** A static genre pill. */
@Composable
private fun GenreChip(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

/**
 * A focusable, clickable name chip — a cast member or the director. Focus mirrors the app's cue
 * (primary fill + border), and it is padded to a comfortable d-pad target. Clicking opens the
 * Person screen for that name.
 */
@Composable
private fun PersonChip(name: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val container = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val content = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Text(
        name,
        style = MaterialTheme.typography.titleSmall,
        color = content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .focusLift(focused)
            .clip(RoundedCornerShape(22.dp))
            .background(container)
            .then(
                if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(22.dp))
                else Modifier,
            )
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun LoadingDetail() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.vod_detail_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Seconds to a compact "1h 52m" / "45m". */
private fun formatRuntime(seconds: Int): String {
    val totalMinutes = seconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
