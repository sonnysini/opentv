/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.football

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.background
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.core.FootballRequests
import app.opentv.R
import app.opentv.data.model.Channel
import app.opentv.data.model.shownName
import app.opentv.data.repo.FootballRepository.Match
import app.opentv.data.repo.FootballRepository.MatchChannel
import app.opentv.data.repo.FootballRepository.Phase
import app.opentv.data.repo.FootballRepository.Team
import app.opentv.ui.theme.focusLift
import coil.compose.AsyncImage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val LIVE_RED = Color(0xFFFF6B6B)

/** YouTube on Fire TV, then on Google TV / Android TV. */
/** How long a launcher's match request waits for the match to show up on the page. */
private const val REQUEST_WAIT_MILLIS = 20_000L

private val YOUTUBE_APPS = listOf("com.amazon.firetv.youtube", "com.google.android.youtube.tv")

/**
 * The football home, streaming-app style: the match on now (or next) over the whole top of the
 * page, its best feed's picture behind, "Watch now" already focused — OK and it plays on the best
 * channel of the user's own lists — then rows of 16:9 tiles: the other live games, today's
 * (finished ones play their YouTube highlights), each coming day's, the last round.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FootballScreen(
    onPlayChannel: (Channel) -> Unit,
    /** Plays a video in our own player: media key, stream URL, title. */
    onPlayVideo: (key: String, url: String, title: String) -> Unit,
    viewModel: FootballViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    val opening by viewModel.opening.collectAsState()
    val scan by viewModel.scan.collectAsState()
    val autoPlay by viewModel.autoPlay.collectAsState()
    val progress by viewModel.progress.collectAsState()
    // Re-read the feeds' looks whenever one lands.
    val looks by viewModel.looks.collectAsState()
    LaunchedEffect(autoPlay) {
        autoPlay?.let {
            viewModel.autoPlayed()
            onPlayChannel(it)
        }
    }
    // Feeds get looked at only while this screen is up: the player needs the one connection.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.setActive(true)
                Lifecycle.Event.ON_PAUSE -> viewModel.setActive(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) viewModel.setActive(true)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setActive(false)
        }
    }
    BackHandler(enabled = scan != null) { viewModel.cancelScan() }
    // Hold OK on a card: its feeds to pick from by hand.
    var picking by remember { mutableStateOf<FootballViewModel.Item?>(null) }
    BackHandler(enabled = picking != null) { picking = null }
    val context = LocalContext.current
    val noYoutube = stringResource(R.string.football_no_youtube)
    val openInYouTube: (String) -> Unit = { id ->
        // Straight into a YouTube app: on Fire TV a bare link also matches Silk and Music, and
        // Android would ask which one to use.
        val uri = Uri.parse("https://www.youtube.com/watch?v=$id")
        val opened = (YOUTUBE_APPS + null).any { pkg ->
            val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (pkg != null) intent.setPackage(pkg)
            try {
                context.startActivity(intent)
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
        if (!opened) Toast.makeText(context, noYoutube, Toast.LENGTH_LONG).show()
    }
    val highlightsTitle = stringResource(R.string.football_highlights)
    val openHighlights: (FootballViewModel.Item, String) -> Unit = { item, id ->
        val title = "${item.match.home.name} - ${item.match.away.name} · $highlightsTitle"
        viewModel.openHighlights(
            id,
            onStream = { url -> onPlayVideo("yt:$id", url, title) },
            onFallback = { openInYouTube(id) },
        )
    }
    val open: (FootballViewModel.Item) -> Unit = { item ->
        val highlight = item.highlights
        when {
            item.match.phase == Phase.FINISHED && highlight != null -> openHighlights(item, highlight)
            item.match.phase != Phase.FINISHED && viewModel.feedsOf(item).isNotEmpty() -> viewModel.autoWatch(item)
        }
    }
    // A launcher's match card: act on it as on a click here, once the match is on the page (its
    // feeds looked up for Watch). Given up after a while: a match gone from the page plays nothing.
    val request by FootballRequests.request.collectAsState()
    LaunchedEffect(request, ui, looks) {
        val r = request ?: return@LaunchedEffect
        val all = listOfNotNull(ui.hero) + ui.heroes + ui.live + ui.today + ui.upcoming + ui.lastRound
        val item = all.firstOrNull { it.match.id == r.matchId }
        val stale = System.currentTimeMillis() - r.requestedAtMillis > REQUEST_WAIT_MILLIS
        when {
            r.action == FootballRequests.Action.OPEN || r.matchId == 0L -> FootballRequests.consume()
            r.action == FootballRequests.Action.HIGHLIGHTS && (r.highlightsId ?: item?.highlights) != null -> {
                FootballRequests.consume()
                val id = r.highlightsId ?: item!!.highlights!!
                if (item != null) {
                    openHighlights(item, id)
                } else {
                    viewModel.openHighlights(
                        id,
                        onStream = { url -> onPlayVideo("yt:$id", url, highlightsTitle) },
                        onFallback = { openInYouTube(id) },
                    )
                }
            }
            r.action == FootballRequests.Action.WATCH && item != null && viewModel.feedsOf(item).isNotEmpty() -> {
                FootballRequests.consume()
                viewModel.autoWatch(item)
            }
            stale || (!ui.loading && ui.failed) -> FootballRequests.consume()
            // Not there yet: the next load/looks change runs this again; a timer covers a quiet page.
            else -> {
                kotlinx.coroutines.delay(REQUEST_WAIT_MILLIS)
                if (FootballRequests.request.value === r) FootballRequests.consume()
            }
        }
    }
    val hold: (FootballViewModel.Item) -> Unit = { item ->
        if (item.match.phase != Phase.FINISHED && viewModel.feedsOf(item).isNotEmpty()) picking = item
    }
    // The game the top of the page is about: the first live (or next) one, or the tab picked.
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val heroItem = ui.hero?.let { first -> ui.heroes.firstOrNull { it.match.id == selectedId } ?: ui.heroes.firstOrNull() ?: first }
    val heroBest = remember(heroItem, looks) { heroItem?.let { h -> viewModel.sources(h).firstOrNull { it.looked && it.usable } } }
    val backdrop = heroItem?.let { h -> heroBest?.feed?.id?.let { viewModel.frame(h, it) } }
    val rowList = rows(ui)
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    // The backdrop belongs to the top of the page: it fades out as the rows scroll up over it.
    val fade by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 0f
            else 1f - (listState.firstVisibleItemScrollOffset / with(density) { HERO_HEIGHT.toPx() }).coerceIn(0f, 1f)
        }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            ui.failed -> Message(stringResource(R.string.football_unavailable))
            ui.hero == null && ui.today.isEmpty() && ui.upcoming.isEmpty() && ui.lastRound.isEmpty() -> Message(stringResource(R.string.football_none))
            else -> {
                if (heroItem != null) Backdrop(backdrop, heroItem.match, looks, Modifier.alpha(fade))
                // TV's default keeps the focused item a third of the way down, which would cut off
                // the top of the page under "Watch now": up and down scroll only as far as needed.
                // The rows keep the TV default sideways.
                val sideways = LocalBringIntoViewSpec.current
                val justEnough = remember(density) { JustEnough(with(density) { 16.dp.toPx() }) }
                CompositionLocalProvider(LocalBringIntoViewSpec provides justEnough) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (heroItem != null) item(key = "hero") {
                        val scope = rememberCoroutineScope()
                        Hero(
                            modifier = Modifier.onFocusChanged { if (it.hasFocus) scope.launch { listState.animateScrollToItem(0) } },
                            tabs = ui.heroes,
                            item = heroItem,
                            firstId = ui.hero!!.match.id,
                            best = heroBest,
                            viewModel = viewModel,
                            looks = looks,
                            progress = progress,
                            onSelect = { selectedId = it },
                            onPick = { picking = heroItem },
                        )
                    }
                    rowList.forEach { (title, items) ->
                        item(key = "row:$title") {
                            CompositionLocalProvider(LocalBringIntoViewSpec provides sideways) {
                                MatchRow(title, items, viewModel, progress, looks, open, hold)
                            }
                        }
                    }
                }
                }
            }
        }
        scan?.let { ScanOverlay(it) }
        picking?.let { item ->
            SourcePicker(item, viewModel, looks) { feed ->
                picking = null
                viewModel.play(item, feed)
            }
        }
        // Reading the stream off YouTube takes a few seconds on a slow box: say so.
        if (opening != null) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.football_highlights_loading), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** Scrolls just enough to show the focused item whole, as on a phone, with [margin] to spare. */
