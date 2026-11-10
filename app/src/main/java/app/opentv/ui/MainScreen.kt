/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.opentv.R
import app.opentv.core.AppSettings
import app.opentv.core.StatusBus
import app.opentv.core.findActivity
import app.opentv.data.model.Channel
import app.opentv.data.model.Movie
import app.opentv.data.model.Recording
import app.opentv.data.model.Series
import app.opentv.ui.channels.HomeScreen
import app.opentv.ui.recordings.RecordingsScreen
import app.opentv.ui.vod.MoviesScreen
import app.opentv.ui.vod.SeriesScreen
import kotlinx.coroutines.delay

/**
 * The shell: a slim navigation rail down the left over a content area. The rail sits collapsed as
 * an icon strip and expands to show labels the moment focus lands in it — the TiviMate-style side
 * menu people asked for, instead of a top bar that ate a row of the guide. It overlays the content
 * rather than pushing it, so expanding the menu never reflows the guide underneath.
 */
enum class Tab(val labelRes: Int, val icon: Int, val iconSelected: Int) {
    LIVE(R.string.nav_live_tv, R.drawable.ic_nav_live, R.drawable.ic_nav_live_fill),
    FOOTBALL(R.string.nav_football, R.drawable.ic_nav_football, R.drawable.ic_nav_football_fill),
    MOVIES(R.string.nav_movies, R.drawable.ic_nav_movies, R.drawable.ic_nav_movies_fill),
    SHOWS(R.string.nav_shows, R.drawable.ic_nav_shows, R.drawable.ic_nav_shows_fill),
    RECORDINGS(R.string.nav_recordings, R.drawable.ic_nav_recordings, R.drawable.ic_nav_recordings_fill),
}


