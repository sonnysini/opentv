/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.channels

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.R
import app.opentv.core.FootballRequests
import app.opentv.core.ServiceLocator
import app.opentv.data.model.Channel
import app.opentv.data.model.Movie
import app.opentv.data.model.Recording
import app.opentv.data.model.RecordingStatus
import app.opentv.data.model.Series
import app.opentv.data.model.shownName
import app.opentv.data.parser.displayTitle
import app.opentv.data.repo.FootballRepository.Phase
import app.opentv.reminders.ReminderScheduler
import app.opentv.ui.ActionPanel
import app.opentv.ui.ActionRow
import app.opentv.ui.CARD_GAP
import app.opentv.ui.RecordRed
import app.opentv.ui.exactCardWidth
import app.opentv.ui.football.FootballViewModel
import app.opentv.ui.football.MatchTile
import app.opentv.ui.search.SearchViewModel
import app.opentv.ui.search.SearchViewModel.LiveHit
import app.opentv.ui.startFocus
import app.opentv.ui.vod.PosterCard
import app.opentv.ui.wholeCardsWindow
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Search, the Stremio way: a bar on top that opens the TV's own keyboard (on a Fire TV a full-screen
 * window: results can't show while typing anyway), then one row per tab in the menu's order — live
 * TV (channels, then guide programmes from now on), football, movies, series, recordings. Rows with
 * nothing are left out; the page scrolls down. The keyboard's Search key closes it and puts the
 * focus on the first result; up to the bar and OK types again.
 */
