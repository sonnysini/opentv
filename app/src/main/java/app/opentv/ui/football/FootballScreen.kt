/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.football

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.PlayArrow
import android.widget.Toast
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import app.opentv.core.ServiceLocator
import app.opentv.core.findActivity
import app.opentv.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.background
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import app.opentv.data.repo.FootballRepository
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
/** How long before kick-off the top of the page starts playing the game's best feed. */
private const val PREVIEW_AHEAD_MILLIS = 15 * 60_000L
/** The preview holds the account's one connection: after this long untouched, back to the still. */
private const val PREVIEW_MAX_MILLIS = 3 * 60_000L
private const val PREVIEW_HANDOFF_MILLIS = 700L

private val YOUTUBE_APPS = listOf("com.amazon.firetv.youtube", "com.google.android.youtube.tv")

/**
 * The football home, streaming-app style: the match on now (or next) over the whole top of the
 * page, its best feed's picture behind, "Watch now" already focused — OK and it plays on the best
 * channel of the user's own lists — then a row of 16:9 tiles per competition (Serie A and the
 * three European cups), the one playing next first; Serie A's finished games play their YouTube
 * highlights. No score anywhere: a game is watched later too.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FootballScreen(
    onPlayChannel: (Channel) -> Unit,
    /** Plays a video in our own player: media key, stream URL, title. */
    onPlayVideo: (key: String, url: String, title: String) -> Unit,
    viewModel: FootballViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val opening by viewModel.opening.collectAsStateWithLifecycle()
    val scan by viewModel.scan.collectAsStateWithLifecycle()
    val autoPlay by viewModel.autoPlay.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    // Re-read the feeds' looks whenever one lands.
    val looks by viewModel.looks.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // ---- Live preview ------------------------------------------------------------------------
    // One muted player for the top of the page, as the guide's: stopped before the full-screen
    // player opens and whenever the screen goes, so the box never decodes twice and the account's
    // one connection is free for the player.
    val graph = remember { ServiceLocator.get(context) }
    val previewSetting by graph.settings.guidePreviewVideo.collectAsStateWithLifecycle()
    val recordings by graph.recordingRepository.observeActive().collectAsStateWithLifecycle(initialValue = emptyList())
    val previewScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    val previewController = remember {
        PlayerController(context, previewScope, graph.httpClient, subtitlesEnabled = false, preview = true)
            .also { it.player.volume = 0f }
    }
    // The feed playing at the top, null when none.
    var previewing by remember { mutableStateOf<Long?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            previewController.release()
            previewScope.cancel()
            viewModel.setPreviewing(false)
        }
    }
    LaunchedEffect(autoPlay) {
        autoPlay?.let {
            val wasPlaying = previewing != null
            previewController.stop()
            previewing = null
            viewModel.setPreviewing(false)
            // The provider counts a connection for a moment after it closes: let the preview's go.
            // Cleared only after the wait: clearing it changes this effect's key and would cancel it.
            if (wasPlaying) kotlinx.coroutines.delay(PREVIEW_HANDOFF_MILLIS)
            viewModel.autoPlayed()
            onPlayChannel(it)
        }
    }
    // Feeds get looked at only while this screen is up: the player needs the one connection.
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    resumed = true
                    viewModel.setActive(true)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    resumed = false
                    previewController.stop()
                    previewing = null
                    viewModel.setPreviewing(false)
                    viewModel.setActive(false)
                }
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
    // The game whose picker was closed: focus goes back to it, not to the menu on the left. A game
    // is on the carousel and in its row too: the picker opened from the carousel goes back there.
    var refocus by remember { mutableStateOf<Long?>(null) }
    var pickedOnTop by remember { mutableStateOf(false) }
    var refocusTop by remember { mutableStateOf(0) }
    // The scan screen takes the focus: when it goes (cancelled, or the game opened), focus goes
    // back to where it was started from — the carousel, or the game's card.
    var scanOnTop by remember { mutableStateOf(true) }
    var scanned by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(scan?.match?.id) {
        val id = scan?.match?.id
        val was = scanned
        if (id == null && was != null) {
            if (scanOnTop) refocusTop++ else refocus = was
        }
        scanned = id
    }
    BackHandler(enabled = picking != null) {
        if (pickedOnTop) refocusTop++ else refocus = picking?.match?.id
        pickedOnTop = false
        picking = null
    }
    val noYoutube = stringResource(R.string.football_no_youtube)
    val noChannels = stringResource(R.string.football_no_channels)
    // A game no channel was found for: say so, rather than nothing happening on OK.
    val noChannel: (FootballViewModel.Item) -> Unit = { item ->
        if (item.channels.isEmpty() && (item.match.phase == Phase.LIVE || item.match.phase == Phase.SCHEDULED)) {
            Toast.makeText(context, noChannels, Toast.LENGTH_SHORT).show()
        }
    }
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
            item.match.phase != Phase.FINISHED && viewModel.feedsOf(item).isNotEmpty() -> {
                scanOnTop = false
                viewModel.autoWatch(item)
            }
            else -> noChannel(item)
        }
    }
    // A launcher's match card: act on it as on a click here, once the match is on the page (its
    // feeds looked up for Watch). Given up after a while: a match gone from the page plays nothing.
    val request by FootballRequests.request.collectAsStateWithLifecycle()
    LaunchedEffect(request, ui, looks) {
        val r = request ?: return@LaunchedEffect
        val item = ui.all.firstOrNull { it.match.id == r.matchId }
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
        pickedOnTop = false
        if (item.match.phase != Phase.FINISHED && viewModel.feedsOf(item).isNotEmpty()) picking = item else noChannel(item)
    }
    // The carousel's card on screen: the first (live, else next) one, then wherever it turned to.
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val heroItem = ui.heroes.firstOrNull { it.match.id == selectedId } ?: ui.heroes.firstOrNull()
    val heroAt = heroItem?.let { h -> ui.heroes.indexOfFirst { it.match.id == h.match.id } } ?: 0
    val heroBest = remember(heroItem, looks) { heroItem?.let { h -> viewModel.sources(h).firstOrNull { it.looked && it.usable } } }
    val backdrop = heroItem?.let { h -> heroBest?.feed?.id?.let { viewModel.frame(h, it) } }
    // The game itself instead of its still, muted: once it's on (or about to be), its best feed
    // known, nothing else using the connection (no scan, no recording), "Watch now" in focus.
    var heroFocused by remember { mutableStateOf(false) }
    // Bumped by moving within the top of the page: restarts the preview's time limit.
    var heroTouched by remember { mutableStateOf(0) }
    val previewFeed = heroBest?.feed?.takeIf {
        val m = heroItem?.match ?: return@takeIf false
        previewSetting && resumed && heroFocused && recordings.isEmpty() &&
            scan == null && progress == null && picking == null && opening == null &&
            (m.phase == Phase.LIVE || (m.phase == Phase.SCHEDULED && m.kickoffUtcMillis - System.currentTimeMillis() <= PREVIEW_AHEAD_MILLIS))
    }
    val previewOn = previewing != null
    fun stopPreview() {
        previewController.stop()
        previewing = null
        viewModel.setPreviewing(false)
    }
    LaunchedEffect(previewFeed?.id, heroTouched) {
        val feed = previewFeed
        if (feed == null) {
            stopPreview()
            return@LaunchedEffect
        }
        // Back into the top of the page with the game on: it carries on, only the time limit restarts.
        if (previewing != feed.id) {
            viewModel.setPreviewing(true)
            previewing = feed.id
            previewController.play(
                PlayerController.Request(
                    url = feed.streamUrl,
                    title = feed.shownName,
                    userAgent = graph.sourceRepository.byId(feed.sourceId)?.userAgent ?: "OpenTV/0.1 (Android)",
                    isLive = true,
                ),
                debounce = true,
            )
        }
        kotlinx.coroutines.delay(PREVIEW_MAX_MILLIS)
        stopPreview()
    }
    // The carousel turns over every few seconds; any key on it starts the count again. Held while
    // its game plays muted or a picker, scan or player is up.
    val turn = remember { Animatable(0f) }
    var turnKicked by remember { mutableStateOf(0) }
    val turnHeld = previewOn || picking != null || scan != null || opening != null || !resumed
    fun step(by: Int) {
        val size = ui.heroes.size
        if (size < 2) return
        selectedId = ui.heroes[((heroAt + by) % size + size) % size].match.id
    }
    LaunchedEffect(heroItem?.match?.id, ui.heroes.size, turnHeld, turnKicked) {
        turn.snapTo(0f)
        if (turnHeld || ui.heroes.size < 2) return@LaunchedEffect
        turn.animateTo(1f, tween(TURN_MILLIS, easing = LinearEasing))
        step(1)
    }
    // The box's screensaver would fire over a game playing at the top of the page.
    DisposableEffect(previewOn) {
        val window = context.findActivity()?.window
        if (previewOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    val rowList = ui.rows
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
            ui.hero == null && ui.rows.isEmpty() -> Message(stringResource(R.string.football_none))
            else -> {
                if (heroItem != null) Backdrop(backdrop, heroItem.match, looks, if (previewOn) previewController.player else null, Modifier.alpha(fade))
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
                            modifier = Modifier.onFocusChanged {
                                heroFocused = it.hasFocus
                                if (it.hasFocus) {
                                    heroTouched++
                                    // After the scroll that brings the focused button into view:
                                    // started together, that one cancels this and the top of the
                                    // card stays off screen.
                                    scope.launch {
                                        kotlinx.coroutines.delay(HERO_SCROLL_DELAY_MILLIS)
                                        listState.animateScrollToItem(0)
                                    }
                                }
                            },
                            count = ui.heroes.size,
                            at = heroAt,
                            turned = turn.value,
                            item = heroItem,
                            firstId = ui.hero!!.match.id,
                            best = heroBest,
                            viewModel = viewModel,
                            looks = looks,
                            progress = progress,
                            onStep = { by ->
                                if (by < 0 && heroAt == 0) false
                                else {
                                    step(by)
                                    true
                                }
                            },
                            onKey = { turnKicked++ },
                            onWatch = {
                                scanOnTop = true
                                if (viewModel.feedsOf(heroItem).isNotEmpty()) viewModel.autoWatch(heroItem) else noChannel(heroItem)
                            },
                            onPick = {
                                if (viewModel.feedsOf(heroItem).isNotEmpty()) {
                                    pickedOnTop = true
                                    picking = heroItem
                                } else noChannel(heroItem)
                            },
                            refocus = refocusTop,
                        )
                    }
                    rowList.forEach { row ->
                        item(key = "row:${row.league}") {
                            CompositionLocalProvider(LocalBringIntoViewSpec provides sideways) {
                                MatchRow(row, viewModel, progress, looks, open, hold, refocus) { refocus = null }
                            }
                        }
                    }
                }
                }
            }
        }
        scan?.let { ScanOverlay(it) }
        picking?.let { item ->
            SourcePicker(item, viewModel, looks, onRescan = {
                scanOnTop = pickedOnTop
                picking = null
                viewModel.rescan(item)
            }) { feed ->
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

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- Top of the page ----------------------------------------------------------------------------

private val HERO_HEIGHT = 318.dp

/** Coming back up to the carousel: the page scrolls to its top after the focus has settled. */
private const val HERO_SCROLL_DELAY_MILLIS = 120L

/** The carousel's turn, as the live TV page's. */
private const val TURN_MILLIS = 8_000

private val Oswald = FontFamily(Font(R.font.oswald_bold, FontWeight.Bold))

/**
 * Behind the top of the page, streaming-app style: the best feed's picture from its look over the
 * right of the screen, fading into the page to the left and below. Before any look, the two teams'
 * colours with their crests faint.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun Backdrop(file: java.io.File?, match: Match, looks: Int, player: ExoPlayer?, modifier: Modifier = Modifier) {
    val page = MaterialTheme.colorScheme.background
    // Faded into the page inside the picture itself: no edge where it starts.
    val fadeOut = Modifier.drawWithContent {
        drawContent()
        drawRect(Brush.horizontalGradient(0f to page, 0.45f to page.copy(alpha = 0.35f), 1f to Color.Transparent))
        drawRect(Brush.verticalGradient(0.5f to Color.Transparent, 1f to page))
    }
    val fadeWhole = Modifier.drawWithContent {
        drawContent()
        drawRect(Brush.horizontalGradient(0f to page, 0.12f to page.copy(alpha = 0.6f), 0.34f to Color.Transparent))
        drawRect(Brush.verticalGradient(0.68f to Color.Transparent, 1f to page))
    }
    Box(modifier.fillMaxWidth().height(HERO_HEIGHT + 60.dp)) {
        if (file != null || player != null) {
            // The whole 16:9 picture, as broadcast (nothing cut off), as tall as the top of the
            // page, melting into the page on the left and below.
            Box(Modifier.align(Alignment.TopEnd).fillMaxHeight().aspectRatio(16f / 9f).then(fadeWhole)) {
                if (file != null) AsyncImage(
                    // The same file is rewritten by a new look: key the request on it.
                    model = coil.request.ImageRequest.Builder(LocalContext.current).data(file)
                        .memoryCacheKey("${file.path}#${file.lastModified()}#$looks").build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // The game playing over its still, filling the space like the still; the still
                // shows through until the first frame, and stays if the feed won't play.
                if (player != null) AndroidView(
                    modifier = Modifier.fillMaxSize().clipToBounds(),
                    factory = { ctx ->
                        (android.view.LayoutInflater.from(ctx).inflate(R.layout.football_preview, null) as PlayerView).apply {
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            this.player = player
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        }
                    },
                    update = { it.player = player },
                    onRelease = { it.player = null },
                )
            }
            if (player != null) {
                // Says the sound is off on purpose, not broken.
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(end = 36.dp, bottom = 76.dp).size(34.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = stringResource(R.string.football_preview_muted),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
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
    /** The carousel's cards, which one this is, how far it's turned to the next. */
    count: Int,
    at: Int,
    turned: Float,
    item: FootballViewModel.Item,
    firstId: Long,
    best: FootballViewModel.Source?,
    viewModel: FootballViewModel,
    looks: Int,
    progress: FootballViewModel.Progress?,
    /** -1 / +1 a card; false when there's none that way and the key goes on (to the menu). */
    onStep: (Int) -> Boolean,
    onKey: () -> Unit,
    onWatch: () -> Unit,
    onPick: () -> Unit,
    refocus: Int = 0,
) {
    val m = item.match
    val watch = remember { FocusRequester() }
    val feeds = viewModel.feedsOf(item)
    val checked = remember(item, looks) { viewModel.sources(item).count { it.looked && it.usable } }
    // The buttons stay whatever card is on: focus doesn't drop off the carousel as it turns.
    LaunchedEffect(firstId) { runCatching { watch.requestFocus() } }
    LaunchedEffect(refocus) { if (refocus > 0) runCatching { watch.requestFocus() } }

    Column(
        modifier.fillMaxWidth().height(HERO_HEIGHT).padding(start = 48.dp, top = 30.dp, end = 48.dp)
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown) onKey()
                false
            },
    ) {
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
        // One button: OK finds the best feed, holding OK picks one by hand. LEFT and RIGHT on it
        // turn the carousel.
        WatchButton(
            stringResource(R.string.football_watch),
            Modifier.focusRequester(watch).onOkHeld(onPick).onPreviewKeyEvent { e ->
                e.type == KeyEventType.KeyDown && when (e.key) {
                    Key.DirectionLeft -> onStep(-1)
                    Key.DirectionRight -> onStep(1)
                    else -> false
                }
            },
            onClick = onWatch,
        )
        Dots(count, at, turned, Modifier.padding(top = 14.dp, start = 2.dp))
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

/** "▶ Watch": a pill, translucent grey, white with black text and a faint ring in focus. */
@Composable
private fun WatchButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val fg = if (focused) Color.Black else Color.White
    Row(
        modifier
            .lift(focused, 1.06f)
            .then(if (focused) Modifier.border(2.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50)).padding(2.dp) else Modifier.padding(2.dp))
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White else Color(0x736E7672))
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(start = 18.dp, end = 24.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        androidx.compose.material3.Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1)
    }
}

