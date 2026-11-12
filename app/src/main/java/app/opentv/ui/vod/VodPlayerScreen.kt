/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.vod

import android.view.ViewGroup
import app.opentv.ui.focusVeil
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import app.opentv.R
import app.opentv.core.ServiceLocator
import app.opentv.core.SleepTimer
import app.opentv.core.findActivity
import app.opentv.player.PlayerController
import app.opentv.player.StreamInfo
import app.opentv.data.repo.PlayerHeading
import app.opentv.ui.player.StreamInfoPanel
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import app.opentv.ui.theme.focusLift
import app.opentv.core.AppText

/**
 * Plays a movie or episode: a single non-live stream, with resume and a proper transport bar.
 *
 * The bar is drawn in Compose rather than left to the Media3 view's own controller, because that
 * controller needs the embedded view to hold d-pad focus to be summoned — which it doesn't, on a
 * TV inside Compose, so it reads as "no controls". With the bar hidden the remote works the way
 * TV players do: left/right seek (accelerating while held), OK pauses. Audio and subtitle picks
 * are remembered for the next film, as are the subtitle look and the picture format.
 */
@OptIn(UnstableApi::class)
@Composable
fun VodPlayerScreen(
    mediaKey: String,
    streamUrl: String,
    title: String,
    userAgent: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val graph = remember { ServiceLocator.get(context) }
    val settings = remember { graph.settings }
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    // A recording that's still being written plays through the tail-following source. It behaves a
    // little differently in the transport: the "length" is how much has been recorded so far, which
    // keeps growing, and seeking is bounded to that recorded extent.
    val growingRec = remember(streamUrl) { streamUrl.startsWith("optvrec://") }
    // "Restart" from the live player: the programme on now, out of the archive. It starts at the
    // top, isn't kept for Continue Watching, and goes back to live when it catches up.
    val restart = remember(mediaKey) { RestartKey.parse(mediaKey) }
    // The SMB source lets a recording stored on a NAS play and seek in-app; harmless for the
    // http/file URLs of ordinary VOD.
    val controller = remember {
        PlayerController(
            context, scope, graph.streamingHttpClient, subtitlesEnabled = false,
            smbDataSourceFactory = app.opentv.player.SmbDataSource.Factory(graph.settings),
            // Lets an `optvrec://<id>` recording play while it's still being written.
            growingDataSourceFactory =
                app.opentv.player.GrowingRecordingDataSource.Factory(context.applicationContext),
            liveRecording = growingRec,
            vod = !growingRec,
        ).also { it.setPreferredLanguages(settings.vodAudioLanguage.value, settings.vodSubtitleLanguage.value) }
    }
    // Skip forward/back within the recorded portion. For a growing recording ExoPlayer won't report
    // the item as seekable (no fixed length), so we seek directly, clamped to what's on disk.
    fun seekRelative(deltaMs: Long) {
        val p = controller.player
        val ceiling = if (growingRec) p.bufferedPosition.coerceAtLeast(0L)
        else (p.duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
        p.seekTo((p.currentPosition + deltaMs).coerceIn(0L, ceiling))
    }
    val state by controller.state.collectAsStateWithLifecycle()
    val tracks by controller.tracks.collectAsStateWithLifecycle()
    val resizeMode by settings.vodResizeMode.collectAsStateWithLifecycle()
    val subtitleSize by settings.subtitleSize.collectAsStateWithLifecycle()
    val subtitleBackground by settings.subtitleBackground.collectAsStateWithLifecycle()

    var paused by remember { mutableStateOf(false) }
    var vodPanel by remember { mutableStateOf(VodPanel.NONE) }
    val panelFocus = remember { FocusRequester() }
    val subtitlesChipFocus = remember { FocusRequester() }
    val audioChipFocus = remember { FocusRequester() }
    // Closing a picker puts focus back on the button that opened it, not on the seek bar.
    var lastPanel by remember { mutableStateOf(VodPanel.NONE) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var bufferedMs by remember { mutableLongStateOf(0L) }
    // Where a run of left/right presses has got to; the player only seeks once they stop, so
    // holding the button glides the marker instead of rebuffering at every step.
    var pendingSeekMs by remember { mutableStateOf<Long?>(null) }
    var seekCommit by remember { mutableStateOf<Job?>(null) }

    // Bumped by the format button; the new format's name shows next to it for a moment.
    var aspectHint by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var aspectHintVisible by remember { mutableStateOf(false) }
    LaunchedEffect(aspectHint) {
        if (aspectHint == 0) return@LaunchedEffect
        aspectHintVisible = true
        delay(1_500)
        aspectHintVisible = false
    }

    var controlsVisible by remember { mutableStateOf(true) }

    // Heading: the title's TMDB logo (or its name) and, for an episode, "S4 · E7 · name".
    var heading by remember { mutableStateOf<PlayerHeading?>(null) }
    var headingLogo by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(mediaKey) {
        val found = runCatching { graph.catalogRepository.playerHeading(mediaKey) }.getOrNull() ?: return@LaunchedEffect
        heading = found
        headingLogo = runCatching {
            graph.vodHomeRepository.logoUrl(
                if (found.isMovie) app.opentv.data.remote.TmdbLists.Kind.MOVIE else app.opentv.data.remote.TmdbLists.Kind.TV,
                found.tmdbId,
            )
        }.getOrNull()
    }

    // "Stats for nerds", from the i button: stays up with the bar hidden until closed.
    var showStats by remember { mutableStateOf(false) }
    var statsInfo by remember { mutableStateOf<StreamInfo.Snapshot?>(null) }
    var statsExtra by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val fileLabel = stringResource(R.string.stream_info_file)
    val screenLabel = stringResource(R.string.stream_info_screen)
    LaunchedEffect(showStats) {
        if (!showStats) return@LaunchedEffect
        val meter = StreamInfo.Meter()
        while (isActive) {
            val stats = controller.stats()
            statsInfo = meter.sample(android.os.SystemClock.elapsedRealtime(), stats)
            statsExtra = listOfNotNull(
                fileRow(stats, streamUrl)?.let { fileLabel to it },
                screenRow(view)?.let { screenLabel to it },
            )
            delay(1_000)
        }
    }

    // The wall clock for "Ends at", refreshed while the bar is up (a paused film keeps moving it on).
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(controlsVisible) {
        while (controlsVisible) {
            nowMillis = System.currentTimeMillis()
            delay(10_000)
        }
    }
    var interaction by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val seekFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }
    // The bar's buttons take focus a beat after it appears; until then the screen itself holds it,
    // and presses in that gap must still seek/pause rather than fall through.
    var rootFocused by remember { mutableStateOf(false) }

    fun reveal() { controlsVisible = true; interaction++ }

    fun togglePause() {
        paused = !paused
        controller.player.playWhenReady = !paused
        reveal()
    }

    // A restarted programme keeps its own clock. The archive restarts its timestamps between
    // segments, so the player's own position and length are nonsense (a 20-minute archive reads as
    // 24 hours): the place is where the current link starts in the programme plus the time played
    // since, and moving is asking the archive again from another minute.
    var restartBaseMs by remember { mutableLongStateOf(0L) }
    var restartPlayedMs by remember { mutableLongStateOf(0L) }
    fun restartPosition() = restartBaseMs + restartPlayedMs

    /** Plays the restarted programme from [targetMs] (to the minute), or goes live if that's past what's out. */
    fun restartJump(targetMs: Long) {
        val r = restart ?: return
        val liveEdge = minOf(r.endUtcMillis, System.currentTimeMillis() - RESTART_LIVE_MARGIN_MILLIS) - r.startUtcMillis
        if (targetMs >= liveEdge) {
            app.opentv.core.EventLog.log("vod", "restart at live, back to the channel")
            onBack()
            return
        }
        val minute = (targetMs.coerceAtLeast(0L) / 60_000L).toInt()
        restartBaseMs = minute * 60_000L
        restartPlayedMs = 0L
        positionMs = restartBaseMs
        app.opentv.core.EventLog.log("vod", "restart from minute $minute")
        controller.play(
            PlayerController.Request(
                url = r.urlFrom(streamUrl, minute),
                title = title,
                userAgent = userAgent,
                startPositionMillis = 0L,
                isLive = false,
            ),
            debounce = false,
        )
    }

    /**
     * One left/right press (or one auto-repeat of a held button). The step grows the longer the
     * button is held — 10 s taps, then 30 s, a minute, two — so crossing a whole film takes seconds.
     */
    fun nudgeSeek(forward: Boolean, repeatCount: Int) {
        if (growingRec) {
            seekRelative(if (forward) 15_000 else -15_000); reveal(); return
        }
        if (restart != null) {
            // Whole minutes: the archive is asked from a minute, so finer steps would just bounce back.
            val step = when {
                repeatCount < 6 -> 60_000L
                repeatCount < 16 -> 5 * 60_000L
                else -> 10 * 60_000L
            }
            val from = pendingSeekMs ?: positionMs
            pendingSeekMs = (from + if (forward) step else -step).coerceIn(0L, durationMs)
            reveal()
            seekCommit?.cancel()
            seekCommit = scope.launch {
                delay(SEEK_COMMIT_DELAY_MILLIS)
                pendingSeekMs?.let { restartJump(it) }
                pendingSeekMs = null
            }
            return
        }
        if (!controller.isSeekable || durationMs <= 0) { reveal(); return }
        val step = when {
            repeatCount < 4 -> 10_000L
            repeatCount < 12 -> 30_000L
            repeatCount < 24 -> 60_000L
            else -> 120_000L
        }
        val from = pendingSeekMs ?: positionMs
        pendingSeekMs = (from + if (forward) step else -step).coerceIn(0L, durationMs)
        reveal()
        seekCommit?.cancel()
        seekCommit = scope.launch {
            delay(SEEK_COMMIT_DELAY_MILLIS)
            pendingSeekMs?.let { target ->
                controller.player.seekTo(target)
                positionMs = target
            }
            pendingSeekMs = null
        }
    }

    /** Where the player is now, as a resume point — null before it knows the length or in the first seconds. */
    fun currentPoint(): app.opentv.data.model.PlaybackPosition? {
        val player = controller.player
        val pos = player.currentPosition
        val dur = player.duration.takeIf { it > 0 } ?: return null
        if (pos <= 5_000) return null
        return app.opentv.data.model.PlaybackPosition(
            profileId = settings.activeProfileId.value,
            mediaKey = mediaKey,
            positionMillis = pos,
            durationMillis = dur,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    suspend fun savePosition() {
        if (restart != null) return
        currentPoint()?.let { graph.playbackPositions.upsert(it) }
    }

    // Keep the screen awake during playback — see the note in PlayerScreen; a film is exactly when
    // the screensaver must not fire. Window flag is the reliable path; keepScreenOn is a backstop.
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view.keepScreenOn = true
        app.opentv.core.EventLog.keepAwake("VodPlayer", "on", context.findActivity())
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            view.keepScreenOn = false
            app.opentv.core.EventLog.keepAwake("VodPlayer", "off (closed)", context.findActivity())
            // Read the point before the player goes, and write it outside this screen's scope:
            // cancelled right below, it would drop the write (leaving only the 15 s saves).
            currentPoint()?.takeIf { restart == null }?.let { last ->
                CoroutineScope(Dispatchers.IO).launch { graph.playbackPositions.upsert(last) }
            }
            controller.release()
            scope.cancel()
        }
    }

    // Out of sight (Home, standby, another app): save the place, pause and let go of the stream,
    // so a box gone to standby doesn't keep the account's one connection. Back in front, the film
    // is reloaded at the same point and waits, paused.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var stopped = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (!stopped) {
                    stopped = true
                    // Undispatched: the place is read now, before the stop below.
                    scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { savePosition() }
                    paused = true
                    controller.player.playWhenReady = false
                    controller.player.stop()
                }
                Lifecycle.Event.ON_START -> if (stopped) {
                    stopped = false
                    if (restart != null) restartJump(restartPosition()) else controller.player.prepare()
                    controlsVisible = true
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(mediaKey) {
        val resumeFrom = if (restart != null) 0L
        else graph.playbackPositions.get(settings.activeProfileId.value, mediaKey)
            ?.takeIf { !it.isFinished }?.positionMillis ?: 0L
        controller.play(
            PlayerController.Request(
                url = streamUrl,
                title = title,
                userAgent = userAgent,
                startPositionMillis = resumeFrom,
                isLive = false,
            ),
            debounce = false,
        )
        while (isActive) {
            delay(15_000)
            savePosition()
        }
    }

    // Sleep timer: leave the film when the armed deadline passes (position is saved on dispose).
    val sleepDeadline by SleepTimer.deadline.collectAsStateWithLifecycle()
    LaunchedEffect(sleepDeadline) {
        val d = sleepDeadline ?: return@LaunchedEffect
        val wait = d - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        SleepTimer.clear()
        onBack()
    }

    // A restarted programme: the archive only holds what has gone out so far, so reaching its end
    // means asking again (it has grown since) — until the programme is over or we're about at live,
    // where the live channel takes over.
    if (restart != null) {
        LaunchedEffect(Unit) {
            while (isActive) {
                delay(1_000)
                val p = controller.player
                if (p.playbackState != androidx.media3.common.Player.STATE_ENDED) continue
                app.opentv.core.EventLog.log("vod", "restart archive end at ${restartPosition() / 1000}s")
                // Goes live by itself when the place is about at live or past the programme's end.
                restartJump(restartPosition())
                delay(5_000)
            }
        }
    }

    // Poll position/duration for the seek bar while the film plays.
    LaunchedEffect(Unit) {
        var lastTick = android.os.SystemClock.elapsedRealtime()
        while (isActive) {
            val p = controller.player
            if (restart != null) {
                val tick = android.os.SystemClock.elapsedRealtime()
                if (p.isPlaying) restartPlayedMs += tick - lastTick
                lastTick = tick
                positionMs = restartPosition()
                bufferedMs = positionMs
                durationMs = restart.endUtcMillis - restart.startUtcMillis
                delay(500)
                continue
            }
            positionMs = p.currentPosition.coerceAtLeast(0)
            bufferedMs = p.bufferedPosition.coerceAtLeast(0)
            // A growing recording has no fixed length; the recorded-so-far extent (how far you
            // can skip ahead) is what's been read into the buffer.
            durationMs = if (growingRec) bufferedMs else p.duration.takeIf { it > 0 } ?: 0
            delay(500)
        }
    }

    // Auto-hide when playing and not paused/seeking and no picker open. A growing recording dips in
    // and out of Buffering as it rides the write head, so for that case Buffering counts as "playing"
    // here — otherwise a single hiccup would pin the transport bar on screen for the rest of the watch.
    LaunchedEffect(controlsVisible, interaction, state, paused, pendingSeekMs, vodPanel) {
        val activelyPlaying = state is PlayerController.State.Playing ||
            (growingRec && state is PlayerController.State.Buffering)
        if (controlsVisible && !paused && pendingSeekMs == null && vodPanel == VodPanel.NONE && activelyPlaying) {
            delay(5_000)
            controlsVisible = false
        }
    }

    LaunchedEffect(controlsVisible, vodPanel) {
        if (controlsVisible) {
            delay(40)
            runCatching {
                when {
                    vodPanel != VodPanel.NONE -> panelFocus.requestFocus()
                    lastPanel == VodPanel.SUBTITLES -> subtitlesChipFocus.requestFocus()
                    lastPanel == VodPanel.AUDIO -> audioChipFocus.requestFocus()
                    else -> seekFocus.requestFocus()
                }
            }
            if (vodPanel != VodPanel.NONE) lastPanel = vodPanel
        } else {
            lastPanel = VodPanel.NONE
            runCatching { rootFocus.requestFocus() }
        }
    }

    BackHandler {
        val activelyPlaying = state is PlayerController.State.Playing ||
            (growingRec && state is PlayerController.State.Buffering)
        when {
            vodPanel != VodPanel.NONE -> vodPanel = VodPanel.NONE
            pendingSeekMs != null -> { seekCommit?.cancel(); pendingSeekMs = null; interaction++ }
            showStats -> showStats = false
            controlsVisible && activelyPlaying && !paused -> controlsVisible = false
            else -> onBack()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val repeat = event.nativeKeyEvent.repeatCount
                when (event.key) {
                    Key.Back, Key.Escape -> false
                    // Remote media keys work whatever has focus.
                    Key.MediaPlayPause -> { togglePause(); true }
                    Key.MediaPlay -> { if (paused) togglePause() else reveal(); true }
                    Key.MediaPause -> { if (!paused) togglePause() else reveal(); true }
                    Key.MediaFastForward -> { nudgeSeek(forward = true, repeat); true }
                    Key.MediaRewind -> { nudgeSeek(forward = false, repeat); true }
                    else -> when {
                        // With the bar hidden: left/right seek straight away, OK pauses, anything
                        // else just brings the bar up.
                        !controlsVisible || rootFocused -> {
                            when (event.key) {
                                Key.DirectionLeft -> nudgeSeek(forward = false, repeat)
                                Key.DirectionRight -> nudgeSeek(forward = true, repeat)
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> togglePause()
                                else -> reveal()
                            }
                            true
                        }
                        else -> { interaction++; false }
                    }
                }
            }
            .onFocusChanged { rootFocused = it.isFocused }
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
                    // The viewer's size and background win over styling baked into the file.
                    subtitleView?.setApplyEmbeddedStyles(false)
                    subtitleView?.setApplyEmbeddedFontSizes(false)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            update = {
                it.resizeMode = resizeMode
                it.subtitleView?.setStyle(subtitleStyle(subtitleBackground))
                it.subtitleView?.setFractionalTextSize(SUBTITLE_SIZES[subtitleSize.coerceIn(SUBTITLE_SIZES.indices)])
            },
        )

        when (val current = state) {
            is PlayerController.State.Buffering ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(current.title, color = Color.White)
                    }
                }

            is PlayerController.State.Error ->
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(48.dp),
                    ) {
                        Text(current.title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Text(current.message, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { controller.retry() }) { Text(stringResource(R.string.common_try_again)) }
                    }
                }

            else -> Unit
        }

        // "Ends at 23:14" in the top right corner, with the bar. The stats panel takes that corner.
        val shownPositionTop = pendingSeekMs ?: positionMs
        AnimatedVisibility(
            visible = controlsVisible && !showStats && !growingRec && durationMs > 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent))),
            ) {
                val endsAt = remember(nowMillis, durationMs, shownPositionTop / 60_000) {
                    android.text.format.DateFormat.getTimeFormat(context)
                        .format(java.util.Date(nowMillis + (durationMs - shownPositionTop).coerceAtLeast(0)))
                }
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
                        .padding(horizontal = 28.dp, vertical = 20.dp),
                ) {
                    Text(
                        stringResource(R.string.vod_ends_at),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.alignByBaseline(),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        endsAt,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                    // Keep the controls clear of the phone/tablet system nav bar (and the side nav
                    // bar / display cutout in landscape). Zero on a TV, so the 10-foot layout is
                    // unchanged; the gradient above stays full-bleed to the screen edge.
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                    )
                    .padding(horizontal = 28.dp, vertical = 20.dp),
            ) {
                if (vodPanel != VodPanel.NONE) {
                    TrackPanel(
                        panel = vodPanel,
                        controller = controller,
                        tracks = tracks,
                        firstFocus = panelFocus,
                        subtitleSize = subtitleSize,
                        subtitleBackground = subtitleBackground,
                        onPicked = { type, language ->
                            if (type == C.TRACK_TYPE_AUDIO) settings.setVodAudioLanguage(language)
                            else settings.setVodSubtitleLanguage(language)
                        },
                        onSubtitleSize = { settings.setSubtitleSize(it); interaction++ },
                        onSubtitleBackground = { settings.setSubtitleBackground(it); interaction++ },
                        onDone = { vodPanel = VodPanel.NONE; interaction++ },
                    )
                    Spacer(Modifier.height(16.dp))
                }

                if (growingRec) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.FiberManualRecord,
                            contentDescription = null,
                            tint = Color(0xFFE53935),
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.rec_watching_live_badge),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFFE53935),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
                PlayerHeadingArt(title, heading, headingLogo)
                Spacer(Modifier.height(12.dp))

                // Full-width seek bar. It holds focus when the bar comes up: left/right seek (faster
                // the longer they're held), OK pauses, down reaches the buttons.
                // Times either side of it: where you are on the left, the length on the right.
                val shownPosition = pendingSeekMs ?: positionMs
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text(
                        formatDuration(shownPosition),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                    )
                    Spacer(Modifier.width(16.dp))
                    SeekBar(
                        positionMs = positionMs,
                        bufferedMs = bufferedMs,
                        durationMs = durationMs,
                        pendingMs = pendingSeekMs,
                        focusRequester = seekFocus,
                        downFocus = playFocus,
                        onSeek = { forward, repeat -> nudgeSeek(forward, repeat) },
                        onToggle = { togglePause() },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        if (durationMs > 0) formatDuration(durationMs) else "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Icon-only buttons with the times on the right, after NuvioTV's player controls
                // (github.com/NuvioMedia/NuvioTV, GPL-3.0). Up goes back to the seek bar, down puts
                // the bar away.
                fun hideFromButtons() { vodPanel = VodPanel.NONE; controlsVisible = false }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Kept for touch screens, where the seek bar can't be dragged; on a TV the bar
                    // itself is quicker.
                    VodIconButton(if (restart != null) Icons.Filled.FastRewind else Icons.Filled.Replay10, stringResource(R.string.player_rewind), seekFocus, ::hideFromButtons) {
                        when {
                            restart != null -> restartJump((restartPosition() - 60_000L).coerceAtLeast(0L))
                            growingRec -> seekRelative(-15_000)
                            else -> controller.seekBackward()
                        }
                        interaction++
                    }
                    VodIconButton(
                        icon = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        label = if (paused) stringResource(R.string.common_play) else stringResource(R.string.player_pause),
                        upFocus = seekFocus,
                        onDown = ::hideFromButtons,
                        focusRequester = playFocus,
                    ) { togglePause() }
                    VodIconButton(if (restart != null) Icons.Filled.FastForward else Icons.Filled.Forward10, stringResource(R.string.player_forward), seekFocus, ::hideFromButtons) {
                        when {
                            restart != null -> restartJump(restartPosition() + 60_000L)
                            growingRec -> seekRelative(15_000)
                            else -> controller.seekForward()
                        }
                        interaction++
                    }
                    if (restart != null) {
                        // Back to the channel, live.
                        VodIconButton(Icons.Filled.LiveTv, stringResource(R.string.player_back_to_live), seekFocus, ::hideFromButtons) {
                            onBack()
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    VodIconButton(
                        Icons.Filled.ClosedCaption, stringResource(R.string.player_subtitles), seekFocus, ::hideFromButtons,
                        subtitlesChipFocus, active = vodPanel == VodPanel.SUBTITLES,
                    ) {
                        vodPanel = if (vodPanel == VodPanel.SUBTITLES) VodPanel.NONE else VodPanel.SUBTITLES
                        interaction++
                    }
                    VodIconButton(
                        Icons.Filled.Audiotrack, stringResource(R.string.player_audio), seekFocus, ::hideFromButtons,
                        audioChipFocus, active = vodPanel == VodPanel.AUDIO,
                    ) {
                        vodPanel = if (vodPanel == VodPanel.AUDIO) VodPanel.NONE else VodPanel.AUDIO
                        interaction++
                    }
                    // One press steps Fit → Fill → Stretch; the new format's name shows briefly beside it.
                    VodIconButton(Icons.Filled.AspectRatio, resizeLabel(resizeMode), seekFocus, ::hideFromButtons) {
                        settings.setVodResizeMode(nextResizeMode(resizeMode))
                        aspectHint++
                        interaction++
                    }
                    AnimatedVisibility(visible = aspectHintVisible, enter = fadeIn(), exit = fadeOut()) {
                        Text(
                            resizeLabel(resizeMode),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    VodIconButton(
                        Icons.Outlined.Info, stringResource(R.string.stream_info_nerd_title), seekFocus, ::hideFromButtons,
                        active = showStats,
                    ) { showStats = !showStats; interaction++ }
                }
            }
        }

        // Over the controls' shade, down the right like the live player's.
        if (showStats) {
            StreamInfoPanel(
                statsInfo,
                Modifier.align(Alignment.CenterEnd),
                extraRows = statsExtra,
                title = stringResource(R.string.stream_info_nerd_title),
            )
        }
    }
}

