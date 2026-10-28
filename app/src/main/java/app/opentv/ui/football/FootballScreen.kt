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
import androidx.compose.foundation.background
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
 * The football home: the match on now (or next) big at the top with "Watch now" already focused —
 * OK and it plays on the best channel of the user's own lists — then rows for the other live games,
 * today's (finished ones play their YouTube highlights) and the fixtures coming up.
 */
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
    // What a card says under the teams, the scan's state included.
    val cardAction: @Composable (FootballViewModel.Item) -> String = { item -> action(item, viewModel, progress, looks) }

    Box(Modifier.fillMaxSize()) {
        when {
            ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            ui.failed -> Message(stringResource(R.string.football_unavailable))
            ui.hero == null && ui.today.isEmpty() && ui.upcoming.isEmpty() && ui.lastRound.isEmpty() -> Message(stringResource(R.string.football_none))
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                ui.hero?.let { hero ->
                    item(key = "hero") {
                        Hero(
                            tabs = ui.heroes,
                            first = hero,
                            viewModel = viewModel,
                            looks = looks,
                            progress = progress,
                            onPlay = { item, feed -> viewModel.play(item, feed) },
                        )
                    }
                }
                if (ui.live.isNotEmpty()) item(key = "live") {
                    MatchRow(stringResource(R.string.football_live_row), ui.live, open, hold, cardAction, hint = true)
                }
                if (ui.today.isNotEmpty()) {
                    val h = ui.live.isEmpty() && ui.today.any { it.match.phase != Phase.FINISHED }
                    item(key = "today") { MatchRow(stringResource(R.string.football_today), ui.today, open, hold, cardAction, hint = h) }
                }
                if (ui.upcoming.isNotEmpty()) item(key = "upcoming") {
                    MatchRow(stringResource(R.string.football_upcoming), ui.upcoming, open, hold, cardAction)
                }
                if (ui.lastRound.isNotEmpty()) item(key = "last_round") {
                    MatchRow(stringResource(R.string.football_last_round), ui.lastRound, open, hold, cardAction)
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

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- Hero ---------------------------------------------------------------------------------------

/**
 * The big card: the teams with "Watch now" under them, the match's feeds best first beside, and
 * a picture of the feed in focus (or the best one). Games on together come as tabs along the top.
 */
@Composable
private fun Hero(
    tabs: List<FootballViewModel.Item>,
    first: FootballViewModel.Item,
    viewModel: FootballViewModel,
    looks: Int,
    progress: FootballViewModel.Progress?,
    onPlay: (FootballViewModel.Item, Channel) -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf(first.match.id) }
    val item = tabs.firstOrNull { it.match.id == selectedId } ?: tabs.firstOrNull() ?: first
    val m = item.match
    val watch = remember { FocusRequester() }
    val feeds = viewModel.feedsOf(item)
    val sources = remember(item, looks) { viewModel.sources(item) }
    val best = sources.firstOrNull { it.looked && it.usable }
    var shown by remember(item.match.id) { mutableStateOf<Long?>(null) }
    // The feed in focus, else the best: whichever has a picture.
    val previewId = listOfNotNull(shown, best?.feed?.id).firstOrNull { viewModel.frame(item, it) != null }
    LaunchedEffect(first.match.id, feeds.isNotEmpty()) { if (feeds.isNotEmpty()) runCatching { watch.requestFocus() } }

    Box(
        Modifier
            .fillMaxWidth()
            .height(262.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF16261B), MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface),
                ),
            ),
    ) {
        // Room along the bottom for the scan's bar.
        Row(Modifier.fillMaxSize().padding(start = 24.dp, end = 18.dp, top = 14.dp, bottom = 26.dp)) {
            Column(Modifier.width(330.dp)) {
                if (tabs.size >= 2) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(tabs, key = { it.match.id }) { t ->
                            MatchTab(t, selected = t.match.id == item.match.id) { selectedId = t.match.id }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (m.phase == Phase.LIVE) {
                            LiveBadge()
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            m.round?.let { stringResource(R.string.football_round, it) } ?: "Serie A",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeroTeam(m.home)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            centre(m),
                            fontSize = if (m.phase == Phase.SCHEDULED) 32.sp else 40.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            heroStatus(m),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (m.phase == Phase.LIVE) LIVE_RED else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HeroTeam(m.away)
                }
                Spacer(Modifier.weight(1f))
                if (feeds.isEmpty()) {
                    Text(
                        stringResource(if (m.phase == Phase.LIVE) R.string.football_no_channel else R.string.football_channels_soon),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val sub = when {
                        best != null -> stringResource(R.string.football_ready, best.name) +
                            (best.probe?.let { " · " + quality(it) } ?: "") + " · " + ago(best.at)
                        progress?.matchId == m.id -> stringResource(R.string.football_scanning_short, progress.done, progress.total)
                        else -> stringResource(R.string.football_auto_hint)
                    }
                    WatchButton(Modifier.focusRequester(watch), sub) { viewModel.autoWatch(item) }
                }
            }
            if (feeds.isNotEmpty()) {
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.football_or_watch_on) + " · " + stringResource(R.string.football_best_first),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        SmallButton(stringResource(R.string.football_rescan)) { viewModel.rescan(item) }
                    }
                    Spacer(Modifier.height(6.dp))
                    // Back at the top once focus leaves: the best feeds are what the card shows.
                    val listState = rememberLazyListState()
                    val scope = rememberCoroutineScope()
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.onFocusChanged {
                            if (!it.hasFocus) {
                                shown = null
                                scope.launch { listState.scrollToItem(0) }
                            }
                        },
                    ) {
                        items(sources.size) { i ->
                            val s = sources[i]
                            SourceRow(s, place = i + 1, onFocus = { shown = s.feed.id }) { onPlay(item, s.feed) }
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                Preview(
                    file = previewId?.let { viewModel.frame(item, it) },
                    label = sources.firstOrNull { it.feed.id == previewId }?.let { "${it.name} · ${ago(it.at)}" },
                    match = m,
                    looks = looks,
                    modifier = Modifier.width(230.dp).align(Alignment.CenterVertically),
                )
            }
        }
        // Looking at feeds by itself: a thin bar along the bottom, and which game.
        progress?.let { p ->
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)) {
                Text(
                    stringResource(R.string.football_scanning_bar, p.title, p.done, p.total),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { if (p.total == 0) 0f else p.done / p.total.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

/** A game on the big card's tabs: crests, score, minute. Focus selects it. */
@Composable
private fun MatchTab(item: FootballViewModel.Item, selected: Boolean, onSelect: () -> Unit) {
    val m = item.match
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) MaterialTheme.colorScheme.primary else if (selected) Color(0xFF26352E) else MaterialTheme.colorScheme.surfaceVariant)
            .then(if (selected && !focused) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onSelect()
            }
            .clickable(interactionSource = null, indication = null, onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        if (m.phase == Phase.LIVE) Text("●", color = LIVE_RED, fontSize = 10.sp)
        Spacer(Modifier.width(4.dp))
        Crest(m.home, 16)
        Text(" ${centre(m)} ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fg)
        Crest(m.away, 16)
        if (m.phase == Phase.LIVE) m.minute?.let { Text(" $it", fontSize = 11.sp, color = fg) }
    }
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

/** The feed's picture from its look; before any, the two crests faint. */
@Composable
private fun Preview(file: java.io.File?, label: String?, match: Match, looks: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(130.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
    ) {
        if (file != null) {
            AsyncImage(
                // The same file is rewritten by a new look: key the request on it.
                model = coil.request.ImageRequest.Builder(LocalContext.current).data(file)
                    .memoryCacheKey("${file.path}#${file.lastModified()}#$looks").build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (label != null) {
                Text(
                    "● " + stringResource(R.string.football_preview) + " · " + label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        } else {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.alpha(0.18f)) { Crest(match.home, 80) }
                Box(Modifier.alpha(0.18f)) { Crest(match.away, 80) }
            }
        }
    }
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

@Composable
private fun HeroTeam(team: Team) {
    Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Crest(team, 60)
        Spacer(Modifier.height(4.dp))
        Text(
            team.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WatchButton(modifier: Modifier = Modifier, sub: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .focusLift(focused)
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        val fg = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        Text("⚡ " + stringResource(R.string.football_watch), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = fg)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) MaterialTheme.colorScheme.primary else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
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
    onOpen: (FootballViewModel.Item) -> Unit,
    onHold: (FootballViewModel.Item) -> Unit,
    action: @Composable (FootballViewModel.Item) -> String,
    /** Say what OK and holding OK do, under the row. */
    hint: Boolean = false,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(items, key = { it.match.id }) { item -> MatchCard(item, action(item), onHold = { onHold(item) }) { onOpen(item) } }
        }
        if (hint) {
            Text(
                stringResource(R.string.football_hold_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun MatchCard(item: FootballViewModel.Item, action: String, onHold: () -> Unit, onClick: () -> Unit) {
    val m = item.match
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .width(190.dp)
            .focusLift(focused)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (focused) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape)
                else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape),
            )
            .onFocusChanged { focused = it.isFocused }
            .onOkHeld(onHold)
            .clickable(interactionSource = null, indication = null, onClick = onClick),
    ) {
        val thumb = item.highlights
        if (m.phase == Phase.FINISHED && thumb != null) {
            Box(Modifier.fillMaxWidth().height(72.dp)) {
                AsyncImage(
                    model = "https://i.ytimg.com/vi/$thumb/mqdefault.jpg",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Row(
                    Modifier.align(Alignment.Center).clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Crest(m.home, 22)
                    Text(
                        "  ${centre(m)}  ",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                    )
                    Crest(m.away, 22)
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Crest(m.home, 44)
                Text(
                    centre(m),
                    fontSize = if (m.phase == Phase.SCHEDULED) 20.sp else 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (m.phase == Phase.FINISHED) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Crest(m.away, 44)
            }
        }
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp)) {
            Text(
                if (m.goals) "${m.home.name} ${m.away.name}" else "${m.home.name} – ${m.away.name}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                status(m, withDay = true),
                style = MaterialTheme.typography.bodySmall,
                color = if (m.phase == Phase.LIVE) LIVE_RED else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                action,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (action.startsWith("⚡")) FontWeight.Bold else null,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun action(item: FootballViewModel.Item, viewModel: FootballViewModel, progress: FootballViewModel.Progress?, looks: Int): String = when {
    item.match.phase == Phase.FINISHED ->
        if (item.highlights != null) "▶ " + stringResource(R.string.football_highlights)
        else stringResource(R.string.football_highlights_soon)
    item.match.phase == Phase.OFF -> "—"
    viewModel.feedsOf(item).isNotEmpty() -> {
        val usable = remember(item, looks) { viewModel.sources(item).count { it.looked && it.usable } }
        "⚡ " + stringResource(R.string.football_watch) + when {
            progress?.matchId == item.match.id -> " · " + stringResource(R.string.football_scanning_short, progress.done, progress.total)
            usable > 0 -> " · " + stringResource(R.string.football_ready_count, usable)
            else -> ""
        }
    }
    else -> stringResource(R.string.football_channels_soon)
}

/** "1080p · 50 fps · 5.1" */
private fun quality(p: AutoScan.Probe): String = listOfNotNull(
    FeedLabel.resolution(p.height),
    p.fps?.let { "$it fps" },
    "Audio HD".takeIf { p.audioHd },
    p.audioChannels?.takeIf { it >= 6 }?.let { "5.1" },
).joinToString(" · ")

/** How long ago a feed was looked at: "4 min ago". */
@Composable
private fun ago(at: Long?): String {
    val min = at?.let { ((System.currentTimeMillis() - it) / 60_000L).toInt() } ?: return ""
    return if (min < 1) stringResource(R.string.football_just_now) else stringResource(R.string.football_minutes_ago, min)
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
private val FULL_DAY = DateTimeFormatter.ofPattern("EEEE d MMMM")

/** The big middle text: the score once it's started, the kick-off time before; GOL on the goal show. */
private fun centre(m: Match): String =
    if (m.goals && m.phase == Phase.LIVE) "GOL"
    else if (m.homeScore != null && m.awayScore != null) "${m.homeScore}-${m.awayScore}"
    else TIME.format(Instant.ofEpochMilli(m.kickoffUtcMillis).atZone(ZoneId.systemDefault()))

/** Under the hero's big kick-off time, the day alone; otherwise the same line as the cards. */
@Composable
private fun heroStatus(m: Match): String {
    if (m.phase != Phase.SCHEDULED) return status(m)
    val at = Instant.ofEpochMilli(m.kickoffUtcMillis).atZone(ZoneId.systemDefault())
    return if (at.toLocalDate() == LocalDate.now()) stringResource(R.string.football_today)
    else FULL_DAY.format(at).replaceFirstChar { it.uppercase() }
}

@Composable
private fun status(m: Match, withDay: Boolean = false): String {
    val at = Instant.ofEpochMilli(m.kickoffUtcMillis).atZone(ZoneId.systemDefault())
    val time = TIME.format(at)
    return when (m.phase) {
        Phase.LIVE -> m.minute ?: stringResource(R.string.football_live)
        Phase.FINISHED ->
            if (withDay && at.toLocalDate() != LocalDate.now()) stringResource(R.string.football_full_time) + " · " + DAY.format(at).replaceFirstChar { it.uppercase() }
            else stringResource(R.string.football_full_time) + " · " + time
        Phase.OFF -> "—"
        Phase.SCHEDULED ->
            if (withDay && at.toLocalDate() != LocalDate.now()) DAY.format(at).replaceFirstChar { it.uppercase() } + " · " + time
            else stringResource(R.string.football_kickoff, time)
    }
}