@Composable
fun SearchScreen(
    onPlayChannel: (Channel) -> Unit,
    onPlayMovie: (Movie) -> Unit,
    onOpenSeries: (Series) -> Unit,
    onPlayRecording: (Recording) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchViewModel = viewModel(),
) {
    var text by rememberSaveable { mutableStateOf("") }
    // The keyboard opens by itself once, on the way in; not again on the way back from the player.
    var opened by rememberSaveable { mutableStateOf(false) }
    val results by viewModel.results.collectAsStateWithLifecycle()
    val field = remember { FocusRequester() }
    val firstResult = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var toResults by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<LiveHit.InGuide?>(null) }

    LaunchedEffect(Unit) {
        if (!opened) {
            opened = true
            runCatching { field.requestFocus() }
            delay(KEYBOARD_DELAY_MILLIS)
            keyboard?.show()
        } else if (!results.isEmpty) {
            delay(KEYBOARD_DELAY_MILLIS)
            runCatching { firstResult.requestFocus() }
        }
    }
    // Typed and the keyboard closed with Back rather than Search: the results are there all the same.
    LaunchedEffect(text) { viewModel.search(text) }
    LaunchedEffect(results, toResults) {
        if (!toResults || !results.done || results.query != text.trim()) return@LaunchedEffect
        toResults = false
        if (!results.isEmpty) {
            delay(FOCUS_DELAY_MILLIS)
            runCatching { firstResult.requestFocus() }
        }
    }
    BackHandler { onBack() }

    val submit = {
        viewModel.search(text, now = true)
        keyboard?.hide()
        toResults = true
    }

    Column(Modifier.fillMaxSize().padding(top = 22.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = MARGIN),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.nav_search).uppercase(),
                fontFamily = Oswald,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(20.dp))
            SearchBar(
                text = text,
                onText = { if (it.length <= MAX_QUERY) text = it },
                onSubmit = submit,
                onDown = { runCatching { firstResult.requestFocus() } },
                modifier = Modifier.weight(1f).focusRequester(field),
            )
        }
        Spacer(Modifier.height(14.dp))

        val q = text.trim()
        when {
            q.length < 2 -> Unit
            results.done && results.query == q && results.isEmpty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.search_no_results, q),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                val tile = exactCardWidth(maxWidth, TILES_PER_ROW, MARGIN)
                val poster = exactCardWidth(maxWidth, POSTERS_PER_ROW, MARGIN)
                // The first row with something gets the focus when the keyboard closes.
                val first = when {
                    results.live.isNotEmpty() -> 0
                    results.matches.isNotEmpty() -> 1
                    results.movies.isNotEmpty() -> 2
                    results.series.isNotEmpty() -> 3
                    else -> 4
                }
                fun Modifier.firstOf(row: Int, index: Int) = if (row == first && index == 0) focusRequester(firstResult) else this
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (results.live.isNotEmpty()) item(key = "live") {
                        Shelf(stringResource(R.string.nav_live_tv), results.live.size) {
                            itemsIndexed(results.live, key = { _, it -> it.key }) { i, hit ->
                                LiveCard(hit, tile, Modifier.firstOf(0, i)) {
                                    when (hit) {
                                        is LiveHit.OnChannel -> onPlayChannel(hit.channel)
                                        is LiveHit.InGuide ->
                                            if (hit.programme.startUtcMillis <= System.currentTimeMillis()) onPlayChannel(hit.channel)
                                            else panel = hit
                                    }
                                }
                            }
                        }
                    }
                    if (results.matches.isNotEmpty()) item(key = "football") {
                        Shelf(stringResource(R.string.nav_football), results.matches.size) {
                            itemsIndexed(results.matches, key = { _, it -> it.match.id }) { i, item ->
                                MatchTile(item, tile, Modifier.firstOf(1, i), onHold = {}) { openMatch(item) }
                            }
                        }
                    }
                    if (results.movies.isNotEmpty()) item(key = "movies") {
                        Shelf(stringResource(R.string.nav_movies), results.movies.size) {
                            itemsIndexed(results.movies, key = { _, it -> it.id }) { i, movie ->
                                PosterCard(
                                    title = movie.displayTitle,
                                    posterUrl = movie.posterUrl,
                                    subtitle = movie.year?.toString(),
                                    rating = movie.rating,
                                    onClick = { onPlayMovie(movie) },
                                    modifier = Modifier.firstOf(2, i).width(poster),
                                )
                            }
                        }
                    }
                    if (results.series.isNotEmpty()) item(key = "series") {
                        Shelf(stringResource(R.string.nav_shows), results.series.size) {
                            itemsIndexed(results.series, key = { _, it -> it.id }) { i, show ->
                                PosterCard(
                                    title = show.displayTitle,
                                    posterUrl = show.posterUrl,
                                    subtitle = show.year?.toString(),
                                    rating = show.rating,
                                    onClick = { onOpenSeries(show) },
                                    modifier = Modifier.firstOf(3, i).width(poster),
                                )
                            }
                        }
                    }
                    if (results.recordings.isNotEmpty()) item(key = "recordings") {
                        Shelf(stringResource(R.string.nav_recordings), results.recordings.size) {
                            itemsIndexed(results.recordings, key = { _, it -> it.id }) { i, rec ->
                                RecordingCard(rec, tile, Modifier.firstOf(4, i)) { onPlayRecording(rec) }
                            }
                        }
                    }
                }
            }
        }
    }

    panel?.let { hit ->
        LaterPanel(
            hit = hit,
            onWatch = { panel = null; onPlayChannel(hit.channel) },
            onDone = { panel = null; viewModel.refresh() },
            onDismiss = { panel = null },
        )
    }
}

/** A match off the results: the Calcio tab takes it from there, as for a launcher's match card. */
private fun openMatch(item: FootballViewModel.Item) {
    val m = item.match
    val soon = m.kickoffUtcMillis - System.currentTimeMillis() < WATCH_AHEAD_MILLIS
    val action = when {
        m.phase == Phase.FINISHED && item.highlights != null -> FootballRequests.Action.HIGHLIGHTS
        m.phase == Phase.LIVE || (m.phase == Phase.SCHEDULED && soon) -> FootballRequests.Action.WATCH
        else -> FootballRequests.Action.OPEN
    }
    FootballRequests.request(FootballRequests.Request(action, m.id, item.highlights))
}

