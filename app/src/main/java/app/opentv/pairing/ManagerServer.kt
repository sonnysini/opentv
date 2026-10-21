/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.pairing

import android.content.Context
import android.util.Log
import app.opentv.BuildConfig
import app.opentv.core.AppSettings
import app.opentv.core.SettingsBackup
import app.opentv.core.LiveLayout
import app.opentv.core.LogoPack
import app.opentv.core.LogoSource
import app.opentv.core.ServiceLocator
import app.opentv.data.model.Channel
import app.opentv.data.model.GroupMerge
import app.opentv.data.model.StreamKind
import app.opentv.data.model.Source
import app.opentv.data.model.SourceKind
import app.opentv.data.model.shownName
import app.opentv.data.repo.CatalogRepository
import app.opentv.data.repo.EpgRepository
import app.opentv.data.work.SyncWorker
import app.opentv.data.repo.SourceRepository
import app.opentv.recording.SmbClient
import app.opentv.recording.SmbConfig
import app.opentv.ui.ChannelsViewModel
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import app.opentv.core.AppText
import app.opentv.R

/**
 * Serves a "manage your channels from your phone or laptop" web app off this television.
 *
 * ## Same ethos as [PairingServer]
 *
 * There is no cloud. The TV runs a tiny HTTP server on the local network for as long as the manage
 * screen is open; a phone or laptop on the same wifi opens a real page — with a keyboard and mouse
 * instead of a D-pad — and browses, hides, favourites, renames and reorders channels. Every change
 * writes straight to this device's database. It works with the internet unplugged.
 *
 * ## What stops the neighbours using it
 *
 * - Binds to the LAN on a **random high port**, and only while the manage screen is open.
 * - The URL carries a **random token**; every route checks it. Knowing the IP and port is not
 *   enough. The token is only ever shown on the TV (QR + link), so only someone who can see the
 *   telly can connect — which is the whole security model. It is kept short enough to type, because
 *   the TV shows the full URL (with the token) as a fallback for when the QR won't scan; a LAN-only,
 *   private-IP-only, idle-closing server on a random port doesn't need more. Unlike pairing there is
 *   no six-digit code, because this handles no credentials and the point is to be frictionless.
 * - Requests from non-private addresses are refused outright.
 * - Request bodies are capped ([MAX_BODY_BYTES]).
 * - Unlike pairing (which closes after the first submit) this keeps serving while the screen is
 *   open, and only closes itself after [IDLE_TIMEOUT_MINUTES] with no requests, or on [stop].
 */
