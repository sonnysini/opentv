/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.football

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.opentv.R
import app.opentv.core.AppSettings
import app.opentv.core.FavoriteTeam
import app.opentv.core.ServiceLocator
import app.opentv.data.repo.FootballRepository
import app.opentv.data.repo.FootballRepository.Match
import app.opentv.data.repo.FootballRepository.Phase
import app.opentv.ui.FOCUS_VEIL_ALPHA
import app.opentv.ui.bottomFade
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The calendar's tabs: the user's own team, everything, then each competition. */
private const val TAB_TEAM = -1
private const val TAB_ALL = 0
private val COMPETITION_TABS = listOf(
    FootballRepository.SERIE_A, FootballRepository.CHAMPIONS, FootballRepository.EUROPA, FootballRepository.CONFERENCE,
)

/**
 * The football calendar, over the football home: the games of the next four weeks, a day at a
 * time, with tabs for the user's team, all of them and each competition. OK on a game hands it to
 * [onOpen] (the home brings its card into focus); BACK closes it.
 */
@Composable
internal fun FootballCalendar(onOpen: (Match) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { ServiceLocator.get(context).footballRepository }
    val settings = remember { AppSettings.get(context) }
    val team by settings.favoriteTeam.collectAsStateWithLifecycle()
    BackHandler(onBack = onClose)
    // null while loading; empty list = nothing (or FotMob unreachable with nothing on disk).
    val games by produceState<List<Match>?>(null) { value = runCatching { repo.calendar() }.getOrElse { emptyList() } }
    val teams by produceState(emptyList<FootballRepository.Team>()) { value = runCatching { repo.serieATeams() }.getOrElse { emptyList() } }
    var tab by rememberSaveable { mutableStateOf(if (team != null) TAB_TEAM else TAB_ALL) }
    val tabsFocus = remember { FocusRequester() }
    val listFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { tabsFocus.requestFocus() } }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 24.dp)) {
            Text(
                stringResource(R.string.nav_football).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.football_calendar).uppercase(),
                fontFamily = CalOswald,
                fontWeight = FontWeight.Bold,
                fontSize = 38.sp,
                color = Color.White,
            )
            Spacer(Modifier.height(14.dp))
            CalendarTabs(
                team = team,
                selected = tab,
                requester = tabsFocus,
                below = listFocus,
                onSelect = { tab = it },
            )
            Spacer(Modifier.height(14.dp))
            val all = games
            when {
                all == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                tab == TAB_TEAM && team == null -> TeamPicker(teams, listFocus) { settings.setFavoriteTeam(FavoriteTeam(it.id, it.name)) }
                else -> {
                    val shown = remember(all, tab, team) {
                        when (tab) {
                            TAB_ALL -> all
                            TAB_TEAM -> team?.let { t -> all.filter { it.home.id == t.id || it.away.id == t.id } }.orEmpty()
                            else -> all.filter { it.league == tab }
                        }
                    }
                    if (shown.isEmpty()) {
                        Text(
                            stringResource(if (all.isEmpty()) R.string.football_calendar_loading_failed else R.string.football_calendar_empty),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 24.dp).focusRequester(listFocus),
                        )
                    } else {
                        GameList(shown, team, listFocus, onOpen)
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun CalendarTabs(
    team: FavoriteTeam?,
    selected: Int,
    requester: FocusRequester,
    below: FocusRequester,
    onSelect: (Int) -> Unit,
) {
    val keys = listOf(TAB_TEAM, TAB_ALL) + COMPETITION_TABS
    val select by rememberUpdatedState(onSelect)
    val requesters = remember { keys.associateWith { FocusRequester() } }
    Row(
        Modifier
            .focusRequester(requester)
            .focusProperties { enter = { requesters[selected] ?: FocusRequester.Default } }
            .focusGroup(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        keys.forEach { key ->
            var focused by remember { mutableStateOf(false) }
            val isSelected = key == selected
            // As the live page's tabs: resting on one opens it.
            LaunchedEffect(focused, isSelected) {
                if (!focused || isSelected) return@LaunchedEffect
                delay(TAB_FOLLOW_MILLIS)
                select(key)
            }
            Column(
                Modifier
                    .focusRequester(requesters.getValue(key))
                    .focusProperties { down = below }
                    .onFocusChanged { focused = it.isFocused }
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (focused) MaterialTheme.colorScheme.primary.copy(alpha = FOCUS_VEIL_ALPHA) else Color.Transparent)
                    .clickable(interactionSource = null, indication = null) { select(key) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        key == TAB_TEAM && team != null -> {
                            AsyncImage(model = FootballRepository.Team(team.id, team.name).crestUrl, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        key > 0 -> {
                            AsyncImage(model = darkLeagueLogo(key), contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                    }
                    Text(
                        tabLabel(key, team).uppercase(),
                        color = when {
                            focused -> MaterialTheme.colorScheme.primary
                            isSelected -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontFamily = CalOswald,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 18.sp,
                        letterSpacing = 1.5.sp,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.width(28.dp).height(2.dp)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun tabLabel(key: Int, team: FavoriteTeam?): String = when (key) {
    TAB_TEAM -> "★ " + (team?.name ?: stringResource(R.string.football_calendar_my_team))
    TAB_ALL -> stringResource(R.string.football_calendar_all)
    FootballRepository.CHAMPIONS -> "Champions"
    FootballRepository.CONFERENCE -> "Conference"
    else -> leagueName(key)
}

/** The games a day at a time: "OGGI · SABATO 28 NOVEMBRE", then its games. */
@Composable
private fun GameList(games: List<Match>, team: FavoriteTeam?, first: FocusRequester, onOpen: (Match) -> Unit) {
    val zone = ZoneId.systemDefault()
    val byDay = remember(games) { games.groupBy { Instant.ofEpochMilli(it.kickoffUtcMillis).atZone(zone).toLocalDate() } }
    val firstId = games.first().id
    LazyColumn(
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.fillMaxSize().bottomFade(),
    ) {
        byDay.forEach { (day, list) ->
            item(key = "day:$day") { DayHeader(day) }
            items(list, key = { it.id }) { m ->
                GameLine(m, team, if (m.id == firstId) Modifier.focusRequester(first) else Modifier) { onOpen(m) }
            }
        }
    }
}

@Composable
private fun DayHeader(day: LocalDate) {
    val today = LocalDate.now()
    val rel = when (day) {
        today -> stringResource(R.string.football_today)
        today.plusDays(1) -> stringResource(R.string.football_calendar_tomorrow)
        else -> null
    }
    Row(Modifier.padding(start = 2.dp, top = 10.dp, bottom = 2.dp)) {
        val style = MaterialTheme.typography.labelLarge
        if (rel != null) Text(
            "${rel.uppercase()} · ",
            style = style, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            DAY_HEADER.format(day).uppercase(),
            style = style, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GameLine(m: Match, team: FavoriteTeam?, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(7.dp)
    val primary = MaterialTheme.colorScheme.primary
    val mine = team != null && (m.home.id == team.id || m.away.id == team.id)
    val stripe = if (mine) TeamColors.of(if (m.home.id == team?.id) m.home else m.away)
        .let { if (it.luminance() < 0.05f) Color.White else it } else Color.Transparent
    Row(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(if (focused) primary.copy(alpha = FOCUS_VEIL_ALPHA * 0.6f) else LINE_GROUND)
            .border(2.dp, if (focused) primary else Color.Transparent, shape)
            .clickable(interactionSource = null, indication = null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(46.dp).background(stripe))
        Spacer(Modifier.width(13.dp))
        Text(
            TIME.format(Instant.ofEpochMilli(m.kickoffUtcMillis).atZone(ZoneId.systemDefault())),
            fontFamily = CalOswald, fontWeight = FontWeight.Normal, fontSize = 19.sp, color = Color.White,
            modifier = Modifier.width(56.dp),
        )
        AsyncImage(model = darkLeagueLogo(m.league), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        AsyncImage(model = m.home.crestUrl, contentDescription = null, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(9.dp))
        Text(
            "${m.home.name}  –  ${m.away.name}",
            fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(9.dp))
        AsyncImage(model = m.away.crestUrl, contentDescription = null, modifier = Modifier.size(26.dp))
        Spacer(Modifier.weight(1f))
        val round = m.round?.takeIf { r -> r.all(Char::isDigit) }
        Text(
            if (round != null && m.league == FootballRepository.SERIE_A) stringResource(R.string.football_matchday, round) else leagueName(m.league),
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
        )
        Spacer(Modifier.width(14.dp))
        GameState(m)
        Spacer(Modifier.width(14.dp))
    }
}

/** On the right of a game: LIVE and the minute, the score once over, "From 14:45" today. */
@Composable
private fun GameState(m: Match) {
    val pill = Modifier.clip(RoundedCornerShape(50)).padding(horizontal = 0.dp)
    when {
        m.phase == Phase.LIVE -> Text(
            listOfNotNull(stringResource(R.string.football_live), m.minute).joinToString(" "),
            fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
            modifier = pill.background(LIVE).padding(horizontal = 9.dp, vertical = 3.dp),
        )
        m.phase == Phase.FINISHED -> Text(
            "${m.homeScore ?: "-"} – ${m.awayScore ?: "-"}",
            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = pill.background(PILL).padding(horizontal = 10.dp, vertical = 3.dp),
        )
        Instant.ofEpochMilli(m.kickoffUtcMillis).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now() -> Text(
            stringResource(R.string.football_watch_from, TIME.format(Instant.ofEpochMilli(m.kickoffUtcMillis - OPENS_BEFORE_MILLIS).atZone(ZoneId.systemDefault()))),
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = pill.background(PILL).padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/** The first time the team tab opens: the Serie A crests to pick one from. */
@Composable
private fun TeamPicker(teams: List<FootballRepository.Team>, first: FocusRequester, onPick: (FootballRepository.Team) -> Unit) {
    Column {
        Text(stringResource(R.string.football_calendar_pick_team), style = MaterialTheme.typography.titleLarge, color = Color.White)
        Text(
            stringResource(R.string.football_calendar_pick_team_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(teams, key = { it.id }) { t ->
                var focused by remember { mutableStateOf(false) }
                val shape = RoundedCornerShape(7.dp)
                Row(
                    (if (t.id == teams.first().id) Modifier.focusRequester(first) else Modifier)
                        .height(46.dp)
                        .onFocusChanged { focused = it.isFocused }
                        .clip(shape)
                        .background(if (focused) MaterialTheme.colorScheme.primary.copy(alpha = FOCUS_VEIL_ALPHA * 0.6f) else LINE_GROUND)
                        .border(2.dp, if (focused) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
                        .clickable(interactionSource = null, indication = null) { onPick(t) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(model = t.crestUrl, contentDescription = null, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(t.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** The pick of Settings › Playback: the same crests, in a window. */
@Composable
internal fun FavoriteTeamList(teams: List<FootballRepository.Team>, current: Long?, onPick: (FootballRepository.Team?) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(teams.isNotEmpty()) { if (teams.isNotEmpty()) runCatching { first.requestFocus() } }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.height(360.dp),
    ) {
        items(teams, key = { it.id }) { t ->
            var focused by remember { mutableStateOf(false) }
            val shape = RoundedCornerShape(7.dp)
            val on = t.id == current
            Row(
                (if (on || (current == null && t.id == teams.first().id)) Modifier.focusRequester(first) else Modifier)
                    .height(42.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .clip(shape)
                    .background(if (focused) MaterialTheme.colorScheme.primary.copy(alpha = FOCUS_VEIL_ALPHA * 0.6f) else LINE_GROUND)
                    .border(2.dp, if (focused || on) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
                    .clickable(interactionSource = null, indication = null) { onPick(t) }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(model = t.crestUrl, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(t.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun darkLeagueLogo(league: Int) = "https://images.fotmob.com/image_resources/logo/leaguelogo/dark/$league.png"

private const val TAB_FOLLOW_MILLIS = 300L
/** "From" on today's games: the feeds are looked at a quarter of an hour before kick-off. */
private const val OPENS_BEFORE_MILLIS = 15 * 60_000L
private val LINE_GROUND = Color.White.copy(alpha = 0.035f)
private val PILL = Color(0x40565C59)
private val LIVE = Color(0xFFE50914)
private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val DAY_HEADER = DateTimeFormatter.ofPattern("EEEE d MMMM")
private val CalOswald = FontFamily(
    Font(R.font.oswald_regular, FontWeight.Normal),
    Font(R.font.oswald_bold, FontWeight.Bold),
)
