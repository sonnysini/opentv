/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.channels

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.Intent
import android.view.WindowManager
import android.widget.Toast
import app.opentv.R
import app.opentv.core.AppSettings
import app.opentv.core.ServiceLocator
import app.opentv.core.findActivity
import app.opentv.core.requestIgnoreBatteryOptimizations
import app.opentv.data.model.Channel
import app.opentv.data.model.Programme
import app.opentv.data.model.Reminder
import app.opentv.data.model.logo
import app.opentv.data.model.shownName
import app.opentv.reminders.ReminderScheduler
import app.opentv.player.MatchFeeds
import app.opentv.player.PlaybackQueue
import app.opentv.player.PlayerController
import app.opentv.ui.ActionCard
import app.opentv.ui.ActionChip
import app.opentv.ui.ActionChips
import app.opentv.ui.ActionLabel
import app.opentv.ui.ActionPanel
import app.opentv.ui.ActionRow
import app.opentv.ui.ChannelsViewModel
import app.opentv.ui.RecordRed
import app.opentv.ui.startFocus
import app.opentv.ui.RecordingBackgroundDialog
import app.opentv.ui.RecordingBackgroundPrompt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.opentv.ui.theme.focusLift

/**
 * Live TV: a category rail on the left, the channel list on the right.
 *
 * The rail is vertical because provider category lists run to dozens of entries and a
 * horizontal chip row hides all but the first few — on a d-pad, anything you cannot see
 * you cannot reach. Favourites and All are pinned at the top.
 *
 * The list shows what is on *now* and *next* against every channel. That doubles as a
 * permanent, glanceable health indicator for the guide: when the EPG stops updating, these
 * lines say so honestly instead of going quietly stale.
 */