/** The pill on top: what's typed, "OK to type" at the end. */
@Composable
private fun SearchBar(
    text: String,
    onText: (String) -> Unit,
    onSubmit: () -> Unit,
    onDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(50)
    val scheme = MaterialTheme.colorScheme
    BasicTextField(
        value = text,
        onValueChange = onText,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleLarge.copy(color = scheme.onSurface),
        cursorBrush = SolidColor(scheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
        // Fire TV's "Next" key (Avanti) closes its keyboard with ImeAction.Next whatever was asked.
        keyboardActions = KeyboardActions(
            onSearch = { onSubmit() }, onDone = { onSubmit() }, onGo = { onSubmit() }, onNext = { onSubmit() },
        ),
        // The remote, with the keyboard closed: the field would keep the arrows for its cursor.
        // Down goes to the results, OK opens the keyboard again.
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                when (e.key) {
                    Key.DirectionDown -> {
                        if (e.type == KeyEventType.KeyDown) onDown()
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (e.type == KeyEventType.KeyUp) keyboard?.show()
                        true
                    }
                    else -> false
                }
            },
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Color.White.copy(alpha = if (focused) 0.10f else 0.05f))
                    .border(2.dp, if (focused) Color.White else Color.White.copy(alpha = 0.14f), shape)
                    .padding(horizontal = 22.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_nav_search),
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(14.dp))
                Box(Modifier.weight(1f)) { inner() }
                Spacer(Modifier.width(14.dp))
                Text(
                    stringResource(R.string.search_ok_to_type),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        },
    )
}

/** A titled row of whole cards, with how many there are. */
@Composable
private fun Shelf(title: String, count: Int, content: LazyListScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = MARGIN, top = 8.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                count.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        LazyRow(
            modifier = Modifier.wholeCardsWindow(MARGIN),
            contentPadding = PaddingValues(horizontal = MARGIN, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(CARD_GAP),
            content = content,
        )
    }
}

/**
 * A live-TV result as a 16:9 card: what it is top left (CHANNEL, or the channel showing the
 * programme), LIVE or when top right, the name big in Oswald, the progress along the bottom while on.
 */
@Composable
private fun LiveCard(hit: LiveHit, width: Dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val now = System.currentTimeMillis()
    val programme = when (hit) {
        is LiveHit.OnChannel -> hit.now
        is LiveHit.InGuide -> hit.programme
    }
    val live = programme != null && programme.isLiveAt(now)
    val channel = when (hit) {
        is LiveHit.OnChannel -> hit.channel
        is LiveHit.InGuide -> hit.channel
    }
    val big: String
    val subtitle: String
    val kicker: String
    when (hit) {
        is LiveHit.OnChannel -> {
            big = channel.shownName
            subtitle = hit.now?.let { stringResource(R.string.guide_now_title, it.title) }.orEmpty()
            kicker = stringResource(R.string.search_channel).uppercase()
        }
        is LiveHit.InGuide -> {
            big = hit.programme.title
            subtitle = (if (live) stringResource(R.string.tv_hero_on_now) else whenText(hit.programme.startUtcMillis, now)) +
                " · " + channel.shownName
            kicker = channel.shownName.uppercase()
        }
    }
    val art = (hit as? LiveHit.InGuide)?.programme?.iconUrl
    val back = if (hit is LiveHit.InGuide) GUIDE_BRUSH else CHANNEL_BRUSH
    val booked = (hit as? LiveHit.InGuide)?.booked == true
    val later = if (hit is LiveHit.InGuide && !live) whenText(hit.programme.startUtcMillis, now) else null
    Card(width, modifier, onClick, big, subtitle) {
        Box(Modifier.fillMaxSize().background(back))
        if (art != null) {
            AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(SCRIM))
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Tag(kicker, Color.Black.copy(alpha = 0.55f), Color.White, Modifier.weight(1f, fill = false))
            Spacer(Modifier.weight(0.01f))
            if (booked) {
                Spacer(Modifier.width(4.dp))
                Tag("● REC", RecordRed, Color.White)
            }
            if (live) {
                Spacer(Modifier.width(4.dp))
                Tag(stringResource(R.string.football_live), LIVE_RED, Color.White)
            } else if (later != null) {
                Spacer(Modifier.width(4.dp))
                Tag(later, Color.White, Color(0xFF111111))
            }
        }
        BigTitle(big, width)
        if (live && programme != null) ProgressLine(programme.progressAt(now), MaterialTheme.colorScheme.primary)
    }
}