@OptIn(ExperimentalFoundationApi::class)
private class JustEnough(private val margin: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val start = offset - margin
        val end = offset + size + margin
        return when {
            start >= 0f && end <= containerSize -> 0f
            start < 0f && end > containerSize -> 0f
            kotlin.math.abs(start) < kotlin.math.abs(end - containerSize) -> start
            else -> end - containerSize
        }
    }
}

/** The rows under the top of the page: on now, today, each coming day, the last round. */
@Composable
private fun rows(ui: FootballViewModel.UiState): List<Pair<String, List<FootballViewModel.Item>>> {
    val out = mutableListOf<Pair<String, List<FootballViewModel.Item>>>()
    if (ui.live.isNotEmpty()) out += stringResource(R.string.football_live_row) to ui.live
    if (ui.today.isNotEmpty()) out += stringResource(R.string.football_today) to ui.today
    ui.upcoming.groupBy { localDay(it.match) }.forEach { (day, items) ->
        out += DAY_TITLE.format(day).replaceFirstChar { it.uppercase() } to items
    }
    if (ui.lastRound.isNotEmpty()) out += stringResource(R.string.football_last_round) to ui.lastRound
    return out
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- Top of the page ----------------------------------------------------------------------------

private val HERO_HEIGHT = 318.dp

private val Oswald = FontFamily(Font(R.font.oswald_bold, FontWeight.Bold))

/**
 * Behind the top of the page, streaming-app style: the best feed's picture from its look over the
 * right of the screen, fading into the page to the left and below. Before any look, the two teams'
 * colours with their crests faint.
 */
@Composable
private fun Backdrop(file: java.io.File?, match: Match, looks: Int, modifier: Modifier = Modifier) {
    val page = MaterialTheme.colorScheme.background
    // Faded into the page inside the picture itself: no edge where it starts.
    val fadeOut = Modifier.drawWithContent {
        drawContent()
        drawRect(Brush.horizontalGradient(0f to page, 0.45f to page.copy(alpha = 0.35f), 1f to Color.Transparent))
        drawRect(Brush.verticalGradient(0.5f to Color.Transparent, 1f to page))
    }
    Box(modifier.fillMaxWidth().height(HERO_HEIGHT + 60.dp)) {
        if (file != null) {
            AsyncImage(
                // The same file is rewritten by a new look: key the request on it.
                model = coil.request.ImageRequest.Builder(LocalContext.current).data(file)
                    .memoryCacheKey("${file.path}#${file.lastModified()}#$looks").build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.75f).fillMaxHeight().then(fadeOut),
            )
        } else {
            Box(Modifier.align(Alignment.TopEnd).fillMaxWidth(0.6f).fillMaxHeight().then(fadeOut)) {
                Box(Modifier.fillMaxSize().alpha(0.4f).split(TeamColors.of(match.home), TeamColors.of(match.away))) {
                    Row(Modifier.fillMaxSize().padding(start = 80.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.alpha(0.35f)) { Crest(match.home, 120) }
                        if (!match.goals) Box(Modifier.alpha(0.35f)) { Crest(match.away, 120) }
                    }
                }
            }
        }
    }
}