@Composable
fun MainScreen(
    isTelevision: Boolean,
    hasSources: Boolean,
    isSyncing: Boolean,
    onPlayChannel: (Channel) -> Unit,
    onOpenMovie: (Movie) -> Unit,
    onOpenSeries: (Series) -> Unit,
    onResume: (mediaKey: String, url: String, title: String) -> Unit,
    onAddSource: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlayRecording: (Recording) -> Unit,
    onPlayCatchup: (mediaKey: String, url: String, title: String, ua: String) -> Unit,
) {
    // Content-type toggles: a switched-off type has its tab hidden here (and its sync skipped in
    // CatalogRepository). Recordings is never a content type, so it always stays — which also means
    // there is always at least one tab and never a blank shell, even with all three types off.
    val context = LocalContext.current
    val settings = remember { AppSettings.get(context) }
    val liveEnabled by settings.liveEnabled.collectAsStateWithLifecycle()
    val moviesEnabled by settings.moviesEnabled.collectAsStateWithLifecycle()
    val seriesEnabled by settings.seriesEnabled.collectAsStateWithLifecycle()
    val visibleTabs = remember(liveEnabled, moviesEnabled, seriesEnabled) {
        buildList {
            if (liveEnabled) add(Tab.LIVE)
            if (liveEnabled) add(Tab.FOOTBALL)
            if (moviesEnabled) add(Tab.MOVIES)
            if (seriesEnabled) add(Tab.SHOWS)
            add(Tab.RECORDINGS)
        }
    }
    // The default/home tab is the first visible one — Live TV normally, otherwise the first type
    // still switched on (or Recordings if none are).
    val homeTab = visibleTabs.first()

    // Saveable: coming back from the player must land on the tab it was opened from (Football).
    var tab by rememberSaveable { mutableStateOf(homeTab) }
    // A launcher asked for Football: switch to it; the Football screen consumes the request.
    val footballRequest by app.opentv.core.FootballRequests.request.collectAsStateWithLifecycle()
    LaunchedEffect(footballRequest, visibleTabs) {
        if (footballRequest == null) return@LaunchedEffect
        if (Tab.FOOTBALL in visibleTabs) tab = Tab.FOOTBALL else app.opentv.core.FootballRequests.consume()
    }

    // The rail stays as an icon strip beside the full-screen live list too, as on every other tab;
    // LEFT out of the list (or Back from the groups) moves focus into it, which opens it.
    var navRevealed by remember { mutableStateOf(false) }
    // Focus is in the menu: the page opening under it leaves the focus there.
    val menuFocused = remember { mutableStateOf(false) }
    val menuHasFocus = remember { { menuFocused.value } }
    val navFocus = remember { FocusRequester() }
    // Into Live TV from the rail: the groups, not whatever sits nearest the menu item (the top's
    // button). Set by the full-screen list while it's on screen.
    var liveNavExit by remember { mutableStateOf<(() -> Boolean)?>(null) }
    // Bumped by OK on a rail entry: the page then takes focus (see focusOnEnter below).
    var enterPage by remember { mutableIntStateOf(0) }
    LaunchedEffect(navRevealed) {
        if (navRevealed) {
            delay(50)
            runCatching { navFocus.requestFocus() }
        }
    }

    // If the selected tab gets hidden (its type toggled off while it's open), drop back to the
    // home tab so the content area never tries to show a tab that's no longer there.
    LaunchedEffect(visibleTabs) {
        if (tab !in visibleTabs) tab = homeTab
    }

    // Back from a non-home tab returns to the home tab rather than dropping out of the app.
    BackHandler(enabled = tab != homeTab) { tab = homeTab }

    // On the home tab, Back would otherwise drop straight out to the TV launcher — one stray press
    // and you've closed the app. Ask first. (A dialog or panel open in a child screen swallows Back
    // before this, so this only fires at the true root.)
    var showExit by remember { mutableStateOf(false) }
    BackHandler(enabled = tab == homeTab) { showExit = true }
    if (showExit) {
        AlertDialog(
            onDismissRequest = { showExit = false },
            title = { Text(stringResource(R.string.exit_title)) },
            text = { Text(stringResource(R.string.exit_body)) },
            confirmButton = {
                TextButton(onClick = { showExit = false; context.findActivity()?.finish() }) {
                    Text(stringResource(R.string.exit_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExit = false }) { Text(stringResource(R.string.exit_cancel)) }
            },
        )
    }

    // The menu is a glass capsule floating over the page's left edge: closed it is a strip of
    // icons the page sits right against, open it spreads over the page, which never moves — on
    // every tab, Live TV included.
    val pageIn = remember { Animatable(1f) }
    var firstTab by remember { mutableStateOf(true) }
    LaunchedEffect(tab) {
        if (firstTab) { firstTab = false; return@LaunchedEffect }
        // The new page slides in from the right as it fades in.
        pageIn.snapTo(0f)
        pageIn.animateTo(1f, tween(PAGE_IN_MILLIS, easing = OutCubic))
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
      Box(Modifier.weight(1f).fillMaxWidth()) {
          Box(
              Modifier
                  .padding(start = PAGE_START)
                  .fillMaxSize()
                  .clipToBounds()
                  .graphicsLayer {
                      val k = pageIn.value
                      alpha = k
                      translationX = (1f - k) * PAGE_IN_SHIFT.toPx()
                  }
                  .focusOnEnter(
                      key = enterPage,
                      enabled = enterPage > 0,
                      // Live TV: into the groups, as RIGHT out of the rail does.
                      enter = { tab == Tab.LIVE && liveNavExit?.invoke() == true },
                  ),
          ) {
           CompositionLocalProvider(LocalMenuHasFocus provides menuHasFocus) {
            when (tab) {
                Tab.LIVE -> HomeScreen(
                    isTelevision = isTelevision,
                    hasSources = hasSources,
                    isSyncing = isSyncing,
                    onPlayChannel = onPlayChannel,
                    onAddSource = onAddSource,
                    onRefresh = onRefresh,
                    onPlayCatchup = onPlayCatchup,
                    onRevealNav = { navRevealed = true },
                    registerNavExit = { liveNavExit = it },
                )
                Tab.FOOTBALL -> app.opentv.ui.football.FootballScreen(
                    onPlayChannel = onPlayChannel,
                    // A one-off stream with a seek bar: the VOD player, like catch-up.
                    onPlayVideo = { key, url, title -> onPlayCatchup(key, url, title, "") },
                )
                Tab.MOVIES -> MoviesScreen(
                    onOpenMovie = onOpenMovie,
                    onOpenSeries = onOpenSeries,
                    onResume = onResume,
                    onOpenSearch = onOpenSearch,
                    hasSources = hasSources,
                    isSyncing = isSyncing,
                )
                Tab.SHOWS -> SeriesScreen(
                    onOpenSeries = onOpenSeries,
                    onResume = onResume,
                    onOpenSearch = onOpenSearch,
                    hasSources = hasSources,
                    isSyncing = isSyncing,
                )
                Tab.RECORDINGS -> RecordingsScreen(onPlay = onPlayRecording)
            }
           }
          }
          NavRail(
              tabs = visibleTabs,
              current = tab,
              // OK on a page's entry opens it and moves focus into it, closing the rail — the same
              // on every page (and again on the page already open).
              onSelect = { tab = it; enterPage++ },
              // Resting on a page's entry opens it, the menu staying open.
              onAutoSelect = { tab = it },
              // RIGHT, or a while without a key: back into the page.
              onEnterPage = { enterPage++ },
              currentFocus = navFocus,
              onFocusLeft = { navRevealed = false },
              onFocusChange = { menuFocused.value = it },
              onOpenSearch = onOpenSearch,
              onOpenSettings = onOpenSettings,
              modifier = Modifier.align(Alignment.CenterStart),
          )
      }
      StatusBar()
    }
}

/**
 * A slim line along the bottom that says what the app is doing in the background — loading
 * channels, building the guide, loading movies — so a slow moment on a big provider reads as work
 * in progress, not a frozen screen. Invisible when there's nothing to report.
 */
@Composable
private fun StatusBar() {
    val message by StatusBus.message.collectAsStateWithLifecycle()
    val progress by StatusBus.progress.collectAsStateWithLifecycle()
    val text = message ?: return
    val p = progress
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (p == null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    "${(p * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (p != null) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { p },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * The menu: a dark glass capsule floating at the left edge, as tall as its entries and centred
 * down the screen. Closed, it is the logo mark and a column of icons; focus landing in it opens it
 * over the page to show the names. The open page is its icon filled and its name in the accent.
 *
 * Moving through it picks for you: the focused entry is a faint accent pill that fills from the
 * left, and once full ([AUTO_SELECT_MILLIS]) its page opens, the menu staying open. Moving on before
 * that changes nothing. Search and Settings open only with OK. A few seconds without a key, the
 * capsule closes and focus goes back into the page; RIGHT or OK goes there at once.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun NavRail(
    tabs: List<Tab>,
    current: Tab,
    onSelect: (Tab) -> Unit,
    onAutoSelect: (Tab) -> Unit,
    onEnterPage: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    // Put on the current tab's item, so the shell can drop focus into the rail when it reveals it.
    currentFocus: FocusRequester? = null,
    // Focus has moved out of the rail (after having been in it).
    onFocusLeft: () -> Unit = {},
    // Focus is in the rail (true) or not, at once.
    onFocusChange: (Boolean) -> Unit = {},
) {
    // Open whenever focus is anywhere inside; back to icons when it leaves.
    var expanded by remember { mutableStateOf(false) }
    // Report "focus left" only once it has really gone: moving between two items can flicker the
    // group's focus state for a frame, and acting on that hid the rail mid-move (DOWN from Live TV
    // dropped you back into the channel list). The effect is cancelled if focus comes straight back.
    var hadFocus by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded) {
            hadFocus = true
        } else if (hadFocus) {
            delay(150)
            hadFocus = false
            onFocusLeft()
        }
    }
    // Any key, or a page opening by itself, restarts the wait before the capsule closes.
    var activity by remember { mutableIntStateOf(0) }
    val enterPage by rememberUpdatedState(onEnterPage)
    LaunchedEffect(expanded, activity, current) {
        if (!expanded) return@LaunchedEffect
        delay(AUTO_CLOSE_MILLIS)
        // A page still loading has nothing to take the focus yet: keep offering it.
        while (true) {
            enterPage()
            delay(AUTO_CLOSE_RETRY_MILLIS)
        }
    }

    val open by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(OPEN_MILLIS, easing = OutCubic),
        label = "railOpen",
    )
    // Open, it is as wide as its longest name and a little more.
    val labels = tabs.map { stringResource(it.labelRes) } +
        listOf(stringResource(R.string.nav_search), stringResource(R.string.nav_settings))
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.titleMedium.copy(fontSize = LABEL_SIZE, fontWeight = FontWeight.SemiBold)
    val longest = remember(labels, labelStyle, density) {
        with(density) { labels.maxOf { measurer.measure(it, labelStyle, maxLines = 1).size.width }.toDp() }
    }
    val openWidth = CAPSULE_CLOSED + longest + CAPSULE_OPEN_EXTRA
    val shape = RoundedCornerShape(CAPSULE_RADIUS)
    // UP and DOWN step through the entries in order, never out of the capsule: left to the focus
    // search, a move could land on the page under the open capsule instead (on Movies it did).
    val requesters = remember(tabs.size) { List(tabs.size + 2) { FocusRequester() } }
    var focusedIndex by remember { mutableIntStateOf(-1) }
    // The names fade in over the second half of the opening.
    val labelAlpha = { ((open - 0.4f) / 0.6f).coerceIn(0f, 1f) }

    Column(
        modifier
            .padding(start = CAPSULE_MARGIN)
            .width(CAPSULE_CLOSED + (openWidth - CAPSULE_CLOSED) * open)
            .clip(shape)
            .background(Glass)
            .border(1.dp, GlassEdge, shape)
            .onFocusChanged { expanded = it.hasFocus; onFocusChange(it.hasFocus) }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                activity++
                when (e.key) {
                    Key.DirectionRight -> { onEnterPage(); true }
                    Key.DirectionDown, Key.DirectionUp -> {
                        val next = focusedIndex + if (e.key == Key.DirectionDown) 1 else -1
                        if (focusedIndex >= 0 && next in requesters.indices) runCatching { requesters[next].requestFocus() }
                        true
                    }
                    Key.DirectionLeft -> true
                    else -> false
                }
            }
            // LEFT from the page lands on the page you're on, not on whichever item happens to
            // be level with the card you left (Recordings, from a row low on the screen).
            .focusProperties { enter = { if (it == FocusDirection.Left && currentFocus != null) currentFocus else FocusRequester.Default } }
            .focusGroup()
            .padding(vertical = CAPSULE_PAD),
    ) {
        // Brand: the logo mark alone when closed, the mark + two-tone "MyTV" wordmark when open,
        // as in the banner; a hairline under it.
        Row(
            Modifier.height(LOGO_BLOCK).wrapContentWidth(Alignment.Start, unbounded = true),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(CAPSULE_CLOSED), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.ic_opentv_logo),
                    contentDescription = "MyTV",
                    modifier = Modifier.size(LOGO_SIZE),
                )
            }
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White)) { append("My") }
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("TV") }
                },
                style = MaterialTheme.typography.titleLarge.copy(fontSize = WORDMARK_SIZE),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.graphicsLayer { alpha = labelAlpha() },
            )
        }
        Hairline()

        tabs.forEachIndexed { i, t ->
            RailItem(
                t.icon,
                t.iconSelected,
                stringResource(t.labelRes),
                labelStyle = labelStyle,
                labelAlpha = labelAlpha,
                selected = current == t,
                autoSelect = true,
                bounce = expanded,
                modifier = Modifier.focusRequester(requesters[i])
                    .then(if (current == t && currentFocus != null) Modifier.focusRequester(currentFocus) else Modifier),
                onFocused = { focusedIndex = i },
                onAutoSelect = { onAutoSelect(t); activity++ },
            ) { onSelect(t) }
        }

        Box(Modifier.fillMaxWidth().height(SEP_GAP), contentAlignment = Alignment.Center) { Hairline() }

        RailItem(
            R.drawable.ic_nav_search, R.drawable.ic_nav_search_fill, stringResource(R.string.nav_search), labelStyle, labelAlpha,
            modifier = Modifier.focusRequester(requesters[tabs.size]),
            onFocused = { focusedIndex = tabs.size },
            onClick = onOpenSearch,
        )
        RailItem(
            R.drawable.ic_nav_settings, R.drawable.ic_nav_settings_fill, stringResource(R.string.nav_settings), labelStyle, labelAlpha,
            modifier = Modifier.focusRequester(requesters[tabs.size + 1]),
            onFocused = { focusedIndex = tabs.size + 1 },
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(1.dp).background(Color.White.copy(alpha = 0.12f)))
}

