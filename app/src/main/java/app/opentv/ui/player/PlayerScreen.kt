/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.player

import android.os.SystemClock
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.clipToBounds
import app.opentv.data.model.logo
import app.opentv.ui.ChannelsViewModel
import app.opentv.ui.channels.GroupBadgeSlot
import app.opentv.ui.channels.LiveGroupEntry
import app.opentv.ui.channels.LocalLogoPack
import app.opentv.ui.channels.LocalLogoSource
import app.opentv.ui.channels.rememberLiveGroupEntries
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.opentv.R
import app.opentv.core.ManualSourceMode
import app.opentv.core.QualityPreference
import app.opentv.core.ServiceLocator
import app.opentv.core.SleepTimer
import app.opentv.core.findActivity
import app.opentv.core.requestIgnoreBatteryOptimizations
import app.opentv.data.model.Channel
import app.opentv.data.model.Programme
import app.opentv.data.model.shownName
import app.opentv.player.HiccupWatch
import app.opentv.player.PlaybackQueue
import app.opentv.player.PlayerController
import app.opentv.player.StallWatch
import app.opentv.player.StreamInfo
import app.opentv.player.QualityNames
import app.opentv.player.WorkingVariants
import app.opentv.ui.RecordingBackgroundDialog
import app.opentv.ui.RecordingBackgroundPrompt
import app.opentv.ui.channels.shownLogo
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import app.opentv.ui.theme.focusLift
import app.opentv.core.AppText

