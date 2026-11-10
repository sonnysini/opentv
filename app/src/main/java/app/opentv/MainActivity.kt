/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import android.app.PictureInPictureParams
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import app.opentv.ui.focusOnEnter
import androidx.navigation.compose.rememberNavController
import app.opentv.core.AppSettings
import app.opentv.core.ServiceLocator
import app.opentv.data.parser.displayTitle
import app.opentv.ui.MainScreen
import app.opentv.ui.ProfilesViewModel
import app.opentv.ui.SourcesViewModel
import app.opentv.ui.VodViewModel
import app.opentv.ui.channels.ChannelManagerScreen
import app.opentv.ui.channels.LocalExternalGroupLogos
import app.opentv.ui.channels.LocalLogoPack
import app.opentv.ui.channels.LocalLogoSource
import app.opentv.ui.channels.SearchScreen
import app.opentv.ui.onboarding.AddSourceScreen
import app.opentv.ui.ChannelsViewModel
import app.opentv.ui.player.PlayerScreen
import app.opentv.ui.settings.AboutScreen
import app.opentv.ui.settings.AppearanceSettingsScreen
import app.opentv.ui.settings.LogoSettingsScreen
import app.opentv.ui.settings.PlaybackSettingsScreen
import app.opentv.ui.settings.TmdbSettingsScreen
import app.opentv.ui.settings.CategoriesScreen
import app.opentv.ui.settings.GroupingScreen
import app.opentv.ui.settings.EpgSettingsScreen
import app.opentv.ui.settings.ParentalControlsScreen
import app.opentv.ui.settings.ProfilesScreen
import app.opentv.ui.settings.ProvidersScreen
import app.opentv.ui.settings.RecordingSettingsScreen
import app.opentv.ui.settings.SettingsHubScreen
import app.opentv.ui.settings.StremioAddonsScreen
import app.opentv.ui.settings.SyncScreen
import app.opentv.ui.settings.WebManagerScreen
import app.opentv.ui.theme.OpenTvTheme
import app.opentv.ui.vod.MovieDetailScreen
import app.opentv.ui.vod.PersonScreen
import app.opentv.ui.vod.SeriesDetailScreen
import app.opentv.ui.vod.VodPlayerScreen
import app.opentv.update.UpdateGate
import app.opentv.ui.theme.focusLift

class MainActivity : ComponentActivity() {