private const val SEEK_COMMIT_DELAY_MILLIS = 800L

/**
 * The bar's heading: the title's own lettering from TMDB when there is one, else its name; under it,
 * for an episode, "S4 · E7 · name". Recordings and catch-up keep the plain title they came with.
 */
@Composable
private fun PlayerHeadingArt(fallbackTitle: String, heading: PlayerHeading?, logoUrl: String?) {
    var logoFailed by remember(logoUrl) { mutableStateOf(false) }
    val name = heading?.title ?: fallbackTitle
    if (logoUrl != null && !logoFailed) {
        AsyncImage(
            model = logoUrl,
            contentDescription = name,
            contentScale = ContentScale.Fit,
            alignment = Alignment.BottomStart,
            onError = { logoFailed = true },
            modifier = Modifier.heightIn(max = 48.dp).widthIn(max = 380.dp),
        )
    } else {
        Text(
            name,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    heading?.episode?.let { ep ->
        Spacer(Modifier.height(8.dp))
        Text(
            listOfNotNull("S${ep.season}", "E${ep.episodeNumber}", episodeName(ep)).joinToString(" · "),
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "MKV · 22.8 GB": the file's container and size, as far as they're known. */
@OptIn(UnstableApi::class)
private fun fileRow(stats: PlayerController.Stats, url: String): String? {
    val container = when (stats.video?.containerMimeType ?: stats.audio?.containerMimeType) {
        MimeTypes.VIDEO_MATROSKA, MimeTypes.AUDIO_MATROSKA -> "MKV"
        MimeTypes.VIDEO_WEBM, MimeTypes.AUDIO_WEBM -> "WebM"
        MimeTypes.VIDEO_MP4, MimeTypes.AUDIO_MP4 -> "MP4"
        MimeTypes.VIDEO_MP2T -> "TS"
        else -> url.substringBefore('?').substringAfterLast('/').substringAfterLast('.', "")
            .takeIf { it.length in 2..4 }?.uppercase(java.util.Locale.ROOT)
    }
    val size = stats.fileBytes?.let { bytes ->
        val gb = bytes / 1_073_741_824.0
        if (gb >= 1) String.format(java.util.Locale.getDefault(), "%.1f GB", gb)
        else String.format(java.util.Locale.getDefault(), "%.0f MB", bytes / 1_048_576.0)
    }
    return listOfNotNull(container, size).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** "3840×2160 · 60 Hz": the mode the screen is in now, to see whether it follows the film. */
private fun screenRow(view: android.view.View): String? {
    val mode = view.display?.mode ?: return null
    val hz = mode.refreshRate
    val rate = if (kotlin.math.abs(hz - Math.round(hz)) < 0.05f) "${Math.round(hz)}"
    else String.format(java.util.Locale.getDefault(), "%.3f", hz).trimEnd('0')
    return "${mode.physicalWidth}×${mode.physicalHeight} · $rate Hz"
}

/** Subtitle text size as a fraction of the video height: small, medium (Media3's default), large. */
private val SUBTITLE_SIZES = floatArrayOf(0.045f, 0.0533f, 0.068f)

@OptIn(UnstableApi::class)
private fun subtitleStyle(background: Int): CaptionStyleCompat = when (background) {
    1 -> CaptionStyleCompat(
        android.graphics.Color.WHITE, 0xB3000000.toInt(), android.graphics.Color.TRANSPARENT,
        CaptionStyleCompat.EDGE_TYPE_NONE, android.graphics.Color.BLACK, null,
    )
    2 -> CaptionStyleCompat(
        android.graphics.Color.WHITE, android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT,
        CaptionStyleCompat.EDGE_TYPE_OUTLINE, android.graphics.Color.BLACK, null,
    )
    else -> CaptionStyleCompat(
        android.graphics.Color.WHITE, android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT,
        CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, android.graphics.Color.BLACK, null,
    )
}

@OptIn(UnstableApi::class)
private fun nextResizeMode(mode: Int): Int = when (mode) {
    AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
}

@OptIn(UnstableApi::class)
@Composable
private fun resizeLabel(mode: Int): String = when (mode) {
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> stringResource(R.string.player_aspect_fill)
    AspectRatioFrameLayout.RESIZE_MODE_FILL -> stringResource(R.string.player_aspect_stretch)
    else -> stringResource(R.string.player_aspect_fit)
}

/**
 * The film's timeline: played, buffered and the rest, with the target of a seek in progress shown
 * as a marker and a time bubble above it. Drawn by hand because Material's Slider doesn't take the
 * d-pad on a TV the way a remote expects.
 */
@Composable
private fun SeekBar(
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    pendingMs: Long?,
    focusRequester: FocusRequester,
    downFocus: FocusRequester,
    onSeek: (forward: Boolean, repeatCount: Int) -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    fun fraction(ms: Long) = if (durationMs > 0) (ms.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    BoxWithConstraints(
        modifier
            .height(44.dp)
            .focusRequester(focusRequester)
            // Down lands on play/pause, not on whichever button sits under the bar's middle.
            .focusProperties { down = downFocus }
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { onSeek(false, event.nativeKeyEvent.repeatCount); true }
                    Key.DirectionRight -> { onSeek(true, event.nativeKeyEvent.repeatCount); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (event.nativeKeyEvent.repeatCount == 0) onToggle(); true
                    }
                    else -> false
                }
            }
            .focusable(),
        contentAlignment = Alignment.BottomStart,
    ) {
        val barHeight = if (focused) 8.dp else 5.dp
        val markerFraction = fraction(pendingMs ?: positionMs)
        Box(
            Modifier
                .padding(bottom = 8.dp)
                .fillMaxWidth()
                .height(barHeight)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.22f)),
        ) {
            Box(Modifier.fillMaxWidth(fraction(bufferedMs)).fillMaxHeight().background(Color.White.copy(alpha = 0.35f)))
            Box(Modifier.fillMaxWidth(fraction(positionMs)).fillMaxHeight().background(accent))
        }
        val knob = if (focused) 16.dp else 0.dp
        if (knob > 0.dp) {
            Box(
                Modifier
                    .padding(bottom = 8.dp + barHeight / 2 - knob / 2)
                    .offset(x = maxWidth * markerFraction - knob / 2)
                    .size(knob)
                    .clip(CircleShape)
                    .background(if (pendingMs != null) Color.White else accent),
            )
        }
        if (pendingMs != null) {
            val bubbleWidth = 132.dp
            Text(
                formatDuration(pendingMs) + "  " + formatDelta(pendingMs - positionMs),
                style = MaterialTheme.typography.labelLarge,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (maxWidth * markerFraction - bubbleWidth / 2).coerceAtMost(maxWidth - bubbleWidth).coerceAtLeast(0.dp), y = (-14).dp)
                    .width(bubbleWidth)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .padding(vertical = 4.dp),
            )
        }
    }
}

/**
 * An icon-only transport button: see-through at rest, a rounded square (10dp, like every other
 * focused thing in the app) when focused. [active] marks the button whose picker is open.
 */
@Composable
private fun VodIconButton(
    icon: ImageVector,
    label: String,
    upFocus: FocusRequester,
    onDown: () -> Unit,
    focusRequester: FocusRequester? = null,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val container = when {
        active -> Color.White.copy(alpha = 0.22f)
        else -> Color.Transparent
    }.let { if (focused) focusVeil(it) else it }
    Box(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusProperties { up = upFocus }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) { onDown(); true } else false
            }
            .onFocusChanged { focused = it.isFocused }
            .focusLift(focused)
            .size(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .clickable(interactionSource = null, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (focused) MaterialTheme.colorScheme.primary else Color.White,
            modifier = Modifier.size(28.dp),
        )
    }
}

private enum class VodPanel { NONE, SUBTITLES, AUDIO }

private data class TrackOption(
    val label: String,
    val detail: String?,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@OptIn(UnstableApi::class)
@Composable
private fun TrackPanel(
    panel: VodPanel,
    controller: PlayerController,
    tracks: Tracks,
    firstFocus: FocusRequester,
    subtitleSize: Int,
    subtitleBackground: Int,
    onPicked: (trackType: Int, language: String) -> Unit,
    onSubtitleSize: (Int) -> Unit,
    onSubtitleBackground: (Int) -> Unit,
    onDone: () -> Unit,
) {
    val trackType = if (panel == VodPanel.SUBTITLES) C.TRACK_TYPE_TEXT else C.TRACK_TYPE_AUDIO
    val groups = tracks.groups.filter { it.type == trackType }
    val options = mutableListOf<TrackOption>()
    val forced = stringResource(R.string.vod_subtitle_forced)
    val mono = stringResource(R.string.vod_track_mono)
    val stereo = stringResource(R.string.vod_track_stereo)

    if (panel == VodPanel.SUBTITLES) {
        val anySelected = groups.any { g -> (0 until g.length).any { g.isTrackSelected(it) } }
        options += TrackOption(stringResource(R.string.player_subtitles_off), null, !anySelected) {
            controller.disableText(); onPicked(trackType, ""); onDone()
        }
    }
    // Tracks in the viewer's own language first — a film can carry twenty subtitle languages.
    val ownLanguage = java.util.Locale.getDefault().isO3Language
    val ordered = groups.flatMap { group -> (0 until group.length).map { group to it } }
        .sortedBy { (group, i) ->
            val code = group.getTrackFormat(i).language
            if (code != null && runCatching { java.util.Locale(code).isO3Language }.getOrNull() == ownLanguage) 0 else 1
        }
    ordered.forEach { (group, i) ->
        run {
            if (!group.isTrackSupported(i)) return@run
            val format = group.getTrackFormat(i)
            val language = format.language?.takeIf { it.isNotBlank() && it != "und" }
            val name = language?.let(::languageName)
            val details = buildList {
                // A track label that only repeats the language ("Italian", "ita") adds nothing.
                format.label?.takeIf { label ->
                    label.isNotBlank() && listOfNotNull(name, language, language?.let(::englishLanguageName), "forced")
                        .none { label.equals(it, ignoreCase = true) }
                }?.let(::add)
                codecName(format.sampleMimeType)?.let(::add)
                if (trackType == C.TRACK_TYPE_AUDIO) when (format.channelCount) {
                    1 -> add(mono)
                    2 -> add(stereo)
                    6 -> add("5.1")
                    8 -> add("7.1")
                }
                if ((format.selectionFlags and C.SELECTION_FLAG_FORCED) != 0) add(forced)
            }
            options += TrackOption(
                label = name ?: format.label?.takeIf { it.isNotBlank() } ?: AppText.get(R.string.track_n, options.size + 1),
                detail = details.joinToString(" · ").ifBlank { null },
                selected = group.isTrackSelected(i),
            ) {
                controller.selectTrack(group, i)
                onPicked(trackType, language ?: if (trackType == C.TRACK_TYPE_TEXT) "und" else "")
                onDone()
            }
        }
    }

    Column(
        Modifier
            .widthIn(max = 460.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.92f))
            .padding(16.dp),
    ) {
        Text(
            if (panel == VodPanel.SUBTITLES) stringResource(R.string.player_subtitles) else stringResource(R.string.player_audio),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(8.dp))
        Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
            if (options.isEmpty()) {
                Text(stringResource(R.string.player_none_available), color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(8.dp))
            }
            options.forEachIndexed { index, option ->
                TrackRow(option.label, option.detail, option.selected, option.onClick, if (index == 0) firstFocus else null)
            }
            if (panel == VodPanel.SUBTITLES) {
                // The look of the subtitles, a press steps to the next choice.
                Spacer(Modifier.height(8.dp))
                val sizes = listOf(
                    stringResource(R.string.vod_subtitle_size_small),
                    stringResource(R.string.vod_subtitle_size_medium),
                    stringResource(R.string.vod_subtitle_size_large),
                )
                val backgrounds = listOf(
                    stringResource(R.string.vod_subtitle_bg_shadow),
                    stringResource(R.string.vod_subtitle_bg_box),
                    stringResource(R.string.vod_subtitle_bg_outline),
                )
                TrackRow(
                    stringResource(R.string.vod_subtitle_size), sizes[subtitleSize.coerceIn(sizes.indices)], false,
                    { onSubtitleSize((subtitleSize + 1) % sizes.size) }, if (options.isEmpty()) firstFocus else null,
                )
                TrackRow(
                    stringResource(R.string.vod_subtitle_background), backgrounds[subtitleBackground.coerceIn(backgrounds.indices)], false,
                    { onSubtitleBackground((subtitleBackground + 1) % backgrounds.size) }, null,
                )
            }
        }
    }
}