/**
 * The game on now (or next), big: round and time, the crests, the teams in tall capitals, one line
 * on the feed that will play, then "Watch now" (focused) and "Pick source". Games on together come
 * as tabs above.
 */
@Composable
private fun Hero(
    modifier: Modifier = Modifier,
    tabs: List<FootballViewModel.Item>,
    item: FootballViewModel.Item,
    firstId: Long,
    best: FootballViewModel.Source?,
    viewModel: FootballViewModel,
    looks: Int,
    progress: FootballViewModel.Progress?,
    onSelect: (Long) -> Unit,
    onPick: () -> Unit,
) {
    val m = item.match
    val watch = remember { FocusRequester() }
    val feeds = viewModel.feedsOf(item)
    val checked = remember(item, looks) { viewModel.sources(item).count { it.looked && it.usable } }
    LaunchedEffect(firstId, feeds.isNotEmpty()) { if (feeds.isNotEmpty()) runCatching { watch.requestFocus() } }

    Column(modifier.fillMaxWidth().height(HERO_HEIGHT).padding(start = 48.dp, top = 30.dp, end = 48.dp)) {
        if (tabs.size >= 2) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                items(tabs, key = { it.match.id }) { t ->
                    MatchTab(t, selected = t.match.id == m.id) { onSelect(t.match.id) }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (m.phase == Phase.LIVE) {
                LiveBadge(m.minute)
                Spacer(Modifier.width(12.dp))
            }
            Text(
                kicker(m),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Crest(m.home, 52)
            if (!m.goals) Crest(m.away, 52)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            title(m),
            fontFamily = Oswald,
            fontWeight = FontWeight.Bold,
            fontSize = 46.sp,
            lineHeight = 50.sp,
            color = Color.White,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(0.72f),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val meta = MaterialTheme.typography.bodyLarge
            val faint = Color.White.copy(alpha = 0.8f)
            when {
                feeds.isEmpty() -> Text(
                    stringResource(if (m.phase == Phase.LIVE) R.string.football_no_channel else R.string.football_channels_soon),
                    style = meta, color = faint,
                )
                progress?.matchId == m.id -> Text(
                    stringResource(R.string.football_auto_scanning) + " " + progress.done + "/" + progress.total,
                    style = meta, color = faint,
                )
                best != null -> {
                    Text(FeedLabel.split(best.feed.shownName, best.feed.qualityLabel).name, style = meta, color = faint, maxLines = 1)
                    best.probe?.let { Tag(compact(it)) }
                    Dot()
                    Text(stringResource(R.string.football_checked_count, checked), style = meta, color = faint)
                }
                else -> Text(stringResource(R.string.football_auto_hint), style = meta, color = faint)
            }
        }
        Spacer(Modifier.height(20.dp))
        if (feeds.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                HeroButton("⚡ " + stringResource(R.string.football_watch), Modifier.focusRequester(watch)) { viewModel.autoWatch(item) }
                HeroButton(stringResource(R.string.football_choose_source), onClick = onPick)
                HeroButton("↻", onClick = { viewModel.rescan(item) })
            }
        }
        // Looking at another game's feeds by itself: a thin line, out of the way.
        progress?.takeIf { it.matchId != m.id }?.let { p ->
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.football_scanning_bar, p.title, p.done, p.total),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { if (p.total == 0) 0f else p.done / p.total.toFloat() },
                modifier = Modifier.width(320.dp).padding(top = 4.dp).height(2.dp).clip(RoundedCornerShape(1.dp)),
            )
        }
    }
}

