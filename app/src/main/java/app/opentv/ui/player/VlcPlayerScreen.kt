/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.player

import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.opentv.R
import app.opentv.core.QualityPreference
import app.opentv.core.ServiceLocator
import app.opentv.core.findActivity
import app.opentv.data.model.Channel
import app.opentv.data.model.shownName
import app.opentv.player.PlaybackQueue
import app.opentv.player.QualityNames
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * The plain VLC player: a second way to watch a live channel, opened from the channel menu.
 *
 * Deliberately bare: no stall watching, no panels. It starts on the channel's preferred quality
 * and, when a stream won't open or shows no picture in time, moves on to the next one, so a dead
 * feed is never mistaken for VLC not working; the banner names the stream in use. Up/down zaps
 * through the same list the guide handed over, Back returns to the guide. The main player is
 * untouched.
 */
@Composable
fun VlcPlayerScreen(channelId: Long?, onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val graph = remember { ServiceLocator.get(context) }
    val scope = rememberCoroutineScope()

    val libVlc = remember { LibVLC(context.applicationContext, arrayListOf("--no-stats", "--network-caching=1000")) }
    val player = remember { MediaPlayer(libVlc) }

    var listedId by remember { mutableStateOf(channelId) }
    var name by remember { mutableStateOf("") }
    var buffering by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var banner by remember { mutableIntStateOf(0) }
    var bannerVisible by remember { mutableStateOf(false) }
    // Opens refused before the first frame. The provider counts connections per client, and the
    // main player's one is still held for a few seconds after it closes, so the first open right
    // after watching there is often turned away (HTTP 404). Same stream, retried a few times.
    var refusals by remember { mutableIntStateOf(0) }
    var started by remember { mutableStateOf(false) }
    // The channel's streams in the order to try them, the one on screen, and a switch notice.
    var variants by remember { mutableStateOf(emptyList<Channel>()) }
    var tryOrder by remember { mutableStateOf(emptyList<Channel>()) }
    var current by remember { mutableStateOf<Channel?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    fun variantName(channel: Channel): String =
        QualityNames.of(variants.map { it.id to it.qualityLabel })[channel.id]?.full?.ifBlank { null }
            ?: channel.qualityLabel.ifBlank { channel.shownName }

    fun playVariant(variant: Channel) {
        current = variant
        failed = false
        buffering = true
        started = false
        refusals = 0
        attempt++
        player.stop()
        scope.launch {
            val source = graph.sourceRepository.byId(variant.sourceId)
            val url = graph.catalogRepository.resolvePlaybackUrl(variant, source)
            val ua = source?.userAgent ?: "OpenTV/0.1 (Android)"
            // Same User-Agent as the main player, so the provider sees one client, not two.
            libVlc.setUserAgent(ua, ua)
            val media = Media(libVlc, Uri.parse(url)).apply {
                setHWDecoderEnabled(true, false)
                addOption(":http-user-agent=$ua")
                // Start a full segment back from the live edge (this provider cuts 10 s segments),
                // so the first one is already complete on the server rather than still being written.
                addOption(":adaptive-livedelay=30000")
            }
            player.media = media
            media.release()
            player.play()
        }
    }

    /** The stream on screen didn't start: on to the next one, or give up when none are left. */
    fun fallBack() {
        val failedOne = current ?: return
        val next = tryOrder.dropWhile { it.id != failedOne.id }.drop(1).firstOrNull()
        if (next == null) {
            player.stop()
            failed = true
            buffering = false
            return
        }
        notice = context.getString(R.string.player_quality_fallback, variantName(failedOne), variantName(next))
        banner++
        playVariant(next)
    }

    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view.keepScreenOn = true
        player.setEventListener { e ->
            when (e.type) {
                MediaPlayer.Event.Buffering -> buffering = e.buffering < 100f
                MediaPlayer.Event.Playing, MediaPlayer.Event.Vout -> { failed = false; started = true }
                MediaPlayer.Event.EncounteredError -> if (!started && refusals < OPEN_RETRIES) {
                    refusals++
                    scope.launch {
                        delay(OPEN_RETRY_MILLIS)
                        player.play()
                    }
                } else if (!started) {
                    fallBack()
                } else {
                    failed = true
                    buffering = false
                }
                // A live stream never ends; one that does is the provider's "not available" clip.
                MediaPlayer.Event.EndReached -> if (!started) fallBack() else { failed = true; buffering = false }
            }
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            view.keepScreenOn = false
            player.setEventListener(null)
            player.stop()
            player.detachViews()
            player.release()
            libVlc.release()
        }
    }

    suspend fun tune(channel: Channel) {
        name = channel.shownName
        notice = null
        // Drop the old channel's last frame straight away instead of leaving it frozen on screen.
        player.stop()
        variants = graph.catalogRepository.variants(channel)
        tryOrder = QualityPreference.order(variants, graph.settings.preferredQuality.value).ifEmpty { listOf(channel) }
        banner++
        playVariant(tryOrder.first())
    }

    // No picture in time on this stream: treat it as dead and move on. VLC takes a few seconds
    // longer to open a stream than the main player, hence the generous wait.
    LaunchedEffect(attempt) {
        if (attempt == 0) return@LaunchedEffect
        delay(START_TIMEOUT_MILLIS)
        if (!started && !failed) fallBack()
    }

    LaunchedEffect(listedId) {
        val id = listedId ?: return@LaunchedEffect
        val channel = graph.catalogRepository.channel(id) ?: run { failed = true; return@LaunchedEffect }
        tune(channel)
    }
    LaunchedEffect(banner) {
        if (banner == 0) return@LaunchedEffect
        bannerVisible = true
        delay(BANNER_MILLIS)
        bannerVisible = false
    }

    fun zapBy(delta: Int) {
        val queue = PlaybackQueue.items
        if (queue.isEmpty()) return
        val cur = queue.indexOfFirst { it.id == listedId }.coerceAtLeast(0)
        val next = (cur + delta).coerceIn(0, queue.size - 1)
        if (next != cur) listedId = queue[next].id
    }

    BackHandler { onBack() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp, Key.ChannelUp -> { zapBy(-1); true }
                    Key.DirectionDown, Key.ChannelDown -> { zapBy(1); true }
                    Key.DirectionCenter, Key.Enter -> {
                        if (failed) listedId?.let { id -> scope.launch { graph.catalogRepository.channel(id)?.let { tune(it) } } }
                        else banner++
                        true
                    }
                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).also { layout -> player.attachViews(layout, null, false, false) }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (buffering && !failed) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        }
        if (failed) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.player_channel_unavailable), color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.player_channel_unavailable_hint), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
            }
        }
        AnimatedVisibility(
            visible = bannerVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart).padding(32.dp),
        ) {
            Text(
                listOfNotNull(name, current?.takeIf { variants.size > 1 }?.let { variantName(it) }, "VLC").joinToString(" · ") +
                    (notice?.let { "\n$it" } ?: ""),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

private const val BANNER_MILLIS = 3_000L
private const val OPEN_RETRIES = 2
private const val START_TIMEOUT_MILLIS = 12_000L
private const val OPEN_RETRY_MILLIS = 2_000L