@Composable
private fun TrackRow(label: String, detail: String?, selected: Boolean, onClick: () -> Unit, focusRequester: FocusRequester?) {
    var focused by remember { mutableStateOf(false) }
    val bg = if (focused) focusVeil(Color.White.copy(alpha = 0.08f)) else Color.White.copy(alpha = 0.08f)
    val fg = if (focused) MaterialTheme.colorScheme.primary else Color.White
    Row(
        Modifier
            .padding(vertical = 2.dp)
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
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = fg)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.7f))
        }
        if (selected) Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.common_selected), tint = fg)
    }
}

/** "ita" / "it" → "Italiano", in the app's language. */
private fun languageName(code: String): String =
    runCatching {
        java.util.Locale(code).getDisplayLanguage(java.util.Locale.getDefault())
            .takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
            ?.replaceFirstChar { it.titlecase(java.util.Locale.getDefault()) }
    }.getOrNull() ?: code

private fun englishLanguageName(code: String): String? =
    runCatching { java.util.Locale(code).getDisplayLanguage(java.util.Locale.ENGLISH) }.getOrNull()

/** What a viewer calls the codec — the bit that tells a Dolby track from a stereo AAC one. */
private fun codecName(mime: String?): String? = when (mime) {
    MimeTypes.AUDIO_AC3 -> "Dolby Digital"
    MimeTypes.AUDIO_E_AC3 -> "Dolby Digital+"
    MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Atmos"
    MimeTypes.AUDIO_AC4 -> "Dolby AC-4"
    MimeTypes.AUDIO_TRUEHD -> "Dolby TrueHD"
    MimeTypes.AUDIO_DTS -> "DTS"
    MimeTypes.AUDIO_DTS_HD -> "DTS-HD"
    MimeTypes.AUDIO_DTS_EXPRESS -> "DTS Express"
    MimeTypes.AUDIO_AAC -> "AAC"
    MimeTypes.AUDIO_MPEG, MimeTypes.AUDIO_MPEG_L2 -> "MP3"
    MimeTypes.AUDIO_OPUS -> "Opus"
    MimeTypes.AUDIO_VORBIS -> "Vorbis"
    MimeTypes.AUDIO_FLAC -> "FLAC"
    MimeTypes.TEXT_SSA -> "ASS"
    MimeTypes.APPLICATION_SUBRIP -> "SRT"
    MimeTypes.TEXT_VTT -> "WebVTT"
    MimeTypes.APPLICATION_PGS -> "PGS"
    MimeTypes.APPLICATION_VOBSUB -> "VobSub"
    MimeTypes.APPLICATION_DVBSUBS -> "DVB"
    else -> null
}