class ManagerServer(
    private val scope: CoroutineScope,
    private val catalog: CatalogRepository,
    private val settings: AppSettings,
    private val sourceRepository: SourceRepository,
    private val epg: EpgRepository,
    private val appContext: Context,
) {

    data class Session(
        /** What the QR code encodes, e.g. `http://192.168.0.14:41234/?t=…` */
        val url: String,
        val host: String,
        val port: Int,
    ) {
        /** The address to type if the QR will not scan. */
        val shortUrl: String get() = "$host:$port"
    }

    sealed interface State {
        data object Idle : State
        data class Listening(val session: Session) : State
        data class Failed(val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var token: String = ""
    @Volatile private var running = false
    @Volatile private var lastActivityMillis = 0L

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun start() {
        if (running) return
        val host = lanAddress()
        if (host == null) {
            _state.value = State.Failed(
                AppText.get(R.string.mgr_no_network),
            )
            return
        }

        val random = SecureRandom()
        // A short token, not a long one, so the "camera won't scan the QR?" fallback URL the TV
        // shows is actually typeable into a laptop browser. It only gates a LAN-only, private-IP-only
        // server on a random high port that closes on idle and is shown solely on this TV screen, so
        // ~40 bits is ample — brute-forcing it on the local network inside the session window isn't
        // realistic.
        token = ByteArray(5).also(random::nextBytes).joinToString("") { "%02x".format(it) }

        val socket = try {
            // Port 0 asks the OS for a free one. A fixed port would be predictable and would
            // clash with whatever else the user runs at home.
            ServerSocket(0)
        } catch (e: Exception) {
            Log.w(TAG, "Could not open a socket", e)
            _state.value = State.Failed(AppText.get(R.string.mgr_start_failed))
            return
        }

        serverSocket = socket
        running = true
        lastActivityMillis = System.currentTimeMillis()

        _state.value = State.Listening(
            Session(
                url = "http://$host:${socket.localPort}/?t=$token",
                host = host,
                port = socket.localPort,
            ),
        )

        // A plain thread rather than a coroutine: ServerSocket.accept() blocks uninterruptibly,
        // and the only reliable way to stop it is to close the socket.
        thread(name = "opentv-manager", isDaemon = true) {
            while (running) {
                val client = try {
                    socket.accept()
                } catch (_: Exception) {
                    break // socket closed, which is how we stop
                }
                runCatching { handle(client) }
                    .onFailure { Log.w(TAG, "Manager request failed", it) }
                runCatching { client.close() }
            }
        }

        // Idle watchdog: keep serving while the screen is open, but do not leave a socket on the
        // user's network for hours if they wander off. Closes after a long unused stretch.
        scope.launch(Dispatchers.Default) {
            while (running) {
                delay(TimeUnit.MINUTES.toMillis(1))
                val idleFor = System.currentTimeMillis() - lastActivityMillis
                if (running && idleFor > TimeUnit.MINUTES.toMillis(IDLE_TIMEOUT_MINUTES)) {
                    stop()
                    _state.value = State.Failed(
                        AppText.get(R.string.mgr_idle_closed),
                    )
                    break
                }
            }
        }
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        token = ""
        if (_state.value is State.Listening) _state.value = State.Idle
    }

    // ---- Request handling ----------------------------------------------------------------------

    private fun handle(client: Socket) {
        client.soTimeout = SOCKET_TIMEOUT_MILLIS

        if (!PairingHttp.isPrivateAddress(client.inetAddress?.hostAddress)) {
            client.getOutputStream().write(
                PairingHttp.httpResponse("403 Forbidden", "text/plain", "Local network only."),
            )
            return
        }

        val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
        val requestLine = reader.readLine() ?: return
        val (method, path, query) = PairingHttp.parseRequestLine(requestLine) ?: return

        val headers = HashMap<String, String>()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            PairingHttp.parseHeader(line)?.let { (name, value) -> headers[name] = value }
        }

        val output = client.getOutputStream()

        // Every route checks the token. Without it, none of this exists.
        val supplied = query["t"] ?: ""
        if (token.isEmpty() || !PairingHttp.secretsMatch(supplied, token)) {
            output.write(PairingHttp.httpResponse("404 Not Found", "text/plain", "Not found."))
            return
        }

        // A valid request counts as activity; the idle watchdog measures from here.
        lastActivityMillis = System.currentTimeMillis()

        val body = if (method == "POST") readBody(reader, headers) else ""

        when {
            method == "GET" && path == "/" ->
                output.write(PairingHttp.httpResponse("200 OK", "text/html", ManagerPage.page(token)))

            method == "GET" && path == "/groups" -> writeJson(output, GroupsDto.serializer(), groupsDto())

            method == "GET" && path == "/group" -> writeJson(output, RowsDto.serializer(), groupRowsDto(query["key"].orEmpty()))

            method == "GET" && path == "/search" -> writeJson(output, RowsDto.serializer(), searchRowsDto(query["q"].orEmpty()))

            method == "POST" && path == "/groups/order" -> handleGroupOrder(output, body)

            method == "POST" && path == "/group/order" -> handleChannelOrder(output, body)

            method == "POST" && path == "/group/switch" -> handleGroupSwitch(output, body)

            method == "POST" && path == "/group/merge" -> handleGroupMerge(output, body)

            method == "POST" && path == "/row" -> handleRowPatch(output, body)

            method == "GET" && path == "/duplicates" -> writeJson(output, DuplicatesDto.serializer(), duplicatesDto())

            method == "POST" && path == "/merge" -> handleChannelMerge(output, body)

            method == "GET" && path == "/recording" ->
                output.write(PairingHttp.httpResponse("200 OK", "application/json", recordingJson()))

            method == "POST" && path == "/recording" -> handleRecording(output, body)

            method == "GET" && path == "/logopack" -> writeJson(output, LogoPackDto.serializer(), logoPackDto(null))

            method == "POST" && path == "/logopack" -> handleLogoPack(output, body)

            method == "POST" && path == "/recording/test" -> handleRecordingTest(output, body)

            method == "POST" && path == "/source" -> handleAddSource(output, body)

            method == "POST" && path == "/source/test" -> handleTestSource(output, body)

            method == "GET" && path == "/backup" -> handleBackup(output)

            method == "POST" && path == "/restore" -> handleRestore(output, body, query)

            else -> output.write(PairingHttp.httpResponse("404 Not Found", "text/plain", "Not found."))
        }
    }

    private fun readBody(reader: BufferedReader, headers: Map<String, String>): String {
        val contentLength = (headers["content-length"]?.toIntOrNull() ?: 0)
            .coerceAtMost(MAX_BODY_BYTES)
        if (contentLength <= 0) return ""
        // Content-Length counts bytes, the reader hands out chars, and one read() may stop short on
        // a big body: keep reading until the UTF-8 size of what arrived reaches the declared length.
        val body = StringBuilder()
        val buffer = CharArray(8 * 1024)
        var bytes = 0
        while (bytes < contentLength) {
            val read = reader.read(buffer, 0, minOf(buffer.size, contentLength - bytes))
            if (read <= 0) break
            body.append(buffer, 0, read)
            bytes += String(buffer, 0, read).toByteArray(Charsets.UTF_8).size
        }
        return body.toString()
    }

    // ---- Routes --------------------------------------------------------------------------------

    // ---- Live layout: groups and logical channels, as the TV lists them ------------------------

    /** The live groups as the TV's list shows them (merges and order applied), plus the switched-off ones. */
    private fun liveGroups(): Pair<List<ChannelsViewModel.CategoryGroup>, List<ChannelsViewModel.CategoryGroup>> = runBlocking {
        val all = catalog.observeAllCategories(StreamKind.LIVE).first()
        val rules = settings.categoryRules.value
        val (off, on) = all.partition { rules.isOff(it) }
        val merge = settings.mergeCategorySources.value
        val strip = settings.stripCategorySources.value
        ChannelsViewModel.foldCategories(on, merge, strip, settings.categoryMerges.value, settings.liveGroupOrder.value) to
            ChannelsViewModel.foldCategories(off, merge, strip)
    }

    /** `GET /groups` → `{groups:[…], off:[…], merges:[…], favourites}` */
    private fun groupsDto(): GroupsDto = runBlocking {
        val (on, off) = liveGroups()
        val byCategory = HashMap<String, MutableSet<String>>()
        for (k in catalog.observeCategoryChannelKeys().first()) {
            byCategory.getOrPut(k.categoryId) { HashSet() } += k.groupKey.ifEmpty { "#" + k.categoryId }
        }
        fun count(g: ChannelsViewModel.CategoryGroup) = g.ids.flatMapTo(HashSet()) { byCategory[it].orEmpty() }.size
        // Labels of the groups before the user's merges, so a merge reads "Sky Calcio → Sky Sport".
        val merges = settings.categoryMerges.value
        val unmerged = if (merges.isEmpty()) emptyList() else runBlocking {
            ChannelsViewModel.foldCategories(
                catalog.observeCategories(StreamKind.LIVE).first(),
                settings.mergeCategorySources.value, settings.stripCategorySources.value,
            )
        }
        fun labelOf(key: String) = unmerged.firstOrNull { key in it.memberKeys }?.label ?: key
        val favourites = catalog.observeFavouriteChannels().first()
            .distinctBy { ChannelsViewModel.rowOrderKey(it) }.size
        GroupsDto(
            groups = on.map { GroupDto(it.key, it.label, it.rawName, count(it), it.memberKeys.toList()) },
            off = off.map { GroupDto(it.key, it.label, it.rawName, 0, it.memberKeys.toList()) },
            merges = merges.map { (from, into) -> GroupMergeDto(from, labelOf(from), into, labelOf(into)) },
            favourites = favourites,
        )
    }

    /** Channels folded into rows the way the TV list does: one per grouping key, best quality first. */
    private fun toRows(channels: List<Channel>): List<RowDto> {
        val byKey = LinkedHashMap<String, MutableList<Channel>>()
        for (c in channels) byKey.getOrPut(ChannelsViewModel.rowOrderKey(c)) { ArrayList() } += c
        return byKey.map { (key, list) ->
            val sorted = list.sortedByDescending { it.qualityRank }
            val first = sorted.first()
            RowDto(
                key = key,
                name = first.shownName,
                original = first.displayName,
                logo = sorted.firstNotNullOfOrNull { it.logoUrl?.takeIf(String::isNotBlank) },
                hidden = list.all { it.hidden },
                favourite = list.any { it.favourite },
                renamed = !first.customName.isNullOrBlank(),
                qualities = sorted.map { it.qualityLabel }.filter { it.isNotBlank() }.distinct(),
                ids = list.map { it.id },
            )
        }
    }

    /** `GET /group?key=K` → that group's channels (hidden ones too) in the TV's order; `fav` = favourites. */
    private fun groupRowsDto(key: String): RowsDto = runBlocking {
        if (key == FAVOURITES) {
            return@runBlocking RowsDto(toRows(catalog.observeFavouriteChannels().first()))
        }
        val group = liveGroups().first.firstOrNull { key in it.memberKeys } ?: return@runBlocking RowsDto(emptyList())
        val rows = toRows(catalog.observeChannelsInIncludingHidden(null, group.ids).first())
        val order = settings.liveChannelOrder.value[group.key]
        RowsDto(if (order == null) rows else LiveLayout.ordered(rows, order) { listOf(it.key) }, group.key)
    }

    /** `GET /search?q=…` → matching channels across every group, for "merge with…". */
    private fun searchRowsDto(q: String): RowsDto = runBlocking {
        if (q.trim().length < 2) RowsDto(emptyList())
        else RowsDto(toRows(catalog.searchChannelsIncludingHidden(q.trim()).first()).take(SEARCH_LIMIT))
    }

    /** `POST /groups/order {keys}` → the live groups' order. */
    private fun handleGroupOrder(output: OutputStream, body: String) {
        val req = decode(KeysReq.serializer(), body) ?: return badRequest(output)
        settings.setLiveGroupOrder(req.keys)
        writeOk(output)
    }

    /** `POST /group/order {group, keys}` → channel order inside a group; favourites order by sortIndex. */
    private fun handleChannelOrder(output: OutputStream, body: String) {
        val req = decode(KeysReq.serializer(), body) ?: return badRequest(output)
        val group = req.group ?: return badRequest(output)
        if (group == FAVOURITES) {
            runBlocking {
                val rows = toRows(catalog.observeFavouriteChannels().first()).associateBy { it.key }
                req.keys.forEachIndexed { index, k -> rows[k]?.ids?.forEach { catalog.setChannelSortIndex(it, index) } }
            }
        } else {
            settings.setLiveChannelOrder(group, req.keys)
        }
        writeOk(output)
    }

    /**
     * `POST /group/switch {key, on}` → switch a whole group off or back on, exactly like the switch in
     * Settings › Categories: off drops its channels now, on brings them back with a catalogue refresh.
     */
    private fun handleGroupSwitch(output: OutputStream, body: String) {
        val req = decode(SwitchReq.serializer(), body) ?: return badRequest(output)
        val keys = req.memberKeys.ifEmpty { listOf(req.key) }.map { "${StreamKind.LIVE}:$it" }.toSet()
        catalog.setCategoriesEnabled(StreamKind.LIVE, keys, req.on)
        if (req.on) {
            SyncWorker.refreshNow(appContext)
        } else {
            runBlocking { catalog.purgeCategories(StreamKind.LIVE, keys) }
        }
        writeOk(output)
    }

    /** `POST /group/merge {from, into}` folds a group into another; `{from}` alone separates it again. */
    private fun handleGroupMerge(output: OutputStream, body: String) {
        val req = decode(MergeReq.serializer(), body) ?: return badRequest(output)
        val into = req.into
        if (into.isNullOrBlank()) settings.removeCategoryMerge(req.from) else settings.addCategoryMerge(req.from, into)
        writeOk(output)
    }

    /** `POST /row {ids, hidden?, favourite?, name?}` → one logical channel: every quality follows. */
    private fun handleRowPatch(output: OutputStream, body: String) {
        val req = decode(RowPatch.serializer(), body) ?: return badRequest(output)
        runBlocking {
            req.ids.filter { it > 0L }.forEach { id ->
                req.hidden?.let { catalog.setChannelHidden(id, it) }
                req.favourite?.let { catalog.setChannelFavourite(id, it) }
                req.name?.let { catalog.setChannelCustomName(id, it) }
            }
        }
        writeOk(output)
    }

    /** `GET /duplicates` → likely duplicate channels to merge or ignore, and the merges already made. */
    private fun duplicatesDto(): DuplicatesDto = runBlocking {
        val suggestions = runCatching { catalog.mergeSuggestions() }.getOrDefault(emptyList())
        DuplicatesDto(
            suggestions = suggestions.map {
                SuggestionDto(it.from.key, it.from.name, it.from.channels, it.into.key, it.into.name, it.into.channels, it.reason.name, it.pairId)
            },
            merges = settings.groupMerges.value.map { MergeDto(it.fromKey, it.fromName, it.intoKey, it.intoName) },
        )
    }

    /**
     * `POST /merge` — `{from, into, fromName, intoName}` merges two channels, `{from}` alone separates
     * one again, `{ignore: pairId}` drops a suggestion. Same choices as Settings › Channel grouping,
     * so they travel with the backup; the regroup runs before the answer, so a reload shows it done.
     */
    private fun handleChannelMerge(output: OutputStream, body: String) {
        val req = decode(MergeReq.serializer(), body) ?: return badRequest(output)
        req.ignore?.let {
            settings.ignoreMergePair(it)
            return writeOk(output)
        }
        val into = req.into
        if (into.isNullOrBlank()) {
            settings.removeGroupMerge(req.from)
        } else {
            if (req.from.isBlank() || req.from == into || req.from.startsWith("#") || into.startsWith("#")) return badRequest(output)
            settings.addGroupMerge(GroupMerge(req.from, into, req.fromName.orEmpty(), req.intoName.orEmpty()))
        }
        runBlocking {
            regroupLock.withLock {
                runCatching {
                    catalog.renormalizeAll()
                    epg.runMatcher()
                }.onFailure { Log.w(TAG, "Regroup after merge failed", it) }
            }
        }
        writeOk(output)
    }

    private fun <T> decode(serializer: kotlinx.serialization.KSerializer<T>, body: String): T? =
        runCatching { json.decodeFromString(serializer, body) }.getOrNull()

    private fun badRequest(output: OutputStream) =
        output.write(PairingHttp.httpResponse("400 Bad Request", "text/plain", "Bad request."))

    private val regroupLock = Mutex()

    /** `GET /backup` → the settings file, as a download. */
    private fun handleBackup(output: OutputStream) {
        val file = runBlocking {
            SettingsBackup.File(
                app = BuildConfig.VERSION_NAME,
                exportedAt = System.currentTimeMillis(),
                settings = SettingsBackup.toJson(settings.backupSettings()),
                guides = epg.feedsForBackup(),
                merges = settings.groupMerges.value,
                ignoredPairs = settings.ignoredMergePairs.value.sorted(),
            )
        }
        val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date())
        output.write(
            PairingHttp.httpResponse(
                "200 OK", "application/json", SettingsBackup.encode(file),
                mapOf("Content-Disposition" to "attachment; filename=\"opentv-settings-$day.json\""),
            ),
        )
    }

    /**
     * `POST /restore?settings=1&merges=1` with a [SettingsBackup] file → apply the parts asked for:
     * settings (and guide feeds) replace this TV's, merges add to the ones already here. The
     * regroup and catalogue refresh run on their own scope, so leaving the screen doesn't stop them.
     */
    private fun handleRestore(output: OutputStream, body: String, query: Map<String, String>) {
        val file = SettingsBackup.decode(body)
        if (file == null) {
            writeJson(output, RestoreDto(ok = false, error = "This isn't an OpenTV settings file."))
            return
        }
        val withSettings = query["settings"] == "1" && file.settings != null
        val withMerges = query["merges"] == "1"
        if (!withSettings && !withMerges) {
            writeJson(output, RestoreDto(ok = false, error = "Nothing selected to import."))
            return
        }
        val language = settings.languageTag.value
        val mergesBefore = settings.groupMerges.value.size
        val guides = runBlocking {
            if (withSettings) {
                settings.restoreSettings(SettingsBackup.fromJson(file.settings!!, settings.backupKinds))
                runCatching { epg.restoreFeeds(file.guides.orEmpty()) }.getOrDefault(0)
            } else 0
        }
        if (withMerges) settings.addMergeChoices(file.merges.orEmpty(), file.ignoredPairs.orEmpty().toSet())
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                catalog.renormalizeAll()
                epg.runMatcher()
            }.onFailure { Log.w(TAG, "Regroup after restore failed", it) }
            // Categories switched back on, content types and guide feeds only arrive with a sync.
            if (withSettings) SyncWorker.refreshNow(appContext)
        }
        writeJson(
            output,
            RestoreDto(
                ok = true,
                settings = withSettings,
                merges = settings.groupMerges.value.size - mergesBefore,
                guides = guides,
                languageChanged = settings.languageTag.value != language,
            ),
        )
    }

    private fun writeJson(output: OutputStream, dto: RestoreDto) {
        output.write(PairingHttp.httpResponse("200 OK", "application/json", json.encodeToString(RestoreDto.serializer(), dto)))
    }

    private fun writeOk(output: OutputStream) {
        output.write(
            PairingHttp.httpResponse("200 OK", "application/json", json.encodeToString(OkDto.serializer(), OkDto())),
        )
    }

    /** `GET /recording` → the current recording target and NAS settings. The password is never sent
     *  back to the browser — only whether one is set. */
    private fun recordingJson(): String = json.encodeToString(
        RecordingDto.serializer(),
        RecordingDto(
            target = settings.recordingTarget.value.name,
            host = settings.smbHost.value,
            share = settings.smbShare.value,
            folder = settings.smbFolder.value,
            user = settings.smbUser.value,
            hasPassword = settings.smbPassword.value.isNotEmpty(),
        ),
    )

    /** `POST /recording {host,share,folder,user,password?}` → save the NAS details and switch
     *  recordings to it. A blank/absent password keeps the one already stored, so the fields can be
     *  filled without re-typing an existing password. */
    private fun handleRecording(output: OutputStream, body: String) {
        val req = runCatching { json.decodeFromString(RecordingReq.serializer(), body) }.getOrNull()
        if (req == null) {
            output.write(PairingHttp.httpResponse("400 Bad Request", "text/plain", "Bad request."))
            return
        }
        val password = req.password?.takeIf { it.isNotEmpty() } ?: settings.smbPassword.value
        settings.setSmbConfig(req.host.trim(), req.share.trim(), req.folder.trim(), req.user.trim(), password)
        settings.setRecordingTarget(AppSettings.RecordingTarget.SMB)
        writeOk(output)
    }

    private fun logoPackDto(error: String?) = LogoPackDto(
        url = settings.logoPackUrl.value,
        count = settings.logoPack.value.byKey.size,
        error = error,
        source = settings.logoSource.value.name,
        externalGroups = settings.externalGroupLogos.value,
    )

    /**
     * `POST /logopack {url?, source?, externalGroups?}` → Settings › Logos from the phone. Each field
     * is optional: a url is saved and loaded now (blank removes the pack), source and externalGroups
     * set the two choices.
     */
    private fun handleLogoPack(output: OutputStream, body: String) {
        val req = runCatching { json.decodeFromString(LogoPackReq.serializer(), body) }.getOrNull()
        if (req == null) {
            output.write(PairingHttp.httpResponse("400 Bad Request", "text/plain", "Bad request."))
            return
        }
        req.source?.let { name -> runCatching { LogoSource.valueOf(name) }.getOrNull()?.let(settings::setLogoSource) }
        req.externalGroups?.let(settings::setExternalGroupLogos)
        val url = req.url ?: return writeJson(output, LogoPackDto.serializer(), logoPackDto(null))
        settings.setLogoPackUrl(url)
        val error = if (url.isBlank()) null else runBlocking {
            runCatching { LogoPack.refresh(settings, ServiceLocator.get(appContext).httpClient) }
                .exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName }
        }
        writeJson(output, LogoPackDto.serializer(), logoPackDto(error))
    }

    /** `POST /recording/test {host,share,folder,user,password?}` → actually open the SMB share and
     *  report whether it worked. Blank fields fall back to what's stored, so the browser can test
     *  the saved settings without re-sending the password. Runs on this handler thread (already off
     *  the main thread), which is where a blocking network probe belongs. */
    private fun handleRecordingTest(output: OutputStream, body: String) {
        val req = runCatching { json.decodeFromString(RecordingReq.serializer(), body) }.getOrNull()
            ?: RecordingReq()
        val cfg = SmbConfig(
            host = req.host.trim().ifBlank { settings.smbHost.value },
            share = req.share.trim().ifBlank { settings.smbShare.value },
            folder = req.folder.trim().ifBlank { settings.smbFolder.value },
            username = req.user.trim().ifBlank { settings.smbUser.value },
            password = req.password?.takeIf { it.isNotEmpty() } ?: settings.smbPassword.value,
        )
        val result = runCatching { SmbClient.test(cfg) }
        val dto = if (result.isSuccess) {
            TestDto(ok = true, error = null)
        } else {
            val e = result.exceptionOrNull()
            TestDto(ok = false, error = e?.message ?: e?.javaClass?.simpleName ?: "Connection failed")
        }
        output.write(PairingHttp.httpResponse("200 OK", "application/json", json.encodeToString(TestDto.serializer(), dto)))
    }

    // ---- Add a provider ------------------------------------------------------------------------

    /**
     * `POST /source {kind,name,url,username?,password?,mac?}` → save the provider and pull its live
     * channels, so the catalogue fills without anyone typing a server address on the TV remote.
     * Mirrors the on-device Add-source screen: save, then load live channels first.
     */
    private fun handleAddSource(output: OutputStream, body: String) {
        val draft = runCatching { json.decodeFromString(SourceReq.serializer(), body) }.getOrNull()?.toDraftOrNull()
        if (draft == null) {
            writeJson(output, AddResultDto.serializer(), AddResultDto(ok = false, error = "Fill in the required fields for this provider type."))
            return
        }
        val result = runBlocking {
            val id = sourceRepository.save(draft)
            val saved = sourceRepository.byId(id)
                ?: return@runBlocking AddResultDto(ok = false, error = "Could not save the source.")
            when (val r = catalog.syncLive(saved, System.currentTimeMillis())) {
                is CatalogRepository.SyncResult.Success -> AddResultDto(ok = true, channels = r.channelCount)
                is CatalogRepository.SyncResult.Failed -> AddResultDto(ok = false, error = r.reason)
            }
        }
        writeJson(output, AddResultDto.serializer(), result)
    }

    /** `POST /source/test {…}` → validate the provider details without saving (handshake / auth). */
    private fun handleTestSource(output: OutputStream, body: String) {
        val draft = runCatching { json.decodeFromString(SourceReq.serializer(), body) }.getOrNull()?.toDraftOrNull()
        if (draft == null) {
            writeJson(output, TestResultDto.serializer(), TestResultDto(ok = false, error = "Fill in the required fields for this provider type."))
            return
        }
        val result = runBlocking { sourceRepository.test(draft) }
        val dto = if (result.isSuccess) {
            TestResultDto(ok = true, message = result.getOrNull())
        } else {
            TestResultDto(ok = false, error = result.exceptionOrNull()?.message ?: "Test failed.")
        }
        writeJson(output, TestResultDto.serializer(), dto)
    }

    /** Turn a web request into a [Source] draft, or null if a field required for its kind is missing. */
    private fun SourceReq.toDraftOrNull(): Source? {
        val k = runCatching { SourceKind.valueOf(kind.trim().uppercase()) }.getOrNull() ?: return null
        if (url.isBlank()) return null
        when (k) {
            SourceKind.XTREAM -> if (username.isNullOrBlank() || password.isNullOrBlank()) return null
            SourceKind.STALKER -> if (mac.isNullOrBlank()) return null
            SourceKind.M3U -> Unit
        }
        return Source(
            name = name.trim().ifBlank { if (k == SourceKind.M3U) "Playlist" else "Provider" },
            kind = k,
            url = url.trim(),
            username = username?.takeIf { it.isNotBlank() },
            password = password?.takeIf { it.isNotBlank() },
            macAddress = mac?.takeIf { it.isNotBlank() },
            userAgent = Source.DEFAULT_USER_AGENT,
        )
    }

    private fun <T> writeJson(output: OutputStream, serializer: kotlinx.serialization.KSerializer<T>, value: T) {
        output.write(PairingHttp.httpResponse("200 OK", "application/json", json.encodeToString(serializer, value)))
    }

    // ---- Finding our own address ---------------------------------------------------------------

    /**
     * The device's address on the LAN.
     *
     * Deliberately the same enumeration as [PairingServer.lanAddress] and [app.opentv.sync.SyncServer]
     * (duplicated, not shared, to keep the servers independent): asks the interfaces rather than
     * ConnectivityManager, because TV boxes are frequently on ethernet and the wifi-shaped API
     * returns nothing useful when the box is wired.
     */
    private fun lanAddress(): String? {
        val candidates = mutableListOf<InetAddress>()
        runCatching {
            for (nic in NetworkInterface.getNetworkInterfaces()) {
                if (!nic.isUp || nic.isLoopback) continue
                for (address in nic.inetAddresses) {
                    if (address.isLoopbackAddress) continue
                    if (address.hostAddress?.contains(':') == true) continue // IPv4 only
                    if (PairingHttp.isPrivateAddress(address.hostAddress)) candidates += address
                }
            }
        }.onFailure { Log.w(TAG, "Could not enumerate interfaces", it) }

        // Prefer a real private range over a 169.254 self-assigned address, which means DHCP
        // failed and nothing will be able to reach us anyway.
        return candidates
            .sortedBy { it.hostAddress?.startsWith("169.254") == true }
            .firstOrNull()
            ?.hostAddress
    }

    private companion object {
        const val TAG = "ManagerServer"
        const val SOCKET_TIMEOUT_MILLIS = 15_000
        const val IDLE_TIMEOUT_MINUTES = 30L

        /** The pseudo-group key for Favourites on the page. */
        const val FAVOURITES = "fav"

        /** Most channels a "merge with…" search returns. */
        const val SEARCH_LIMIT = 60

        // Generous next to pairing's 16 KB: a /reorder body carries every channel id in a
        // category, which can be a few thousand on a big provider, and a /restore file every
        // switched-off category name.
        const val MAX_BODY_BYTES = 1024 * 1024
    }
}