@Composable
fun HomeScreen(
    isTelevision: Boolean,
    hasSources: Boolean,
    isSyncing: Boolean,
    onPlayChannel: (Channel) -> Unit,
    onAddSource: () -> Unit,
    onRefresh: () -> Unit,
    onPlayCatchup: (mediaKey: String, url: String, title: String, ua: String) -> Unit = { _, _, _, _ -> },
    // Full-screen layout only: LEFT on a row asks the shell to bring its navigation menu back.
    onRevealNav: () -> Unit = {},
    // Hands the shell what RIGHT out of its menu should do (null: nothing special).
    registerNavExit: ((() -> Boolean)?) -> Unit = {},
    viewModel: ChannelsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val graph = remember { ServiceLocator.get(context) }
    val settings = remember { graph.settings }
    val previewSetting by settings.guidePreviewVideo.collectAsStateWithLifecycle()
    val channelLayout by settings.channelLayout.collectAsStateWithLifecycle()
    val fullscreen = channelLayout == AppSettings.ChannelLayout.FULLSCREEN
    // The full-screen list has no preview pane, so it never runs the preview player either.
    val previewEnabled = previewSetting && !fullscreen

    val categories by viewModel.visibleCategoryGroups.collectAsStateWithLifecycle()
    val fullscreenCategories by viewModel.nonEmptyCategoryGroups.collectAsStateWithLifecycle()
    val groupCounts by viewModel.groupCounts.collectAsStateWithLifecycle()
    val liveTabs by viewModel.liveTabs.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val recentLiveGroups by viewModel.recentLiveGroups.collectAsStateWithLifecycle()
    val showRecentGroups by viewModel.showRecentGroups.collectAsStateWithLifecycle()
    val recentGroups = if (showRecentGroups) recentLiveGroups else emptyList()
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val rowsLoaded by viewModel.rowsLoaded.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val favouritesOnly by viewModel.favouritesOnly.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val selectedSource by viewModel.selectedSource.collectAsStateWithLifecycle()
    val windowStart by viewModel.windowStartMillis.collectAsStateWithLifecycle()
    // How many days ahead the guide is scrolled (0 = live now). Drives the ‹ Today › nav.
    val guideDayOffset by viewModel.guideDayOffset.collectAsStateWithLifecycle()
    // Tri-state: null = still checking the catalogue, true = channels on disk, false = confirmed
    // empty. Drives the choice between the loading spinner and a recoverable error below.
    val channelsPresent by viewModel.channelsPresent.collectAsStateWithLifecycle()

    // Two separate ideas, on purpose:
    //  - highlightedRow: where the d-pad is in the grid. Moves freely with up/down.
    //  - selectedRow: what the preview pane plays. Only changes when you press OK, so scrolling
    //    the list is calm and silent instead of re-tuning a stream on every keypress.
    // Starts on the channel last watched (the player records every zap), so coming back from
    // full-screen — or opening the app — lands on it rather than on the top of the list.
    fun lastWatched(list: List<ChannelsViewModel.Row>) =
        settings.lastChannelId.let { id -> list.firstOrNull { row -> row.variants.any { it.id == id } } }
    var highlightedRow by remember { mutableStateOf(lastWatched(rows)) }
    var selectedRow by remember { mutableStateOf<ChannelsViewModel.Row?>(null) }
    val previewSound by settings.guidePreviewSound.collectAsStateWithLifecycle()
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Recording from the guide: what's capturing now, and a scope to kick a capture off.
    val activeRecordings by graph.recordingRepository.observeActive().collectAsStateWithLifecycle(initialValue = emptyList())
    val recordScope = rememberCoroutineScope()
    // The programme the user pressed OK on in the grid — drives the per-programme record menu.
    var recordTarget by remember { mutableStateOf<Pair<ChannelsViewModel.Row, Programme>?>(null) }
    // The channel whose OK menu (Watch / Record / Schedule) is open.
    var channelMenu by remember { mutableStateOf<ChannelsViewModel.Row?>(null) }
    var logoPicker by remember { mutableStateOf<ChannelsViewModel.Row?>(null) }
    var guidePicker by remember { mutableStateOf<ChannelsViewModel.Row?>(null) }
    // Long-press › Merge with…: the row waiting for the user to pick the channel it joins.
    var mergeFrom by remember { mutableStateOf<ChannelsViewModel.Row?>(null) }
    var mergeRefocus by remember { mutableStateOf<Triple<Any, Any, List<ChannelsViewModel.Row>>?>(null) }

    // First time the user records or schedules while OpenTV isn't exempt from battery optimisation,
    // offer the exemption so the capture survives standby. Once per session; never blocks recording.
    var showBackgroundPrompt by remember { mutableStateOf(false) }
    fun promptBackgroundIfNeeded() {
        if (RecordingBackgroundPrompt.shouldShow(context)) {
            RecordingBackgroundPrompt.markShown()
            showBackgroundPrompt = true
        }
    }

    // ---- Category rail collapse (Change 2) ---------------------------------------------------
    // The rail is full-width while focus is on it, then slides shut once focus moves into the
    // guide (onFocusRow) so the grid gets the whole width. Pressing d-pad LEFT from the guide's
    // leftmost (channel) column slides it back and drops focus on the selected category.
    var railExpanded by remember { mutableStateOf(true) }
    val railWidth by animateDpAsState(
        targetValue = if (railExpanded) 240.dp else 0.dp,
        label = "railWidth",
    )
    val railFocusRequester = remember { FocusRequester() }
    // Set when LEFT reopens the rail; the effect waits for the rail to be laid out again before
    // moving focus onto it — a just-revealed node isn't focusable on the very same frame.
    var pendingRailFocus by remember { mutableStateOf(false) }
    LaunchedEffect(pendingRailFocus) {
        if (pendingRailFocus) {
            delay(50)
            // runCatching: if the selected category is scrolled out of the rail's list it may not
            // be composed; a second LEFT press then still reaches the rail by ordinary navigation.
            runCatching { railFocusRequester.requestFocus() }
            pendingRailFocus = false
        }
    }

    // Re-evaluate "now" on each minute so progress bars advance without leaving the screen; only
    // while the app is on screen (pressing Home stops it, coming back catches up at once).
    val tickLifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        tickLifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                nowMillis = System.currentTimeMillis()
                viewModel.tick()
                delay(60_000 - nowMillis % 60_000)
            }
        }
    }

    // Keep both valid as the list changes (e.g. switching category): stay on the same channel if
    // it's still present, otherwise fall back to the top of the new list.
    LaunchedEffect(rows) {
        highlightedRow = rows.firstOrNull { it.key == highlightedRow?.key } ?: lastWatched(rows) ?: rows.firstOrNull()
        selectedRow = rows.firstOrNull { it.key == selectedRow?.key } ?: rows.firstOrNull()
    }

    // ---- Live preview player -----------------------------------------------------------------
    // One muted player, reused. It only ever decodes while the guide is the foreground screen,
    // and is stopped before any hand-off to full-screen, so the box never runs two decoders at
    // once — the thing that used to lock up cheap sticks.
    val previewScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    val previewController = remember {
        PlayerController(context, previewScope, graph.httpClient, subtitlesEnabled = false, preview = true)
            .also { it.player.volume = 0f }
    }
    DisposableEffect(Unit) {
        onDispose {
            previewController.release()
            previewScope.cancel()
        }
    }

    // Watching a live channel while a recording runs opens a second stream on the same line — which
    // cuts the recording and can get a single-connection account banned. So every jump to full-screen
    // live is funnelled through [requestLive]: with a recording active it asks first.
    var pendingLiveChannel by remember { mutableStateOf<Channel?>(null) }
    val logoSource = LocalLogoSource.current
    val logoPack = LocalLogoPack.current
    fun startLive(channel: Channel) {
        MatchFeeds.clear()
        PlaybackQueue.items = rows.map {
            PlaybackQueue.Item(it.primary.id, it.primary.shownName, it.primary.logo(logoSource, logoPack), it.primary.number)
        }
        previewController.stop()
        onPlayChannel(channel)
    }
    fun requestLive(channel: Channel) {
        if (activeRecordings.isNotEmpty()) pendingLiveChannel = channel else startLive(channel)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var screenResumed by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> screenResumed = true
                Lifecycle.Event.ON_PAUSE -> {
                    screenResumed = false
                    previewController.stop()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Hold the screen awake while the guide's live preview is playing — otherwise the box's
    // screensaver fires while you're browsing with a channel running in the preview pane.
    DisposableEffect(previewEnabled, screenResumed) {
        val window = context.findActivity()?.window
        if (previewEnabled && screenResumed) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            app.opentv.core.EventLog.keepAwake("Home", "on (preview)", context.findActivity())
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            app.opentv.core.EventLog.keepAwake("Home", "off (preview=$previewEnabled resumed=$screenResumed)", context.findActivity())
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            app.opentv.core.EventLog.keepAwake("Home", "off (closed)", context.findActivity())
        }
    }

    // Preview audio follows the setting; muted by default so browsing stays quiet.
    LaunchedEffect(previewSound) {
        previewController.player.volume = if (previewSound) 1f else 0f
    }

    // Tune the preview to the highlighted channel, so it follows the d-pad as you browse — the
    // standard TV-guide behaviour. Debounced, so holding a direction doesn't re-tune every step.
    // While a recording is running the preview is silenced entirely: on a single-connection line a
    // muted preview is still a second stream, which fights the recording and risks a provider ban.
    val recordingActive = activeRecordings.isNotEmpty()
    LaunchedEffect(highlightedRow?.key, previewEnabled, screenResumed, recordingActive) {
        val row = highlightedRow
        if (!previewEnabled || !screenResumed || recordingActive || row == null) {
            previewController.stop()
            return@LaunchedEffect
        }
        val channel = row.primary
        val source = graph.sourceRepository.byId(channel.sourceId)
        previewController.play(
            PlayerController.Request(
                url = channel.streamUrl,
                title = channel.shownName,
                userAgent = source?.userAgent ?: "OpenTV/0.1 (Android)",
                isLive = true,
            ),
            debounce = true,
        )
    }

    // A remembered category that has since gone (deleted by the provider, switched off, hidden)
    // would leave an empty list after a restart — fall back to All once the categories are in.
    LaunchedEffect(categories, selectedCategory) {
        val key = selectedCategory ?: return@LaunchedEffect
        if (categories.isEmpty()) return@LaunchedEffect
        // A tab's entry (group, country, "+1") is gone when it no longer resolves.
        val gone = if (app.opentv.ui.LiveMacros.plainKey(key) != key) app.opentv.ui.LiveMacros.resolve(key, categories) == null
        else categories.none { key in it.memberKeys }
        if (gone) viewModel.selectCategory(null)
    }

    // Tonight's big programmes for the top of the full-screen list: what is on hand at once (the
    // disk copy), then asked again every few minutes — the guide is only read once a day, so this
    // just drops the ones over and retries a read that failed.
    val highlights by androidx.compose.runtime.produceState(graph.tvHighlightsRepository.cached().orEmpty(), fullscreen) {
        if (!fullscreen) return@produceState
        // No polling: woken when the next programme ends (or at midnight for tomorrow's), and only
        // after a read that failed sooner, to try again. Paused while the app is in the background.
        tickLifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = System.currentTimeMillis()
                value = graph.tvHighlightsRepository.tonight(now)
                delay(graph.tvHighlightsRepository.nextChangeAt(now, HIGHLIGHTS_RETRY_MILLIS) - now)
            }
        }
    }

    if (fullscreen) {
        FullscreenLive(
            registerNavExit = registerNavExit,
            highlights = highlights,
            onWatchHighlight = { h ->
                recordScope.launch {
                    // As pressing that channel in the list: its row's pick, then the player's own choice.
                    val variants = app.opentv.data.repo.distinctByQuality(
                        graph.tvHighlightsRepository.channelsFor(h).sortedByDescending { it.qualityRank },
                    )
                    app.opentv.core.QualityPreference.order(variants, settings.preferredQuality.value)
                        .firstOrNull()?.let { requestLive(it) }
                }
            },
            rows = rows,
            categories = fullscreenCategories,
            selectedCategory = selectedCategory,
            favouritesOnly = favouritesOnly,
            highlightedKey = highlightedRow?.key,
            rowsLoaded = rowsLoaded && !isSyncing,
            fallbackKey = { lastWatched(it)?.key },
            groupCounts = groupCounts,
            recentGroups = recentGroups,
            tabs = liveTabs,
            selectedTab = selectedTab,
            onSelectTab = viewModel::selectTab,
            nowMillis = nowMillis,
            onSelectCategory = viewModel::selectCategory,
            onSelectFavourites = viewModel::selectFavourites,
            onFocusRow = { highlightedRow = it },
            onPlay = { row ->
                val from = mergeFrom
                if (from == null) {
                    viewModel.markGroupWatched()
                    requestLive(row.primary)
                }
                else if (row.key != from.key) {
                    mergeFrom = null
                    mergeRefocus = Triple(row.key, from.key, rows)
                    viewModel.mergeRows(from, row)
                    Toast.makeText(context, context.getString(R.string.live_merge_done, row.primary.shownName), Toast.LENGTH_LONG).show()
                }
            },
            onLongPress = { if (mergeFrom == null) channelMenu = it },
            mergingName = mergeFrom?.primary?.shownName,
            onCancelMerge = { mergeFrom = null },
            refocus = mergeRefocus,
            onRefocusDone = { mergeRefocus = null },
            onExitLeft = onRevealNav,
            emptyContent = {
                when {
                    favouritesOnly -> NoFavouritesState()
                    isSyncing || channelsPresent == null || channelsPresent == true -> LoadingState(isSyncing)
                    hasSources -> ChannelsErrorState(onRetry = onRefresh, onEditProvider = onAddSource)
                    else -> EmptyState(onAddSource)
                }
            },
        )
    } else Row(Modifier.fillMaxSize()) {

        // ---- Category rail -----------------------------------------------------------------
        // Width animates to 0 while focus is in the guide (see onFocusRow) so the grid gets the
        // whole screen; d-pad LEFT from the guide's channel column slides it back (onExitLeft…).
        Column(
            Modifier
                .width(railWidth)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .clipToBounds()
                .padding(vertical = 16.dp),
        ) {
            Text(
                stringResource(R.string.nav_live_tv),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))
            // The top "Search channels" bar was removed — Search now lives in the global nav rail.

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // Provider switch — only when there's more than one source. Lets the user keep
                // several playlists and flip between them (cardiodoc's request); "All" folds them.
                if (sources.size > 1) {
                    item(key = "provider-header") {
                        Text(
                            stringResource(R.string.channels_manager_source_header),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                    item(key = "provider-all") {
                        RailEntry(
                            label = stringResource(R.string.channels_manager_all_sources),
                            selected = selectedSource == null,
                            onClick = { viewModel.selectSource(null) },
                        )
                    }
                    items(sources, key = { "src-${it.id}" }) { source ->
                        RailEntry(
                            label = source.name,
                            selected = selectedSource == source.id,
                            onClick = { viewModel.selectSource(source.id) },
                        )
                    }
                    item(key = "provider-divider") { Spacer(Modifier.height(10.dp)) }
                }
                // The currently-selected entry carries the rail's FocusRequester, so reopening the
                // rail (d-pad LEFT in the guide) lands focus straight back on the current category.
                item {
                    RailEntry(
                        label = stringResource(R.string.guide_favourites),
                        selected = favouritesOnly,
                        onClick = viewModel::selectFavourites,
                        modifier = if (favouritesOnly) Modifier.focusRequester(railFocusRequester) else Modifier,
                    )
                }
                item {
                    val allSelected = !favouritesOnly && selectedCategory == null
                    RailEntry(
                        label = stringResource(R.string.guide_all_channels),
                        selected = allSelected,
                        onClick = { viewModel.selectCategory(null) },
                        modifier = if (allSelected) Modifier.focusRequester(railFocusRequester) else Modifier,
                    )
                }
                items(categories, key = { it.key }) { group ->
                    val groupSelected = !favouritesOnly && selectedCategory in group.memberKeys
                    RailEntry(
                        label = group.label,
                        selected = groupSelected,
                        onClick = { viewModel.selectCategory(group.key) },
                        modifier = if (groupSelected) Modifier.focusRequester(railFocusRequester) else Modifier,
                    )
                }
            }
        }

        // ---- Preview + guide ---------------------------------------------------------------
        Column(Modifier.weight(1f)) {
            if (rows.isEmpty()) {
                when {
                    favouritesOnly -> NoFavouritesState()
                    // Work genuinely in progress: a sync is running, the catalogue check hasn't
                    // returned yet, or channels ARE on disk and the guide is still building. A
                    // large provider takes a while, and showing "No channels" during it reads as
                    // failure — which is how someone concludes an app is broken thirty seconds
                    // after installing it.
                    isSyncing || channelsPresent == null || channelsPresent == true -> LoadingState(isSyncing)
                    // Nothing is syncing and the catalogue is confirmed empty. With a provider
                    // configured, the last load failed or returned nothing — surface a clear error
                    // with Retry and a way back to setup instead of spinning forever.
                    hasSources -> ChannelsErrorState(onRetry = onRefresh, onEditProvider = onAddSource)
                    else -> EmptyState(onAddSource)
                }
            } else {
                // Hand the player the list you're browsing so it can zap channel up/down.
                fun goFullscreen(channel: Channel) = requestLive(channel)

                // Record the highlighted channel's now-programme (bounded to its end), or stop it
                // if it's already recording. Powers the preview pane's quick record dot.
                fun recordSelected() {
                    val row = highlightedRow ?: return
                    val active = activeRecordings.firstOrNull { it.channelId == row.primary.id }
                    if (active != null) {
                        graph.recordingEngine.stop(active.id)
                        Toast.makeText(context, context.getString(R.string.rec_recording_stopped), Toast.LENGTH_SHORT).show()
                    } else {
                        recordScope.launch { graph.recordingEngine.startChannel(row.primary, row.now) }
                        Toast.makeText(context, context.getString(R.string.rec_recording_started_see_tab, row.primary.shownName), Toast.LENGTH_LONG).show()
                        promptBackgroundIfNeeded()
                    }
                }

                val dayLabel = when (guideDayOffset) {
                    0 -> stringResource(R.string.guide_today)
                    1 -> stringResource(R.string.guide_tomorrow)
                    else -> remember(windowStart) {
                        SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(windowStart))
                    }
                }
                GuidePreview(
                    row = highlightedRow,
                    nowMillis = nowMillis,
                    onWatch = { highlightedRow?.let { goFullscreen(it.primary) } },
                    onRefresh = onRefresh,
                    onAddSource = onAddSource,
                    previewPlayer = if (previewEnabled && !recordingActive) previewController.player else null,
                    isRecording = highlightedRow?.primary?.id?.let { id ->
                        activeRecordings.any { it.channelId == id }
                    } == true,
                    onRecord = { recordSelected() },
                    dayLabel = dayLabel,
                    canGoPrevDay = guideDayOffset > 0,
                    onPrevDay = { viewModel.nudgeGuideDay(-1) },
                    onNextDay = { viewModel.nudgeGuideDay(1) },
                )
                // Shared by both layouts: focus follows the highlight and collapses the rail; LEFT
                // from the leftmost element reopens the rail (consumed only when it was hidden).
                val onFocusChannel: (ChannelsViewModel.Row) -> Unit = {
                    highlightedRow = it
                    railExpanded = false
                }
                val onExitLeftChannel: () -> Boolean = {
                    if (!railExpanded) {
                        railExpanded = true
                        pendingRailFocus = true
                        true
                    } else {
                        false
                    }
                }
                if (channelLayout == AppSettings.ChannelLayout.LIST) {
                    ChannelList(
                        rows = rows,
                        selectedKey = highlightedRow?.key,
                        onSelectRow = { row -> channelMenu = row },
                        onFocusRow = onFocusChannel,
                        onToggleFavourite = { viewModel.toggleFavourite(it) },
                        onExitLeftFromChannel = onExitLeftChannel,
                        modifier = Modifier.weight(1f).padding(start = 12.dp, end = 12.dp),
                    )
                } else {
                    GuideGrid(
                        rows = rows,
                        windowStartMillis = windowStart,
                        dayOffset = guideDayOffset,
                        selectedKey = highlightedRow?.key,
                        // OK on a channel opens its menu: Watch, Record now, Schedule a later show,
                        // Record series. The preview already follows the highlight as you browse.
                        onSelectRow = { row -> channelMenu = row },
                        onFocusRow = onFocusChannel,
                        onProgramme = { row, programme -> recordTarget = row to programme },
                        onToggleFavourite = { viewModel.toggleFavourite(it) },
                        onExitLeftFromChannel = onExitLeftChannel,
                        modifier = Modifier.weight(1f).padding(start = 12.dp, end = 12.dp),
                    )
                }
            }
        }
    }

    // The panel for a programme picked in the grid (or under "Later" in a channel's panel).
    recordTarget?.let { (targetRow, programme) ->
        val channel = targetRow.primary
        // Which quality actually gets recorded. Defaults to the best variant, but the user can pick
        // (Sky Q's "record HD or SD"). Each variant channel carries its own stream URL, so the pick
        // flows straight through to the capture. Only surfaced when the channel has more than one.
        var chosenVariant by remember(targetRow.primary.id) { mutableStateOf(targetRow.primary) }
        // Which provider records it, when this channel exists on more than one. Recording from a
        // second provider's account is what lets you keep watching on the first — the only real way
        // around a single-connection provider (something even TiviMate can't do).
        var sourceOptions by remember(channel.id) { mutableStateOf<List<Channel>>(emptyList()) }
        LaunchedEffect(channel.id) { sourceOptions = graph.catalogRepository.recordSourceOptions(channel) }
        val multiSource = sourceOptions.size > 1
        LaunchedEffect(sourceOptions) {
            if (sourceOptions.size > 1) {
                chosenVariant = sourceOptions.firstOrNull { it.sourceId == channel.sourceId } ?: sourceOptions.first()
            }
        }
        val liveNow = nowMillis in programme.startUtcMillis until programme.endUtcMillis
        val recordingThis = activeRecordings.firstOrNull { it.channelId == channel.id }
        val isPast = programme.endUtcMillis <= nowMillis
        // Reminders — only for something that hasn't started yet.
        var reminderSet by remember(channel.id, programme.startUtcMillis) { mutableStateOf<Boolean?>(null) }
        LaunchedEffect(channel.id, programme.startUtcMillis) {
            if (!isPast) reminderSet = graph.reminderRepository.forProgramme(channel.id, programme.startUtcMillis) != null
        }
        val unknownSource = stringResource(R.string.rec_source_unknown)
        val sdLabel = stringResource(R.string.rec_quality_sd)
        val day = when (dayOffsetOf(programme.startUtcMillis, nowMillis)) {
            0 -> stringResource(R.string.guide_today)
            1 -> stringResource(R.string.guide_tomorrow)
            else -> remember(programme.startUtcMillis) {
                SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(programme.startUtcMillis))
            }
        }
        val close = { recordTarget = null }
        val hasPrimary = liveNow || !isPast || channel.tvArchive
        // Up/down into the chips lands on the one chosen, not on whichever sits under the row's middle.
        val chosenChip = remember { FocusRequester() }
        val choices: List<Pair<Channel, String>> = when {
            isPast -> emptyList()
            multiSource -> sourceOptions.map { opt -> opt to (sources.firstOrNull { it.id == opt.sourceId }?.name ?: unknownSource) }
            targetRow.variants.size > 1 -> targetRow.variants.map { it to it.qualityLabel.ifBlank { sdLabel } }
            else -> emptyList()
        }
        val downToChip = Modifier.focusProperties { if (choices.isNotEmpty()) down = chosenChip }
        val upToChip = Modifier.focusProperties { if (choices.isNotEmpty()) up = chosenChip }
        ActionPanel(
            kicker = "$day ${formatTime(programme.startUtcMillis)} – ${formatTime(programme.endUtcMillis)} · ${channel.shownName}",
            title = programme.title,
            onDismiss = close,
        ) { first ->
            if (hasPrimary) item(key = "primary") {
                when {
                    liveNow && recordingThis != null -> ActionRow(
                        stringResource(R.string.panel_stop_recording), downToChip.startFocus(first), icon = R.drawable.ic_act_stop,
                    ) {
                        graph.recordingEngine.stop(recordingThis.id)
                        Toast.makeText(context, context.getString(R.string.rec_recording_stopped), Toast.LENGTH_SHORT).show()
                        close()
                    }
                    liveNow -> ActionRow(
                        stringResource(R.string.panel_record_live), downToChip.startFocus(first), icon = R.drawable.ic_act_record, iconTint = RecordRed,
                    ) {
                        recordScope.launch { graph.recordingEngine.startChannel(chosenVariant, programme) }
                        Toast.makeText(context, context.getString(R.string.rec_recording_channel, channel.shownName), Toast.LENGTH_LONG).show()
                        promptBackgroundIfNeeded()
                        close()
                    }
                    // A finished programme on a catch-up channel plays back from the archive.
                    isPast -> ActionRow(
                        stringResource(R.string.panel_watch_from_start), downToChip.startFocus(first), icon = R.drawable.ic_act_from_start,
                    ) {
                        recordScope.launch {
                            val source = graph.sourceRepository.byId(channel.sourceId)
                            if (source == null) {
                                Toast.makeText(context, context.getString(R.string.guide_catchup_link_failed), Toast.LENGTH_SHORT).show()
                            } else {
                                val mins = (programme.durationMillis / 60000).toInt().coerceAtLeast(1)
                                val url = graph.xtreamApi.catchupUrl(source, channel.streamId, programme.startUtcMillis, mins)
                                onPlayCatchup(
                                    "catchup:${channel.id}:${programme.startUtcMillis}",
                                    url,
                                    "${channel.shownName} — ${programme.title}",
                                    source.userAgent,
                                )
                            }
                        }
                        close()
                    }
                    else -> ActionRow(
                        stringResource(R.string.panel_schedule), downToChip.startFocus(first), icon = R.drawable.ic_act_record, iconTint = RecordRed,
                    ) {
                        recordScope.launch { graph.recordingEngine.scheduleProgramme(chosenVariant, programme) }
                        Toast.makeText(context, context.getString(R.string.rec_scheduled_title, programme.title), Toast.LENGTH_LONG).show()
                        promptBackgroundIfNeeded()
                        close()
                    }
                }
            }
            // Record from which provider (keeps the other account free to watch on), or in which quality.
            if (choices.isNotEmpty()) {
                item(key = "label:in") {
                    ActionLabel(stringResource(if (multiSource) R.string.rec_record_from else R.string.rec_quality_label))
                }
                item(key = "chips") {
                    ActionChips {
                        // Always one chip holds the requester, even before the chosen one is known.
                        val target = choices.indexOfFirst { it.first.id == chosenVariant.id }.coerceAtLeast(0)
                        choices.forEachIndexed { i, (opt, label) ->
                            ActionChip(
                                label,
                                selected = opt.id == chosenVariant.id,
                                modifier = if (i == target) Modifier.focusRequester(chosenChip) else Modifier,
                            ) { chosenVariant = opt }
                        }
                    }
                }
            }
            item(key = "series") {
                ActionRow(
                    stringResource(R.string.rec_record_series),
                    upToChip.startFocus(first, on = !hasPrimary),
                    icon = R.drawable.ic_act_series,
                ) {
                    recordScope.launch { graph.recordingEngine.recordSeries(channel, programme, targetRow.programmes) }
                    Toast.makeText(context, context.getString(R.string.rec_series_recording_set, programme.title), Toast.LENGTH_LONG).show()
                    promptBackgroundIfNeeded()
                    close()
                }
            }
            if (!isPast && reminderSet != null) {
                item(key = "label:reminder") { ActionLabel(stringResource(R.string.panel_reminder)) }
                if (reminderSet == true) item(key = "reminder:off") {
                    ActionRow(stringResource(R.string.panel_cancel_reminder), icon = R.drawable.ic_act_bell_off) {
                        recordScope.launch {
                            graph.reminderRepository.forProgramme(channel.id, programme.startUtcMillis)?.let {
                                ReminderScheduler.cancel(context, it.id)
                                graph.reminderRepository.delete(it.id)
                            }
                        }
                        Toast.makeText(context, context.getString(R.string.guide_reminder_removed), Toast.LENGTH_SHORT).show()
                        close()
                    }
                } else {
                    item(key = "reminder:on") {
                        ActionRow(stringResource(R.string.panel_remind_me), icon = R.drawable.ic_act_bell) {
                            recordScope.launch { setReminder(graph, context, channel, programme, autoTune = false) }
                            Toast.makeText(context, context.getString(R.string.guide_reminder_set, programme.title), Toast.LENGTH_LONG).show()
                            close()
                        }
                    }
                    item(key = "reminder:auto") {
                        ActionRow(stringResource(R.string.panel_auto_switch), icon = R.drawable.ic_act_auto_switch) {
                            recordScope.launch { setReminder(graph, context, channel, programme, autoTune = true) }
                            Toast.makeText(context, context.getString(R.string.guide_will_switch, channel.shownName), Toast.LENGTH_LONG).show()
                            close()
                        }
                    }
                }
            }
            item(key = "label:more") { ActionLabel(stringResource(R.string.panel_more)) }
            item(key = "channel") {
                ActionRow(stringResource(R.string.guide_watch_channel), icon = R.drawable.ic_act_play) {
                    close()
                    requestLive(channel)
                }
            }
        }
    }

    // Single-connection guard: confirm before opening a live stream that would fight a recording.
    // A straight question, so it stays a card in the middle; the focus starts on the safe answer.
    pendingLiveChannel?.let { liveChannel ->
        val recTitle = activeRecordings.firstOrNull()?.title.orEmpty()
        ActionCard(
            kicker = stringResource(R.string.rec_live_warn_title),
            title = recTitle.ifBlank { liveChannel.shownName },
            text = stringResource(R.string.panel_live_warn_body),
            onDismiss = { pendingLiveChannel = null },
        ) { first ->
            ActionRow(
                stringResource(R.string.rec_live_warn_keep), Modifier.startFocus(first), icon = R.drawable.ic_act_record, iconTint = RecordRed,
            ) { pendingLiveChannel = null }
            ActionRow(stringResource(R.string.panel_live_watch), icon = R.drawable.ic_act_play) {
                pendingLiveChannel = null
                activeRecordings.forEach { graph.recordingEngine.stop(it.id) }
                startLive(liveChannel)
            }
        }
    }

    // The channel's panel — opened by OK (or holding OK) on a channel: watch, record what's on,
    // the programmes coming up, the channel's own settings.
    channelMenu?.let { menuRow ->
        val channel = menuRow.primary
        val nowProg = menuRow.now
        val recordingThis = activeRecordings.firstOrNull { it.channelId == channel.id }
        val upcoming = menuRow.programmes.filter { it.startUtcMillis > nowMillis }.take(8)
        val close = { channelMenu = null }
        val leftLabel = nowProg?.let {
            val mins = ((it.endUtcMillis - nowMillis + 59_999) / 60_000).toInt().coerceAtLeast(0)
            stringResource(
                R.string.panel_left,
                if (mins >= 60) stringResource(R.string.panel_hours_minutes, mins / 60, mins % 60)
                else stringResource(R.string.panel_minutes, mins),
            )
        }
        ActionPanel(
            kicker = nowProg?.let { "${formatTime(it.startUtcMillis)} – ${formatTime(it.endUtcMillis)} · ${it.title}" },
            title = channel.shownName,
            onDismiss = close,
            progress = nowProg?.takeIf { it.durationMillis > 0 }
                ?.let { (nowMillis - it.startUtcMillis).toFloat() / it.durationMillis },
            progressLabel = leftLabel,
        ) { first ->
            item(key = "watch") {
                ActionRow(stringResource(R.string.panel_watch), Modifier.startFocus(first), icon = R.drawable.ic_act_play) {
                    close()
                    requestLive(channel)
                }
            }
            item(key = "record") {
                if (recordingThis != null) {
                    ActionRow(stringResource(R.string.panel_stop_recording), icon = R.drawable.ic_act_stop) {
                        graph.recordingEngine.stop(recordingThis.id)
                        Toast.makeText(context, context.getString(R.string.rec_recording_stopped), Toast.LENGTH_SHORT).show()
                        close()
                    }
                } else {
                    ActionRow(stringResource(R.string.panel_record_now), icon = R.drawable.ic_act_record, iconTint = RecordRed) {
                        recordScope.launch { graph.recordingEngine.startChannel(channel, nowProg) }
                        Toast.makeText(context, context.getString(R.string.rec_recording_started_see_tab, channel.shownName), Toast.LENGTH_LONG).show()
                        promptBackgroundIfNeeded()
                        close()
                    }
                }
            }
            if (nowProg != null) item(key = "series") {
                ActionRow(stringResource(R.string.panel_record_series_named, nowProg.title), icon = R.drawable.ic_act_series) {
                    recordScope.launch { graph.recordingEngine.recordSeries(channel, nowProg, menuRow.programmes) }
                    Toast.makeText(context, context.getString(R.string.rec_series_recording_set, nowProg.title), Toast.LENGTH_LONG).show()
                    promptBackgroundIfNeeded()
                    close()
                }
            }
            if (upcoming.isNotEmpty()) {
                item(key = "label:later") { ActionLabel(stringResource(R.string.panel_later)) }
                items(upcoming, key = { "later:" + it.startUtcMillis }) { programme ->
                    // The programme's own panel: record, remind or auto-switch — not a surprise one-tap recording.
                    ActionRow(programme.title, time = formatTime(programme.startUtcMillis)) {
                        channelMenu = null
                        recordTarget = menuRow to programme
                    }
                }
            }
            item(key = "label:channel") { ActionLabel(stringResource(R.string.panel_channel)) }
            item(key = "favourite") {
                ActionRow(
                    stringResource(if (channel.favourite) R.string.live_favourite_remove else R.string.live_favourite_add),
                    icon = if (channel.favourite) R.drawable.ic_act_star_fill else R.drawable.ic_act_star,
                ) {
                    close()
                    viewModel.toggleFavourite(menuRow)
                }
            }
            if (fullscreen && channel.groupKey.isNotEmpty()) item(key = "merge") {
                ActionRow(stringResource(R.string.live_merge_action), icon = R.drawable.ic_act_merge) {
                    close()
                    mergeFrom = menuRow
                }
            }
            if (channel.groupKey.isNotEmpty()) item(key = "logo") {
                ActionRow(stringResource(R.string.logo_picker_action), icon = R.drawable.ic_act_logo) {
                    close()
                    logoPicker = menuRow
                }
            }
            item(key = "guide") {
                ActionRow(stringResource(R.string.guide_picker_action), icon = R.drawable.ic_act_guide) {
                    close()
                    guidePicker = menuRow
                }
            }
            item(key = "label:more") { ActionLabel(stringResource(R.string.panel_more)) }
            item(key = "external") {
                ActionRow(stringResource(R.string.guide_open_external), icon = R.drawable.ic_act_external) {
                    close()
                    recordScope.launch {
                        val ua = graph.sourceRepository.byId(channel.sourceId)?.userAgent ?: "OpenTV/0.1 (Android)"
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(android.net.Uri.parse(channel.streamUrl), "video/*")
                            putExtra("title", channel.shownName)
                            // MX Player / VLC read the User-Agent from this header extra.
                            putExtra("headers", arrayOf("User-Agent", ua))
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching {
                            context.startActivity(
                                Intent.createChooser(intent, context.getString(R.string.guide_play_channel_with))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }.onFailure {
                            Toast.makeText(context, context.getString(R.string.guide_no_external_player), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    logoPicker?.let { pickerRow ->
        LogoPickerDialog(
            row = pickerRow,
            catalog = graph.catalogRepository,
            onPick = { url ->
                graph.settings.setCustomLogo(pickerRow.primary.groupKey, url)
                logoPicker = null
            },
            onDismiss = { logoPicker = null },
        )
    }

    guidePicker?.let { pickerRow ->
        GuidePickerDialog(
            row = pickerRow,
            epg = graph.epgRepository,
            onPick = { epgId ->
                guidePicker = null
                recordScope.launch {
                    graph.epgRepository.setManualOverride(pickerRow.primary, epgId)
                    Toast.makeText(
                        context,
                        context.getString(
                            if (epgId == null) R.string.guide_picker_cleared else R.string.guide_picker_set,
                            pickerRow.primary.shownName,
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            onDismiss = { guidePicker = null },
        )
    }

    if (showBackgroundPrompt) {
        RecordingBackgroundDialog(
            onAllow = {
                showBackgroundPrompt = false
                context.requestIgnoreBatteryOptimizations()
            },
            onDismiss = { showBackgroundPrompt = false },
        )
    }
}

/** Inserts a reminder for a future programme and arms its alarm. No-op if one already exists. */
internal suspend fun setReminder(
    graph: ServiceLocator.Graph,
    context: android.content.Context,
    channel: Channel,
    programme: Programme,
    autoTune: Boolean,
) {
    if (graph.reminderRepository.forProgramme(channel.id, programme.startUtcMillis) != null) return
    val id = graph.reminderRepository.insert(
        Reminder(
            channelId = channel.id,
            channelName = channel.displayName,
            logoUrl = channel.logo(graph.settings.logoSource.value, graph.settings.logoPack.value),
            title = programme.title,
            startUtcMillis = programme.startUtcMillis,
            endUtcMillis = programme.endUtcMillis,
            autoTune = autoTune,
            createdAtMillis = System.currentTimeMillis(),
        ),
    )
    ReminderScheduler.set(context, id, programme.startUtcMillis)
}

@Composable
private fun RailEntry(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
private fun ChannelRow(
    row: ChannelsViewModel.Row,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleFavourite: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val background =
        if (isSelected) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.background

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLogo(
            logoUrl = row.primary.shownLogo(),
            name = row.primary.shownName,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            // Logo, name, guide — and nothing else. Quality is a playback decision;
            // its switch lives in the player, not as clutter on every row.
            Text(
                row.primary.shownName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = row.now?.let { "${formatTime(it.startUtcMillis)}  ${it.title}" }
                    // Honest rather than blank. A user who sees this on every channel knows
                    // the guide is the problem, not their provider.
                    ?: stringResource(R.string.guide_no_info),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            row.now?.let { programme ->
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { programme.progressAt(now) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                )
            }
            row.next?.let { next ->
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.guide_next_prefix, formatTime(next.startUtcMillis), next.title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        IconButton(onClick = onToggleFavourite) {
            Icon(
                imageVector = if (row.primary.favourite) Icons.Default.Star
                else Icons.Outlined.StarOutline,
                contentDescription = if (row.primary.favourite) stringResource(R.string.common_remove_favourite) else stringResource(R.string.common_favourite),
            )
        }
    }
}

@Composable
private fun LoadingState(isSyncing: Boolean) {
    val s by app.opentv.core.StatusBus.message.collectAsStateWithLifecycle()
    val p by app.opentv.core.StatusBus.progress.collectAsStateWithLifecycle()
    val status = s
    val progress = p
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 460.dp),
        ) {
            if (progress == null) {
                CircularProgressIndicator()
            } else {
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.guide_loading_channels), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    // Actively assembling — this is work in progress, not a failure.
                    status != null -> status
                    isSyncing -> stringResource(R.string.guide_fetching_desc)
                    // Channels are already on the device; the guide is being built from them.
                    // A big provider takes a moment (longer on this debug build) — not a failure.
                    else -> stringResource(R.string.guide_building_desc)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (progress != null) {
                Spacer(Modifier.height(20.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                )
            }
        }
    }
}

@Composable
private fun NoFavouritesState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.guide_no_favourites_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.guide_no_favourites_desc),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Shown when a provider is configured but there are no channels to display and nothing is
 * syncing — i.e. the last catalogue load failed or came back empty. Replaces the endless
 * "Loading your channels" spinner with something the user can act on.
 */
@Composable
private fun ChannelsErrorState(onRetry: () -> Unit, onEditProvider: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 460.dp),
        ) {
            Text(
                stringResource(R.string.guide_load_failed_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.guide_load_failed_desc),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.Button(onClick = onRetry) {
                    Text(stringResource(R.string.common_try_again))
                }
                androidx.compose.material3.OutlinedButton(onClick = onEditProvider) {
                    Text(stringResource(R.string.guide_edit_provider))
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onAddSource: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.guide_no_channels_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.guide_add_provider_desc),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.Button(onClick = onAddSource) { Text(stringResource(R.string.guide_add_provider_button)) }
        }
    }
}

/** UTC in the database, device zone on screen. Converted here and nowhere else. */
private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun formatTime(utcMillis: Long): String = timeFormat.format(Date(utcMillis))

/** After a read of tonight's programmes that failed: when to try again. */
private const val HIGHLIGHTS_RETRY_MILLIS = 5 * 60_000L

/** Days from [now]'s day to [at]'s, in the device's time zone (0 = today, 1 = tomorrow). */
private fun dayOffsetOf(at: Long, now: Long): Int {
    val zone = java.util.TimeZone.getDefault()
    fun day(t: Long) = Math.floorDiv(t + zone.getOffset(t), 86_400_000L)
    return (day(at) - day(now)).toInt()
}
