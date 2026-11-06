/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.vod

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filter
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentv.R
import app.opentv.data.model.Movie
import app.opentv.data.model.Series
import app.opentv.data.parser.displayTitle
import app.opentv.data.repo.ChartHit
import app.opentv.data.repo.MovieHit
import app.opentv.data.repo.SeriesHit
import app.opentv.ui.CARD_GAP
import app.opentv.ui.WholeCards
import app.opentv.ui.wholeCardsWindow
import app.opentv.ui.exactCardWidth
import coil.compose.AsyncImage

/**
 * One turn of the VOD hero, whatever fed it: a TMDB chart hit or, with no TMDB key, a title the
 * provider added lately. [rank] is its place in the week's chart, null when it isn't from one.
 * Exactly one of [movie] and [series] is set.
 */
@androidx.compose.runtime.Immutable
data class HeroSlide(
    val key: Long,
    val title: String,
    val logoUrl: String?,
    val backdropUrl: String?,
    val year: Int?,
    val genres: String,
    val runtimeMinutes: Int?,
    val rating: Double?,
    val qualities: List<String>,
    val overview: String?,
    val rank: Int?,
    val movie: Movie? = null,
    val series: Series? = null,
    /** Shown in place of a missing backdrop, whole, at the right — provider films often have none. */
    val posterUrl: String? = null,
)

/**
 * The top of the Movies and Shows pages, the live page's prime-time banner made for VOD: the backdrop
 * over the right melting into the page, the title's own lettering (or its name in Oswald), year · genres ·
 * running time · rating · the copies on offer, two lines of plot, then Watch and Details.
 *
 * Turns over every few seconds by itself. RIGHT from Details steps to the next title, LEFT from
 * Watch to the previous one (from the first, LEFT goes on to the menu as anywhere else).
 */
@Composable
internal fun VodHero(
    slides: List<HeroSlide>,
    kicker: String,
    onWatch: (HeroSlide) -> Unit,
    onDetails: (HeroSlide) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (slides.isEmpty()) return
    var index by remember { mutableIntStateOf(0) }
    if (index >= slides.size) index = 0
    val slide = slides[index]

    val turn = remember { Animatable(0f) }
    LaunchedEffect(index, slides.size) {
        turn.snapTo(0f)
        if (slides.size < 2) return@LaunchedEffect
        turn.animateTo(1f, tween(TURN_MILLIS, easing = LinearEasing))
        index = (index + 1) % slides.size
    }

    Box(modifier.fillMaxWidth().height(HERO_HEIGHT).clipToBounds()) {
        HeroBackdrop(slide, Modifier.align(Alignment.TopEnd))
        Column(Modifier.padding(start = 32.dp, top = 22.dp, end = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                slide.rank?.let {
                    Text(
                        stringResource(R.string.vod_hero_rank, it).uppercase(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = AccentDark,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Accent).padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
                Text(
                    kicker.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(10.dp))
            Crossfade(slide, animationSpec = tween(500), label = "heroTitle") { shown ->
                TitleArt(shown.title.replace(TRAILING_YEAR, "").ifBlank { shown.title }, shown.logoUrl, LOGO_HEIGHT, 320.dp)
            }
            Spacer(Modifier.height(8.dp))
            HeroMeta(
                listOfNotNull(
                    slide.year?.toString(),
                    slide.genres.takeIf { it.isNotBlank() },
                    slide.runtimeMinutes?.takeIf { it > 0 }?.let { "${it / 60}h ${"%02d".format(it % 60)}" },
                ),
                slide.rating,
                slide.qualities,
            )
            slide.overview?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.5f),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroButton(
                    text = "▶  " + stringResource(R.string.vod_hero_watch),
                    onClick = { onWatch(slide) },
                    onLeft = { if (index > 0) { index--; true } else false },
                    onRight = { false },
                )
                HeroButton(
                    text = stringResource(R.string.vod_hero_details),
                    onClick = { onDetails(slide) },
                    onLeft = { false },
                    onRight = { index = (index + 1) % slides.size; true },
                )
            }
            Spacer(Modifier.height(12.dp))
            HeroDots(slides.size, index, turn.value)
        }
    }
}

/** The title's own lettering from TMDB, or its name in Oswald capitals when there is none. */
@Composable
private fun TitleArt(title: String, logoUrl: String?, height: androidx.compose.ui.unit.Dp, maxWidth: androidx.compose.ui.unit.Dp) {
    Box(Modifier.height(height), contentAlignment = Alignment.CenterStart) {
        var logoFailed by remember(logoUrl) { mutableStateOf(false) }
        if (logoUrl != null && !logoFailed) {
            AsyncImage(
                model = logoUrl,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                onError = { logoFailed = true },
                modifier = Modifier.heightIn(max = height).widthIn(max = maxWidth),
            )
        } else {
            Text(
                title.uppercase(),
                fontFamily = Oswald,
                fontWeight = FontWeight.Bold,
                fontSize = 42.sp,
                lineHeight = 46.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.62f),
            )
        }
    }
}