/** One dot per card; this one's is a pill filling up until the next turns over. */
@Composable
private fun Dots(count: Int, current: Int, turned: Float, modifier: Modifier = Modifier) {
    if (count < 2) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
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
 * One feed in the source list, on two lines: the channel and the quality it claims, big, with
 * "recommended" on the best; under it the badges of what its look measured, as the player's bar
 * shows them (✓ right game, 1080p, 50fps, 8.4 Mb/s, AUDIO HD 5.1). Not looked at yet: the name
 * alone. Dead or another game: why, instead of the badges. The restream source small at the end.
 */
@Composable
private fun SourceLine(s: FootballViewModel.Source, recommended: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val parts = remember(s.feed.id, s.feed.shownName, s.feed.qualityLabel) { FeedLabel.split(s.feed.shownName, s.feed.qualityLabel) }
    val fg = if (focused) scheme.onPrimary else scheme.onSurface
    val faint = if (focused) scheme.onPrimary.copy(alpha = 0.6f) else scheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (s.usable || focused) 1f else 0.45f)
            .clip(RoundedCornerShape(7.dp))
            .background(if (focused) scheme.primary else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    listOf(parts.name, parts.claimed).filter(String::isNotBlank).joinToString(" "),
                    Modifier.weight(1f, fill = false),
                    color = fg,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (recommended) Text(
                    stringResource(R.string.football_recommended).uppercase(),
                    color = if (focused) scheme.onPrimary else scheme.primary,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                )
            }
            val p = s.probe
            val why = when (s.verdict) {
                AutoScan.Verdict.DEAD -> stringResource(R.string.football_auto_dead)
                AutoScan.Verdict.WRONG_GAME -> stringResource(R.string.football_auto_wrong_game)
                else -> null
            }
            when {
                !s.looked -> Unit
                why != null -> Text(why, Modifier.padding(top = 3.dp), color = faint, fontSize = 10.sp, maxLines = 1)
                p != null -> Row(
                    Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (s.verdict == AutoScan.Verdict.CONFIRMED) Text(
                        "✓",
                        color = if (focused) scheme.onPrimary else scheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    badges(p).forEach { QualityBadge(it, focused) }
                }
            }
        }
        if (parts.source.isNotEmpty()) Text(
            parts.source,
            Modifier.widthIn(max = 64.dp),
            color = faint,
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** What a look measured, as the player's bar says it: `1080p` `50fps` `8.4 Mb/s` `AUDIO HD 5.1`. */
private fun badges(p: AutoScan.Probe): List<String> = listOfNotNull(
    FeedLabel.resolution(p.height),
    p.fps?.let { "${it}fps" },
    p.kbps?.let { app.opentv.player.StreamInfo.bitrateLabel(it * 1000L) },
    listOfNotNull("AUDIO HD".takeIf { p.audioHd }, "5.1".takeIf { (p.audioChannels ?: 0) >= 6 })
        .joinToString(" ").ifEmpty { null },
)

/** A badge as the player's [StreamBadges]: thin white outline; on the focused row, the theme's dark green. */
@Composable
private fun QualityBadge(text: String, focused: Boolean) {
    val colour = if (focused) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.9f)
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = colour,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .border(1.dp, if (focused) colour.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.45f), RoundedCornerShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 1.dp),
    )
}

