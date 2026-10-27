/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.core

import android.content.Context
import app.opentv.data.model.GroupMerge
import app.opentv.data.model.StremioAddon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The handful of user preferences that are not "data" (sources, guides) but "how the app
 * behaves". Backed by [android.content.SharedPreferences] rather than DataStore on purpose:
 * three flags read at composition time do not need an async, Flow-based store and the
 * ceremony that comes with it. Each setting is also mirrored into a [StateFlow] so Compose
 * recomposes the moment one changes.
 */
class AppSettings private constructor(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("opentv_settings", Context.MODE_PRIVATE)

    private val addonJson = Json { ignoreUnknownKeys = true }

    /** Re-reads of every persisted flow, run by [reload] after a settings import rewrote the prefs. */
    private val reloaders = mutableListOf<() -> Unit>()

    /** A flow seeded from prefs by [read], and re-seeded by the same [read] on [reload]. */
    private fun <T> pref(read: () -> T): MutableStateFlow<T> =
        MutableStateFlow(read()).also { flow -> reloaders += { flow.value = read() } }

    /** How the app chooses light vs dark. TV defaults to dark under [ThemeMode.SYSTEM]. */
    enum class ThemeMode { SYSTEM, DARK, LIGHT }

    private val _themeMode = pref { readThemeMode() }
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    /**
     * The colour/font style: [AppStyle.OWNTV] (teal on green-black, Plus Jakarta Sans, after the
     * OwnTV app) or [AppStyle.CLASSIC], the original indigo OpenTV look.
     */
    enum class AppStyle { OWNTV, CLASSIC }

    private val _appStyle = pref {
        runCatching { AppStyle.valueOf(prefs.getString(KEY_APP_STYLE, null) ?: "") }.getOrDefault(AppStyle.OWNTV)
    }
    val appStyle: StateFlow<AppStyle> = _appStyle.asStateFlow()

    fun setAppStyle(style: AppStyle) {
        prefs.edit().putString(KEY_APP_STYLE, style.name).apply()
        _appStyle.value = style
    }

    /**
     * How the live-TV channel list is laid out: the scrolling EPG time-[ChannelLayout.GRID], or a
     * compact one-channel-per-row [ChannelLayout.LIST] (logo, name, now/next) for people who find
     * the full grid busy, or [ChannelLayout.FULLSCREEN]: the same list across the whole screen with
     * no preview or menus, ☰ switching to the categories. Defaults to the full-screen list.
     */
    enum class ChannelLayout { GRID, LIST, FULLSCREEN }

    private val _channelLayout = pref { readChannelLayout() }
    val channelLayout: StateFlow<ChannelLayout> = _channelLayout.asStateFlow()

    /** Whether embedded subtitles/closed captions are shown when a stream carries them. */
    private val _subtitlesEnabled = pref { prefs.getBoolean(KEY_SUBTITLES, true) }
    val subtitlesEnabled: StateFlow<Boolean> = _subtitlesEnabled.asStateFlow()

    /**
     * Where channel logos come from: the provider's alone (the default), the provider's with the
     * logo pack and the guides filling the gaps, or those first. See Channel.logo.
     */
    private val _logoSource = pref { readLogoSource() }
    val logoSource: StateFlow<LogoSource> = _logoSource.asStateFlow()

    fun setLogoSource(source: LogoSource) {
        prefs.edit().putString(KEY_LOGO_SOURCE, source.name).apply()
        _logoSource.value = source
    }

    /** Where the user's logo pack lives (its `index.json`); blank = none. See [LogoPack]. */
    private val _logoPackUrl = pref { prefs.getString(KEY_LOGO_PACK_URL, "").orEmpty() }
    val logoPackUrl: StateFlow<String> = _logoPackUrl.asStateFlow()

    /** The last logo pack downloaded from [logoPackUrl], kept so logos show offline and at once. */
    private val _logoPack = pref {
        LogoPack(
            readJson(KEY_LOGO_PACK) { addonJson.decodeFromString<Map<String, String>>(it) } ?: emptyMap(),
            readJson(KEY_CUSTOM_LOGOS) { addonJson.decodeFromString<Map<String, String>>(it) } ?: emptyMap(),
        )
    }
    val logoPack: StateFlow<LogoPack> = _logoPack.asStateFlow()

    fun setLogoPackUrl(url: String) {
        val clean = url.trim()
        prefs.edit().putString(KEY_LOGO_PACK_URL, clean).apply()
        _logoPackUrl.value = clean
        if (clean.isEmpty()) setLogoPack(emptyMap())
    }

    fun setLogoPack(byKey: Map<String, String>) {
        prefs.edit().putString(KEY_LOGO_PACK, addonJson.encodeToString(byKey)).apply()
        _logoPack.value = LogoPack(byKey, _logoPack.value.custom)
    }

    /** Logos picked by hand for single channels: group key → URL. Null [url] goes back to automatic. */
    fun setCustomLogo(groupKey: String, url: String?) {
        if (groupKey.isBlank()) return
        val current = _logoPack.value
        val next = if (url.isNullOrBlank()) current.custom - groupKey else current.custom + (groupKey to url)
        prefs.edit().putString(KEY_CUSTOM_LOGOS, addonJson.encodeToString(next)).apply()
        _logoPack.value = LogoPack(current.byKey, next)
    }

    /** Whether the selected channel plays live inside the guide's preview pane. */
    private val _guidePreviewVideo = pref { prefs.getBoolean(KEY_PREVIEW_VIDEO, true) }
    val guidePreviewVideo: StateFlow<Boolean> = _guidePreviewVideo.asStateFlow()

    /** Whether the guide preview plays sound (off by default — quieter while browsing). */
    private val _guidePreviewSound = pref { prefs.getBoolean(KEY_PREVIEW_SOUND, false) }
    val guidePreviewSound: StateFlow<Boolean> = _guidePreviewSound.asStateFlow()

    /** The profile whose watch history is active. Defaults to the built-in profile (id 1). */
    private val _activeProfileId = pref { prefs.getLong(KEY_ACTIVE_PROFILE, 1L) }
    val activeProfileId: StateFlow<Long> = _activeProfileId.asStateFlow()

    fun setActiveProfile(id: Long) {
        prefs.edit().putLong(KEY_ACTIVE_PROFILE, id).apply()
        _activeProfileId.value = id
    }

    // ---- Parental controls -------------------------------------------------------------------

    /** Whether a parental PIN is set. The PIN itself is only ever stored as a salted hash. */
    private val _pinIsSet = pref { prefs.getString(KEY_PIN_HASH, null) != null }
    val pinIsSet: StateFlow<Boolean> = _pinIsSet.asStateFlow()

    /** Category group keys the user has marked adult/hidden. */
    private val _hiddenCategories =
        pref { prefs.getStringSet(KEY_HIDDEN_CATS, emptySet())!!.toSet() }
    val hiddenCategories: StateFlow<Set<String>> = _hiddenCategories.asStateFlow()

    /**
     * Session unlock. Deliberately *not* persisted: revealing hidden categories lasts until the
     * app is next launched, so a child restarting the app is back behind the lock.
     */
    private val _hiddenUnlocked = MutableStateFlow(false)
    val hiddenUnlocked: StateFlow<Boolean> = _hiddenUnlocked.asStateFlow()

    fun setPin(pin: String) {
        prefs.edit().putString(KEY_PIN_HASH, hashPin(pin)).apply()
        _pinIsSet.value = true
    }

    fun clearPin() {
        prefs.edit().remove(KEY_PIN_HASH).apply()
        _pinIsSet.value = false
        _hiddenUnlocked.value = true
    }

    fun verifyPin(pin: String): Boolean {
        val stored = prefs.getString(KEY_PIN_HASH, null) ?: return true
        return stored == hashPin(pin)
    }

    fun setHiddenCategories(keys: Set<String>) {
        prefs.edit().putStringSet(KEY_HIDDEN_CATS, keys).apply()
        _hiddenCategories.value = keys.toSet()
    }

    fun setHiddenUnlocked(unlocked: Boolean) {
        _hiddenUnlocked.value = unlocked
    }

    // ---- Switched-off categories -------------------------------------------------------------

    /**
     * Category keys (see [app.opentv.data.repo.CatalogRepository.categoryKey]) the user has switched
     * off. Unlike [hiddenCategories] this is not a view filter: a switched-off category's channels
     * and titles are never stored, so they cost no memory, no guide build time and no sync time.
     */
    private val _disabledCategories =
        pref { prefs.getStringSet(KEY_DISABLED_CATS, emptySet())!!.toSet() }
    val disabledCategories: StateFlow<Set<String>> = _disabledCategories.asStateFlow()

    @Synchronized
    fun setDisabledCategories(keys: Set<String>) {
        prefs.edit().putStringSet(KEY_DISABLED_CATS, keys.toSet()).apply()
        _disabledCategories.value = keys.toSet()
        publishCategoryRules()
    }

    /**
     * Live category keys the user switched back on by hand while a language rule
     * ([italianOnly]/[foreignSportOff]) would otherwise switch them off.
     */
    private val _forcedOnCategories =
        pref { prefs.getStringSet(KEY_FORCED_ON_CATS, emptySet())!!.toSet() }

    /** "Italian only": every foreign-language live category is switched off, new ones included. */
    private val _italianOnly = pref { prefs.getBoolean(KEY_ITALIAN_ONLY, false) }
    val italianOnly: StateFlow<Boolean> = _italianOnly.asStateFlow()

    /** International sport groups with foreign commentary (ESPN+, NBA Pass, …) switched off. */
    /** Fold live categories split by restream source ('Sky Sport (A)' + '(B)') into one. */
    private val _mergeCategorySources = pref { prefs.getBoolean(KEY_MERGE_CATEGORY_SOURCES, true) }
    val mergeCategorySources: StateFlow<Boolean> = _mergeCategorySources.asStateFlow()

    fun setMergeCategorySources(on: Boolean) {
        prefs.edit().putBoolean(KEY_MERGE_CATEGORY_SOURCES, on).apply()
        _mergeCategorySources.value = on
    }

    /** Drop the restream source from a lone group's name: 'Bambini (A)' reads "Bambini". */
    private val _stripCategorySources = pref { prefs.getBoolean(KEY_STRIP_CATEGORY_SOURCES, true) }
    val stripCategorySources: StateFlow<Boolean> = _stripCategorySources.asStateFlow()

    fun setStripCategorySources(on: Boolean) {
        prefs.edit().putBoolean(KEY_STRIP_CATEGORY_SOURCES, on).apply()
        _stripCategorySources.value = on
    }

    /**
     * Group badges from outside (broadcaster logos, flags) before the provider's emoji. Off, the
     * badge is only ever the emoji the provider put in the name. See GroupBadge.
     */
    private val _externalGroupLogos = pref {
        if (prefs.contains(KEY_GROUP_LOGOS_EXTERNAL)) prefs.getBoolean(KEY_GROUP_LOGOS_EXTERNAL, false)
        // Before Settings › Logos the badges switch was on unless turned off: keep what was showing.
        else prefs.getBoolean(KEY_GROUP_BADGES, false)
    }
    val externalGroupLogos: StateFlow<Boolean> = _externalGroupLogos.asStateFlow()

    fun setExternalGroupLogos(on: Boolean) {
        prefs.edit().putBoolean(KEY_GROUP_LOGOS_EXTERNAL, on).apply()
        _externalGroupLogos.value = on
    }

    /** The "Recent" section at the top of the live groups list. */
    private val _showRecentGroups = pref { prefs.getBoolean(KEY_SHOW_RECENT_GROUPS, true) }
    val showRecentGroups: StateFlow<Boolean> = _showRecentGroups.asStateFlow()

    fun setShowRecentGroups(on: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_RECENT_GROUPS, on).apply()
        _showRecentGroups.value = on
    }

    private val _foreignSportOff = pref { prefs.getBoolean(KEY_FOREIGN_SPORT_OFF, false) }
    val foreignSportOff: StateFlow<Boolean> = _foreignSportOff.asStateFlow()

    private val _categoryRules = MutableStateFlow(currentCategoryRules())

    /** Everything that decides whether a category is switched off — see [CategoryRules.isOff]. */
    val categoryRules: StateFlow<CategoryRules> = _categoryRules.asStateFlow()

    @Synchronized
    fun setForcedOnCategories(keys: Set<String>) {
        prefs.edit().putStringSet(KEY_FORCED_ON_CATS, keys.toSet()).apply()
        _forcedOnCategories.value = keys.toSet()
        publishCategoryRules()
    }

    @Synchronized
    fun setLanguageRules(italianOnly: Boolean, foreignSportOff: Boolean) {
        prefs.edit()
            .putBoolean(KEY_ITALIAN_ONLY, italianOnly)
            .putBoolean(KEY_FOREIGN_SPORT_OFF, foreignSportOff)
            .apply()
        _italianOnly.value = italianOnly
        _foreignSportOff.value = foreignSportOff
        publishCategoryRules()
    }

    private fun currentCategoryRules() = CategoryRules(
        disabled = _disabledCategories.value,
        forcedOn = _forcedOnCategories.value,
        italianOnly = _italianOnly.value,
        foreignSportOff = _foreignSportOff.value,
    )

    private fun publishCategoryRules() {
        _categoryRules.value = currentCategoryRules()
    }

    private fun hashPin(pin: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("opentv-pin::$pin".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    fun setChannelLayout(layout: ChannelLayout) {
        prefs.edit().putString(KEY_CHANNEL_LAYOUT, layout.name).apply()
        _channelLayout.value = layout
    }

    /**
     * The live group last opened — "fav", "all", or a category group key — so the app reopens on
     * it. Read once at start-up by the live screen; not a flow because nothing needs to observe it.
     */
    var lastLiveGroup: String?
        get() = prefs.getString(KEY_LAST_LIVE_GROUP, null)
        set(value) { prefs.edit().putString(KEY_LAST_LIVE_GROUP, value).apply() }

    /** The live groups last watched from, newest first ("fav"/"all"/group key) — the list's "Recent". */
    private val _recentLiveGroups = pref {
        prefs.getString(KEY_RECENT_LIVE_GROUPS, null)?.split('\n')?.filter { it.isNotEmpty() }.orEmpty()
    }
    val recentLiveGroups: StateFlow<List<String>> = _recentLiveGroups.asStateFlow()

    fun addRecentLiveGroup(group: String) {
        val list = (listOf(group) + _recentLiveGroups.value.filter { it != group }).take(RECENT_LIVE_GROUPS_MAX)
        if (list == _recentLiveGroups.value) return
        prefs.edit().putString(KEY_RECENT_LIVE_GROUPS, list.joinToString("\n")).apply()
        _recentLiveGroups.value = list
    }

    fun setSubtitlesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SUBTITLES, enabled).apply()
        _subtitlesEnabled.value = enabled
    }

    fun setGuidePreviewVideo(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PREVIEW_VIDEO, enabled).apply()
        _guidePreviewVideo.value = enabled
    }

    fun setGuidePreviewSound(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PREVIEW_SOUND, enabled).apply()
        _guidePreviewSound.value = enabled
    }

    /** Whether launching the app jumps straight back to the last channel you watched. */
    private val _resumeLastChannel = pref { prefs.getBoolean(KEY_RESUME_LAST, false) }
    val resumeLastChannel: StateFlow<Boolean> = _resumeLastChannel.asStateFlow()

    fun setResumeLastChannel(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RESUME_LAST, enabled).apply()
        _resumeLastChannel.value = enabled
    }

    // ---- Channel quality ---------------------------------------------------------------------

    /** Which quality variant of a channel plays first. See [QualityPreference.order]. */
    private val _preferredQuality = pref {
        runCatching { PreferredQuality.valueOf(prefs.getString(KEY_PREFERRED_QUALITY, null) ?: "") }
            .getOrDefault(PreferredQuality.FHD)
    }
    val preferredQuality: StateFlow<PreferredQuality> = _preferredQuality.asStateFlow()

    fun setPreferredQuality(quality: PreferredQuality) {
        prefs.edit().putString(KEY_PREFERRED_QUALITY, quality.name).apply()
        _preferredQuality.value = quality
    }

    /**
     * Restream sources to try first, best first (`Src A`, `Src B`): a channel starts on a variant
     * from the first listed source it has, before any quality rule. Sources left out keep the
     * plain quality order after the listed ones. See [QualityPreference.order].
     */
    private val _preferredSources = pref {
        prefs.getString(KEY_PREFERRED_SOURCES, "").orEmpty().split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    }
    val preferredSources: StateFlow<List<String>> = _preferredSources.asStateFlow()

    fun setPreferredSources(sources: List<String>) {
        val clean = sources.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        prefs.edit().putString(KEY_PREFERRED_SOURCES, clean.joinToString("\n")).apply()
        _preferredSources.value = clean
    }

    /** When a variant errors or shows no picture within [qualityFallbackMillis], try the next one. */
    private val _qualityFallback = pref { prefs.getBoolean(KEY_QUALITY_FALLBACK, true) }
    val qualityFallback: StateFlow<Boolean> = _qualityFallback.asStateFlow()

    fun setQualityFallback(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_QUALITY_FALLBACK, enabled).apply()
        _qualityFallback.value = enabled
    }

    /** Wait in milliseconds, in [FALLBACK_STEP_MILLIS] steps; migrates the old whole-seconds pref. */
    private val _qualityFallbackMillis = pref {
        prefs.getInt(
            KEY_QUALITY_FALLBACK_MILLIS,
            prefs.getInt(KEY_QUALITY_FALLBACK_SECONDS, DEFAULT_FALLBACK_SECONDS) * 1_000,
        ).coerceIn(MIN_FALLBACK_MILLIS, MAX_FALLBACK_MILLIS)
    }
    val qualityFallbackMillis: StateFlow<Int> = _qualityFallbackMillis.asStateFlow()

    fun setQualityFallbackMillis(millis: Int) {
        val ms = millis.coerceIn(MIN_FALLBACK_MILLIS, MAX_FALLBACK_MILLIS)
        prefs.edit().putInt(KEY_QUALITY_FALLBACK_MILLIS, ms).apply()
        _qualityFallbackMillis.value = ms
    }

    /** While watching, a stream that stops moving for [stallFallbackMillis] moves to the next variant. */
    private val _stallFallback = pref { prefs.getBoolean(KEY_STALL_FALLBACK, true) }
    val stallFallback: StateFlow<Boolean> = _stallFallback.asStateFlow()

    fun setStallFallback(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_STALL_FALLBACK, enabled).apply()
        _stallFallback.value = enabled
    }

    /**
     * While watching, this many short stutters within a minute move to the channel's next variant
     * (see [app.opentv.player.HiccupWatch]). 0 is off.
     */
    private val _hiccupLimit = pref {
        prefs.getInt(KEY_HICCUP_LIMIT, DEFAULT_HICCUP_LIMIT).coerceIn(0, MAX_HICCUP_LIMIT)
    }
    val hiccupLimit: StateFlow<Int> = _hiccupLimit.asStateFlow()

    fun setHiccupLimit(count: Int) {
        val n = count.coerceIn(0, MAX_HICCUP_LIMIT)
        prefs.edit().putInt(KEY_HICCUP_LIMIT, n).apply()
        _hiccupLimit.value = n
    }

    /** Switch the screen to the channel's refresh rate (25/50 fps → 50 Hz) while a stream plays. */
    private val _matchFrameRate = pref { prefs.getBoolean(KEY_MATCH_FRAME_RATE, true) }
    val matchFrameRate: StateFlow<Boolean> = _matchFrameRate.asStateFlow()

    fun setMatchFrameRate(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MATCH_FRAME_RATE, enabled).apply()
        _matchFrameRate.value = enabled
    }

    /**
     * Seconds behind the live edge for live streams; 0 leaves it to the player (three HLS segments
     * back, ~30 s with 10 s segments). Lower is closer to live but risks freezes on slow panels.
     */
    private val _liveOffsetSeconds = pref {
        prefs.getInt(KEY_LIVE_OFFSET_SECONDS, 0).coerceIn(0, MAX_LIVE_OFFSET_SECONDS)
    }
    val liveOffsetSeconds: StateFlow<Int> = _liveOffsetSeconds.asStateFlow()

    fun setLiveOffsetSeconds(seconds: Int) {
        val n = seconds.coerceIn(0, MAX_LIVE_OFFSET_SECONDS)
        prefs.edit().putInt(KEY_LIVE_OFFSET_SECONDS, n).apply()
        _liveOffsetSeconds.value = n
    }

    /** Which automatic switches still apply to a source the viewer picked by hand. */
    private val _manualSourceMode = pref {
        prefs.getString(KEY_MANUAL_SOURCE_MODE, null)
            ?.let { saved -> runCatching { ManualSourceMode.valueOf(saved) }.getOrNull() }
            ?: ManualSourceMode.STUTTER_ONLY
    }
    val manualSourceMode: StateFlow<ManualSourceMode> = _manualSourceMode.asStateFlow()

    fun setManualSourceMode(mode: ManualSourceMode) {
        prefs.edit().putString(KEY_MANUAL_SOURCE_MODE, mode.name).apply()
        _manualSourceMode.value = mode
    }

    /** Start each channel on the feed last picked by hand for it, before the preferred order. */
    private val _rememberPicks = pref { prefs.getBoolean(KEY_REMEMBER_PICKS, true) }
    val rememberPicks: StateFlow<Boolean> = _rememberPicks.asStateFlow()

    fun setRememberPicks(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMEMBER_PICKS, enabled).apply()
        _rememberPicks.value = enabled
    }

    /**
     * Feeds picked by hand: logical channel key → the provider stream id of the picked variant.
     * Only a hand pick writes here; automatic switches leave it alone. See [rememberPicks].
     */
    private val _pickedVariants = pref {
        readJson(KEY_PICKED_VARIANTS) { addonJson.decodeFromString<Map<String, String>>(it) } ?: emptyMap()
    }
    val pickedVariants: StateFlow<Map<String, String>> = _pickedVariants.asStateFlow()

    fun setPickedVariant(channelKey: String, streamId: String) {
        if (channelKey.isBlank() || streamId.isBlank()) return
        if (_pickedVariants.value[channelKey] == streamId) return
        val next = _pickedVariants.value + (channelKey to streamId)
        prefs.edit().putString(KEY_PICKED_VARIANTS, addonJson.encodeToString(next)).apply()
        _pickedVariants.value = next
    }

    fun clearPickedVariants() {
        prefs.edit().remove(KEY_PICKED_VARIANTS).apply()
        _pickedVariants.value = emptyMap()
    }

    /** Freeze wait in milliseconds, in [FALLBACK_STEP_MILLIS] steps; migrates the old whole-seconds pref. */
    private val _stallFallbackMillis = pref {
        prefs.getInt(
            KEY_STALL_FALLBACK_MILLIS,
            prefs.getInt(KEY_STALL_FALLBACK_SECONDS, DEFAULT_STALL_SECONDS) * 1_000,
        ).coerceIn(MIN_FALLBACK_MILLIS, MAX_FALLBACK_MILLIS)
    }
    val stallFallbackMillis: StateFlow<Int> = _stallFallbackMillis.asStateFlow()

    fun setStallFallbackMillis(millis: Int) {
        val ms = millis.coerceIn(MIN_FALLBACK_MILLIS, MAX_FALLBACK_MILLIS)
        prefs.edit().putInt(KEY_STALL_FALLBACK_MILLIS, ms).apply()
        _stallFallbackMillis.value = ms
    }

    /** How long the player's control bar stays up after the last key, in whole seconds. */
    private val _controlsTimeoutSeconds = pref {
        prefs.getInt(KEY_CONTROLS_TIMEOUT, DEFAULT_CONTROLS_TIMEOUT_SECONDS)
            .coerceIn(MIN_CONTROLS_TIMEOUT_SECONDS, MAX_CONTROLS_TIMEOUT_SECONDS)
    }
    val controlsTimeoutSeconds: StateFlow<Int> = _controlsTimeoutSeconds.asStateFlow()

    fun setControlsTimeoutSeconds(seconds: Int) {
        val s = seconds.coerceIn(MIN_CONTROLS_TIMEOUT_SECONDS, MAX_CONTROLS_TIMEOUT_SECONDS)
        prefs.edit().putInt(KEY_CONTROLS_TIMEOUT, s).apply()
        _controlsTimeoutSeconds.value = s
    }

    /**
     * Whole-UI zoom in percent, so a TV viewed from afar can fit more rows. 100% is
     * [UI_SCALE_BASE] of the system density; the 0.14.5 pref (100% = system density) is migrated.
     */
    private val _uiScalePercent = pref {
        (if (prefs.contains(KEY_UI_SCALE)) prefs.getInt(KEY_UI_SCALE, 100)
        else prefs.getInt(KEY_UI_SCALE_V1, 80).let { Math.round(it / UI_SCALE_BASE / UI_SCALE_STEP) * UI_SCALE_STEP })
            .coerceIn(MIN_UI_SCALE, MAX_UI_SCALE)
    }
    val uiScalePercent: StateFlow<Int> = _uiScalePercent.asStateFlow()

    fun setUiScalePercent(percent: Int) {
        val p = percent.coerceIn(MIN_UI_SCALE, MAX_UI_SCALE)
        prefs.edit().putInt(KEY_UI_SCALE, p).apply()
        _uiScalePercent.value = p
    }

    // ---- Recording behaviour -----------------------------------------------------------------

    /** Minutes to start a scheduled recording early, so a late start isn't clipped. */
    private val _recordPadStartMinutes = pref { prefs.getInt(KEY_PAD_START, 1) }
    val recordPadStartMinutes: StateFlow<Int> = _recordPadStartMinutes.asStateFlow()

    /** Minutes to keep recording past the listed end, so an overrun (sport/news) isn't cut off. */
    private val _recordPadEndMinutes = pref { prefs.getInt(KEY_PAD_END, 5) }
    val recordPadEndMinutes: StateFlow<Int> = _recordPadEndMinutes.asStateFlow()

    fun setRecordPadding(startMinutes: Int, endMinutes: Int) {
        val s = startMinutes.coerceIn(0, 30)
        val e = endMinutes.coerceIn(0, 60)
        prefs.edit().putInt(KEY_PAD_START, s).putInt(KEY_PAD_END, e).apply()
        _recordPadStartMinutes.value = s
        _recordPadEndMinutes.value = e
    }

    /**
     * When a scheduled recording starts, switch the box to it (playing from the growing file, so no
     * extra provider connection). Essential on a single-connection provider, where recording one
     * channel while another plays gets one of them cut. Default on.
     */
    private val _recordAutoSwitch = pref { prefs.getBoolean(KEY_REC_AUTOSWITCH, true) }
    val recordAutoSwitch: StateFlow<Boolean> = _recordAutoSwitch.asStateFlow()

    fun setRecordAutoSwitch(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REC_AUTOSWITCH, enabled).apply()
        _recordAutoSwitch.value = enabled
    }

    /**
     * Pause & rewind live TV via a rolling on-disk buffer of the current channel. Experimental and
     * heavier on weak boxes, so it's opt-in (off by default); when off, playback is unchanged.
     */
    private val _livePauseEnabled = pref { prefs.getBoolean(KEY_LIVE_PAUSE, false) }
    val livePauseEnabled: StateFlow<Boolean> = _livePauseEnabled.asStateFlow()

    fun setLivePauseEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LIVE_PAUSE, enabled).apply()
        _livePauseEnabled.value = enabled
    }

    // ---- Content types -----------------------------------------------------------------------

    /**
     * Which content types the user wants synced and shown. Turning one off skips fetching that
     * type's catalogue on the next sync (the speed-up) and hides its tab; already-synced rows are
     * left in place, so turning it back on and refreshing brings everything straight back.
     */
    private val _liveEnabled = pref { prefs.getBoolean(KEY_CONTENT_LIVE, true) }
    val liveEnabled: StateFlow<Boolean> = _liveEnabled.asStateFlow()

    private val _moviesEnabled = pref { prefs.getBoolean(KEY_CONTENT_MOVIES, true) }
    val moviesEnabled: StateFlow<Boolean> = _moviesEnabled.asStateFlow()

    private val _seriesEnabled = pref { prefs.getBoolean(KEY_CONTENT_SERIES, true) }
    val seriesEnabled: StateFlow<Boolean> = _seriesEnabled.asStateFlow()

    fun setLiveEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CONTENT_LIVE, enabled).apply()
        _liveEnabled.value = enabled
    }

    fun setMoviesEnabled(enabled: Boolean) {
        // Also reset the VOD freshness stamp so the next Movies/Shows open re-syncs immediately
        // rather than waiting out the cache TTL — turning a content type on should show it now.
        prefs.edit()
            .putBoolean(KEY_CONTENT_MOVIES, enabled)
            .putLong(KEY_VOD_SYNCED_AT, 0L)
            .apply()
        _moviesEnabled.value = enabled
    }

    fun setSeriesEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_CONTENT_SERIES, enabled)
            .putLong(KEY_VOD_SYNCED_AT, 0L)
            .apply()
        _seriesEnabled.value = enabled
    }

    /**
     * UI language override. Blank = follow the device; otherwise a BCP-47 tag ("en", "es").
     * Applied at [android.content.ContextWrapper.attachBaseContext] time so the whole app —
     * including notifications built off the app context — picks it up.
     */
    private val _languageTag = pref { prefs.getString(KEY_LANGUAGE, "").orEmpty() }
    val languageTag: StateFlow<String> = _languageTag.asStateFlow()

    fun setLanguageTag(tag: String) {
        prefs.edit().putString(KEY_LANGUAGE, tag).apply()
        _languageTag.value = tag
    }

    /** The last channel played, for boot-to-last-channel. Not a flow — only read once at launch. */
    var lastChannelId: Long
        get() = prefs.getLong(KEY_LAST_CHANNEL, 0L)
        set(value) { prefs.edit().putLong(KEY_LAST_CHANNEL, value).apply() }

    /**
     * Video scaling in the player, as an [androidx.media3.ui.AspectRatioFrameLayout] RESIZE_MODE_*
     * constant (0 = Fit). Persisted so the choice survives leaving the player, which testers asked
     * for — picking Fill every single time you open a channel gets old fast.
     */
    private val _playerResizeMode = pref { prefs.getInt(KEY_RESIZE_MODE, 0) }
    val playerResizeMode: StateFlow<Int> = _playerResizeMode.asStateFlow()

    fun setPlayerResizeMode(mode: Int) {
        prefs.edit().putInt(KEY_RESIZE_MODE, mode).apply()
        _playerResizeMode.value = mode
    }

    private fun readThemeMode(): ThemeMode =
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM)

    private fun readLogoSource(): LogoSource {
        prefs.getString(KEY_LOGO_SOURCE, null)?.let { saved ->
            runCatching { return LogoSource.valueOf(saved) }
        }
        // Before Settings › Logos: "Logos from the guide" on, or a logo pack set (it beat the provider's),
        // meant outside logos first. Anything else starts from the provider's alone.
        val externalFirst = prefs.getBoolean(KEY_PREFER_GUIDE_LOGOS, false) ||
            !prefs.getString(KEY_LOGO_PACK_URL, null).isNullOrBlank()
        return if (externalFirst) LogoSource.EXTERNAL_THEN_PROVIDER else LogoSource.PROVIDER
    }

    private fun readChannelLayout(): ChannelLayout =
        runCatching { ChannelLayout.valueOf(prefs.getString(KEY_CHANNEL_LAYOUT, null) ?: "") }
            .getOrDefault(ChannelLayout.FULLSCREEN)

    // ---- Recording ---------------------------------------------------------------------------

    /**
     * Where recordings are written: this box's internal storage, a NAS over SMB, or a plugged-in
     * USB / external drive addressed through the Storage Access Framework (a granted tree URI —
     * the only way to write removable storage on modern Android without a raw filesystem path).
     */
    enum class RecordingTarget { INTERNAL, SMB, USB }

    private val _recordingTarget = pref { readRecordingTarget() }
    val recordingTarget: StateFlow<RecordingTarget> = _recordingTarget.asStateFlow()

    /**
     * The SAF tree URI the user granted for USB recordings (a `content://` document-tree URI), or
     * null if none has been picked. Persisted as a string; a matching persistable permission is
     * taken when it is chosen, so the grant survives restarts.
     */
    private val _usbTreeUri = pref { prefs.getString(KEY_USB_TREE, null) }
    val usbTreeUri: StateFlow<String?> = _usbTreeUri.asStateFlow()

    /** A friendly name for the chosen USB folder (e.g. the DocumentFile's name), for display. */
    private val _usbFolderLabel = pref { prefs.getString(KEY_USB_LABEL, null) }
    val usbFolderLabel: StateFlow<String?> = _usbFolderLabel.asStateFlow()

    /** Save (or clear, with null) the granted USB tree URI and its display label together. */
    fun setUsbTree(treeUri: String?, label: String?) {
        prefs.edit()
            .putString(KEY_USB_TREE, treeUri)
            .putString(KEY_USB_LABEL, label)
            .apply()
        _usbTreeUri.value = treeUri
        _usbFolderLabel.value = label
    }

    /** SMB / NAS connection. Stored on-device only, exactly like provider credentials. */
    private val _smbHost = pref { prefs.getString(KEY_SMB_HOST, "").orEmpty() }
    val smbHost: StateFlow<String> = _smbHost.asStateFlow()

    private val _smbShare = pref { prefs.getString(KEY_SMB_SHARE, "").orEmpty() }
    val smbShare: StateFlow<String> = _smbShare.asStateFlow()

    /** Sub-folder within the share, e.g. `Recordings`. Blank = share root. */
    private val _smbFolder = pref { prefs.getString(KEY_SMB_FOLDER, "OpenTV").orEmpty() }
    val smbFolder: StateFlow<String> = _smbFolder.asStateFlow()

    private val _smbUser = pref { prefs.getString(KEY_SMB_USER, "").orEmpty() }
    val smbUser: StateFlow<String> = _smbUser.asStateFlow()

    private val _smbPassword = pref { prefs.getString(KEY_SMB_PASS, "").orEmpty() }
    val smbPassword: StateFlow<String> = _smbPassword.asStateFlow()

    fun setRecordingTarget(target: RecordingTarget) {
        prefs.edit().putString(KEY_REC_TARGET, target.name).apply()
        _recordingTarget.value = target
    }

    fun setSmbConfig(host: String, share: String, folder: String, user: String, password: String) {
        prefs.edit()
            .putString(KEY_SMB_HOST, host.trim())
            .putString(KEY_SMB_SHARE, share.trim())
            .putString(KEY_SMB_FOLDER, folder.trim())
            .putString(KEY_SMB_USER, user)
            .putString(KEY_SMB_PASS, password)
            .apply()
        _smbHost.value = host.trim()
        _smbShare.value = share.trim()
        _smbFolder.value = folder.trim()
        _smbUser.value = user
        _smbPassword.value = password
    }

    private fun readRecordingTarget(): RecordingTarget =
        runCatching { RecordingTarget.valueOf(prefs.getString(KEY_REC_TARGET, null) ?: "") }
            .getOrDefault(RecordingTarget.INTERNAL)

    // ---- NAS ("cloud") sync ------------------------------------------------------------------

    /**
     * A stable, random id for this install. It names this device's bundle file in the NAS sync
     * folder, so every device writes its own file and reads the others'. Generated once, on first
     * read, then persisted — distinct per device without needing any hardware identifier.
     */
    val syncDeviceId: String
        get() = prefs.getString(KEY_SYNC_DEVICE_ID, null) ?: java.util.UUID.randomUUID().toString()
            .also { prefs.edit().putString(KEY_SYNC_DEVICE_ID, it).apply() }

    /** Whether to run a NAS sync automatically each time the app is opened. Off by default. */
    private val _nasAutoSync = pref { prefs.getBoolean(KEY_NAS_AUTO_SYNC, false) }
    val nasAutoSync: StateFlow<Boolean> = _nasAutoSync.asStateFlow()

    fun setNasAutoSync(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NAS_AUTO_SYNC, enabled).apply()
        _nasAutoSync.value = enabled
    }

    // ---- Catalogue freshness & TMDB ----------------------------------------------------------

    /**
     * When the VOD (movies + series) catalogue was last fetched from the provider, in epoch
     * millis; 0 = never. A provider's 40k-title VOD list is expensive to re-download and
     * re-write, so [app.opentv.ui.VodViewModel.ensureVodLoaded] uses this to skip the sync on a
     * warm launch and show the already-stored rows instantly — the fetch only runs on first load
     * or once this goes stale. Not a flow: it is read once when Movies/Shows is first opened.
     */
    var vodSyncedAtMillis: Long
        get() = prefs.getLong(KEY_VOD_SYNCED_AT, 0L)
        set(value) { prefs.edit().putLong(KEY_VOD_SYNCED_AT, value).apply() }

    /**
     * The user's own TMDB API key (v3 auth), stored on-device only exactly like provider
     * credentials, used to fill in artwork/metadata a provider left blank. Blank = the TMDB
     * fallback is off. Each user brings their own key, so no single key carries everyone's traffic.
     */
    private val _tmdbApiKey = pref { prefs.getString(KEY_TMDB_KEY, "").orEmpty() }
    val tmdbApiKey: StateFlow<String> = _tmdbApiKey.asStateFlow()

    fun setTmdbApiKey(key: String) {
        val trimmed = key.trim()
        prefs.edit().putString(KEY_TMDB_KEY, trimmed).apply()
        _tmdbApiKey.value = trimmed
    }

    // ---- Stremio add-ons ---------------------------------------------------------------------

    /**
     * The user's Stremio add-ons, stored on-device as JSON. OpenTV ships with none; each entry is a
     * manifest URL the user pasted (a debrid key, if any, is baked into that URL on the add-on's own
     * site — never entered here). Empty = the whole feature stays inert.
     */
    private val _stremioAddons = pref { readStremioAddons() }
    val stremioAddons: StateFlow<List<StremioAddon>> = _stremioAddons.asStateFlow()

    fun addStremioAddon(addon: StremioAddon) {
        val deduped = _stremioAddons.value.filterNot { it.manifestUrl.equals(addon.manifestUrl, ignoreCase = true) }
        persistStremioAddons(deduped + addon)
    }

    fun removeStremioAddon(manifestUrl: String) {
        persistStremioAddons(_stremioAddons.value.filterNot { it.manifestUrl == manifestUrl })
    }

    private fun persistStremioAddons(list: List<StremioAddon>) {
        prefs.edit().putString(KEY_STREMIO_ADDONS, addonJson.encodeToString(list)).apply()
        _stremioAddons.value = list
    }

    private fun readStremioAddons(): List<StremioAddon> =
        runCatching {
            prefs.getString(KEY_STREMIO_ADDONS, null)?.let { addonJson.decodeFromString<List<StremioAddon>>(it) }
        }.getOrNull() ?: emptyList()

    /** Channel merges the user confirmed (Settings › Channel grouping), applied at every normalisation. */
    private val _groupMerges = pref { readGroupMerges() }
    val groupMerges: StateFlow<List<GroupMerge>> = _groupMerges.asStateFlow()

    /** Suggested pairs the user answered "Ignore" to, as [app.opentv.data.parser.MergeSuggester.pairId]. */
    private val _ignoredMergePairs = pref { prefs.getStringSet(KEY_IGNORED_MERGE_PAIRS, emptySet())!!.toSet() }
    val ignoredMergePairs: StateFlow<Set<String>> = _ignoredMergePairs.asStateFlow()

    fun addGroupMerge(merge: GroupMerge) {
        // A row merged again replaces its old choice; rows that pointed at it follow it along.
        val kept = _groupMerges.value.filterNot { it.fromKey == merge.fromKey }
            .map { if (it.intoKey == merge.fromKey) it.copy(intoKey = merge.intoKey, intoName = merge.intoName) else it }
        persistGroupMerges(kept + merge)
    }

    fun removeGroupMerge(fromKey: String) = persistGroupMerges(_groupMerges.value.filterNot { it.fromKey == fromKey })

    fun ignoreMergePair(pairId: String) {
        val next = _ignoredMergePairs.value + pairId
        prefs.edit().putStringSet(KEY_IGNORED_MERGE_PAIRS, next).apply()
        _ignoredMergePairs.value = next
    }

    private fun persistGroupMerges(list: List<GroupMerge>) {
        prefs.edit().putString(KEY_GROUP_MERGES, addonJson.encodeToString(list)).apply()
        _groupMerges.value = list
    }

    private fun readGroupMerges(): List<GroupMerge> =
        runCatching {
            prefs.getString(KEY_GROUP_MERGES, null)?.let { addonJson.decodeFromString<List<GroupMerge>>(it) }
        }.getOrNull() ?: emptyList()

    // ---- Live layout (set from the phone page) ------------------------------------------------

    /** Live groups in the user's order, by group key; groups not in it follow in the provider's order. */
    private val _liveGroupOrder = pref { readJson(KEY_LIVE_GROUP_ORDER) { addonJson.decodeFromString<List<String>>(it) } ?: emptyList() }
    val liveGroupOrder: StateFlow<List<String>> = _liveGroupOrder.asStateFlow()

    fun setLiveGroupOrder(keys: List<String>) {
        val clean = keys.filter { it.isNotBlank() }.distinct()
        prefs.edit().putString(KEY_LIVE_GROUP_ORDER, addonJson.encodeToString(clean)).apply()
        _liveGroupOrder.value = clean
    }

    /** Channel order inside a live group: group key → channel row keys, top first. */
    private val _liveChannelOrder = pref {
        readJson(KEY_LIVE_CHANNEL_ORDER) { addonJson.decodeFromString<Map<String, List<String>>>(it) } ?: emptyMap()
    }
    val liveChannelOrder: StateFlow<Map<String, List<String>>> = _liveChannelOrder.asStateFlow()

    fun setLiveChannelOrder(groupKey: String, rowKeys: List<String>) {
        if (groupKey.isBlank()) return
        val clean = rowKeys.filter { it.isNotBlank() }.distinct()
        val next = if (clean.isEmpty()) _liveChannelOrder.value - groupKey else _liveChannelOrder.value + (groupKey to clean)
        prefs.edit().putString(KEY_LIVE_CHANNEL_ORDER, addonJson.encodeToString(next)).apply()
        _liveChannelOrder.value = next
    }

    /** Live groups folded into another by hand: group key → the group key it joins. */
    private val _categoryMerges = pref {
        readJson(KEY_CATEGORY_MERGES) { addonJson.decodeFromString<Map<String, String>>(it) } ?: emptyMap()
    }
    val categoryMerges: StateFlow<Map<String, String>> = _categoryMerges.asStateFlow()

    fun addCategoryMerge(fromKey: String, intoKey: String) {
        if (fromKey.isBlank() || intoKey.isBlank() || fromKey == intoKey) return
        // Groups that pointed at the one being merged follow it into its new home.
        val next = _categoryMerges.value.mapValues { (_, into) -> if (into == fromKey) intoKey else into } + (fromKey to intoKey)
        persistCategoryMerges(next)
    }

    fun removeCategoryMerge(fromKey: String) = persistCategoryMerges(_categoryMerges.value - fromKey)

    private fun persistCategoryMerges(map: Map<String, String>) {
        prefs.edit().putString(KEY_CATEGORY_MERGES, addonJson.encodeToString(map)).apply()
        _categoryMerges.value = map
    }

    private fun <T> readJson(key: String, decode: (String) -> T): T? =
        runCatching { prefs.getString(key, null)?.let(decode) }.getOrNull()

    // ---- Backup ------------------------------------------------------------------------------

    /**
     * Everything in Settings worth carrying to another box or a reinstall, as raw pref values. Left
     * out on purpose: per-device state (last channel/group, profile, sync id, VOD stamp), secrets
     * (NAS login, TMDB key, Stremio URLs that may carry a debrid key) and the recording target that
     * goes with them. Waits are written as the values in use, so a never-migrated pref travels right.
     */
    fun backupSettings(): Map<String, Any> {
        val all = prefs.all
        val out = LinkedHashMap<String, Any>()
        for (key in BACKUP_KEYS.keys) all[key]?.let { out[key] = it }
        out[KEY_QUALITY_FALLBACK_MILLIS] = _qualityFallbackMillis.value
        out[KEY_STALL_FALLBACK_MILLIS] = _stallFallbackMillis.value
        out[KEY_UI_SCALE] = _uiScalePercent.value
        return out
    }

    /**
     * Replaces the backed-up settings with [values] (a key missing from it goes back to its default)
     * and re-reads every flow, so the app follows at once. Values of the wrong type are skipped.
     */
    @Synchronized
    fun restoreSettings(values: Map<String, Any>) {
        val edit = prefs.edit()
        for ((key, kind) in BACKUP_KEYS) {
            val value = values[key]
            if (value == null) edit.remove(key) else SettingsBackup.put(edit, key, kind, value)
        }
        BACKUP_LEGACY_KEYS.forEach(edit::remove)
        edit.commit()
        reload()
    }

    /** Adds [merges] and [ignoredPairs] to the ones already here; a row this box already merged keeps its choice. */
    @Synchronized
    fun addMergeChoices(merges: List<GroupMerge>, ignoredPairs: Set<String>) {
        val known = _groupMerges.value.map { it.fromKey }.toSet()
        persistGroupMerges(_groupMerges.value + merges.filter { it.fromKey !in known && it.fromKey != it.intoKey })
        val ignored = _ignoredMergePairs.value + ignoredPairs
        prefs.edit().putStringSet(KEY_IGNORED_MERGE_PAIRS, ignored).apply()
        _ignoredMergePairs.value = ignored
    }

    /** The type of each backed-up pref, for [SettingsBackup.fromJson]. */
    val backupKinds: Map<String, SettingsBackup.Kind> get() = BACKUP_KEYS

    private fun reload() {
        reloaders.forEach { it() }
        publishCategoryRules()
    }

    companion object {
        private const val KEY_GROUP_MERGES = "group_merges"
        private const val KEY_IGNORED_MERGE_PAIRS = "ignored_merge_pairs"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_APP_STYLE = "app_style"
        private const val KEY_CHANNEL_LAYOUT = "channel_layout"
        private const val KEY_LAST_LIVE_GROUP = "last_live_group"
        private const val KEY_RECENT_LIVE_GROUPS = "recent_live_groups"
        private const val RECENT_LIVE_GROUPS_MAX = 3
        private const val KEY_SUBTITLES = "subtitles_enabled"
        private const val KEY_PREVIEW_VIDEO = "guide_preview_video"
        private const val KEY_PREFER_GUIDE_LOGOS = "prefer_guide_logos"
        private const val KEY_LOGO_SOURCE = "logo_source"
        private const val KEY_PREVIEW_SOUND = "guide_preview_sound"
        private const val KEY_PIN_HASH = "parental_pin_hash"
        private const val KEY_HIDDEN_CATS = "hidden_categories"
        private const val KEY_DISABLED_CATS = "disabled_categories"
        private const val KEY_FORCED_ON_CATS = "forced_on_categories"
        private const val KEY_ITALIAN_ONLY = "italian_only"
        private const val KEY_MERGE_CATEGORY_SOURCES = "merge_category_sources"
        private const val KEY_STRIP_CATEGORY_SOURCES = "strip_category_sources"
        private const val KEY_GROUP_BADGES = "group_badges"
        private const val KEY_GROUP_LOGOS_EXTERNAL = "group_logos_external"
        private const val KEY_SHOW_RECENT_GROUPS = "show_recent_groups"
        private const val KEY_FOREIGN_SPORT_OFF = "foreign_sport_off"
        private const val KEY_ACTIVE_PROFILE = "active_profile_id"
        private const val KEY_RESUME_LAST = "resume_last_channel"
        private const val KEY_CONTENT_LIVE = "content_live"
        private const val KEY_CONTENT_MOVIES = "content_movies"
        private const val KEY_CONTENT_SERIES = "content_series"
        private const val KEY_LAST_CHANNEL = "last_channel_id"
        private const val KEY_RESIZE_MODE = "player_resize_mode"
        private const val KEY_LANGUAGE = "language_tag"

        /**
         * Reads the saved language tag straight from prefs, for use in attachBaseContext before
         * the settings singleton (or anything else) is initialised. Blank = follow the device.
         */
        fun savedLanguageTag(context: Context): String =
            context.getSharedPreferences("opentv_settings", Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, "").orEmpty()
        private const val KEY_REC_TARGET = "recording_target"
        private const val KEY_SMB_HOST = "smb_host"
        private const val KEY_SMB_SHARE = "smb_share"
        private const val KEY_SMB_FOLDER = "smb_folder"
        private const val KEY_SMB_USER = "smb_user"
        private const val KEY_SMB_PASS = "smb_password"
        private const val KEY_USB_TREE = "usb_tree_uri"
        private const val KEY_USB_LABEL = "usb_folder_label"
        private const val KEY_SYNC_DEVICE_ID = "sync_device_id"
        private const val KEY_NAS_AUTO_SYNC = "nas_auto_sync"
        private const val KEY_VOD_SYNCED_AT = "vod_synced_at"
        private const val KEY_TMDB_KEY = "tmdb_api_key"
        private const val KEY_STREMIO_ADDONS = "stremio_addons"
        private const val KEY_PAD_START = "rec_pad_start_min"
        private const val KEY_PAD_END = "rec_pad_end_min"
        private const val KEY_REC_AUTOSWITCH = "rec_auto_switch"
        private const val KEY_LIVE_PAUSE = "live_pause_enabled"
        private const val KEY_PREFERRED_QUALITY = "preferred_quality"
        private const val KEY_PREFERRED_SOURCES = "preferred_sources"
        private const val KEY_QUALITY_FALLBACK = "quality_fallback"
        private const val KEY_MATCH_FRAME_RATE = "match_frame_rate"
        private const val KEY_QUALITY_FALLBACK_SECONDS = "quality_fallback_seconds"
        private const val KEY_QUALITY_FALLBACK_MILLIS = "quality_fallback_millis"
        private const val KEY_STALL_FALLBACK = "stall_fallback"
        private const val KEY_HICCUP_LIMIT = "hiccup_limit"
        private const val KEY_LIVE_OFFSET_SECONDS = "live_offset_seconds"
        private const val KEY_MANUAL_SOURCE_MODE = "manual_source_mode"
        private const val KEY_REMEMBER_PICKS = "remember_picks"
        private const val KEY_PICKED_VARIANTS = "picked_variants"
        private const val KEY_STALL_FALLBACK_SECONDS = "stall_fallback_seconds"
        private const val KEY_STALL_FALLBACK_MILLIS = "stall_fallback_millis"
        private const val KEY_UI_SCALE_V1 = "ui_scale_percent"
        private const val KEY_UI_SCALE = "ui_scale"
        private const val KEY_CONTROLS_TIMEOUT = "controls_timeout_seconds"
        private const val KEY_LIVE_GROUP_ORDER = "live_group_order"
        private const val KEY_LIVE_CHANNEL_ORDER = "live_channel_order"
        private const val KEY_CATEGORY_MERGES = "category_merges"
        private const val KEY_LOGO_PACK_URL = "logo_pack_url"
        private const val KEY_LOGO_PACK = "logo_pack"
        private const val KEY_CUSTOM_LOGOS = "custom_logos"

        /** What [backupSettings] carries, by pref type. See there for what is left out and why. */
        private val BACKUP_KEYS: Map<String, SettingsBackup.Kind> = buildMap {
            listOf(
                KEY_SUBTITLES, KEY_PREVIEW_VIDEO, KEY_PREVIEW_SOUND, KEY_ITALIAN_ONLY,
                KEY_MERGE_CATEGORY_SOURCES, KEY_STRIP_CATEGORY_SOURCES, KEY_GROUP_LOGOS_EXTERNAL, KEY_SHOW_RECENT_GROUPS, KEY_FOREIGN_SPORT_OFF, KEY_RESUME_LAST,
                KEY_CONTENT_LIVE, KEY_CONTENT_MOVIES, KEY_CONTENT_SERIES, KEY_REC_AUTOSWITCH, KEY_LIVE_PAUSE,
                KEY_QUALITY_FALLBACK, KEY_STALL_FALLBACK, KEY_MATCH_FRAME_RATE,
                KEY_REMEMBER_PICKS,
                // The old logo switches: a restore of an older backup still migrates from them.
                KEY_PREFER_GUIDE_LOGOS, KEY_GROUP_BADGES,
            ).forEach { put(it, SettingsBackup.Kind.BOOL) }
            listOf(
                KEY_RESIZE_MODE, KEY_PAD_START, KEY_PAD_END, KEY_QUALITY_FALLBACK_MILLIS,
                KEY_STALL_FALLBACK_MILLIS, KEY_UI_SCALE, KEY_CONTROLS_TIMEOUT, KEY_HICCUP_LIMIT,
                KEY_LIVE_OFFSET_SECONDS,
            ).forEach { put(it, SettingsBackup.Kind.INT) }
            listOf(
                KEY_THEME, KEY_APP_STYLE, KEY_CHANNEL_LAYOUT, KEY_PIN_HASH, KEY_LANGUAGE, KEY_PREFERRED_QUALITY,
                KEY_LIVE_GROUP_ORDER, KEY_LIVE_CHANNEL_ORDER, KEY_CATEGORY_MERGES, KEY_LOGO_PACK_URL, KEY_LOGO_SOURCE,
                KEY_CUSTOM_LOGOS, KEY_MANUAL_SOURCE_MODE, KEY_PREFERRED_SOURCES, KEY_PICKED_VARIANTS,
            ).forEach { put(it, SettingsBackup.Kind.STRING) }
            listOf(KEY_HIDDEN_CATS, KEY_DISABLED_CATS, KEY_FORCED_ON_CATS).forEach { put(it, SettingsBackup.Kind.SET) }
        }

        /** Pre-migration keys a restore clears, so they can't shadow a default the backup relies on. */
        private val BACKUP_LEGACY_KEYS =
            listOf(KEY_QUALITY_FALLBACK_SECONDS, KEY_STALL_FALLBACK_SECONDS, KEY_UI_SCALE_V1)

        const val MIN_UI_SCALE = 60
        const val MAX_UI_SCALE = 130
        const val UI_SCALE_STEP = 5
        /** Density multiplier at 100%: the stock layout is roomier than a TV list needs. */
        const val UI_SCALE_BASE = 0.8f

        const val DEFAULT_CONTROLS_TIMEOUT_SECONDS = 5

        const val MIN_CONTROLS_TIMEOUT_SECONDS = 1

        const val MAX_CONTROLS_TIMEOUT_SECONDS = 10

        const val MIN_FALLBACK_MILLIS = 1_000
        const val MAX_FALLBACK_MILLIS = 10_000
        const val FALLBACK_STEP_MILLIS = 500
        private const val DEFAULT_FALLBACK_SECONDS = 4
        private const val DEFAULT_STALL_SECONDS = 3
        const val DEFAULT_HICCUP_LIMIT = 4
        const val MAX_HICCUP_LIMIT = 10
        const val MAX_LIVE_OFFSET_SECONDS = 60
        const val LIVE_OFFSET_STEP_SECONDS = 5

        @Volatile private var instance: AppSettings? = null

        fun get(context: Context): AppSettings =
            instance ?: synchronized(this) {
                instance ?: AppSettings(context).also { instance = it }
            }
    }
}

/**
 * A source picked by hand (Quality button, its list, or ☰) and the automatic switches:
 * [STAY] none at all; [STUTTER_ONLY] gives it all the time it needs to start and rides out a
 * single freeze, but still leaves if it keeps stuttering; [AUTO] treats it like any other.
 */
enum class ManualSourceMode { STAY, STUTTER_ONLY, AUTO }