/** Streaming-app button: translucent grey, white with black text in focus. */
@Composable
private fun HeroButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        text,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        color = if (focused) Color.Black else Color.White,
        maxLines = 1,
        modifier = modifier
            .lift(focused, 1.06f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color.White else Color(0x736E7672))
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 11.dp),
    )
}

/** A game on the top of the page's tabs: crests, score, minute. Focus selects it. */
@Composable
private fun MatchTab(item: FootballViewModel.Item, selected: Boolean, onSelect: () -> Unit) {
    val m = item.match
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color.White else if (selected) Color(0x996E7672) else Color(0x4D6E7672))
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onSelect()
            }
            .clickable(interactionSource = null, indication = null, onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (focused) Color.Black else Color.White
        if (m.phase == Phase.LIVE) Text("● ", color = LIVE_RED, fontSize = 10.sp)
        Crest(m.home, 18)
        Text(" ${centre(m)} ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fg)
        if (!m.goals) Crest(m.away, 18)
        if (m.phase == Phase.LIVE) m.minute?.let { Text(" $it", fontSize = 12.sp, color = fg) }
    }
}

/** "1080p50" in a thin outline. */
@Composable
private fun Tag(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        modifier = Modifier
            .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
private fun Dot() {
    Box(Modifier.size(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.5f)))
}