    // Apply the chosen UI language before any view or resource is resolved. A language change in
    // settings calls recreate(), which re-runs this with the new tag.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(app.opentv.core.LocaleUtils.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A fresh launch (not a rotation/recreate) retries every channel's preferred quality.
        if (savedInstanceState == null) app.opentv.player.WorkingVariants.clear()
        app.opentv.core.EventLog.log("activity", "create (${if (savedInstanceState == null) "fresh" else "recreated"})")
        runCatching {
            androidx.core.content.ContextCompat.registerReceiver(
                this,
                screenReceiver,
                android.content.IntentFilter().apply {
                    addAction(Intent.ACTION_DREAMING_STARTED)
                    addAction(Intent.ACTION_DREAMING_STOPPED)
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                },
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
        enableEdgeToEdge()
        handlePlayIntent(intent)
        val isTelevision = isRunningOnTelevision(this)
        // Move the TV to 50 Hz right away, while the list loads, so Italian channels (25/50 fps)
        // start without a refresh-rate switch; the player still switches for the odd 24/60 fps one.
        val appSettings = ServiceLocator.get(this).settings
        app.opentv.ui.player.DisplayModes.set(window, app.opentv.ui.player.DisplayModes.homeId(window, appSettings.matchFrameRate.value))

        setContent {
            val settings = remember { ServiceLocator.get(this).settings }
            val themeMode by settings.themeMode.collectAsStateWithLifecycle()
            val uiScale by settings.uiScalePercent.collectAsStateWithLifecycle()
            val appStyle by settings.appStyle.collectAsStateWithLifecycle()
            val logoSource by settings.logoSource.collectAsStateWithLifecycle()
            val logoPack by settings.logoPack.collectAsStateWithLifecycle()
            val externalGroupLogos by settings.externalGroupLogos.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                AppSettings.ThemeMode.DARK -> true
                AppSettings.ThemeMode.LIGHT -> false
                // A living-room screen defaults to dark; a phone/tablet follows the system.
                AppSettings.ThemeMode.SYSTEM -> isTelevision || isSystemInDarkTheme()
            }
            // Scaling the density zooms every dp/sp in the app at once; the video surface just fills
            // the screen, so only overlays and lists change size.
            val baseDensity = LocalDensity.current
            val scaledDensity = remember(baseDensity, uiScale) {
                Density(baseDensity.density * AppSettings.UI_SCALE_BASE * uiScale / 100f, baseDensity.fontScale)
            }
            CompositionLocalProvider(
                LocalDensity provides scaledDensity,
                LocalLogoSource provides logoSource,
                LocalLogoPack provides logoPack,
                LocalExternalGroupLogos provides externalGroupLogos,
            ) {
                OpenTvTheme(darkTheme = darkTheme, style = appStyle) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        OpenTvApp(isTelevision = isTelevision)
                    }
                }
            }
        }
    }

    // singleTask: a reminder tapped while the app is already running arrives here, not a fresh
    // onCreate. Either way the requested channel is handed to the nav graph via [PlayRequests].
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        app.opentv.core.EventLog.log("activity", "new intent ${intent.extras?.keySet()?.joinToString().orEmpty()}")
        handlePlayIntent(intent)
    }

    // The event log's view of the app coming and going, and of the box's screensaver/standby:
    // what the screen's keep-awake state was at the moment the screensaver started is the clue.
    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val what = when (intent.action) {
                Intent.ACTION_DREAMING_STARTED -> "screensaver started"
                Intent.ACTION_DREAMING_STOPPED -> "screensaver stopped"
                Intent.ACTION_SCREEN_OFF -> "screen off"
                Intent.ACTION_SCREEN_ON -> "screen on"
                else -> intent.action.orEmpty()
            }
            app.opentv.core.EventLog.log("screen", "$what, ${app.opentv.core.EventLog.awakeState(this@MainActivity)}")
        }
    }

    override fun onStart() {
        super.onStart()
        app.opentv.core.EventLog.log("activity", "start, ${app.opentv.core.EventLog.awakeState(this)}")
    }

    override fun onResume() {
        super.onResume()
        app.opentv.core.EventLog.log("activity", "resume")
    }

    override fun onPause() {
        app.opentv.core.EventLog.log("activity", "pause")
        super.onPause()
    }

    override fun onStop() {
        app.opentv.core.EventLog.log("activity", "stop, ${app.opentv.core.EventLog.awakeState(this)}")
        super.onStop()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        app.opentv.core.EventLog.log("activity", "destroy")
        super.onDestroy()
    }

    private fun handlePlayIntent(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_PLAY_CHANNEL, 0L) ?: 0L
        if (id != 0L) app.opentv.core.PlayRequests.request(id)
        if (intent?.getStringExtra(EXTRA_OPEN) == OPEN_FOOTBALL) {
            val action = when (intent.getStringExtra(EXTRA_MATCH_ACTION)) {
                "watch" -> app.opentv.core.FootballRequests.Action.WATCH
                "highlights" -> app.opentv.core.FootballRequests.Action.HIGHLIGHTS
                else -> app.opentv.core.FootballRequests.Action.OPEN
            }
            app.opentv.core.FootballRequests.request(
                app.opentv.core.FootballRequests.Request(
                    action = action,
                    matchId = intent.getLongExtra(EXTRA_MATCH_ID, 0L),
                    highlightsId = intent.getStringExtra(EXTRA_HIGHLIGHTS_ID),
                ),
            )
        }
        val recId = intent?.getLongExtra(EXTRA_WATCH_RECORDING, 0L) ?: 0L
        if (recId != 0L) {
            app.opentv.core.RecordingSignals.requestWatch(recId, System.currentTimeMillis())
        }
    }

    // ---- Picture-in-picture ------------------------------------------------------------------

    private fun supportsPip(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /** Explicit request from the player's PiP button — no eligibility guard, the user asked. */
    fun enterPipNow() {
        if (!supportsPip()) return
        runCatching {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build(),
            )
        }
    }

    fun pipSupported(): Boolean = supportsPip()

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        app.opentv.core.PipState.setInPip(isInPictureInPictureMode)
    }

    companion object {
        /** A reminder notification carries the channel to tune to in this extra. */
        const val EXTRA_PLAY_CHANNEL = "opentv.play_channel"

        /** A recording auto-switch notification carries the recording to watch in this extra. */
        const val EXTRA_WATCH_RECORDING = "opentv.watch_recording"

        /**
         * A home-screen launcher opens a part of the app: [OPEN_FOOTBALL] goes to the Football tab,
         * with [EXTRA_MATCH_ID] (FotMob id) and [EXTRA_MATCH_ACTION] `watch` (Watch automatically)
         * or `highlights` ([EXTRA_HIGHLIGHTS_ID] = YouTube id) to act on one match.
         */
        const val EXTRA_OPEN = "opentv.open"
        const val OPEN_FOOTBALL = "football"
        const val EXTRA_MATCH_ID = "opentv.match_id"
        const val EXTRA_MATCH_ACTION = "opentv.match_action"
        const val EXTRA_HIGHLIGHTS_ID = "opentv.highlights_id"
    }
}