private fun formatDelta(ms: Long): String = (if (ms < 0) "−" else "+") + formatDuration(kotlin.math.abs(ms))

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** How close to live a restarted programme has to get before the live channel takes over. */
private const val RESTART_LIVE_MARGIN_MILLIS = 90_000L

/** `restart:<channelId>:<startUtcMillis>:<endUtcMillis>`, the key the live player's "Restart" opens. */
internal data class RestartKey(val channelId: Long, val startUtcMillis: Long, val endUtcMillis: Long) {
    companion object {
        fun parse(key: String): RestartKey? {
            val parts = key.split(":")
            if (parts.size != 4 || parts[0] != "restart") return null
            val ch = parts[1].toLongOrNull() ?: return null
            val start = parts[2].toLongOrNull() ?: return null
            val end = parts[3].toLongOrNull() ?: return null
            return if (end > start) RestartKey(ch, start, end) else null
        }

        private val TIMESHIFT = Regex("""/timeshift/([^/]+)/([^/]+)/\d+/\d{4}-\d{2}-\d{2}:\d{2}-\d{2}/""")
    }

    /**
     * The archive link [url] moved to start [minute] minutes into the programme, asking for the rest
     * of it. Same form and time zone as XtreamApi.catchupUrl.
     */
    fun urlFrom(url: String, minute: Int, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): String {
        val from = startUtcMillis + minute * 60_000L
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd:HH-mm", java.util.Locale.US)
            .apply { timeZone = zone }
            .format(java.util.Date(from))
        val minutes = ((endUtcMillis - from + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
        val m = TIMESHIFT.find(url) ?: return url
        return url.replaceRange(m.range, "/timeshift/${m.groupValues[1]}/${m.groupValues[2]}/$minutes/$stamp/")
    }
}