/** A recording as a 16:9 card: REC top right, its title big. */
@Composable
private fun RecordingCard(rec: Recording, width: Dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val now = System.currentTimeMillis()
    val day = remember(rec.startedAtMillis) { SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(rec.startedAtMillis)) }
    val recording = rec.status == RecordingStatus.RECORDING
    Card(width, modifier, onClick, rec.title, "${rec.channelName} · $day") {
        Box(Modifier.fillMaxSize().background(CHANNEL_BRUSH))
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Tag(rec.channelName.uppercase(), Color.Black.copy(alpha = 0.55f), Color.White, Modifier.weight(1f, fill = false))
            Spacer(Modifier.weight(0.01f))
            Spacer(Modifier.width(4.dp))
            Tag(if (recording) "● REC" else "REC", RecordRed, Color.White)
        }
        BigTitle(rec.title, width)
        if (recording && rec.scheduledEndMillis > rec.startedAtMillis) {
            val p = (now - rec.startedAtMillis).toFloat() / (rec.scheduledEndMillis - rec.startedAtMillis)
            ProgressLine(p.coerceIn(0f, 1f), RecordRed)
        }
    }
}

@Composable
private fun BoxScope.ProgressLine(progress: Float, color: Color) {
    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.18f))) {
        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(color))
    }
}

/** The card frame shared by the live and recording results: 16:9 art, two lines under it. */
@Composable
private fun Card(
    width: Dp,
    modifier: Modifier,
    onClick: () -> Unit,
    title: String,
    subtitle: String,
    art: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier
            .width(width)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = null, indication = null, onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape)
                .then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier),
            content = art,
        )
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp),
        )
        if (subtitle.isNotBlank()) Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BoxScope.BigTitle(text: String, width: Dp) {
    Text(
        text.uppercase(),
        fontFamily = Oswald,
        fontWeight = FontWeight.Bold,
        fontSize = (width.value * 0.095f).coerceIn(14f, 22f).sp,
        lineHeight = (width.value * 0.105f).coerceIn(16f, 25f).sp,
        color = Color.White,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 10.dp, bottom = 12.dp),
    )
}

@Composable
private fun Tag(text: String, background: Color, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.clip(RoundedCornerShape(4.dp)).background(background).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** "OGGI 21:00", "DOMANI 13:00", "MER 23:15". */
@Composable
private fun whenText(startMillis: Long, now: Long): String {
    val day = when (daysFromToday(startMillis, now)) {
        0 -> stringResource(R.string.guide_today)
        1 -> stringResource(R.string.guide_tomorrow)
        else -> remember(startMillis) { SimpleDateFormat("EEE", Locale.getDefault()).format(Date(startMillis)) }
    }
    val time = remember(startMillis) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(startMillis)) }
    return "${day.uppercase().trimEnd('.')} $time"
}