/** "2026 · Horror, Thriller · 1h 47 · ★ 7.3" and the copies on offer, boxed: 4K FHD HD. */
@Composable
private fun HeroMeta(parts: List<String>, rating: Double?, qualities: List<String>) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            parts.joinToString("  ·  "),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.88f),
            maxLines = 1,
        )
        rating?.takeIf { it > 0.0 && it < 10.0 }?.let {
            if (parts.isNotEmpty()) Text("·", color = Color.White.copy(alpha = 0.5f))
            Text("★ ${formatRating(it)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Gold)
        }
        qualities.take(3).forEach {
            Text(
                it,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 0.dp),
            )
        }
    }
}

/**
 * The top of a film's or a show's page, the home hero standing still: the backdrop over the right
 * melting into the page (the poster when the backdrop won't load), a kicker, the title's lettering,
 * year · genres · … · rating and the copies on offer, three lines of plot, then [actions].
 */
@Composable
internal fun DetailHero(
    kicker: String,
    title: String,
    logoUrl: String?,
    backdropUrl: String?,
    posterUrl: String?,
    meta: List<String>,
    rating: Double?,
    qualities: List<String>,
    overview: String?,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val page = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxWidth().height(DETAIL_HEIGHT).clipToBounds()) {
        val art = remember(backdropUrl, posterUrl) { listOfNotNull(backdropUrl, posterUrl).distinct() }
        var artIndex by remember(art) { mutableIntStateOf(0) }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .aspectRatio(16f / 9f)
                .drawWithContent {
                    drawContent()
                    drawRect(Brush.horizontalGradient(0f to page, 0.3f to page.copy(alpha = 0.55f), 0.65f to Color.Transparent))
                    drawRect(Brush.verticalGradient(0.55f to Color.Transparent, 1f to page))
                },
        ) {
            art.getOrNull(artIndex)?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = { if (artIndex < art.lastIndex) artIndex++ },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 32.dp, end = 32.dp, bottom = 12.dp)) {
            Text(
                kicker.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
            )
            Spacer(Modifier.height(8.dp))
            // The year is on the meta line; provider names often carry it too ("Akka (2026)").
            TitleArt(title.replace(TRAILING_YEAR, "").ifBlank { title }, logoUrl, DETAIL_LOGO_HEIGHT, 380.dp)
            Spacer(Modifier.height(8.dp))
            HeroMeta(meta, rating, qualities)
            overview?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.55f),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

@Composable
private fun HeroBackdrop(slide: HeroSlide, modifier: Modifier) {
    val page = MaterialTheme.colorScheme.background
    Crossfade(slide, animationSpec = tween(600), label = "heroBackdrop", modifier = modifier.fillMaxHeight().aspectRatio(16f / 9f)) { shown ->
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    drawRect(Brush.horizontalGradient(0f to page, 0.3f to page.copy(alpha = 0.55f), 0.65f to Color.Transparent))
                    drawRect(Brush.verticalGradient(0.55f to Color.Transparent, 1f to page))
                },
        ) {
            when {
                shown.backdropUrl != null -> AsyncImage(
                    model = shown.backdropUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                shown.posterUrl != null -> AsyncImage(
                    model = shown.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterEnd,
                    modifier = Modifier.fillMaxSize().padding(end = 48.dp, top = 16.dp, bottom = 16.dp),
                )
            }
        }
    }
}

/** The football/live page button: translucent grey, white with black text in focus. */
@Composable
internal fun HeroButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLeft: () -> Boolean = { false },
    onRight: () -> Boolean = { false },
) {
    var focused by remember { mutableStateOf(false) }
    val scale = if (focused) 1.05f else 1f
    Text(
        text,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = if (focused) Color.Black else Color.White,
        maxLines = 1,
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color.White else Color(0x736E7672))
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> onLeft()
                    Key.DirectionRight -> onRight()
                    else -> false
                }
            }
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
    )
}

@Composable
private fun HeroDots(count: Int, current: Int, turned: Float) {
    if (count < 2) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val dim = Color.White.copy(alpha = 0.28f)
            if (i == current) {
                Box(Modifier.width(28.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(dim)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(turned.coerceIn(0.05f, 1f)).clip(RoundedCornerShape(3.dp)).background(Color.White))
                }
            } else {
                Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(dim))
            }
        }
    }
}