/**
 * One menu entry: the icon centred in the closed capsule, the name beside it. Focused, a faint
 * accent pill that fills from the left to a stronger accent; for a page entry, full means its page
 * opens ([onAutoSelect]). Icon and name stay in the accent the whole time, filled icon included.
 */
@Composable
private fun RailItem(
    @DrawableRes icon: Int,
    @DrawableRes iconFocused: Int,
    label: String,
    labelStyle: TextStyle,
    labelAlpha: () -> Float,
    selected: Boolean = false,
    // A page entry: resting on it opens it.
    autoSelect: Boolean = false,
    // Bounce the icon when it becomes the open page (only while the menu is open to see it).
    bounce: Boolean = false,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    onAutoSelect: () -> Unit = {},
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    val fill = remember { Animatable(0f) }
    val autoPick by rememberUpdatedState(onAutoSelect)
    LaunchedEffect(focused, selected) {
        when {
            !focused -> fill.snapTo(0f)
            selected || !autoSelect -> fill.snapTo(1f)
            else -> {
                fill.snapTo(0f)
                fill.animateTo(1f, tween(AUTO_SELECT_MILLIS, easing = SmoothStep))
                autoPick()
            }
        }
    }
    val pop = remember { Animatable(0f) }
    var wasSelected by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        if (selected && !wasSelected && bounce) {
            pop.snapTo(0f)
            pop.animateTo(1f, tween(BOUNCE_MILLIS, easing = LinearEasing))
            pop.snapTo(0f)
        }
        wasSelected = selected
    }
    val lit = focused || selected
    val tint = if (lit) primary else Dim
    val pillShape = RoundedCornerShape(PILL_RADIUS)

    Box(
        modifier
            .fillMaxWidth()
            .height(ITEM_PITCH)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(interactionSource = null, indication = null, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Spacer(
            Modifier
                .padding(horizontal = PILL_INSET)
                .fillMaxWidth()
                .height(PILL_HEIGHT)
                .clip(pillShape)
                .drawBehind {
                    if (!focused) return@drawBehind
                    drawRect(primary.copy(alpha = 0.16f))
                    val f = fill.value
                    if (f > 0f) drawRect(primary.copy(alpha = 0.42f), size = Size(size.width * f, size.height))
                },
        )
        Row(
            Modifier.wrapContentWidth(Alignment.Start, unbounded = true),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(CAPSULE_CLOSED), contentAlignment = Alignment.Center) {
                Icon(
                    ImageVector.vectorResource(if (lit) iconFocused else icon),
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier
                        .size(ICON_SIZE)
                        .graphicsLayer {
                            val s = 1f + 0.22f * kotlin.math.sin(pop.value * Math.PI.toFloat())
                            scaleX = s
                            scaleY = s
                        },
                )
            }
            Text(
                label,
                style = labelStyle,
                color = tint,
                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.graphicsLayer { alpha = labelAlpha() },
            )
        }
    }
}