/**
 * One feed in the big card's list: place, short name, badges for what its look measured (✓ right
 * game, resolution, 50 fps, Audio HD, 5.1), the restream source small at the end. Not looked at
 * yet: the quality the name claims, dashed. Dead or another game: faded, at the bottom. OK plays it.
 */
@Composable
private fun SourceRow(s: FootballViewModel.Source, place: Int, onFocus: () -> Unit = {}, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val parts = remember(s.feed.id, s.feed.shownName, s.feed.qualityLabel) { FeedLabel.split(s.feed.shownName, s.feed.qualityLabel) }
    val dim = if (s.usable || focused) 1f else 0.4f
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(dim)
            .clip(RoundedCornerShape(7.dp))
            .background(if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val fg = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        val faint = if (focused) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
        Text("$place", Modifier.width(22.dp), color = faint, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        Text(
            parts.name,
            Modifier.weight(1f),
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val p = s.probe
        when {
            !s.looked -> Badge("${parts.claimed}?", focused, dashed = true)
            s.verdict == AutoScan.Verdict.DEAD -> Badge(stringResource(R.string.football_auto_dead).uppercase(), focused)
            s.verdict == AutoScan.Verdict.WRONG_GAME -> Badge(stringResource(R.string.football_auto_wrong_game).uppercase(), focused)
            p != null -> {
                if (s.verdict == AutoScan.Verdict.CONFIRMED) Badge("✓", focused, accent = true)
                Badge(FeedLabel.resolution(p.height), focused)
                FeedLabel.smooth(p.fps)?.let { Badge(it, focused) }
                if (p.audioHd) Badge("AUDIO HD", focused)
                if ((p.audioChannels ?: 0) >= 6) Badge("5.1", focused)
            }
        }
        Text(
            parts.source,
            Modifier.width(64.dp),
            color = faint,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            letterSpacing = 1.sp,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A thin-outlined tag ("1080p", "50 FPS"): neutral, in the theme's colours. [accent]: the one that
 * means something (✓ right game) takes the primary colour — off the focused row, where it would
 * vanish. [dashed]: a guess (the name's claim before any look).
 */
@Composable
private fun Badge(text: String, focused: Boolean, accent: Boolean = false, dashed: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val colour = when {
        focused -> scheme.onPrimary
        accent -> scheme.primary
        dashed -> scheme.onSurfaceVariant.copy(alpha = 0.7f)
        else -> scheme.onSurface.copy(alpha = 0.85f)
    }
    val line = when {
        focused -> scheme.onPrimary.copy(alpha = 0.5f)
        accent -> scheme.primary
        else -> scheme.outlineVariant
    }
    Text(
        text,
        color = colour,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        maxLines = 1,
        modifier = Modifier
            .drawBehind {
                val r = 3.dp.toPx()
                val w = 1.dp.toPx()
                drawRoundRect(
                    color = line,
                    topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = w,
                        pathEffect = if (dashed) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())) else null,
                    ),
                )
            }
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** Hold OK on a card: the match's feeds, best first, to pick one by hand. */
@Composable
private fun SourcePicker(item: FootballViewModel.Item, viewModel: FootballViewModel, looks: Int, onPick: (Channel) -> Unit) {
    val sources = remember(item, looks) { viewModel.sources(item) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    // Opened by holding OK, the same press keeps repeating into the picker and its release
    // would tap the first source. Ignore OK until a fresh press.
    var armed by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .onPreviewKeyEvent { e ->
                    val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                    when {
                        armed || !ok -> false
                        e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0 -> { armed = true; false }
                        e.type == KeyEventType.KeyUp -> { armed = true; true }
                        else -> true
                    }
                }
                .width(460.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(18.dp),
        ) {
            Text(stringResource(R.string.football_pick_source), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${item.match.home.name} - ${item.match.away.name}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            LazyColumn(Modifier.height(300.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(sources.size) { i ->
                    val s = sources[i]
                    Box(if (i == 0) Modifier.focusRequester(first) else Modifier) {
                        SourceRow(s, place = i + 1) { onPick(s.feed) }
                    }
                }
            }
        }
    }
}

/** The feeds being looked at, then their ranking, before the best one opens. */
@Composable
private fun ScanOverlay(scan: FootballViewModel.Scan) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)).padding(horizontal = 80.dp, vertical = 40.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                stringResource(if (scan.finished) R.string.football_auto_ranked else R.string.football_auto_scanning),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(scan.title, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.7f))
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { if (scan.rows.isEmpty()) 1f else scan.done / scan.rows.size.toFloat() },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            )
            Spacer(Modifier.height(14.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(scan.rows, key = { it.id }) { row -> ScanLine(row) }
            }
        }
    }
}

@Composable
private fun ScanLine(row: FootballViewModel.ScanRow) {
    val (mark, colour, note) = when (row.verdict) {
        null -> Triple("…", Color.White.copy(alpha = 0.4f), null)
        AutoScan.Verdict.CONFIRMED -> Triple("✓", Color(0xFF6BDB8A), stringResource(R.string.football_auto_confirmed))
        AutoScan.Verdict.OK -> Triple("✓", Color(0xFF6BDB8A), null)
        AutoScan.Verdict.WRONG_GAME -> Triple("✗", LIVE_RED, stringResource(R.string.football_auto_wrong_game))
        AutoScan.Verdict.DEAD -> Triple("✗", LIVE_RED, stringResource(R.string.football_auto_dead))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(row.place?.let { "$it." } ?: "", Modifier.width(36.dp), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
        Text(mark, Modifier.width(24.dp), color = colour, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        Text(row.name, Modifier.weight(1f), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        Text(
            listOfNotNull(row.detail, row.board, note).joinToString("   "),
            color = Color.White.copy(alpha = 0.75f),
            maxLines = 1,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// ---- Rows ---------------------------------------------------------------------------------------

@Composable
private fun MatchRow(
    title: String,
    items: List<FootballViewModel.Item>,
    viewModel: FootballViewModel,
    progress: FootballViewModel.Progress?,
    looks: Int,
    onOpen: (FootballViewModel.Item) -> Unit,
    onHold: (FootballViewModel.Item) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 48.dp, bottom = 4.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.match.id }) { item ->
                MatchTile(item, corner(item, viewModel, progress, looks), onHold = { onHold(item) }) { onOpen(item) }
            }
        }
    }
}

/**
 * A game as a 16:9 tile: the two teams' colours split on a slant with their crests big, the time
 * (or LIVE minute, or Highlights) on a chip top left, how many feeds are ready bottom right; the
 * teams (and score) under it.
 */
@Composable
private fun MatchTile(item: FootballViewModel.Item, corner: String?, onHold: () -> Unit, onClick: () -> Unit) {
    val m = item.match
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    // The whole tile takes focus, name included: scrolling brings that into view too.
    Column(
        Modifier
            .width(200.dp)
            .onFocusChanged { focused = it.isFocused }
            .onOkHeld(onHold)
            .clickable(interactionSource = null, indication = null, onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(112.dp)
                .lift(focused, 1.05f)
                .clip(shape)
                .split(TeamColors.of(m.home), TeamColors.of(m.away))
                .then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier),
        ) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.4f)))))
            Row(
                Modifier.fillMaxSize().padding(horizontal = 22.dp),
                horizontalArrangement = if (m.goals) Arrangement.Center else Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Crest(m.home, 52)
                if (!m.goals) Crest(m.away, 52)
            }
            Chip(chip(m, item.highlights), live = m.phase == Phase.LIVE, modifier = Modifier.align(Alignment.TopStart).padding(7.dp))
            corner?.let {
                Text(
                    it,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            name(m),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp),
        )
        tileStatus(m)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun Chip(text: String, live: Boolean, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        color = Color.White,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (live) Color(0xFFE50914) else Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Bottom right of a tile: the scan on it ("12/30"), else how many feeds are ready ("⚡ 17"). */
@Composable
private fun corner(item: FootballViewModel.Item, viewModel: FootballViewModel, progress: FootballViewModel.Progress?, looks: Int): String? {
    if (item.match.phase == Phase.FINISHED || item.match.phase == Phase.OFF) return null
    if (viewModel.feedsOf(item).isEmpty()) return null
    if (progress?.matchId == item.match.id) return "${progress.done}/${progress.total}"
    val usable = remember(item, looks) { viewModel.sources(item).count { it.looked && it.usable } }
    return if (usable > 0) "⚡ $usable" else null
}

/** Paints the box in two colours split on a slant: home left, away right. */
private fun Modifier.split(home: Color, away: Color): Modifier = drawBehind {
    drawRect(away)
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(0f, 0f)
        lineTo(size.width * 0.58f, 0f)
        lineTo(size.width * 0.42f, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(path, home)
}

/** Grows a little in focus, whatever the app's style. */
private fun Modifier.lift(focused: Boolean, to: Float): Modifier = composed {
    val scale by animateFloatAsState(if (focused) to else 1f, tween(150), label = "lift")
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Holding OK (centre or Enter) for a moment calls [onHeld] instead of the click: the key repeats
 * while held, and the release that follows is swallowed.
 */
private fun Modifier.onOkHeld(onHeld: () -> Unit): Modifier = composed {
    var held by remember { mutableStateOf(false) }
    onPreviewKeyEvent { e ->
        val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
        when {
            !ok -> false
            e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 1 && !held -> {
                held = true
                onHeld()
                true
            }
            e.type == KeyEventType.KeyDown && held -> true
            e.type == KeyEventType.KeyUp && held -> {
                held = false
                true
            }
            else -> false
        }
    }
}

@Composable
private fun LiveBadge() {
    Text(
        stringResource(R.string.football_live),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF1A0505),
        modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(LIVE_RED).padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
private fun LiveBadge(minute: String?) {
    Text(
        listOfNotNull(stringResource(R.string.football_live), minute).joinToString(" "),
        fontSize = 13.sp,
        fontWeight = FontWeight.ExtraBold,
        color = Color.White,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFE50914)).padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
private fun Crest(team: Team, size: Int) {
    AsyncImage(
        model = team.crestUrl,
        contentDescription = team.name,
        modifier = Modifier.size(size.dp),
    )
}

// ---- Text ---------------------------------------------------------------------------------------

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val DAY = DateTimeFormatter.ofPattern("EEE d")
private val DAY_TITLE = DateTimeFormatter.ofPattern("EEEE d")

private fun localDay(m: Match): LocalDate = Instant.ofEpochMilli(m.kickoffUtcMillis).atZone(ZoneId.systemDefault()).toLocalDate()

/** The score once it's started, the kick-off time before; GOL on the goal show. */
private fun centre(m: Match): String =
    if (m.goals && m.phase == Phase.LIVE) "GOL"
    else if (m.homeScore != null && m.awayScore != null) "${m.homeScore}-${m.awayScore}"
    else TIME.format(Instant.ofEpochMilli(m.kickoffUtcMillis).atZone(ZoneId.systemDefault()))

/** Over the big title: "SERIE A · 9ª GIORNATA · OGGI 18:30". */
@Composable
private fun kicker(m: Match): String {
    val at = Instant.ofEpochMilli(m.kickoffUtcMillis).atZone(ZoneId.systemDefault())
    val round = m.round?.let { stringResource(R.string.football_round, it) } ?: "SERIE A"
    val when_ = when (m.phase) {
        Phase.SCHEDULED ->
            if (at.toLocalDate() == LocalDate.now()) stringResource(R.string.football_today) + " " + TIME.format(at)
            else DAY_TITLE.format(at) + " " + TIME.format(at)
        Phase.FINISHED -> stringResource(R.string.football_full_time)
        else -> null
    }
    return listOfNotNull(round, when_?.uppercase()).joinToString(" · ")
}

/** The big title: "SASSUOLO vs LAZIO", "SASSUOLO 1 – 0 LAZIO" once it's on. */
private fun title(m: Match): AnnotatedString = buildAnnotatedString {
    if (m.goals) {
        append(m.home.name.uppercase())
        return@buildAnnotatedString
    }
    append(m.home.name.uppercase())
    if (m.homeScore != null && m.awayScore != null) {
        append("  ${m.homeScore} – ${m.awayScore}  ")
    } else {
        withStyle(SpanStyle(color = Color.White.copy(alpha = 0.5f), fontSize = 30.sp)) { append("  vs  ") }
    }
    append(m.away.name.uppercase())
}

/** Under a tile: the teams, with the score once it's over. */
private fun name(m: Match): String = when {
    m.goals -> m.home.name
    m.phase == Phase.FINISHED && m.homeScore != null && m.awayScore != null -> "${m.home.name} ${m.homeScore} – ${m.awayScore} ${m.away.name}"
    else -> "${m.home.name} – ${m.away.name}"
}

/** The tile's chip: kick-off time, LIVE minute, Highlights. */
@Composable
private fun chip(m: Match, highlights: String?): String = when (m.phase) {
    Phase.SCHEDULED -> TIME.format(Instant.ofEpochMilli(m.kickoffUtcMillis).atZone(ZoneId.systemDefault()))
    Phase.LIVE -> if (m.goals) stringResource(R.string.football_live) else m.minute ?: stringResource(R.string.football_live)
    Phase.FINISHED -> if (highlights != null) "▶ " + stringResource(R.string.football_highlights) else stringResource(R.string.football_highlights_later)
    Phase.OFF -> "—"
}

/** The line under a finished tile: "Finale", "Finale · Dom 25". */
@Composable
private fun tileStatus(m: Match): String? {
    if (m.phase != Phase.FINISHED) return null
    val at = Instant.ofEpochMilli(m.kickoffUtcMillis).atZone(ZoneId.systemDefault())
    val full = stringResource(R.string.football_full_time)
    return if (at.toLocalDate() == LocalDate.now()) full else full + " · " + DAY.format(at).replaceFirstChar { it.uppercase() }
}

/** "1080p50", "720p" */
private fun compact(p: AutoScan.Probe): String {
    val res = FeedLabel.resolution(p.height)
    val fps = p.fps?.takeIf { it >= 48 }?.let { if (it >= 56) 60 else 50 }
    return if (fps != null && res.endsWith("p")) "$res$fps" else res
}