object Routes {
    const val HOME = "home"
    const val ADD_SOURCE = "add-source"
    const val PLAYER = "player/{channelId}"
    const val SEARCH = "search"
    const val EPG_SETTINGS = "epg-settings"
    const val APPEARANCE = "appearance"
    const val PLAYBACK = "playback"
    const val TMDB = "tmdb"
    const val LOGOS = "logos"
    const val SETTINGS_HUB = "settings"
    const val PROVIDERS = "providers"
    const val ADDONS = "addons"
    const val CHANNELS = "channels"
    const val WEB_MANAGER = "web-manager"
    const val PROFILES = "profiles"
    const val PARENTAL = "parental"
    const val CATEGORIES = "categories"
    const val GROUPING = "grouping"
    const val SYNC = "sync"
    const val REC_SETTINGS = "recording-settings"
    const val ABOUT = "about"
    const val SERIES_DETAIL = "series/{seriesId}"
    const val MOVIE_DETAIL = "movie/{movieId}"
    const val EDIT_SOURCE = "edit-source/{sourceId}"

    // A person's name goes in a query arg, URL-encoded, so spaces and punctuation survive the round
    // trip — the same inline-encode/decode approach as the VOD player below.
    const val PERSON = "person?name={name}"

    // VOD plays carry the stream inline; a movie/episode is a one-off URL, not a stored id
    // the player can look up the way a channel is.
    const val VOD_PLAYER = "vod?key={key}&url={url}&title={title}&ua={ua}"

    fun player(channelId: Long) = "player/$channelId"
    fun seriesDetail(seriesId: Long) = "series/$seriesId"
    fun movieDetail(movieId: Long) = "movie/$movieId"
    fun editSource(sourceId: Long) = "edit-source/$sourceId"
    fun person(name: String) = "person?name=${java.net.URLEncoder.encode(name, "UTF-8")}"
    fun vodPlayer(key: String, url: String, title: String, ua: String): String {
        fun e(v: String) = java.net.URLEncoder.encode(v, "UTF-8")
        return "vod?key=${e(key)}&url=${e(url)}&title=${e(title)}&ua=${e(ua)}"
    }
}