/** "FONTI", "NON DISPONIBILI": small spaced capitals over a part of the list. */
@Composable
private fun ListLabel(text: String) {
    Text(
        text.uppercase(),
        Modifier.padding(start = 11.dp, top = 9.dp, bottom = 4.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        fontSize = 8.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.5.sp,
    )
}

/** "↻ Check the sources again", with when they were last checked and how many of them. */
@Composable
private fun RescanLine(sub: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val fg = if (focused) scheme.onPrimary else scheme.onSurface
    val faint = if (focused) scheme.onPrimary.copy(alpha = 0.7f) else scheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (focused) scheme.primary else scheme.surfaceVariant)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text("↻", color = faint, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.width(16.dp))
        Column {
            Text(stringResource(R.string.football_rescan), color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(sub, color = faint, fontSize = 9.sp, maxLines = 1)
        }
    }
}

private val PICKER_WIDTH = 360.dp

/**
 * Hold OK on "Watch" or a card: the match's feeds in a panel down the right, best first, to pick
 * one by hand; the page darkens behind. On top "Check again" (the focus starts on the best feed,
 * one down), then the feeds that play, then the ones that don't under "Unavailable".
 */
@Composable
private fun SourcePicker(
    item: FootballViewModel.Item,
    viewModel: FootballViewModel,
    looks: Int,
    /** "Check again": the scan screen takes over, then the best feed plays. */
    onRescan: () -> Unit,
    onPick: (Channel) -> Unit,
) {
    val sources = remember(item, looks) { viewModel.sources(item) }
    val (playing, gone) = remember(sources) { sources.partition { it.usable } }
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    // Opened by holding OK, the same press keeps repeating into the picker and its release
    // would tap the first source. Ignore OK until a fresh press.
    var armed by remember { mutableStateOf(false) }
    val surface = MaterialTheme.colorScheme.surface
    val shown = remember { androidx.compose.animation.core.MutableTransitionState(false) }.apply { targetState = true }
    val sub = progress?.takeIf { it.matchId == item.match.id }
        ?.let { stringResource(R.string.football_rescan_running, it.done, it.total) }
        ?: run {
            val checked = sources.count { it.looked }
            val last = sources.mapNotNull { it.at }.maxOrNull()
            if (last == null) stringResource(R.string.football_rescan_never)
            else stringResource(
                R.string.football_rescan_last,
                java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(last)),
                checked,
                sources.size,
            )
        }
    Box(
        Modifier.fillMaxSize().background(
            Brush.horizontalGradient(0f to Color(0xFF08090A).copy(alpha = 0.55f), 0.6f to Color(0xFF08090A).copy(alpha = 0.8f)),
        ),
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visibleState = shown,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            enter = androidx.compose.animation.slideInHorizontally(tween(220)) { it } + androidx.compose.animation.fadeIn(tween(220)),
        ) {
            Row(Modifier.fillMaxHeight()) {
                // The panel's edge melts into the darkened page.
                Box(Modifier.width(60.dp).fillMaxHeight().background(Brush.horizontalGradient(listOf(Color.Transparent, surface))))
                Column(
                    Modifier
                        .width(PICKER_WIDTH)
                        .fillMaxHeight()
                        .background(surface)
                        .onPreviewKeyEvent { e ->
                            val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                            when {
                                armed || !ok -> false
                                e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0 -> { armed = true; false }
                                e.type == KeyEventType.KeyUp -> { armed = true; true }
                                else -> true
                            }
                        }
                        .padding(start = 28.dp, end = 36.dp, top = 32.dp),
                ) {
                    Text(
                        stringResource(R.string.football_pick_source).uppercase(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp,
                    )
                    Text(
                        name(item.match).uppercase(),
                        Modifier.padding(top = 4.dp, bottom = 16.dp),
                        fontFamily = Oswald,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        lineHeight = 28.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        item(key = "rescan") { RescanLine(sub, onRescan) }
                        if (playing.isNotEmpty()) item(key = "label:on") { ListLabel(stringResource(R.string.football_sources)) }
                        items(playing, key = { "on:" + it.feed.id }) { s ->
                            Box(if (s === playing.first()) Modifier.focusRequester(first) else Modifier) {
                                SourceLine(s, recommended = s === playing.first() && s.looked) { onPick(s.feed) }
                            }
                        }
                        if (gone.isNotEmpty()) item(key = "label:off") { ListLabel(stringResource(R.string.football_sources_unavailable)) }
                        items(gone, key = { "off:" + it.feed.id }) { s ->
                            Box(if (playing.isEmpty() && s === gone.first()) Modifier.focusRequester(first) else Modifier) {
                                SourceLine(s, recommended = false) { onPick(s.feed) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Where a feed is on the scan screen. */
private enum class Look { WAITING, CHECKING, PLAYS, LEAVING }

/**
 * The feeds being looked at, before the best one opens. On the left the game and the last picture
 * a look caught, with the feed being looked at, how many are done and a thin bar; on the right
 * the feeds as the source picker lists them. As each look comes back, its badges come in one by
 * one and the row slides to its place in the ranking — the best on top, "recommended"; a feed
 * that won't play says so, then leaves to the right and the rows under it move up. At the end:
 * "Found", and the one about to play. Back cancels.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScanOverlay(scan: FootballViewModel.Scan) {
    val m = scan.match
    val scheme = MaterialTheme.colorScheme
    val surface = scheme.surface
    // Feeds seen playing (or still to look at) on this screen: the ones among them that die
    // stay a moment, saying why, before they go. The ones dead from an earlier scan aren't shown.
    val alive = remember(m.id) { HashSet<Long>() }
    val left = remember(m.id) { androidx.compose.runtime.mutableStateMapOf<Long, Unit>() }
    LaunchedEffect(scan) { scan.sources.forEach { if (it.usable) alive += it.feed.id } }
    val plays = scan.sources.filter { it.looked && it.usable }
    val dying = scan.sources.filter { !it.usable && it.feed.id in alive && it.feed.id !in left }
    val waiting = scan.sources.filter { !it.looked }
    val shown = plays + dying + waiting
    val checking = scan.sources.firstOrNull { it.feed.id == scan.checking }
    // The screen holds the focus while it's up: no key goes to the page or the menu under it
    // (Back still reaches its handler).
    val holder = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { holder.requestFocus() } }
    // A long list: it follows the feed being looked at down, and goes back to the top at the end.
    val list = rememberLazyListState()
    LaunchedEffect(scan.checking, scan.finished) {
        if (scan.finished) {
            list.animateScrollToItem(0)
            return@LaunchedEffect
        }
        val at = shown.indexOfFirst { it.feed.id == scan.checking }
        if (at < 0) return@LaunchedEffect
        val seen = list.layoutInfo.visibleItemsInfo
        val last = seen.lastOrNull()?.index ?: return@LaunchedEffect
        if (at >= last - 1) list.animateScrollToItem((at - (seen.size - 3)).coerceAtLeast(0))
    }

    Box(
        Modifier.fillMaxSize().background(surface)
            .focusRequester(holder)
            .focusable()
            .onPreviewKeyEvent { e -> e.key != Key.Back },
    ) {
        // Behind it all: the last picture, blurred and dark. Modifier.blur needs Android 12 (the
        // Fire Sticks are 7–9): the picture is read a few pixels wide and stretched, which blurs
        // it on any box.
        scan.frame?.let { file ->
            AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current).data(file)
                    .size(BLURRED_WIDTH, BLURRED_WIDTH * 9 / 16)
                    .memoryCacheKey("${file.path}#${file.lastModified()}#blurred").build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.1f; scaleY = 1.1f; alpha = 0.3f }.blur(28.dp),
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to surface.copy(alpha = 0.2f), 0.5f to surface.copy(alpha = 0.6f), 0.62f to surface)))

        Column(Modifier.padding(start = 48.dp, top = 55.dp).width(410.dp)) {
            Text(
                stringResource(if (scan.finished) R.string.football_scan_found else R.string.football_scan_searching).uppercase(),
                color = if (scan.finished) scheme.primary else scheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
            )
            Row(Modifier.padding(top = 13.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Crest(m.home, 36)
                if (!m.goals) Crest(m.away, 36)
            }
            Text(
                name(m).uppercase(),
                Modifier.padding(top = 8.dp),
                fontFamily = Oswald,
                fontWeight = FontWeight.Bold,
                fontSize = 38.sp,
                lineHeight = 40.sp,
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // The last picture a look caught, with the feed being looked at (or about to play).
            Box(
                Modifier.padding(top = 22.dp).width(360.dp).aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(9.dp)).background(Color.Black),
            ) {
                scan.frame?.let { file ->
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(LocalContext.current).data(file)
                            .memoryCacheKey("${file.path}#${file.lastModified()}").build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                val on = if (scan.finished) scan.best else checking
                if (on != null) Row(
                    Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 9.dp)
                        .clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (scan.finished) Text("▶", color = Color.White, fontSize = 10.sp)
                    else Spinner()
                    Text(feedTitle(on.feed), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
            Row(Modifier.padding(top = 15.dp, bottom = 6.dp).width(360.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = scheme.onSurface, fontWeight = FontWeight.Bold)) { append("${scan.done}") }
                        append(" " + stringResource(R.string.football_scan_count, scan.total))
                    },
                    color = scheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
                Text(
                    scan.best?.takeIf { scan.finished }?.let { stringResource(R.string.football_scan_starts, feedTitle(it.feed)) }
                        ?: stringResource(R.string.football_scan_auto),
                    color = scheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            val part by animateFloatAsState(if (scan.total == 0) 1f else scan.done / scan.total.toFloat(), tween(500), label = "scanned")
            Box(Modifier.width(360.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(scheme.surfaceVariant)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(part).clip(RoundedCornerShape(2.dp)).background(scheme.primary))
            }
        }
        Row(
            Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                stringResource(R.string.football_scan_back),
                color = scheme.onSurface.copy(alpha = 0.8f),
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.border(1.dp, scheme.outline, RoundedCornerShape(3.dp)).padding(horizontal = 4.dp),
            )
            Text(stringResource(R.string.football_scan_cancel), color = scheme.onSurfaceVariant, fontSize = 10.sp)
        }

        Column(Modifier.align(Alignment.TopEnd).width(PICKER_WIDTH + 64.dp).fillMaxHeight().padding(start = 28.dp, end = 36.dp, top = 55.dp)) {
            ListLabel(stringResource(R.string.football_sources))
            LazyColumn(
                Modifier.weight(1f),
                state = list,
                userScrollEnabled = false,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(shown, key = { it.feed.id }) { s ->
                    val id = s.feed.id
                    val look = when {
                        !s.usable -> Look.LEAVING
                        s.looked -> Look.PLAYS
                        id == scan.checking -> Look.CHECKING
                        else -> Look.WAITING
                    }
                    Box(
                        Modifier.animateItem(
                            fadeInSpec = null,
                            fadeOutSpec = null,
                            placementSpec = tween(700, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                        ),
                    ) {
                        ScanLine(s, look, recommended = plays.firstOrNull() === s) { left[id] = Unit }
                    }
                }
            }
        }
    }
}

/** A small turning ring, in the theme's green. */
@Composable
private fun Spinner() {
    CircularProgressIndicator(
        modifier = Modifier.size(10.dp),
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
    )
}

/** "Sky Sport Uno FHD": the channel and the quality it claims. */
private fun feedTitle(feed: Channel): String =
    FeedLabel.split(feed.shownName, feed.qualityLabel).let { p -> listOf(p.name, p.claimed).filter(String::isNotBlank).joinToString(" ") }

/**
 * One feed on the scan screen, laid out as in the source picker. Still to look at: the name,
 * faded. Being looked at: on grey, with a spinner. Plays: its badges come in one after another.
 * Leaving: why it won't play, then it slides off to the right and [onGone].
 */
@Composable
private fun ScanLine(s: FootballViewModel.Source, look: Look, recommended: Boolean, onGone: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val parts = remember(s.feed.id, s.feed.shownName, s.feed.qualityLabel) { FeedLabel.split(s.feed.shownName, s.feed.qualityLabel) }
    val away = remember { Animatable(0f) }
    val far = with(LocalDensity.current) { 450.dp.toPx() }
    if (look == Look.LEAVING) LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(LEAVE_PAUSE_MILLIS)
        away.animateTo(1f, tween(550, easing = androidx.compose.animation.core.FastOutLinearInEasing))
        onGone()
    }
    val tag by animateFloatAsState(if (recommended) 1f else 0f, tween(400, delayMillis = 500), label = "recommended")
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = away.value * far
                alpha = (if (look == Look.WAITING) 0.5f else 1f) * (1f - away.value)
            }
            .clip(RoundedCornerShape(7.dp))
            .background(if (look == Look.CHECKING) scheme.surfaceVariant else Color.Transparent)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    listOf(parts.name, parts.claimed).filter(String::isNotBlank).joinToString(" "),
                    Modifier.weight(1f, fill = false),
                    color = scheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (tag > 0f) Text(
                    stringResource(R.string.football_recommended).uppercase(),
                    Modifier.alpha(tag),
                    color = scheme.primary,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                )
            }
            when (look) {
                Look.WAITING -> Unit
                Look.CHECKING -> Row(
                    Modifier.padding(top = 4.dp).height(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Spinner()
                    Text(stringResource(R.string.football_scan_checking), color = scheme.primary, fontSize = 10.sp)
                }
                Look.LEAVING -> Text(
                    stringResource(if (s.verdict == AutoScan.Verdict.WRONG_GAME) R.string.football_auto_wrong_game else R.string.football_auto_dead),
                    Modifier.padding(top = 4.dp).height(16.dp),
                    color = scheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
                Look.PLAYS -> Row(
                    Modifier.padding(top = 4.dp).height(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (s.verdict == AutoScan.Verdict.CONFIRMED) Text("✓", color = scheme.primary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    s.probe?.let(::badges)?.forEachIndexed { i, b ->
                        val enter = remember { Animatable(0f) }
                        LaunchedEffect(Unit) { enter.animateTo(1f, tween(350, delayMillis = i * 70)) }
                        Box(Modifier.graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 6.dp.toPx() }) {
                            QualityBadge(b, focused = false)
                        }
                    }
                }
            }
        }
        if (parts.source.isNotEmpty()) Text(
            parts.source,
            Modifier.widthIn(max = 64.dp),
            color = scheme.onSurfaceVariant,
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The scan screen's background picture is read this many pixels wide, to come out blurred. */
private const val BLURRED_WIDTH = 24

/** A feed that won't play says so this long before it leaves. */
private const val LEAVE_PAUSE_MILLIS = 500L

// ---- Rows ---------------------------------------------------------------------------------------

/** A competition's games: its logo, name and matchday over a row of tiles. */
@Composable
private fun MatchRow(
    row: FootballViewModel.LeagueRow,
    viewModel: FootballViewModel,
    progress: FootballViewModel.Progress?,
    looks: Int,
    onOpen: (FootballViewModel.Item) -> Unit,
    onHold: (FootballViewModel.Item) -> Unit,
    refocus: Long? = null,
    onRefocused: () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 48.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            // League logos are drawn for a light page: on a white chip they read on the dark one.
            Box(
                Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).background(Color.White).padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(model = Team(-row.league.toLong(), "").crestUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(10.dp))
            Text(
                leagueName(row.league),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            row.round?.takeIf { r -> r.all(Char::isDigit) }?.let {
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.football_matchday, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LazyRow(
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(row.items, key = { it.match.id }) { item ->
                val me = remember { FocusRequester() }
                LaunchedEffect(refocus) {
                    if (refocus == item.match.id) {
                        runCatching { me.requestFocus() }
                        onRefocused()
                    }
                }
                MatchTile(item, corner(item, viewModel, progress, looks), Modifier.focusRequester(me), onHold = { onHold(item) }) { onOpen(item) }
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
private fun MatchTile(
    item: FootballViewModel.Item,
    corner: String?,
    modifier: Modifier = Modifier,
    onHold: () -> Unit,
    onClick: () -> Unit,
) {
    val m = item.match
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    // The whole tile takes focus, name included: scrolling brings that into view too.
    Column(
        modifier
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
private val WEEKDAY = DateTimeFormatter.ofPattern("EEE")

/** The competitions' own names, the same in every language. */
internal fun leagueName(league: Int): String = when (league) {
    FootballRepository.CHAMPIONS -> "Champions League"
    FootballRepository.EUROPA -> "Europa League"
    FootballRepository.CONFERENCE -> "Conference League"
    else -> "Serie A"
}

/** Over the big title: "CHAMPIONS LEAGUE · 4ª GIORNATA · OGGI 21:00". */
@Composable
private fun kicker(m: Match): String {
    val at = Instant.ofEpochMilli(m.kickoffUtcMillis).atZone(ZoneId.systemDefault())
    val league = leagueName(m.league).uppercase()
    val round = m.round?.takeIf { r -> r.all(Char::isDigit) }?.let { stringResource(R.string.football_round, league, it) } ?: league
    val when_ = when (m.phase) {
        Phase.SCHEDULED ->
            if (at.toLocalDate() == LocalDate.now()) stringResource(R.string.football_today) + " " + TIME.format(at)
            else DAY_TITLE.format(at) + " " + TIME.format(at)
        Phase.FINISHED -> stringResource(R.string.football_full_time)
        else -> null
    }
    return listOfNotNull(round, when_?.uppercase()).joinToString(" · ")
}

/** The big title: "SASSUOLO vs LAZIO" — the score stays off even once it's on. */
private fun title(m: Match): AnnotatedString = buildAnnotatedString {
    append(m.home.name.uppercase())
    if (m.goals) return@buildAnnotatedString
    withStyle(SpanStyle(color = Color.White.copy(alpha = 0.5f), fontSize = 30.sp)) { append("  vs  ") }
    append(m.away.name.uppercase())
}

/** Under a tile: the teams, never the score. */
private fun name(m: Match): String = if (m.goals) m.home.name else "${m.home.name} – ${m.away.name}"

/** The tile's chip: day and kick-off ("OGGI 21:00", "GIO 18:45"), LIVE minute, Highlights. */
@Composable
private fun chip(m: Match, highlights: String?): String = when (m.phase) {
    Phase.SCHEDULED -> {
        val at = Instant.ofEpochMilli(m.kickoffUtcMillis).atZone(ZoneId.systemDefault())
        val day = if (at.toLocalDate() == LocalDate.now()) stringResource(R.string.football_today) else WEEKDAY.format(at).trimEnd('.')
        day.uppercase() + " " + TIME.format(at)
    }
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
