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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredWidth
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
import androidx.compose.ui.input.key.onKeyEvent
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

    // The menu's style (Settings › Appearance): always a solid full-height bar beside the page —
    // icons that open with the names, nothing at rest until LEFT brings it in, or icons only.
    val menuStyle by settings.menuStyle.collectAsStateWithLifecycle()
    val hidden = menuStyle == AppSettings.MenuStyle.HIDDEN
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    // A new Animatable per tab, created at 0 in the very composition that switches the tab: snapping
    // an old one to 0 from the effect let the new page show one frame in place first, then jump
    // right and slide back — a little bounce.
    var firstTab by remember { mutableStateOf(true) }
    val pageIn = remember(tab) { Animatable(if (firstTab) 1f else 0f) }
    LaunchedEffect(tab) {
        if (firstTab) { firstTab = false; return@LaunchedEffect }
        // The new page slides in from the right as it fades in.
        pageIn.animateTo(1f, tween(PAGE_IN_MILLIS, easing = OutCubic))
    }
    val pageModifier = Modifier
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
        )
    val page: @Composable () -> Unit = {
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
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
          val screenWidth = maxWidth
          Row(Modifier.fillMaxSize()) {
              NavRail(
                  tabs = visibleTabs,
                  current = tab,
                  style = menuStyle,
                  // OK on a page's entry opens it and moves focus into it, closing the menu — the
                  // same on every page (and again on the page already open).
                  onSelect = { tab = it; enterPage++ },
                  // RIGHT: back into the page.
                  onEnterPage = { enterPage++ },
                  currentFocus = navFocus,
                  onFocusLeft = { navRevealed = false },
                  onFocusChange = { menuFocused.value = it },
                  onOpenSearch = onOpenSearch,
                  onOpenSettings = onOpenSettings,
              )
              // The pages of cards keep the width they have with the menu closed: opening it slides
              // them right, partly off screen, instead of squeezing them (their cards are sized from
              // the page's width). Live TV's lists reflow, so it takes the space that's left.
              Box(
                  Modifier.weight(1f).fillMaxHeight().clipToBounds()
                      // Full screen: no menu to move into at rest, so LEFT with nowhere left to go
                      // on the page brings it in.
                      .then(
                          if (hidden) {
                              Modifier.onKeyEvent { e ->
                                  if (e.type != KeyEventType.KeyDown || e.key != Key.DirectionLeft) return@onKeyEvent false
                                  if (!focusManager.moveFocus(FocusDirection.Left)) navRevealed = true
                                  true
                              }
                          } else Modifier,
                      ),
              ) {
                  val width = if (tab == Tab.LIVE) Modifier.fillMaxSize() else Modifier
                      .wrapContentWidth(Alignment.Start, unbounded = true)
                      .requiredWidth(screenWidth - if (hidden) 0.dp else BAR_PAGE_START)
                      .fillMaxHeight()
                  Box(width.then(pageModifier)) { page() }
              }
          }
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
 * The menu, a solid full-height bar with rounded corners and the logo on top, Search and Settings
 * at the bottom, in one of three styles ([AppSettings.MenuStyle]):
 *
 * - the bar: icons when closed; focus landing in it opens it with the names, pushing the page
 *   aside, and it stays open until focus leaves it.
 * - full screen ([AppSettings.MenuStyle.HIDDEN]): nothing at rest; focus landing in it slides it in
 *   already open, pushing the page aside, and it slides away again when focus leaves.
 * - compact: icons only, it never opens.
 *
 * UP and DOWN only move the focus — a faint accent pill; OK opens the entry (and closes the menu),
 * RIGHT goes back into the page. The open page is its icon filled and its name in the accent.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun NavRail(
    tabs: List<Tab>,
    current: Tab,
    style: AppSettings.MenuStyle,
    onSelect: (Tab) -> Unit,
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
    val hidden = style == AppSettings.MenuStyle.HIDDEN
    val compact = style == AppSettings.MenuStyle.COMPACT
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

    val open by animateFloatAsState(
        targetValue = if (expanded && !compact) 1f else 0f,
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
    val openWidth = BAR_CLOSED + longest + BAR_OPEN_EXTRA
    val shape = RoundedCornerShape(BAR_RADIUS)
    // UP and DOWN step through the entries in order, never out of the menu.
    val requesters = remember(tabs.size) { List(tabs.size + 2) { FocusRequester() } }
    var focusedIndex by remember { mutableIntStateOf(-1) }
    // The names fade in over the second half of the opening; the full-screen menu slides in with
    // them already there.
    val labelAlpha = { if (hidden) 1f else ((open - 0.4f) / 0.6f).coerceIn(0f, 1f) }

    // Full screen: the slot it takes beside the page grows from nothing as it slides in, so the page
    // is pushed by as much as the menu shows.
    val slot = if (hidden) {
        Modifier.width((MENU_MARGIN + openWidth) * open).fillMaxHeight().clipToBounds()
    } else {
        Modifier
    }
    Box(modifier.then(slot)) {
    Column(
        Modifier
            .then(if (hidden) Modifier.wrapContentWidth(Alignment.Start, unbounded = true) else Modifier)
            .padding(start = MENU_MARGIN)
            .padding(vertical = MENU_MARGIN)
            .fillMaxHeight()
            .then(
                if (hidden) {
                    Modifier
                        .requiredWidth(openWidth)
                        .graphicsLayer { translationX = -(MENU_MARGIN + openWidth).toPx() * (1f - open) }
                } else {
                    Modifier.width(BAR_CLOSED + (openWidth - BAR_CLOSED) * open)
                },
            )
            .clip(shape)
            .background(MenuSurface)
            .onFocusChanged { expanded = it.hasFocus; onFocusChange(it.hasFocus) }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
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
            .padding(vertical = BAR_PAD),
    ) {
        // Brand: the logo mark alone when closed, the mark + two-tone "MyTV" wordmark when open,
        // as in the banner; a hairline under it.
        Row(
            Modifier.height(LOGO_BLOCK).wrapContentWidth(Alignment.Start, unbounded = true),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(BAR_CLOSED), contentAlignment = Alignment.Center) {
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
        Spacer(Modifier.height(BAR_PAD))

        tabs.forEachIndexed { i, t ->
            RailItem(
                t.icon,
                t.iconSelected,
                stringResource(t.labelRes),
                labelStyle = labelStyle,
                labelAlpha = labelAlpha,
                closed = BAR_CLOSED,
                selected = current == t,
                modifier = Modifier.focusRequester(requesters[i])
                    .then(if (current == t && currentFocus != null) Modifier.focusRequester(currentFocus) else Modifier),
                onFocused = { focusedIndex = i },
            ) { onSelect(t) }
        }

        // Search and Settings at the bottom, as before.
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(SEP_GAP), contentAlignment = Alignment.Center) { Hairline() }

        RailItem(
            R.drawable.ic_nav_search, R.drawable.ic_nav_search_fill, stringResource(R.string.nav_search), labelStyle, labelAlpha,
            closed = BAR_CLOSED,
            modifier = Modifier.focusRequester(requesters[tabs.size]),
            onFocused = { focusedIndex = tabs.size },
            onClick = onOpenSearch,
        )
        RailItem(
            R.drawable.ic_nav_settings, R.drawable.ic_nav_settings_fill, stringResource(R.string.nav_settings), labelStyle, labelAlpha,
            closed = BAR_CLOSED,
            modifier = Modifier.focusRequester(requesters[tabs.size + 1]),
            onFocused = { focusedIndex = tabs.size + 1 },
            onClick = onOpenSettings,
        )
    }
    }
}

@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(1.dp).background(Color.White.copy(alpha = 0.12f)))
}