/**
 * The genre tabs under the hero, in the live page's type: Oswald capitals, the open one bold with a
 * teal bar under it. OK opens a tab.
 */
@Composable
internal fun VodTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { i, label ->
            var focused by remember { mutableStateOf(false) }
            val isSelected = i == selected
            Column(
                Modifier
                    .onFocusChanged { focused = it.isFocused }
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (focused) TabFocused else Color.Transparent)
                    .clickable(interactionSource = null, indication = null) { onSelect(i) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label.uppercase(),
                    color = if (focused || isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = OswaldTabs,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 18.sp,
                    letterSpacing = 1.5.sp,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Box(Modifier.width(28.dp).height(2.dp).background(if (isSelected) Accent else Color.Transparent))
            }
        }
    }
}

/** "Top 10": big outlined numerals, each with its poster leaning on it, five to a screen. */
@Composable
internal fun <T : ChartHit> TopTenRow(title: String, hits: List<T>, onOpen: (T) -> Unit) {
    if (hits.isEmpty()) return
    val sizes = LocalVodRowSizes.current
    val posterWidth = sizes.topTen * TOP_TEN_POSTER_SHARE
    // The art inside the card, under its 4dp inset: what the numeral stands as tall as.
    val artHeight = (posterWidth - 8.dp) * 1.5f
    val numeralSize = with(LocalDensity.current) { (artHeight * 0.82f).toSp() }
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(title)
        VodRow(sizes.topTen, posterWidth / 2) {
            itemsIndexed(hits, key = { _, it -> it.key }) { i, hit ->
                Box(Modifier.width(sizes.topTen)) {
                    Text(
                        "${i + 1}",
                        fontFamily = Oswald,
                        fontWeight = FontWeight.Bold,
                        fontSize = numeralSize,
                        lineHeight = numeralSize,
                        letterSpacing = (-6).sp,
                        color = Color(0xFF6E7672),
                        style = TextStyle(drawStyle = Stroke(width = 4f)),
                        softWrap = false,
                        maxLines = 1,
                        modifier = Modifier.requiredHeight(artHeight + 8.dp).align(Alignment.TopStart),
                    )
                    Box(Modifier.align(Alignment.TopEnd)) { HitPoster(hit, onOpen, posterWidth) }
                }
            }
        }
    }
}

/** A titled shelf of chart hits. */
@Composable
internal fun <T : ChartHit> HitRow(title: String, hits: List<T>, onOpen: (T) -> Unit) {
    if (hits.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(title)
        VodRow(LocalVodRowSizes.current.poster) {
            itemsIndexed(hits, key = { _, it -> it.key }) { _, hit -> HitPoster(hit, onOpen) }
        }
    }
}

/** A genre tab's titles as rows of posters, so the whole tab scrolls as one page under the hero. */
@Composable
internal fun <T : ChartHit> HitGridRow(hits: List<T>, onOpen: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = ROW_MARGIN, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(CARD_GAP),
    ) {
        hits.forEach { HitPoster(it, onOpen) }
    }
}

@Composable
private fun <T : ChartHit> HitPoster(hit: T, onOpen: (T) -> Unit, width: Dp = LocalVodRowSizes.current.poster) {
    PosterCard(
        title = hit.tmdb.title,
        posterUrl = hit.posterUrl,
        subtitle = hit.tmdb.year?.toString(),
        rating = hit.tmdb.rating,
        qualityBadge = hit.badge,
        onClick = { onOpen(hit) },
        modifier = Modifier.width(width),
    )
}

/**
 * A shelf of whole cards: the page's margin on both sides, nothing peeking past it, and moving past
 * the last card on screen shifts the shelf by exactly one card.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun VodRow(itemWidth: Dp, centreToEnd: Dp = itemWidth / 2, content: LazyListScope.() -> Unit) {
    val density = LocalDensity.current
    val spec = remember(density, itemWidth, centreToEnd) {
        with(density) { WholeCards(ROW_MARGIN.toPx(), itemWidth.toPx(), centreToEnd.toPx()) }
    }
    // Whatever the focus scroll lands on, a row at rest starts with a whole card at its margin:
    // nudge it onto the nearest one when it stops a few pixels off.
    val state = rememberLazyListState()
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.filter { !it }.collect {
            val nearest = state.layoutInfo.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset) } ?: return@collect
            val off = nearest.offset
            if (off != 0 && kotlin.math.abs(off) < nearest.size / 2) state.animateScrollBy(off.toFloat())
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
        LazyRow(
            state = state,
            modifier = Modifier.wholeCardsWindow(ROW_MARGIN),
            contentPadding = PaddingValues(horizontal = ROW_MARGIN, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(CARD_GAP),
            content = content,
        )
    }
}

/**
 * How wide the cards of the VOD rows are on this page, so a whole number of them fills the screen
 * with the same margin left and right: posters seven to a row, Continue Watching four, the Top 10
 * five (numeral and poster together).
 */