private fun daysFromToday(millis: Long, now: Long): Int {
    fun midnight(t: Long) = Calendar.getInstance().apply {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    // An hour's slack for the days the clocks change.
    return ((midnight(millis) - midnight(now) + 3_600_000L) / 86_400_000L).toInt()
}

/**
 * A programme still to come, picked: book it, a reminder, or the channel now. The reminder is set
 * or cleared the way the guide does it.
 */
@Composable
private fun LaterPanel(hit: LiveHit.InGuide, onWatch: () -> Unit, onDone: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val graph = remember { ServiceLocator.get(context) }
    val scope = rememberCoroutineScope()
    val channel = hit.channel
    val programme = hit.programme
    var reminder by remember(hit.key) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(hit.key) { reminder = graph.reminderRepository.forProgramme(channel.id, programme.startUtcMillis) != null }
    val time = remember(programme.startUtcMillis, programme.endUtcMillis) {
        val f = SimpleDateFormat("HH:mm", Locale.getDefault())
        "${f.format(Date(programme.startUtcMillis))} – ${f.format(Date(programme.endUtcMillis))}"
    }
    val day = whenText(programme.startUtcMillis, System.currentTimeMillis()).substringBeforeLast(' ')
    ActionPanel(
        kicker = "$day $time · ${channel.shownName}",
        title = programme.title,
        onDismiss = onDismiss,
    ) { first ->
        if (!hit.booked) item(key = "record") {
            ActionRow(
                stringResource(R.string.panel_schedule), Modifier.startFocus(first), icon = R.drawable.ic_act_record, iconTint = RecordRed,
            ) {
                scope.launch {
                    graph.recordingEngine.scheduleProgramme(channel, programme)
                    onDone()
                }
                Toast.makeText(context, context.getString(R.string.rec_scheduled_title, programme.title), Toast.LENGTH_LONG).show()
            }
        }
        when (reminder) {
            true -> item(key = "reminder:off") {
                ActionRow(
                    stringResource(R.string.panel_cancel_reminder), Modifier.startFocus(first, on = hit.booked), icon = R.drawable.ic_act_bell_off,
                ) {
                    scope.launch {
                        graph.reminderRepository.forProgramme(channel.id, programme.startUtcMillis)?.let {
                            ReminderScheduler.cancel(context, it.id)
                            graph.reminderRepository.delete(it.id)
                        }
                        onDone()
                    }
                    Toast.makeText(context, context.getString(R.string.guide_reminder_removed), Toast.LENGTH_SHORT).show()
                }
            }
            false -> item(key = "reminder:on") {
                ActionRow(
                    stringResource(R.string.panel_remind_me), Modifier.startFocus(first, on = hit.booked), icon = R.drawable.ic_act_bell,
                ) {
                    scope.launch {
                        setReminder(graph, context, channel, programme, autoTune = false)
                        onDone()
                    }
                    Toast.makeText(context, context.getString(R.string.guide_reminder_set, programme.title), Toast.LENGTH_LONG).show()
                }
            }
            null -> Unit
        }
        item(key = "channel") {
            ActionRow(
                stringResource(R.string.guide_watch_channel),
                Modifier.startFocus(first, on = hit.booked && reminder == null),
                icon = R.drawable.ic_act_play,
            ) { onWatch() }
        }
    }
}

private val Oswald = FontFamily(Font(R.font.oswald_bold, FontWeight.Bold))
private val MARGIN = 48.dp
private const val TILES_PER_ROW = 5
private const val POSTERS_PER_ROW = 7
private const val MAX_QUERY = 60
private const val KEYBOARD_DELAY_MILLIS = 150L
private const val FOCUS_DELAY_MILLIS = 80L
private const val WATCH_AHEAD_MILLIS = 30L * 60 * 1000
private val LIVE_RED = Color(0xFFE50914)
private val CHANNEL_BRUSH = Brush.linearGradient(listOf(Color(0xFF34373A), Color(0xFF17191B)))
private val GUIDE_BRUSH = Brush.linearGradient(listOf(Color(0xFF0E4A8F), Color(0xFF0A1B36)))
private val SCRIM = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.85f)))