// Sizes from the approved mock (1920×1080 px at 2 px per dp).
private val CAPSULE_MARGIN = 7.dp
private val CAPSULE_CLOSED = 42.dp
// The page sits right against the closed capsule.
private val PAGE_START = CAPSULE_MARGIN + CAPSULE_CLOSED
private val CAPSULE_OPEN_EXTRA = 26.dp
private val CAPSULE_RADIUS = 16.dp
private val CAPSULE_PAD = 9.dp
private val LOGO_BLOCK = 39.dp
private val LOGO_SIZE = 22.dp
private val WORDMARK_SIZE = 15.sp
private val ITEM_PITCH = 35.dp
private val PILL_HEIGHT = 28.dp
private val PILL_INSET = 4.dp
private val PILL_RADIUS = 8.dp
private val ICON_SIZE = 16.dp
private val LABEL_SIZE = 13.sp
private val SEP_GAP = 11.dp
private val PAGE_IN_SHIFT = 30.dp
private const val OPEN_MILLIS = 400
private const val AUTO_SELECT_MILLIS = 400
private const val BOUNCE_MILLIS = 180
private const val PAGE_IN_MILLIS = 220
private const val AUTO_CLOSE_MILLIS = 3_000L
private const val AUTO_CLOSE_RETRY_MILLIS = 2_500L
private val Glass = Color(0xA80E1513)
private val GlassEdge = Color.White.copy(alpha = 0.09f)
private val Dim = Color(0xFFDEE4E1).copy(alpha = 0.6f)
private val OutCubic = Easing { x -> 1f - (1f - x) * (1f - x) * (1f - x) }
private val SmoothStep = Easing { x -> x * x * (3f - 2f * x) }