@androidx.compose.runtime.Immutable
internal data class VodRowSizes(val poster: Dp, val resume: Dp, val topTen: Dp)

internal fun vodRowSizes(pageWidth: Dp) = VodRowSizes(
    poster = exactCardWidth(pageWidth, POSTERS_PER_ROW, ROW_MARGIN),
    resume = exactCardWidth(pageWidth, RESUME_PER_ROW, ROW_MARGIN),
    topTen = exactCardWidth(pageWidth, TOP_TEN_PER_ROW, ROW_MARGIN),
)

/** Outside a page that measured itself (a detail page's "more like this"): a 1280dp-wide page's. */
internal val LocalVodRowSizes = staticCompositionLocalOf { vodRowSizes(1280.dp - 80.dp) }

internal const val POSTERS_PER_ROW = 7
private const val RESUME_PER_ROW = 4
private const val TOP_TEN_PER_ROW = 5
private const val TOP_TEN_POSTER_SHARE = 0.7f
internal val ROW_MARGIN = 16.dp

/** A slide for a hit off the week's chart. */
internal fun MovieHit.toSlide(rank: Int?, logoUrl: String?, genres: String): HeroSlide = HeroSlide(
    key = movie.id,
    title = tmdb.title,
    logoUrl = logoUrl,
    backdropUrl = backdropUrl,
    year = tmdb.year ?: movie.year,
    genres = genres,
    runtimeMinutes = movie.durationSeconds?.let { it / 60 },
    rating = tmdb.rating,
    qualities = qualities,
    overview = tmdb.overview ?: movie.plot,
    rank = rank,
    movie = movie,
)

/** A slide for a show off the week's TV chart. Shows carry no running time. */
internal fun SeriesHit.toSlide(rank: Int?, logoUrl: String?, genres: String): HeroSlide = HeroSlide(
    key = series.id,
    title = tmdb.title,
    logoUrl = logoUrl,
    backdropUrl = backdropUrl,
    year = tmdb.year ?: series.year,
    genres = genres,
    runtimeMinutes = null,
    rating = tmdb.rating,
    qualities = emptyList(),
    overview = tmdb.overview ?: series.plot,
    rank = rank,
    series = series,
)

/** A slide for a film straight from the catalogue — the hero with no TMDB key. */
internal fun Movie.toSlide(): HeroSlide = HeroSlide(
    key = id,
    title = displayTitle,
    logoUrl = null,
    backdropUrl = backdropUrl,
    year = year ?: TRAILING_YEAR.find(displayTitle)?.groupValues?.get(0)?.filter { it.isDigit() }?.toIntOrNull(),
    genres = genre.orEmpty().split(',', '|', '/').map { it.trim() }.filter { it.isNotEmpty() }.take(2).joinToString(", "),
    runtimeMinutes = durationSeconds?.let { it / 60 },
    rating = rating?.takeIf { it in 0.1..9.9 },
    qualities = emptyList(),
    overview = plot,
    rank = null,
    movie = this,
    posterUrl = posterUrl,
)

/** A slide for a show straight from the catalogue — the Shows hero with no TMDB key. */
internal fun Series.toSlide(): HeroSlide = HeroSlide(
    key = id,
    title = displayTitle,
    logoUrl = null,
    backdropUrl = backdropUrl,
    year = year,
    genres = genre.orEmpty().split(',', '|', '/').map { it.trim() }.filter { it.isNotEmpty() }.take(2).joinToString(", "),
    runtimeMinutes = null,
    rating = rating?.takeIf { it in 0.1..9.9 },
    qualities = emptyList(),
    overview = plot,
    rank = null,
    series = this,
    posterUrl = posterUrl,
)

private val HERO_HEIGHT = 280.dp
private val LOGO_HEIGHT = 64.dp
private val DETAIL_HEIGHT = 312.dp
private val DETAIL_LOGO_HEIGHT = 80.dp
private val TRAILING_YEAR = Regex("""\s*\((19|20)\d{2}\)\s*$""")
private const val TURN_MILLIS = 8_000
private val Accent = Color(0xFF57D9C5)
private val AccentDark = Color(0xFF062320)
private val Gold = Color(0xFFF5C518)
private val TabFocused = Color(0xFF2A2F2D)
private val Oswald = FontFamily(Font(R.font.oswald_bold, FontWeight.Bold))
private val OswaldTabs = FontFamily(
    Font(R.font.oswald_regular, FontWeight.Normal),
    Font(R.font.oswald_bold, FontWeight.Bold),
)