/**
 * One menu entry: the icon centred in the closed menu's width, the name beside it. Focused, a faint
 * accent pill with icon (filled) and name in the accent; the open page has the same accent, no pill.
 */
@Composable
private fun RailItem(
    @DrawableRes icon: Int,
    @DrawableRes iconFocused: Int,
    label: String,
    labelStyle: TextStyle,
    labelAlpha: () -> Float,
    closed: androidx.compose.ui.unit.Dp,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
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
                .background(if (focused) primary.copy(alpha = FOCUS_VEIL_ALPHA) else Color.Transparent),
        )
        Row(
            Modifier.wrapContentWidth(Alignment.Start, unbounded = true),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(closed), contentAlignment = Alignment.Center) {
                Icon(
                    ImageVector.vectorResource(if (lit) iconFocused else icon),
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(ICON_SIZE),
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

// Sizes from the approved mocks (1920×1080 px at 2 px per dp).
private val MENU_MARGIN = 7.dp
// Solid, no glass and no edge: the app's surface.
private val MenuSurface = Color(0xFF0E1513)
// The full-height bar; its page sits right against it.
private val BAR_CLOSED = 76.dp
private val BAR_RADIUS = 28.dp
// The bar has more room on the left of its names, so a little more after them too.
private val BAR_OPEN_EXTRA = 44.dp
private val BAR_PAD = 14.dp
private val BAR_PAGE_START = MENU_MARGIN + BAR_CLOSED
private val LOGO_BLOCK = 46.dp
private val LOGO_SIZE = 27.dp
private val WORDMARK_SIZE = 17.sp
private val ITEM_PITCH = 44.dp
private val PILL_HEIGHT = 36.dp
private val PILL_INSET = 4.dp
private val PILL_RADIUS = 10.dp
private val ICON_SIZE = 23.dp
private val LABEL_SIZE = 15.sp
private val SEP_GAP = 13.dp
private val PAGE_IN_SHIFT = 30.dp
private const val OPEN_MILLIS = 400
private const val PAGE_IN_MILLIS = 220
private val Dim = Color(0xFFDEE4E1).copy(alpha = 0.6f)
private val OutCubic = Easing { x -> 1f - (1f - x) * (1f - x) * (1f - x) }