@Composable
private fun OpenTvApp(isTelevision: Boolean) {
    val navController = rememberNavController()
    val sourcesViewModel: SourcesViewModel = viewModel()
    val vodViewModel: VodViewModel = viewModel()
    val profilesViewModel: ProfilesViewModel = viewModel()
    val sourcesUi by sourcesViewModel.ui.collectAsStateWithLifecycle()
    val profiles by profilesViewModel.profiles.collectAsStateWithLifecycle()
    val activeProfileId by profilesViewModel.activeProfileId.collectAsStateWithLifecycle()
    val activeProfileName = profiles.firstOrNull { it.id == activeProfileId }?.name ?: "Me"

    // Every screen change into the event log. Stream URLs and user agents stay out of it: a URL
    // carries the provider login.
    DisposableEffect(navController) {
        val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, destination, args ->
            val shown = args?.keySet()
                ?.filter { it != "url" && it != "ua" && !it.startsWith("android-support-nav") }
                ?.joinToString(" ") { key -> "$key=${args.get(key)}" }
                .orEmpty()
            app.opentv.core.EventLog.log("nav", "${destination.route} $shown".trim())
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    // Until the saved sources have loaded from the database, we cannot tell a first run from a
    // returning user — and guessing "first run" drops a returning user on the setup screen and
    // asks for their provider again. NavHost locks in its start destination on first
    // composition, so wait for that first load before building it.
    if (!sourcesUi.loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // First run goes straight to setup — an empty channel list with no explanation is the
    // worst possible first impression.
    val start = if (sourcesUi.sources.isEmpty()) Routes.ADD_SOURCE else Routes.HOME

    // Boot to last channel: if enabled and we have one, jump straight into the player on launch.
    // Runs once; backing out returns to the guide and doesn't re-trigger.
    val bootContext = androidx.compose.ui.platform.LocalContext.current
    val bootSettings = remember { ServiceLocator.get(bootContext).settings }
    LaunchedEffect(start) {
        if (start == Routes.HOME && bootSettings.resumeLastChannel.value && bootSettings.lastChannelId != 0L) {
            navController.navigate(Routes.player(bootSettings.lastChannelId))
        }
    }

    // A tapped reminder notification asks for a specific channel. Consume it so it fires once and
    // never re-triggers on a later launch.
    val playRequest by app.opentv.core.PlayRequests.channelId.collectAsState()
    LaunchedEffect(playRequest) {
        val id = playRequest
        if (id != null && id != 0L) {
            app.opentv.core.PlayRequests.consume()
            navController.navigate(Routes.player(id))
        }
    }

    // A launcher's match card: back to the home, where the Football tab picks the request up.
    val footballRequest by app.opentv.core.FootballRequests.request.collectAsState()
    LaunchedEffect(footballRequest) {
        if (footballRequest == null || start != Routes.HOME) return@LaunchedEffect
        runCatching { navController.popBackStack(Routes.HOME, inclusive = false) }
    }

    // Auto-switch: a scheduled recording started, so move the live view onto the recording file
    // (single-connection safe). Ignore a stale request a long-backgrounded app only now sees.
    val watchRequest by app.opentv.core.RecordingSignals.watch.collectAsState()
    LaunchedEffect(watchRequest) {
        val req = watchRequest ?: return@LaunchedEffect
        app.opentv.core.RecordingSignals.consumeWatch()
        val fresh = System.currentTimeMillis() - req.requestedAtMillis < 10 * 60_000L
        if (req.recordingId != 0L && fresh) {
            val rec = runCatching {
                ServiceLocator.get(bootContext).recordingRepository.byId(req.recordingId)
            }.getOrNull() ?: return@LaunchedEffect
            runCatching {
                navController.navigate(
                    Routes.vodPlayer(
                        key = "rec:${rec.id}",
                        url = "optvrec://${rec.id}",
                        title = rec.title,
                        ua = rec.userAgent,
                    ),
                ) {
                    launchSingleTop = true
                    // Drop the live channel we switched away from off the back stack, so Back from
                    // the recording returns to the home screen — never to a live stream. On a single-
                    // connection line, returning to live TV while still recording would open a second
                    // stream and risk the provider banning the account.
                    popUpTo(Routes.PLAYER) { inclusive = true }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        val playRecording: (app.opentv.data.model.Recording) -> Unit = { rec ->
            // A NAS recording plays straight off its smb:// locator and a USB one off
            // its content:// document URI (Media3 reads both); an internal one through
            // a file:// uri. An internal recording that's still capturing plays through
            // the tail-following optvrec:// source, so it can be watched as it records
            // with no second connection to the provider. All go through the VOD player.
            val stillRecording = rec.status == app.opentv.data.model.RecordingStatus.RECORDING
            // Internal and NAS in-progress recordings are both tail-followable via the
            // growing source; USB (content://) isn't.
            val growable = !app.opentv.recording.RecordingStorage.isContent(rec.filePath) &&
                !app.opentv.recording.RecordingStorage.isUsbPlaceholder(rec.filePath)
            val url = when {
                stillRecording && growable -> "optvrec://${rec.id}"
                app.opentv.recording.SmbClient.isSmb(rec.filePath) -> rec.filePath
                app.opentv.recording.RecordingStorage.isContent(rec.filePath) -> rec.filePath
                else -> android.net.Uri.fromFile(java.io.File(rec.filePath)).toString()
            }
            navController.navigate(
                Routes.vodPlayer(
                    key = "rec:${rec.id}",
                    url = url,
                    title = rec.title,
                    ua = rec.userAgent,
                ),
            )
        }

        NavHost(navController = navController, startDestination = start) {
            composable(Routes.ADD_SOURCE) {
                AddSourceScreen(
                    viewModel = sourcesViewModel,
                    onFinished = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ADD_SOURCE) { inclusive = true }
                        }
                    },
                )
            }

            composable(Routes.EDIT_SOURCE) { entry ->
                val sourceId = entry.arguments?.getString("sourceId")?.toLongOrNull() ?: return@composable
                AddSourceScreen(
                    viewModel = sourcesViewModel,
                    editingSourceId = sourceId,
                    onFinished = { navController.popBackStack() },
                )
            }

            composable(Routes.HOME) {
                MainScreen(
                    isTelevision = isTelevision,
                    hasSources = sourcesUi.sources.isNotEmpty(),
                    isSyncing = sourcesUi.syncing,
                    onPlayChannel = { channel -> navController.navigate(Routes.player(channel.id)) },
                    onOpenMovie = { movie ->
                        navController.navigate(Routes.movieDetail(movie.id))
                    },
                    onOpenSeries = { series ->
                        navController.navigate(Routes.seriesDetail(series.id))
                    },
                    onResume = { key, url, title ->
                        navController.navigate(
                            Routes.vodPlayer(key, url, title, "OpenTV/0.1 (Android)"),
                        )
                    },
                    onAddSource = { navController.navigate(Routes.ADD_SOURCE) },
                    onRefresh = sourcesViewModel::refreshAll,
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS_HUB) },
                    onPlayRecording = playRecording,
                    onPlayCatchup = { key, url, title, ua ->
                        // Catch-up is a seekable archive stream — plays through the VOD player.
                        navController.navigate(Routes.vodPlayer(key, url, title, ua))
                    },
                )
            }

            screen(Routes.PROFILES) {
                ProfilesScreen(
                    viewModel = profilesViewModel,
                    onBack = { navController.popBackStack() },
                )
            }

            screen(Routes.SEARCH) {
                SearchScreen(
                    onPlayChannel = { channel -> navController.navigate(Routes.player(channel.id)) },
                    onPlayMovie = { movie ->
                        navController.navigate(
                            Routes.vodPlayer(
                                key = "movie:${movie.id}",
                                url = movie.streamUrl,
                                title = movie.displayTitle,
                                ua = "OpenTV/0.1 (Android)",
                            ),
                        )
                    },
                    onOpenSeries = { series -> navController.navigate(Routes.seriesDetail(series.id)) },
                    onPlayRecording = playRecording,
                    onBack = { navController.popBackStack() },
                )
            }

            screen(Routes.SETTINGS_HUB) {
                SettingsHubScreen(
                    accountName = activeProfileName,
                    onOpenAccount = { navController.navigate(Routes.PROFILES) },
                    onOpenProviders = { navController.navigate(Routes.PROVIDERS) },
                    onOpenAddons = { navController.navigate(Routes.ADDONS) },
                    onOpenGuide = { navController.navigate(Routes.EPG_SETTINGS) },
                    onOpenChannels = { navController.navigate(Routes.CHANNELS) },
                    onOpenCategories = { navController.navigate(Routes.CATEGORIES) },
                    onOpenGrouping = { navController.navigate(Routes.GROUPING) },
                    onOpenWebManager = { navController.navigate(Routes.WEB_MANAGER) },
                    onOpenLogos = { navController.navigate(Routes.LOGOS) },
                    onOpenTmdb = { navController.navigate(Routes.TMDB) },
                    onOpenAppearance = { navController.navigate(Routes.APPEARANCE) },
                    onOpenPlayback = { navController.navigate(Routes.PLAYBACK) },
                    onOpenParental = { navController.navigate(Routes.PARENTAL) },
                    onOpenSync = { navController.navigate(Routes.SYNC) },
                    onOpenRecordings = { navController.navigate(Routes.REC_SETTINGS) },
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                    onBack = { navController.popBackStack() },
                )
            }

            screen(Routes.SYNC) {
                SyncScreen(onBack = { navController.popBackStack() })
            }

            screen(Routes.REC_SETTINGS) {
                RecordingSettingsScreen(onBack = { navController.popBackStack() })
            }

            screen(Routes.CHANNELS) {
                ChannelManagerScreen(onBack = { navController.popBackStack() })
            }

            screen(Routes.WEB_MANAGER) {
                WebManagerScreen(onBack = { navController.popBackStack() })
            }

            screen(Routes.CATEGORIES) {
                CategoriesScreen(onBack = { navController.popBackStack() })
            }

            screen(Routes.GROUPING) {
                GroupingScreen(onBack = { navController.popBackStack() })
            }

            screen(Routes.PARENTAL) {
                ParentalControlsScreen(onBack = { navController.popBackStack() })
            }

            screen(Routes.PROVIDERS) {
                ProvidersScreen(
                    viewModel = sourcesViewModel,
                    onAddSource = { navController.navigate(Routes.ADD_SOURCE) },
                    onEditSource = { src -> navController.navigate(Routes.editSource(src.id)) },
                    onBack = { navController.popBackStack() },
                )
            }

            screen(Routes.ADDONS) {
                StremioAddonsScreen(onBack = { navController.popBackStack() })
            }

            screen(Routes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }

            screen(Routes.EPG_SETTINGS) {
                EpgSettingsScreen(onBack = { navController.popBackStack() })
            }

            screen(Routes.APPEARANCE) {
                AppearanceSettingsScreen(onBack = { navController.popBackStack() })
            }

            screen(Routes.PLAYBACK) {
                PlaybackSettingsScreen(onBack = { navController.popBackStack() })
            }

            screen(Routes.TMDB) {
                TmdbSettingsScreen(onBack = { navController.popBackStack() })
            }

            screen(Routes.LOGOS) {
                LogoSettingsScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.PLAYER) { entry ->
                val channelId = entry.arguments?.getString("channelId")?.toLongOrNull()
                // The guide's own view model, so the side menu browses the same groups and the
                // group picked there is the one the guide shows on the way back.
                val homeEntry = remember(entry) {
                    runCatching { navController.getBackStackEntry(Routes.HOME) }.getOrNull()
                }
                val channelsViewModel: ChannelsViewModel =
                    if (homeEntry != null) viewModel(homeEntry) else viewModel()
                PlayerScreen(
                    channelId = channelId,
                    channels = channelsViewModel,
                    onBack = { navController.popBackStack() },
                )
            }

            screen(Routes.SERIES_DETAIL) { entry ->
                val seriesId = entry.arguments?.getString("seriesId")?.toLongOrNull() ?: return@screen
                SeriesDetailScreen(
                    seriesId = seriesId,
                    viewModel = vodViewModel,
                    onPlayEpisode = { key, url, title ->
                        navController.navigate(
                            Routes.vodPlayer(key, url, title, "OpenTV/0.1 (Android)"),
                        )
                    },
                    onOpenSeries = { series -> navController.navigate(Routes.seriesDetail(series.id)) },
                    onOpenPerson = { name -> navController.navigate(Routes.person(name)) },
                )
            }

            screen(Routes.MOVIE_DETAIL) { entry ->
                val movieId = entry.arguments?.getString("movieId")?.toLongOrNull() ?: return@screen
                MovieDetailScreen(
                    movieId = movieId,
                    viewModel = vodViewModel,
                    onPlay = { movie ->
                        navController.navigate(
                            Routes.vodPlayer(
                                key = "movie:${movie.id}",
                                url = movie.streamUrl,
                                title = movie.displayTitle,
                                ua = "OpenTV/0.1 (Android)",
                            ),
                        )
                    },
                    onPlayUrl = { key, url, title ->
                        navController.navigate(
                            Routes.vodPlayer(key = key, url = url, title = title, ua = "OpenTV/0.1 (Android)"),
                        )
                    },
                    onOpenMovie = { movie -> navController.navigate(Routes.movieDetail(movie.id)) },
                    onOpenPerson = { name -> navController.navigate(Routes.person(name)) },
                )
            }

            screen(Routes.PERSON) { entry ->
                val name = entry.arguments?.getString("name")
                    ?.let { java.net.URLDecoder.decode(it, "UTF-8") }.orEmpty()
                PersonScreen(
                    name = name,
                    viewModel = vodViewModel,
                    onOpenMovie = { movie -> navController.navigate(Routes.movieDetail(movie.id)) },
                    onOpenSeries = { series -> navController.navigate(Routes.seriesDetail(series.id)) },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.VOD_PLAYER) { entry ->
                fun arg(name: String) = entry.arguments?.getString(name)
                    ?.let { java.net.URLDecoder.decode(it, "UTF-8") }.orEmpty()
                VodPlayerScreen(
                    mediaKey = arg("key"),
                    streamUrl = arg("url"),
                    title = arg("title"),
                    userAgent = arg("ua").ifEmpty { "OpenTV/0.1 (Android)" },
                    onBack = { navController.popBackStack() },
                )
            }
        }

        // Sits above the whole nav graph so a found update can prompt from any screen.
        UpdateGate()

        // "About to switch to a recording" banner — shows 30s before an auto-switch fires.
        RecordingSwitchBanner()
    }
}

/**
 * A slim banner shown 30 seconds before a scheduled recording takes over the screen (single-
 * connection auto-switch). It gives the viewer a heads-up and a way out: "Keep watching" suppresses
 * the switch for that booking. It clears itself once the switch would have happened.
 */
@Composable
private fun RecordingSwitchBanner() {
    val imminent by app.opentv.core.RecordingSignals.imminent.collectAsState()
    val current = imminent ?: return

    // Auto-clear a little after the switch instant, in case the switch itself never lands.
    LaunchedEffect(current.recordingId) {
        val wait = (current.startAtMillis + 5_000L) - System.currentTimeMillis()
        kotlinx.coroutines.delay(wait.coerceIn(5_000L, 60_000L))
        if (app.opentv.core.RecordingSignals.imminent.value?.recordingId == current.recordingId) {
            app.opentv.core.RecordingSignals.clearImminent()
        }
    }

    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.FiberManualRecord,
                contentDescription = null,
                tint = Color(0xFFE53935),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.rec_switch_banner, current.title, current.channelName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(14.dp))
            var focused by remember { mutableStateOf(false) }
            Text(
                stringResource(R.string.rec_switch_keep_watching),
                style = MaterialTheme.typography.labelLarge,
                color = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .focusLift(focused)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (focused) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .onFocusChanged { focused = it.isFocused }
                    .focusable()
                    .clickable(interactionSource = null, indication = null) { app.opentv.core.RecordingSignals.suppress(current.recordingId) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * Detects a ten-foot device.
 *
 * Checked at runtime rather than by shipping a separate leanback build: one APK for phone,
 * tablet, Android TV and Fire TV means one thing to release and one thing to test.
 */
fun isRunningOnTelevision(context: Context): Boolean {
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
    val packageManager = context.packageManager
    return packageManager.hasSystemFeature("android.software.leanback") ||
        packageManager.hasSystemFeature("android.hardware.type.television")
}

/**
 * A destination that takes focus on its first item when it opens and places none itself (Settings,
 * Search, Profiles and the rest), so the remote works on it without a first press to wake focus.
 * Not for the players, the source form (its first item is a text field: the keyboard would pop up)
 * or the home shell, which handles its own.
 */
private fun NavGraphBuilder.screen(route: String, content: @Composable (NavBackStackEntry) -> Unit) =
    composable(route) { entry -> Box(Modifier.fillMaxSize().focusOnEnter()) { content(entry) } }