// ---- JSON DTOs ---------------------------------------------------------------------------------
// kotlinx.serialization handles the escaping that matters here: channel names carry quotes, `|`
// and unicode, and hand-rolled string concatenation is exactly where that goes wrong.

@Serializable private data class OkDto(val ok: Boolean = true)

@Serializable private data class RestoreDto(
    val ok: Boolean,
    val settings: Boolean = false,
    val merges: Int = 0,
    val guides: Int = 0,
    val languageChanged: Boolean = false,
    val error: String? = null,
)

@Serializable private data class RecordingDto(
    val target: String,
    val host: String,
    val share: String,
    val folder: String,
    val user: String,
    val hasPassword: Boolean,
)

@Serializable private data class RecordingReq(
    val host: String = "",
    val share: String = "",
    val folder: String = "",
    val user: String = "",
    val password: String? = null,
)

@Serializable private data class TestDto(val ok: Boolean, val error: String?)

@Serializable private data class LogoPackReq(
    val url: String? = null,
    val source: String? = null,
    val externalGroups: Boolean? = null,
)

@Serializable private data class LogoPackDto(
    val url: String,
    val count: Int,
    val error: String?,
    val source: String,
    val externalGroups: Boolean,
)

@Serializable private data class SourceReq(
    val kind: String = "xtream",
    val name: String = "",
    val url: String = "",
    val username: String? = null,
    val password: String? = null,
    val mac: String? = null,
)