/**
 * Full-screen live playback.
 *
 * The video fills the screen with no chrome. A single control bar slides up from the bottom and
 * holds everything — transport (play/pause, rewind, forward), and pickers for subtitles, audio,
 * quality and aspect ratio. It hides after a few seconds and any remote button brings it back.
 * Nothing is ever left permanently painted over the picture.
 *
 * Subtitles and audio come from the actual tracks in the stream ([PlayerController.tracks]) and
 * are selected explicitly — that is the fix for "captions on but nothing shows", which happens
 * when the renderer is merely enabled and left to guess a language.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    channelId: Long?,
    // The guide's view model: the side menu browses its groups and channels.
    channels: ChannelsViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val graph = remember { ServiceLocator.get(context) }
    val settings = remember { graph.settings }
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    val subtitlesDefault by settings.subtitlesEnabled.collectAsState()
    val controller = remember {
        PlayerController(
            context, scope, graph.streamingHttpClient,
            subtitlesEnabled = settings.subtitlesEnabled.value,
            // Opt-in shallow DVR so the transport's pause/rewind actually holds on a live stream.
            dvr = settings.livePauseEnabled.value,
        )
    }
    val state by controller.state.collectAsState()
    val tracks by controller.tracks.collectAsState()
    // What's recording right now, so the Record button can show as armed for this channel.
    val activeRecordings by graph.recordingRepository.observeActive().collectAsState(initial = emptyList())

    // Hold the screen awake while the player is on screen. A view-level keepScreenOn flag isn't
    // reliable on every TV box, so we set the window flag on the Activity directly — that's what
    // actually stops the system screensaver from firing mid-programme. keepScreenOn stays on too,
    // as a belt-and-braces backstop.
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view.keepScreenOn = true
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            view.keepScreenOn = false
            controller.release()
            scope.cancel()
        }
    }

    var variants by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var currentId by remember { mutableStateOf<Long?>(null) }
    // The channel as listed in [queue]; [currentId] is whichever quality variant of it is playing,
    // which a fallback or a manual quality pick can change. Zapping and the list follow this one.
    var listedId by remember { mutableStateOf<Long?>(null) }
    var listedChannel by remember { mutableStateOf<Channel?>(null) }
    // Quality fallback: the variants in try order (preferred first), the ones already tried for this
    // channel, and whether the current attempt has shown a picture yet. [attempt] restarts the timer.
    var tryOrder by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var tried by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var started by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var fallbackNotice by remember { mutableStateOf<String?>(null) }
    // Every variant of the channel failed or timed out: say so instead of spinning forever.
    var unavailable by remember { mutableStateOf(false) }
    // The variant that was playing before a stall switch: if every other one then fails to start,
    // go back to it rather than give up — it was working a moment ago.
    var stallReturnId by remember { mutableStateOf<Long?>(null) }
    // The playing variant was picked by hand: Settings › Playback decides which automatic switches
    // still apply to it. Cleared by a channel change or an automatic switch.
    var manualPick by remember { mutableStateOf(false) }
    // The channel we were on before this one — powers the "Last channel" recall in the list.
    var previousChannel by remember { mutableStateOf<Channel?>(null) }
    // Digits typed on the remote accumulate here, then jump to that channel number after a beat.
    var numberEntry by remember { mutableStateOf("") }
    var paused by remember { mutableStateOf(false) }
    // Bumped on a seek or a same-stream retry so the stall watch starts its clock over.
    var watchEpoch by remember { mutableIntStateOf(0) }
    var resizeMode by remember { mutableIntStateOf(settings.playerResizeMode.value) }

    var controlsVisible by remember { mutableStateOf(true) }
    // Set on a Back key-down that will close a layer, so its key-up does the closing.
    var backArmed by remember { mutableStateOf(false) }
    // What's on the current channel right now and next, from the guide. Null when it has no guide.
    var onAir by remember { mutableStateOf<OnAir?>(null) }
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var panel by remember { mutableStateOf(Panel.NONE) }
    // The Kodi-style technical panel (top right). Stays up across zaps until toggled off.
    var showInfo by remember { mutableStateOf(false) }
    var streamInfo by remember { mutableStateOf<StreamInfo.Snapshot?>(null) }
    var channelListVisible by remember { mutableStateOf(false) }
    // The side menu shows the groups in place of the channels (LEFT from a channel).
    var sideGroups by remember { mutableStateOf(false) }
    // A group was just picked there: the channels it had until the new group's arrive.
    var awaitingRows by remember { mutableStateOf<List<ChannelsViewModel.Row>?>(null) }
    // Where focus sits in the side menu, so focusing it can keep asking until it really lands.
    var channelsHaveFocus by remember { mutableStateOf(false) }
    var groupsHaveFocus by remember { mutableStateOf(false) }
    // Bumped once a picked group's channels are in, to put focus on them.
    var listRefocus by remember { mutableIntStateOf(0) }
    var interaction by remember { mutableIntStateOf(0) }
    // Offered once per session the first time the user records here while OpenTV isn't exempt from
    // battery optimisation, so the capture survives the screen sleeping. Never blocks recording.
    var showBackgroundPrompt by remember { mutableStateOf(false) }

    // Picture-in-picture. While the player is on it is "eligible" to shrink to a floating window
    // (pressing Home does it, handled in MainActivity); [inPip] drives hiding all the chrome.
    val inPip by app.opentv.core.PipState.inPip.collectAsState()
    val pipSupported = remember {
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE,
            )
    }
    DisposableEffect(Unit) {
        app.opentv.core.PipState.eligible = true
        onDispose {
            app.opentv.core.PipState.eligible = false
            app.opentv.core.PipState.isPlaying = false
        }
    }
    LaunchedEffect(paused) { app.opentv.core.PipState.isPlaying = !paused }
    val barFocus = remember { FocusRequester() }
    val panelFocus = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }
    val listFocus = remember { FocusRequester() }
    val groupFocus = remember { FocusRequester() }

    // The channel list you were browsing, for channel up/down. Snapshotted on entry so it doesn't
    // shift under you mid-session; playing from another group in the side menu swaps it for that one.
    var queue by remember { mutableStateOf(PlaybackQueue.items) }

    // The side menu browses the guide's current group, with what's on now, and its groups.
    val browseRows by channels.rows.collectAsState()
    val browseGroups by channels.nonEmptyCategoryGroups.collectAsState()
    val groupCounts by channels.groupCounts.collectAsState()
    val recentLiveGroups by channels.recentLiveGroups.collectAsState()
    val showRecentGroups by channels.showRecentGroups.collectAsState()
    val browseCategory by channels.selectedCategory.collectAsState()
    val browseFavourites by channels.favouritesOnly.collectAsState()
    val groupEntries = rememberLiveGroupEntries(
        categories = browseGroups,
        selectedCategory = browseCategory,
        favouritesOnly = browseFavourites,
        counts = groupCounts,
        recentGroups = if (showRecentGroups) recentLiveGroups else emptyList(),
        favouritesLabel = stringResource(R.string.guide_favourites),
        allLabel = stringResource(R.string.guide_all_channels),
        onSelectCategory = channels::selectCategory,
        onSelectFavourites = channels::selectFavourites,
    )
    val logoSource = LocalLogoSource.current
    val logoPack = LocalLogoPack.current
    fun canBrowse() = queue.isNotEmpty() || browseRows.isNotEmpty() || groupEntries.isNotEmpty()

    fun reveal() {
        controlsVisible = true
        interaction++
    }

    fun tuneTo(channel: Channel) {
        currentId = channel.id
        paused = false
        tried = tried + channel.id
        started = false
        unavailable = false
        attempt++
        settings.lastChannelId = channel.id
        scope.launch {
            val source = graph.sourceRepository.byId(channel.sourceId)
            // Xtream/M3U carry a ready URL; a Stalker channel's URL is minted here from its cmd.
            val url = graph.catalogRepository.resolvePlaybackUrl(channel, source)
            controller.play(
                PlayerController.Request(
                    url = url,
                    title = channel.shownName,
                    userAgent = source?.userAgent ?: "OpenTV/0.1 (Android)",
                    isLive = true,
                    liveOffsetMillis = settings.liveOffsetSeconds.value.takeIf { it > 0 }?.times(1_000L),
                ),
                debounce = false,
            )
        }
    }

    /** A variant's name for the switch notice and the picker: `FHD 2 · B`, numbered among its kind. */
    fun variantName(channel: Channel): String =
        QualityNames.of(variants.map { it.id to it.qualityLabel })[channel.id]?.full?.ifBlank { null }
            ?: channel.qualityLabel.ifBlank { channel.shownName }

    /** The Quality button: on to the channel's next feed, wrapping round, and say which one. */
    fun cycleQuality(notice: Boolean = true) {
        val next = StallWatch.nextAfter(variants, currentId) { it.id } ?: return
        tried = emptySet()
        stallReturnId = null
        manualPick = true
        if (notice) fallbackNotice = variantName(next)
        tuneTo(next)
        interaction++
    }

    // ☰ steps through the channel's sources; each press shows their list for a moment, the
    // highlight sliding onto the new one.
    var sourcesShown by remember { mutableStateOf(false) }
    var sourcesPing by remember { mutableIntStateOf(0) }
    LaunchedEffect(sourcesPing) {
        if (sourcesPing == 0) return@LaunchedEffect
        sourcesShown = true
        delay(SOURCES_SHOWN_MILLIS)
        sourcesShown = false
    }

    /** Plays a logical channel from its preferred quality, with a fresh fallback chain. */
    suspend fun startChannel(channel: Channel) {
        listedId = channel.id
        listedChannel = channel
        variants = graph.catalogRepository.variants(channel)
        val preferred = QualityPreference.order(variants, settings.preferredQuality.value)
        // The variant that worked last time this session goes first; the rest stay as fallback.
        val known = WorkingVariants.forChannel(channel)?.let { id -> preferred.firstOrNull { it.id == id } }
        tryOrder = if (known == null) preferred else listOf(known) + (preferred - known)
        tried = emptySet()
        stallReturnId = null
        manualPick = false
        tuneTo(tryOrder.firstOrNull() ?: channel)
    }

    /** Whether the automatic switches leave the playing variant alone: picked by hand, and set to. */
    fun holdingPick(): Boolean = manualPick && settings.manualSourceMode.value != ManualSourceMode.AUTO

    /** The current variant didn't start: move on to the next untried quality, if there is one. */
    fun fallBack() {
        if (started || unavailable || holdingPick()) return
        val next = if (settings.qualityFallback.value) tryOrder.firstOrNull { it.id !in tried } else null
        val back = stallReturnId?.let { id -> variants.firstOrNull { it.id == id } }
        if (next == null && back != null) {
            stallReturnId = null
            tuneTo(back)
            return
        }
        if (next == null) {
            unavailable = true
            controller.stop()
            return
        }
        val failed = tryOrder.firstOrNull { it.id == currentId } ?: variants.firstOrNull { it.id == currentId } ?: next
        fallbackNotice = context.getString(R.string.player_quality_fallback, variantName(failed), variantName(next))
        manualPick = false
        tuneTo(next)
        sourcesPing++
    }

    /**
     * A playing stream stopped moving, or keeps stuttering ([notice] says which): on to the next
     * variant, wrapping round, or retry the only one.
     */
    fun stallSwitch(notice: Int = R.string.player_stall_fallback) {
        val next = StallWatch.nextAfter(tryOrder.ifEmpty { variants }, currentId) { it.id }
        if (next == null) {
            controller.retry()
            watchEpoch++
            return
        }
        val stalled = variants.firstOrNull { it.id == currentId }
        fallbackNotice = context.getString(
            notice,
            stalled?.let { variantName(it) }.orEmpty(),
            variantName(next),
        )
        // The frozen feed sits out the start-up fallback this time round; it comes back in rotation later.
        tried = setOfNotNull(currentId)
        stallReturnId = currentId
        manualPick = false
        tuneTo(next)
        sourcesPing++
    }

    fun playChannelId(id: Long) {
        // Remember where we came from so "Last channel" can bounce straight back. Quality switches
        // go through tuneTo directly, so they never count as a channel change here.
        listedChannel?.let { if (it.id != id) previousChannel = it }
        scope.launch {
            val channel = graph.catalogRepository.channel(id) ?: return@launch
            startChannel(channel)
        }
    }

    /** Position of the playing channel in [queue], matched by any of its quality variants. */
    fun queueIndex(): Int = queue.indexOfFirst { item ->
        item.id == listedId || item.id == currentId || variants.any { it.id == item.id }
    }

    fun zapBy(delta: Int) {
        if (queue.isEmpty()) return
        val cur = queueIndex().let { if (it < 0) 0 else it }
        val next = (cur + delta).coerceIn(0, queue.size - 1)
        if (next != cur) playChannelId(queue[next].id)
    }

    fun toggleRecord() {
        val active = activeRecordings.firstOrNull { it.channelId == currentId }
        if (active != null) {
            graph.recordingEngine.stop(active.id)
        } else {
            val channel = variants.firstOrNull { it.id == currentId } ?: return
            scope.launch { graph.recordingEngine.startChannel(channel) }
            if (RecordingBackgroundPrompt.shouldShow(context)) {
                RecordingBackgroundPrompt.markShown()
                showBackgroundPrompt = true
            }
        }
        interaction++
    }

    LaunchedEffect(channelId) {
        val id = channelId ?: return@LaunchedEffect
        val channel = graph.catalogRepository.channel(id) ?: return@LaunchedEffect
        startChannel(channel)
    }

    // A picture means this variant works; an error before that means try the next quality now.
    LaunchedEffect(Unit) {
        controller.state.collect { s ->
            when (s) {
                is PlayerController.State.Playing -> {
                    started = true
                    stallReturnId = null
                    val listed = listedChannel
                    val id = currentId
                    if (listed != null && id != null) WorkingVariants.remember(listed, id)
                }
                is PlayerController.State.Error ->
                    if (holdingPick() && !started) {
                        // A hand-picked source that errors gets another go rather than being left.
                        val id = currentId
                        scope.launch {
                            delay(MANUAL_RETRY_MILLIS)
                            if (currentId == id && !started && holdingPick()) controller.retry()
                        }
                    } else {
                        fallBack()
                    }
                else -> Unit
            }
        }
    }

    // No picture within the configured wait (a stream stuck "loading" never errors): next quality,
    // or "channel not available" when none is left.
    LaunchedEffect(attempt) {
        if (attempt == 0) return@LaunchedEffect
        delay(settings.qualityFallbackMillis.value.toLong())
        fallBack()
    }

    // While watching: a stream that stops moving — stuck loading, an error the silent restart can't
    // clear, or a frozen picture — moves to the channel's next variant after the configured wait.
    val stallFallback by settings.stallFallback.collectAsState()
    val qualityButtonCycles by settings.qualityButtonCycles.collectAsState()
    val controlsTimeoutSeconds by settings.controlsTimeoutSeconds.collectAsState()
    val stallMillis by settings.stallFallbackMillis.collectAsState()
    // ...and one that keeps stuttering, too briefly each time for the freeze wait, does the same.
    val hiccupLimit by settings.hiccupLimit.collectAsState()
    val manualSourceMode by settings.manualSourceMode.collectAsState()
    LaunchedEffect(started, paused, stallFallback, stallMillis, hiccupLimit, manualPick, manualSourceMode, currentId, watchEpoch) {
        if (!started || paused) return@LaunchedEffect
        val held = manualPick && manualSourceMode != ManualSourceMode.AUTO
        val watch = if (stallFallback && !held) StallWatch(stallMillis.toLong()) else null
        val hiccups = if (hiccupLimit > 0 && !(manualPick && manualSourceMode == ManualSourceMode.STAY)) {
            HiccupWatch(hiccupLimit)
        } else {
            null
        }
        if (watch == null && hiccups == null) return@LaunchedEffect
        while (true) {
            delay(StallWatch.SAMPLE_MILLIS)
            // Paused from outside the bar (lifecycle, PiP): not a stall.
            if (!controller.player.playWhenReady) { watch?.reset(); hiccups?.reset(); continue }
            val p = controller.progress()
            val now = SystemClock.elapsedRealtime()
            if (watch?.sample(now, p.positionMillis, p.renderedFrames, p.hasVideo) == true) {
                stallSwitch()
                return@LaunchedEffect
            }
            if (hiccups?.sample(now, p.positionMillis, p.renderedFrames, p.hasVideo) == true) {
                stallSwitch(R.string.player_stutter_fallback)
                return@LaunchedEffect
            }
        }
    }

    LaunchedEffect(fallbackNotice) {
        if (fallbackNotice != null) {
            delay(FALLBACK_NOTICE_MILLIS)
            fallbackNotice = null
        }
    }

    // Now/next for the channel on screen. Walks the channel's guide ids like the guide does (first
    // one with programmes wins) and reloads when the current programme ends.
    LaunchedEffect(currentId) {
        onAir = null
        val id = currentId ?: return@LaunchedEffect
        val channel = variants.firstOrNull { it.id == id } ?: graph.catalogRepository.channel(id) ?: return@LaunchedEffect
        while (true) {
            val now = System.currentTimeMillis()
            val upcoming = channel.epgCandidates.firstNotNullOfOrNull { epgId ->
                runCatching { graph.epgRepository.upcoming(epgId, now, 2) }.getOrNull()?.takeIf { it.isNotEmpty() }
            }.orEmpty()
            val current = upcoming.firstOrNull()?.takeIf { it.startUtcMillis <= now }
            val next = if (current != null) upcoming.getOrNull(1) else upcoming.firstOrNull()
            onAir = if (current == null && next == null) null else OnAir(current, next)
            val changeAt = (current?.endUtcMillis ?: next?.startUtcMillis) ?: (now + EPG_RETRY_MILLIS)
            delay((changeAt - now).coerceIn(1_000L, EPG_RETRY_MILLIS))
        }
    }

    // Keep the clock and progress bars moving while the panel or the side menu is on screen;
    // ticks on the minute.
    LaunchedEffect(controlsVisible, channelListVisible) {
        while (controlsVisible || channelListVisible) {
            val now = System.currentTimeMillis()
            clock = now
            delay((60_000L - now % 60_000L).coerceAtMost(15_000L))
        }
    }

    // Stream specs for the bar's badges and the info panel, sampled once a second while either
    // is on screen. A new variant starts a fresh meter so its frame rate isn't averaged with the old.
    LaunchedEffect(controlsVisible, showInfo, currentId) {
        if (!controlsVisible && !showInfo) return@LaunchedEffect
        val meter = StreamInfo.Meter()
        while (true) {
            streamInfo = meter.sample(SystemClock.elapsedRealtime(), controller.stats())
            delay(STREAM_INFO_SAMPLE_MILLIS)
        }
    }

    // Sleep timer: when the armed deadline passes, stop and leave the player. Re-arming from
    // settings restarts this effect with the new deadline.
    val sleepDeadline by SleepTimer.deadline.collectAsState()
    LaunchedEffect(sleepDeadline) {
        val d = sleepDeadline ?: return@LaunchedEffect
        val wait = d - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        SleepTimer.clear()
        controller.stop()
        onBack()
    }

    // Apply the saved captions default once the stream's tracks arrive. Only auto-selects when
    // nothing is chosen yet, so it never overrides a track the user picked by hand.
    LaunchedEffect(tracks) {
        val hasText = tracks.groups.any { it.type == C.TRACK_TYPE_TEXT }
        val textChosen = tracks.groups.any { g ->
            g.type == C.TRACK_TYPE_TEXT && (0 until g.length).any { g.isTrackSelected(it) }
        }
        if (subtitlesDefault && hasText && !textChosen) controller.setSubtitlesEnabled(true)
    }

    // Auto-hide the bar after a few seconds — but never while paused or with a picker open.
    LaunchedEffect(controlsVisible, interaction, state, paused, panel, controlsTimeoutSeconds) {
        if (controlsVisible && !paused && panel == Panel.NONE &&
            state is PlayerController.State.Playing
        ) {
            delay(controlsTimeoutSeconds * 1_000L)
            controlsVisible = false
        }
    }

    // Focus: a picker's first row when one is open, otherwise the bar, otherwise the full-screen
    // catcher (so the next remote press brings the bar back).
    LaunchedEffect(controlsVisible, panel) {
        if (controlsVisible) {
            delay(40)
            runCatching { if (panel != Panel.NONE) panelFocus.requestFocus() else barFocus.requestFocus() }
        } else {
            runCatching { rootFocus.requestFocus() }
        }
    }

    // Back steps back out one layer at a time — channel list, then picker, then the control bar —
    // and only leaves the player once nothing is on screen. From immersive it's a single press out,
    // so it never traps you, but it also no longer throws you all the way to the guide just because
    // you wanted to dismiss the bar.
    BackHandler {
        when {
            channelListVisible && sideGroups -> sideGroups = false
            channelListVisible -> channelListVisible = false
            panel != Panel.NONE -> panel = Panel.NONE
            controlsVisible -> controlsVisible = false
            else -> {
                controller.stop()
                onBack()
            }
        }
    }

    // Number entry: once digits stop coming, jump to that channel number in the browsing list.
    LaunchedEffect(numberEntry) {
        if (numberEntry.isEmpty()) return@LaunchedEffect
        delay(NUMBER_ENTRY_TIMEOUT_MILLIS)
        val num = numberEntry.toIntOrNull()
        numberEntry = ""
        val target = num?.let { n -> queue.firstOrNull { it.number == n } }
        if (target != null) playChannelId(target.id)
    }

    // Focus the side menu's channels or groups as they come up (they slide in, so keep asking
    // until the row is there); hand focus back to the video catcher when the menu closes.
    suspend fun focusSide(groups: Boolean) {
        repeat(20) {
            delay(60)
            if (if (groups) groupsHaveFocus else channelsHaveFocus) return
            runCatching { (if (groups) groupFocus else listFocus).requestFocus() }
        }
    }
    LaunchedEffect(channelListVisible, sideGroups) {
        if (channelListVisible) {
            focusSide(sideGroups)
        } else {
            sideGroups = false
            awaitingRows = null
            if (!controlsVisible) runCatching { rootFocus.requestFocus() }
        }
    }

    // A group picked in the side menu: once its channels are in, land on the first (or the one playing).
    LaunchedEffect(browseRows, awaitingRows) {
        val old = awaitingRows ?: return@LaunchedEffect
        if (browseRows === old) {
            // The group may simply hold the same list; don't wait on it forever.
            delay(GROUP_LOAD_TIMEOUT_MILLIS)
        }
        awaitingRows = null
        listRefocus++
    }
    LaunchedEffect(listRefocus) {
        if (listRefocus > 0) focusSide(groups = false)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                // Back closes one layer per press. Handled here, before the focused button sees it:
                // otherwise Compose spends the first press just clearing the button's focus and the
                // bar needs a second Back. From immersive it falls through to the BackHandler.
                if (event.key == Key.Back || event.key == Key.Escape) {
                    val layerUp = channelListVisible || panel != Panel.NONE || controlsVisible
                    return@onPreviewKeyEvent when (event.type) {
                        KeyEventType.KeyDown -> { backArmed = layerUp; layerUp }
                        KeyEventType.KeyUp -> {
                            if (!backArmed) return@onPreviewKeyEvent false
                            backArmed = false
                            when {
                                channelListVisible && sideGroups -> sideGroups = false
                                channelListVisible -> channelListVisible = false
                                panel != Panel.NONE -> { panel = Panel.NONE; interaction++ }
                                else -> controlsVisible = false
                            }
                            true
                        }
                        else -> layerUp
                    }
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val digit = keyToDigit(event.key)
                when {
                    // Typing a channel number jumps to it, TiviMate-style.
                    digit != null -> {
                        numberEntry = (numberEntry + digit).take(4)
                        reveal(); interaction++; true
                    }
                    // ☰ (the remote's menu key) moves on to the channel's next source, from anywhere.
                    event.key == Key.Menu -> {
                        channelListVisible = false
                        panel = Panel.NONE
                        controlsVisible = false
                        if (variants.size > 1) cycleQuality(notice = false)
                        sourcesPing++
                        true
                    }
                    // A picker or the channel list owns the whole d-pad while it's up.
                    channelListVisible || panel != Panel.NONE -> {
                        interaction++
                        false
                    }
                    // Channel up/down works whether or not the bar is showing — this remote has no
                    // CH+/CH- keys, so up/down IS the channel changer. Each zap flashes the bar as a
                    // channel banner, then it auto-hides.
                    event.key == Key.DirectionUp || event.key == Key.ChannelUp -> { zapBy(-1); reveal(); true }
                    event.key == Key.DirectionDown || event.key == Key.ChannelDown -> { zapBy(1); reveal(); true }
                    // OK on "channel not available" tries the whole chain again.
                    unavailable && (event.key == Key.DirectionCenter || event.key == Key.Enter) -> {
                        listedChannel?.let { scope.launch { startChannel(it) } }
                        true
                    }
                    // With the bar up, left/right drive its buttons; let them through.
                    controlsVisible -> {
                        interaction++
                        false
                    }
                    // Immersive: left opens the channel list, right brings up the playback bar.
                    event.key == Key.DirectionLeft -> { if (canBrowse()) channelListVisible = true; true }
                    event.key == Key.DirectionRight -> { reveal(); true }
                    else -> { reveal(); true }
                }
            }
            .focusRequester(rootFocus)
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures { if (controlsVisible) controlsVisible = false else reveal() }
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = controller.player
                    useController = false
                    subtitleView?.setUserDefaultStyle()
                    subtitleView?.setUserDefaultTextSize()
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            update = { it.resizeMode = resizeMode },
        )

        fallbackNotice?.let { notice ->
            if (!inPip) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(32.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.82f))
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Text(notice, color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        if (showInfo && !inPip) {
            StreamInfoPanel(streamInfo, Modifier.align(Alignment.TopEnd).padding(32.dp))
        }

        AnimatedVisibility(
            visible = sourcesShown && !inPip && variants.isNotEmpty(),
            enter = fadeIn() + slideInHorizontally { it / 2 },
            exit = fadeOut() + slideOutHorizontally { it / 2 },
            modifier = Modifier.align(Alignment.TopEnd).padding(32.dp),
        ) {
            SourcesList(
                names = variants.map { variantName(it) },
                current = variants.indexOfFirst { it.id == currentId }.coerceAtLeast(0),
            )
        }

        // The channel number as you type it, top-right, until it resolves.
        if (numberEntry.isNotEmpty() && !inPip) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(32.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.82f))
                    .padding(horizontal = 28.dp, vertical = 16.dp),
            ) {
                Text(numberEntry, color = Color.White, style = MaterialTheme.typography.displaySmall)
            }
        }

        if (unavailable) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(48.dp),
                ) {
                    Text(
                        listedChannel?.shownName.orEmpty(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.player_channel_unavailable),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.player_channel_unavailable_hint),
                        color = Color.White.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else when (val current = state) {
            is PlayerController.State.Buffering -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(current.title, color = Color.White)
                    }
                }
            }

            is PlayerController.State.Error -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(48.dp),
                    ) {
                        Text(current.title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            current.message,
                            color = Color.White.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { controller.retry() }) { Text(stringResource(R.string.common_try_again)) }
                    }
                }
            }

            else -> Unit
        }

        val channelTitle = when (val s = state) {
            is PlayerController.State.Buffering -> s.title
            is PlayerController.State.Playing -> s.title
            is PlayerController.State.Error -> s.title
            else -> ""
        }

        AnimatedVisibility(
            visible = controlsVisible && !inPip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    // Fades in over the top inset, then stays dark behind all the text: with the
                    // now/next synopsis the bar is tall, and a plain two-stop gradient left the
                    // title and times over near-clear video — unreadable on a bright pitch.
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.18f to Color.Black.copy(alpha = 0.72f),
                            1f to Color.Black.copy(alpha = 0.92f),
                        ),
                    )
                    // Keep the controls clear of the phone/tablet system nav bar (and the side nav
                    // bar / display cutout in landscape). Zero on a TV, so the 10-foot layout is
                    // unchanged; the gradient above stays full-bleed to the screen edge.
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                    )
                    .padding(start = 28.dp, end = 28.dp, top = 64.dp, bottom = 20.dp),
            ) {
                // The open picker sits above the transport row.
                if (panel != Panel.NONE) {
                    OptionPanel(
                        panel = panel,
                        controller = controller,
                        settings = settings,
                        tracks = tracks,
                        variants = variants,
                        currentId = currentId,
                        resizeMode = resizeMode,
                        onResize = { resizeMode = it; settings.setPlayerResizeMode(it) },
                        onTune = { tried = emptySet(); manualPick = true; tuneTo(it) },
                        variantName = { variantName(it) },
                        firstFocus = panelFocus,
                        onDone = { panel = Panel.NONE; interaction++ },
                    )
                    Spacer(Modifier.height(16.dp))
                }

                // The channel's logo, large, on the left; everything else runs from it to the right edge.
                val logoUrl = (variants.firstOrNull { it.id == currentId }?.shownLogo() ?: listedChannel?.shownLogo())
                    ?.takeIf { it.isNotBlank() }
                Row {
                    if (logoUrl != null) {
                        Box(
                            Modifier
                                .padding(top = 4.dp)
                                .size(LOGO_SIZE)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .padding(14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AsyncImage(
                                model = logoUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Spacer(Modifier.width(28.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    channelTitle,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                streamInfo?.badges?.takeIf { it.isNotEmpty() }?.let { badges ->
                                    Spacer(Modifier.width(16.dp))
                                    StreamBadges(badges)
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Text(
                                clockText(clock),
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White.copy(alpha = 0.9f),
                            )
                        }
                        Spacer(Modifier.height(12.dp))

                        onAir?.let { info ->
                            OnAirPanel(info, clock)
                            Spacer(Modifier.height(16.dp))
                        }

                        // Icon-only buttons; the focused one shows its name above it (BarChip), so the
                        // spacer above leaves room for that label.
                        Spacer(Modifier.height(20.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (controller.isSeekable) {
                                BarChip(Icons.Filled.FastRewind, stringResource(R.string.player_rewind), false) {
                                    controller.seekBackward(); watchEpoch++; interaction++
                                }
                                Spacer(Modifier.width(10.dp))
                            }
                            BarChip(
                                icon = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                label = if (paused) stringResource(R.string.common_play) else stringResource(R.string.player_pause),
                                selected = false,
                                focusRequester = barFocus,
                            ) {
                                paused = !paused
                                controller.player.playWhenReady = !paused
                                interaction++
                            }
                            if (controller.isSeekable) {
                                Spacer(Modifier.width(10.dp))
                                BarChip(Icons.Filled.FastForward, stringResource(R.string.player_forward), false) {
                                    controller.seekForward(); watchEpoch++; interaction++
                                }
                            }

                            // Most-used first: quality, then audio and subtitles; aspect is rarely touched.
                            Spacer(Modifier.width(28.dp))
                            if (variants.size > 1) {
                                // OK steps to the next feed and a long press opens the full list — or,
                                // with the setting off, OK opens the list straight away.
                                val togglePicker: () -> Unit = {
                                    panel = if (panel == Panel.QUALITY) Panel.NONE else Panel.QUALITY
                                    interaction++
                                }
                                BarChip(
                                    icon = Icons.Filled.HighQuality,
                                    label = stringResource(R.string.player_quality),
                                    selected = panel == Panel.QUALITY,
                                    badge = QualityNames.of(variants.map { it.id to it.qualityLabel })[currentId]?.badge,
                                    onLongClick = if (qualityButtonCycles) togglePicker else null,
                                    onClick = if (qualityButtonCycles) ({ cycleQuality() }) else togglePicker,
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                            BarChip(Icons.Filled.Audiotrack, stringResource(R.string.player_audio), panel == Panel.AUDIO) {
                                panel = if (panel == Panel.AUDIO) Panel.NONE else Panel.AUDIO; interaction++
                            }
                            Spacer(Modifier.width(10.dp))
                            BarChip(Icons.Filled.Subtitles, stringResource(R.string.player_subtitles), panel == Panel.SUBTITLES) {
                                panel = if (panel == Panel.SUBTITLES) Panel.NONE else Panel.SUBTITLES; interaction++
                            }
                            Spacer(Modifier.width(10.dp))
                            BarChip(Icons.Filled.AspectRatio, stringResource(R.string.player_aspect), panel == Panel.ASPECT) {
                                panel = if (panel == Panel.ASPECT) Panel.NONE else Panel.ASPECT; interaction++
                            }
                            Spacer(Modifier.width(10.dp))
                            BarChip(Icons.Filled.Info, stringResource(R.string.player_info), showInfo) {
                                showInfo = !showInfo; interaction++
                            }

                            // Record sits apart so it isn't hit by accident.
                            Spacer(Modifier.width(28.dp))
                            val recordingThis = activeRecordings.any { it.channelId == currentId }
                            BarChip(
                                icon = Icons.Filled.FiberManualRecord,
                                label = if (recordingThis) stringResource(R.string.common_stop) else stringResource(R.string.player_record),
                                selected = recordingThis,
                                iconTint = Color(0xFFE53935),
                            ) { toggleRecord() }
                            if (pipSupported) {
                                Spacer(Modifier.width(10.dp))
                                BarChip(Icons.Filled.PictureInPictureAlt, stringResource(R.string.player_pop_out), false) {
                                    (context.findActivity() as? app.opentv.MainActivity)?.enterPipNow()
                                    interaction++
                                }
                            }
                        }
                    }
                }
            }
        }

        // Left-side transparent menu — d-pad Left opens the current group's channels with what's
        // on now; LEFT again slides the groups in, in their place, to browse another one.
        AnimatedVisibility(
            visible = channelListVisible && !inPip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            AnimatedContent(
                targetState = sideGroups,
                transitionSpec = {
                    // Groups come in from the left, channels from the right.
                    val dir = if (targetState) -1 else 1
                    (slideInHorizontally { it * dir } + fadeIn()) togetherWith (slideOutHorizontally { -it * dir } + fadeOut())
                },
                modifier = Modifier
                    .fillMaxHeight()
                    .width(SIDE_MENU_WIDTH)
                    .background(Color.Black.copy(alpha = 0.74f))
                    .clipToBounds(),
                label = "sideMenu",
            ) { showGroups ->
                if (showGroups) {
                    SideGroups(
                        modifier = Modifier.onFocusChanged { groupsHaveFocus = it.hasFocus },
                        entries = groupEntries,
                        focusRequester = groupFocus,
                        onPick = { entry ->
                            if (!entry.selected) {
                                awaitingRows = browseRows
                                entry.pick()
                            }
                            sideGroups = false
                        },
                        onRight = { sideGroups = false },
                    )
                } else {
                    val playingIndex = browseRows.indexOfFirst { row ->
                        row.variants.any { it.id == listedId || it.id == currentId }
                    }
                    val title = groupEntries.firstOrNull { it.selected }?.label
                        ?: stringResource(R.string.common_channels)
                    Column(
                        Modifier
                            .fillMaxSize()
                            .onFocusChanged { channelsHaveFocus = it.hasFocus }
                            .padding(vertical = 16.dp),
                    ) {
                        Text(
                            "◀  $title",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        // A quick "jump back to the channel you just left" pin, TiviMate-style.
                        val last = previousChannel
                        if (last != null && last.id != listedId) {
                            val lastRow = browseRows.firstOrNull { row -> row.variants.any { it.id == last.id } }
                            ChannelListRow(
                                name = last.shownName,
                                logoUrl = last.shownLogo(),
                                now = lastRow?.onAt(clock),
                                nowMillis = clock,
                                playing = false,
                                focusRequester = null,
                                leadingLabel = stringResource(R.string.player_last),
                                onLeft = { sideGroups = true },
                                onClick = {
                                    playChannelId(last.id)
                                    channelListVisible = false
                                },
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        when {
                            awaitingRows != null -> Box(
                                Modifier.fillMaxSize().focusRequester(listFocus).focusable(),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator() }
                            browseRows.isNotEmpty() -> {
                                val start = playingIndex.coerceAtLeast(0)
                                val listState = rememberLazyListState()
                                LaunchedEffect(browseRows) { runCatching { listState.scrollToItem((start - 3).coerceAtLeast(0)) } }
                                LazyColumn(state = listState) {
                                    itemsIndexed(browseRows, key = { _, row -> row.key }) { index, row ->
                                        ChannelListRow(
                                            name = row.primary.shownName,
                                            logoUrl = row.primary.shownLogo(),
                                            now = row.onAt(clock),
                                            nowMillis = clock,
                                            playing = index == playingIndex,
                                            focusRequester = if (index == start) listFocus else null,
                                            onLeft = { sideGroups = true },
                                            onClick = {
                                                // Zapping now runs through the group this channel came from.
                                                queue = browseRows.map {
                                                    PlaybackQueue.Item(it.primary.id, it.primary.shownName, it.primary.logo(logoSource, logoPack), it.primary.number)
                                                }
                                                PlaybackQueue.items = queue
                                                channels.markGroupWatched()
                                                playChannelId(row.primary.id)
                                                channelListVisible = false
                                            },
                                        )
                                    }
                                }
                            }
                            // The guide's list isn't in yet (straight into the player at start-up):
                            // the snapshot this player opened with, without the guide.
                            else -> {
                                val currentIndex = queueIndex().coerceAtLeast(0)
                                val listState = rememberLazyListState()
                                LaunchedEffect(Unit) { runCatching { listState.scrollToItem(currentIndex) } }
                                LazyColumn(state = listState) {
                                    itemsIndexed(queue, key = { _, item -> item.id }) { index, item ->
                                        ChannelListRow(
                                            name = item.name,
                                            logoUrl = item.logoUrl,
                                            now = null,
                                            nowMillis = clock,
                                            playing = index == currentIndex,
                                            focusRequester = if (index == currentIndex) listFocus else null,
                                            onLeft = { sideGroups = true },
                                            onClick = {
                                                playChannelId(item.id)
                                                channelListVisible = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
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

/** The guide's current and following programme for the channel on screen; either may be missing. */
private data class OnAir(val now: Programme?, val next: Programme?)

/** The stream's specs as small outlined tags next to the channel name, Kodi-style. */
@Composable
private fun StreamBadges(badges: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        badges.forEach { badge ->
            Text(
                badge,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.9f),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

/** The full technical read-out, like Kodi's codec info: what the stream is and how it's playing. */
@Composable
private fun StreamInfoPanel(info: StreamInfo.Snapshot?, modifier: Modifier = Modifier) {
    val hardware = stringResource(R.string.stream_info_hardware)
    val software = stringResource(R.string.stream_info_software)
    val rows = buildList {
        if (info != null) {
            listOfNotNull(info.size, info.videoCodec, info.frameRate?.let { "$it fps" }, info.hdr ?: "SDR".takeIf { info.videoCodec != null }, info.videoBitrate)
                .takeIf { it.isNotEmpty() }
                ?.let { add(stringResource(R.string.stream_info_video) to it.joinToString(" · ")) }
            listOfNotNull(info.audioCodec, info.channels, info.sampleRate, info.audioBitrate, info.audioLanguage)
                .takeIf { it.isNotEmpty() }
                ?.let { add(stringResource(R.string.stream_info_audio) to it.joinToString(" · ")) }
            info.videoDecoder?.let { name ->
                add(stringResource(R.string.stream_info_decoder) to "${if (info.videoDecoderSoftware) software else hardware} · $name")
            }
            if (info.videoDecoder != null) {
                add(
                    stringResource(R.string.stream_info_frames) to
                        stringResource(R.string.stream_info_frames_value, info.renderedFrames, info.droppedFrames),
                )
            }
            info.container?.let { add(stringResource(R.string.stream_info_format) to it) }
            listOfNotNull(info.download, info.downloaded).takeIf { it.isNotEmpty() }
                ?.let { add(stringResource(R.string.stream_info_network) to it.joinToString(" · ")) }
            add(stringResource(R.string.stream_info_buffer) to String.format(Locale.getDefault(), "%.1f s", info.bufferedSeconds))
            info.liveDelaySeconds?.let {
                add(stringResource(R.string.stream_info_live_delay) to String.format(Locale.getDefault(), "%.1f s", it))
            }
        }
    }
    Column(
        modifier
            .widthIn(max = 560.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.82f))
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            stringResource(R.string.stream_info_title),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(10.dp))
        if (info == null || (info.videoCodec == null && info.audioCodec == null)) {
            Text(stringResource(R.string.stream_info_waiting), style = MaterialTheme.typography.bodyMedium, color = Color.White)
        }
        rows.forEach { (label, value) ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.width(150.dp),
                )
                Text(value, style = MaterialTheme.typography.bodyMedium, color = Color.White)
            }
        }
    }
}

/** "What's on" block in the player bar: title, times and progress, a short synopsis, then what's next. */
@Composable
private fun OnAirPanel(info: OnAir, clock: Long) {
    Column(Modifier.fillMaxWidth()) {
        info.now?.let { now ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${clockText(now.startUtcMillis)}–${clockText(now.endUtcMillis)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.75f),
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    now.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            val progress = if (now.durationMillis > 0) {
                ((clock - now.startUtcMillis).toFloat() / now.durationMillis).coerceIn(0f, 1f)
            } else 0f
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.25f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Spacer(Modifier.width(14.dp))
                // Rounded up, so the last minute still reads "−1 min" rather than nothing.
                val minutesLeft = ((now.endUtcMillis - clock + 59_999L) / 60_000L).coerceAtLeast(0L)
                Text(
                    stringResource(R.string.player_minutes_left, minutesLeft.toInt()),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.75f),
                )
            }
            now.description?.takeIf { it.isNotBlank() }?.let { desc ->
                Spacer(Modifier.height(8.dp))
                Text(
                    desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        info.next?.let { next ->
            if (info.now != null) Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.guide_next_prefix, clockText(next.startUtcMillis), next.title),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun clockText(utcMillis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(utcMillis))

/**
 * The channel's sources, as ☰ shows them: a short window of the list that keeps [current] in
 * view, with a highlight that slides from one source to the next.
 */
@Composable
private fun SourcesList(names: List<String>, current: Int) {
    val rowHeight = 36.dp
    val visible = names.size.coerceAtMost(SOURCES_VISIBLE)
    val first = (current - SOURCES_VISIBLE / 2).coerceIn(0, (names.size - visible).coerceAtLeast(0))
    val scroll by animateDpAsState(rowHeight * first, label = "sourcesScroll")
    val highlight by animateDpAsState(rowHeight * current, label = "sourcesHighlight")
    Column(
        Modifier
            .width(260.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.78f))
            .padding(vertical = 8.dp),
    ) {
        Text(
            stringResource(R.string.player_sources),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Box(Modifier.fillMaxWidth().height(rowHeight * visible).clipToBounds()) {
            // The whole list at full height, unbounded by the window: squeezed into it, the rows past
            // the ninth were measured to nothing and scrolled in blank.
            Box(
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .offset(y = -scroll),
            ) {
                Box(
                    Modifier
                        .offset(y = highlight)
                        .fillMaxWidth()
                        .height(rowHeight)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Column {
                    names.forEachIndexed { i, name ->
                        Box(
                            Modifier.fillMaxWidth().height(rowHeight).padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (i == current) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (i == current) MaterialTheme.colorScheme.onPrimary else Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** What [this] channel airs at [millis]: from its guide window, so it keeps up while the menu is open. */
private fun ChannelsViewModel.Row.onAt(millis: Long): Programme? =
    programmes.firstOrNull { millis >= it.startUtcMillis && millis < it.endUtcMillis }
        ?: now?.takeIf { millis < it.endUtcMillis }

/** A channel in the side menu: logo, name, and what's on now with its times and progress. */
@Composable
private fun ChannelListRow(
    name: String,
    logoUrl: String?,
    now: Programme?,
    nowMillis: Long,
    playing: Boolean,
    focusRequester: FocusRequester?,
    onLeft: () -> Unit,
    onClick: () -> Unit,
    leadingLabel: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> MaterialTheme.colorScheme.primary
        playing -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        else -> Color.Transparent
    }
    val fg = if (focused) MaterialTheme.colorScheme.onPrimary else Color.White
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                if (focused && e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) { onLeft(); true } else false
            }
            .focusLift(focused)
            .background(bg)
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // No channel number (the provider's jump about): its room goes to the logo.
        AsyncImage(
            model = logoUrl,
            contentDescription = null,
            modifier = Modifier.size(width = 64.dp, height = 32.dp).clip(RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (leadingLabel != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(leadingLabel, style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.7f), maxLines = 1)
                }
            }
            if (now != null) {
                Text(
                    now.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = fg.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${clockText(now.startUtcMillis)}–${clockText(now.endUtcMillis)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = fg.copy(alpha = 0.6f),
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(fg.copy(alpha = 0.2f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(now.progressAt(nowMillis).coerceIn(0f, 1f))
                                .height(3.dp)
                                .background(if (focused) fg else MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
        }
    }
}

/** The side menu's groups, in the channels' place: OK or RIGHT on the current one goes back to its channels. */
@Composable
private fun SideGroups(
    modifier: Modifier,
    entries: List<LiveGroupEntry>,
    focusRequester: FocusRequester,
    onPick: (LiveGroupEntry) -> Unit,
    onRight: () -> Unit,
) {
    val selectedIndex = entries.indexOfFirst { it.selected }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 3).coerceAtLeast(0))
    Column(modifier.fillMaxSize().padding(vertical = 16.dp)) {
        Text(
            stringResource(R.string.player_groups),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(state = listState) {
            itemsIndexed(entries, key = { _, e -> e.key }) { index, entry ->
                Column {
                    entry.section?.let {
                        Text(
                            it.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp),
                        )
                    }
                    var focused by remember { mutableStateOf(false) }
                    val fg = if (focused) MaterialTheme.colorScheme.onPrimary else Color.White
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(if (index == selectedIndex) Modifier.focusRequester(focusRequester) else Modifier)
                            .onFocusChanged { focused = it.isFocused }
                            .onPreviewKeyEvent { e ->
                                if (focused && e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight) { onRight(); true } else false
                            }
                            .focusLift(focused)
                            .background(
                                when {
                                    focused -> MaterialTheme.colorScheme.primary
                                    entry.selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    else -> Color.Transparent
                                },
                            )
                            .clickable(interactionSource = null, indication = null) { onPick(entry) }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GroupBadgeSlot(entry.badge, entry.label, initialsIfBlank = false, modifier = Modifier.size(width = 64.dp, height = 32.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            entry.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (entry.selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = fg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        entry.count?.let {
                            Spacer(Modifier.width(8.dp))
                            Text("$it", style = MaterialTheme.typography.labelLarge, color = fg.copy(alpha = 0.6f), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

private enum class Panel { NONE, SUBTITLES, AUDIO, QUALITY, ASPECT }

@OptIn(UnstableApi::class)
@Composable
private fun OptionPanel(
    panel: Panel,
    controller: PlayerController,
    settings: app.opentv.core.AppSettings,
    tracks: Tracks,
    variants: List<Channel>,
    currentId: Long?,
    resizeMode: Int,
    onResize: (Int) -> Unit,
    onTune: (Channel) -> Unit,
    variantName: (Channel) -> String,
    firstFocus: FocusRequester,
    onDone: () -> Unit,
) {
    val offLabel = stringResource(R.string.player_subtitles_off)
    val standardLabel = stringResource(R.string.player_quality_standard)
    val fitLabel = stringResource(R.string.player_aspect_fit)
    val fillLabel = stringResource(R.string.player_aspect_fill)
    val stretchLabel = stringResource(R.string.player_aspect_stretch)
    val options: List<Option> = when (panel) {
        Panel.SUBTITLES -> buildSubtitleOptions(controller, settings, tracks, offLabel, onDone)
        Panel.AUDIO -> buildAudioOptions(controller, tracks, onDone)
        Panel.QUALITY -> variants.map { v ->
            Option(if (v.qualityLabel.isEmpty()) standardLabel else variantName(v), v.id == currentId) { onTune(v); onDone() }
        }
        Panel.ASPECT -> listOf(
            Option(fitLabel, resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                onResize(AspectRatioFrameLayout.RESIZE_MODE_FIT); onDone()
            },
            Option(fillLabel, resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
                onResize(AspectRatioFrameLayout.RESIZE_MODE_ZOOM); onDone()
            },
            Option(stretchLabel, resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FILL) {
                onResize(AspectRatioFrameLayout.RESIZE_MODE_FILL); onDone()
            },
        )
        Panel.NONE -> emptyList()
    }

    val title = when (panel) {
        Panel.SUBTITLES -> stringResource(R.string.player_subtitles)
        Panel.AUDIO -> stringResource(R.string.player_audio)
        Panel.QUALITY -> stringResource(R.string.player_quality)
        Panel.ASPECT -> stringResource(R.string.player_aspect_ratio_title)
        Panel.NONE -> ""
    }

    // Opened by holding OK on Quality, the same press keeps repeating into the list once focus lands
    // here, and its release would pick the first row. Ignore OK until a fresh press.
    var armed by remember(panel) { mutableStateOf(false) }
    Column(
        Modifier
            // The options are a vertical list: left/right mean nothing here, and without this they
            // let focus escape sideways onto the bar (right from Quality landed on Record).
            .onPreviewKeyEvent { e ->
                val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                when {
                    e.key == Key.DirectionLeft || e.key == Key.DirectionRight -> true
                    armed || !ok -> false
                    e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0 -> { armed = true; false }
                    e.type == KeyEventType.KeyUp -> { armed = true; true }
                    else -> true
                }
            }
            .widthIn(max = 460.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.92f))
            .padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (options.isEmpty()) {
                Text(stringResource(R.string.player_none_available), color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(8.dp))
            }
            options.forEachIndexed { index, option ->
                OptionRow(
                    label = option.label,
                    selected = option.selected,
                    onClick = option.onClick,
                    focusRequester = if (index == 0) firstFocus else null,
                )
            }
        }
    }
}

private data class Option(val label: String, val selected: Boolean, val onClick: () -> Unit)

@OptIn(UnstableApi::class)
private fun buildSubtitleOptions(
    controller: PlayerController,
    settings: app.opentv.core.AppSettings,
    tracks: Tracks,
    offLabel: String,
    onDone: () -> Unit,
): List<Option> {
    val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
    val anySelected = textGroups.any { g -> (0 until g.length).any { g.isTrackSelected(it) } }
    val list = mutableListOf<Option>()
    list += Option(offLabel, !anySelected) {
        controller.disableText()
        settings.setSubtitlesEnabled(false)
        onDone()
    }
    textGroups.forEach { group ->
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            val format = group.getTrackFormat(i)
            list += Option(trackLabel(format.label, format.language, list.size), group.isTrackSelected(i)) {
                controller.selectTrack(group, i)
                settings.setSubtitlesEnabled(true)
                onDone()
            }
        }
    }
    return list
}

@OptIn(UnstableApi::class)
private fun buildAudioOptions(
    controller: PlayerController,
    tracks: Tracks,
    onDone: () -> Unit,
): List<Option> {
    val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    val list = mutableListOf<Option>()
    audioGroups.forEach { group ->
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            val format = group.getTrackFormat(i)
            list += Option(trackLabel(format.label, format.language, list.size), group.isTrackSelected(i)) {
                controller.selectTrack(group, i)
                onDone()
            }
        }
    }
    return list
}

private fun trackLabel(label: String?, language: String?, index: Int): String {
    if (!label.isNullOrBlank()) return label
    if (!language.isNullOrBlank() && language != "und") {
        return runCatching { Locale(language).displayLanguage.ifBlank { language } }.getOrDefault(language)
    }
    return AppText.get(R.string.track_n, index + 1)
}

@Composable
private fun OptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = if (focused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f)
    val fg = if (focused) MaterialTheme.colorScheme.onPrimary else Color.White
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusLift(focused)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = fg, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.common_selected), tint = fg)
        }
    }
}

@Composable
private fun BarChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    focusRequester: FocusRequester? = null,
    iconTint: Color? = null,
    /** Short text drawn instead of the icon (the Quality button's `FHD 2`). */
    badge: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    // Holding OK: the remote repeats the key-down, and the first repeat is the long press.
    var longPressed by remember { mutableStateOf(false) }
    val container = when {
        focused -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        else -> Color.White.copy(alpha = 0.14f)
    }
    val content = if (focused) MaterialTheme.colorScheme.onPrimary else Color.White

    Box(contentAlignment = Alignment.TopCenter) {
        Box(
            modifier = Modifier
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .focusLift(focused)
                .clip(RoundedCornerShape(12.dp))
                .background(container)
                .then(
                    if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                    else Modifier,
                )
                .then(
                    if (onLongClick == null) Modifier
                    else Modifier.onPreviewKeyEvent { e ->
                        if (e.key != Key.DirectionCenter && e.key != Key.Enter && e.key != Key.NumPadEnter) {
                            return@onPreviewKeyEvent false
                        }
                        when (e.type) {
                            KeyEventType.KeyDown -> when {
                                e.nativeKeyEvent.repeatCount == 0 -> longPressed = false
                                !longPressed -> { longPressed = true; onLongClick() }
                            }
                            KeyEventType.KeyUp -> if (!longPressed) onClick()
                        }
                        true
                    },
                )
                .clickable(interactionSource = null, indication = null, onClick = onClick)
                .padding(12.dp),
        ) {
            if (badge.isNullOrBlank()) {
                Icon(icon, contentDescription = label, tint = if (focused) content else (iconTint ?: content))
            } else {
                Box(Modifier.height(24.dp).widthIn(min = 24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        badge,
                        color = content,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        // The name floats above the focused button without taking space, so the row never shifts.
        if (focused) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.layout { measurable, constraints ->
                    val p = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
                    val gap = 8.dp.roundToPx()
                    layout(0, 0) { p.place(-p.width / 2, -p.height - gap) }
                },
            )
        }
    }
}

private const val EPG_RETRY_MILLIS = 5 * 60_000L
// ☰'s list of sources: how long it stays after the last press, and how many rows it shows at once.
private const val SOURCES_SHOWN_MILLIS = 3_000L
private const val SOURCES_VISIBLE = 9
// The side menu: narrow enough to leave most of the picture showing.
private val SIDE_MENU_WIDTH = 360.dp
// How long a picked group may take to bring its channels before the menu stops waiting.
private const val GROUP_LOAD_TIMEOUT_MILLIS = 5_000L
private const val NUMBER_ENTRY_TIMEOUT_MILLIS = 2_000L
private const val FALLBACK_NOTICE_MILLIS = 3_000L
private const val MANUAL_RETRY_MILLIS = 2_000L
private const val STREAM_INFO_SAMPLE_MILLIS = 1_000L
private val LOGO_SIZE = 128.dp

/** Maps a remote's number keys (top row and numeric keypad) to a digit, or null for other keys. */
private fun keyToDigit(key: Key): Char? = when (key) {
    Key.Zero, Key.NumPad0 -> '0'
    Key.One, Key.NumPad1 -> '1'
    Key.Two, Key.NumPad2 -> '2'
    Key.Three, Key.NumPad3 -> '3'
    Key.Four, Key.NumPad4 -> '4'
    Key.Five, Key.NumPad5 -> '5'
    Key.Six, Key.NumPad6 -> '6'
    Key.Seven, Key.NumPad7 -> '7'
    Key.Eight, Key.NumPad8 -> '8'
    Key.Nine, Key.NumPad9 -> '9'
    else -> null
}
