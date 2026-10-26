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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import app.opentv.R
import app.opentv.data.model.Channel
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
        val channel = item.channels.firstOrNull()?.channel
        when {
            item.match.phase == Phase.FINISHED && highlight != null -> openHighlights(item, highlight)
            item.match.phase != Phase.FINISHED && channel != null -> onPlayChannel(channel)
        }
    }

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
                ui.hero?.let { hero -> item(key = "hero") { Hero(hero, onPlayChannel) } }
                if (ui.live.isNotEmpty()) item(key = "live") {
                    MatchRow(stringResource(R.string.football_live_row), ui.live, open)
                }
                if (ui.today.isNotEmpty()) item(key = "today") {
                    MatchRow(stringResource(R.string.football_today), ui.today, open)
                }
                if (ui.upcoming.isNotEmpty()) item(key = "upcoming") {
                    MatchRow(stringResource(R.string.football_upcoming), ui.upcoming, open)
                }
                if (ui.lastRound.isNotEmpty()) item(key = "last_round") {
                    MatchRow(stringResource(R.string.football_last_round), ui.lastRound, open)
                }
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

@Composable
private fun Hero(item: FootballViewModel.Item, onPlayChannel: (Channel) -> Unit) {
    val m = item.match
    val watch = remember { FocusRequester() }
    val first = item.channels.firstOrNull()
    LaunchedEffect(m.id, first != null) { if (first != null) runCatching { watch.requestFocus() } }

    Row(
        Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF16261B), MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface),
                ),
            )
            .padding(horizontal = 32.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
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
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeroTeam(m.home)
                Column(Modifier.width(150.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        centre(m),
                        fontSize = if (m.phase == Phase.SCHEDULED) 34.sp else 46.sp,
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
        }
        Column(Modifier.width(300.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (first == null) {
                Text(
                    stringResource(if (m.phase == Phase.LIVE) R.string.football_no_channel else R.string.football_channels_soon),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                WatchButton(first, Modifier.focusRequester(watch)) { onPlayChannel(first.channel) }
                val others = item.channels.drop(1)
                if (others.isNotEmpty()) {
                    Text(
                        stringResource(R.string.football_also_on),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(others, key = { it.channel.id }) { c -> ChannelChip(c.name) { onPlayChannel(c.channel) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroTeam(team: Team) {
    Column(Modifier.width(120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Crest(team, 76)
        Spacer(Modifier.height(6.dp))
        Text(
            team.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WatchButton(channel: MatchChannel, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .focusLift(focused)
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        val fg = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        Text("▶  " + stringResource(R.string.football_watch), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = fg)
        Text(channel.name, style = MaterialTheme.typography.bodyMedium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ChannelChip(name: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        name,
        style = MaterialTheme.typography.labelLarge,
        color = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

// ---- Rows ---------------------------------------------------------------------------------------

@Composable
private fun MatchRow(title: String, items: List<FootballViewModel.Item>, onOpen: (FootballViewModel.Item) -> Unit) {
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
            items(items, key = { it.match.id }) { item -> MatchCard(item) { onOpen(item) } }
        }
    }
}

@Composable
private fun MatchCard(item: FootballViewModel.Item, onClick: () -> Unit) {
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
                "${m.home.name} – ${m.away.name}",
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
                action(item),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun action(item: FootballViewModel.Item): String = when {
    item.match.phase == Phase.FINISHED ->
        if (item.highlights != null) "▶ " + stringResource(R.string.football_highlights)
        else stringResource(R.string.football_highlights_soon)
    item.match.phase == Phase.OFF -> "—"
    item.channels.isNotEmpty() -> "▶ " + item.channels.first().name +
        (if (item.channels.size > 1) "  +${item.channels.size - 1}" else "")
    else -> stringResource(R.string.football_channels_soon)
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

/** The big middle text: the score once it's started, the kick-off time before. */
private fun centre(m: Match): String =
    if (m.homeScore != null && m.awayScore != null) "${m.homeScore}-${m.awayScore}"
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