@Serializable private data class AddResultDto(val ok: Boolean, val channels: Int = 0, val error: String? = null)

@Serializable private data class TestResultDto(val ok: Boolean, val message: String? = null, val error: String? = null)

@Serializable private data class GroupDto(
    val key: String,
    val label: String,
    val raw: String,
    val count: Int,
    val memberKeys: List<String>,
)

@Serializable private data class GroupMergeDto(val from: String, val fromLabel: String, val into: String, val intoLabel: String)

@Serializable private data class GroupsDto(
    val groups: List<GroupDto>,
    val off: List<GroupDto>,
    val merges: List<GroupMergeDto>,
    val favourites: Int,
)

@Serializable private data class RowDto(
    val key: String,
    val name: String,
    val original: String,
    val logo: String?,
    val hidden: Boolean,
    val favourite: Boolean,
    val renamed: Boolean,
    val qualities: List<String>,
    val ids: List<Long>,
)

@Serializable private data class RowsDto(val rows: List<RowDto>, val group: String? = null)

@Serializable private data class KeysReq(val keys: List<String>, val group: String? = null)

@Serializable private data class SwitchReq(val key: String, val on: Boolean, val memberKeys: List<String> = emptyList())

@Serializable private data class MergeReq(
    val from: String = "",
    val into: String? = null,
    val fromName: String? = null,
    val intoName: String? = null,
    val ignore: String? = null,
)

@Serializable private data class RowPatch(
    val ids: List<Long>,
    val hidden: Boolean? = null,
    val favourite: Boolean? = null,
    val name: String? = null,
)

@Serializable private data class SuggestionDto(
    val from: String,
    val fromName: String,
    val fromCount: Int,
    val into: String,
    val intoName: String,
    val intoCount: Int,
    val reason: String,
    val pairId: String,
)

@Serializable private data class MergeDto(val from: String, val fromName: String, val into: String, val intoName: String)

@Serializable private data class DuplicatesDto(val suggestions: List<SuggestionDto>, val merges: List<MergeDto>)
