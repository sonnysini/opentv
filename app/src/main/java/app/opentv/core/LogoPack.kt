/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.core

import app.opentv.data.parser.ChannelNameNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * A logo pack: the user's own set of logos (team crests, say) that beats the provider's and the
 * guide's on the channels it names. The app ships no logos — the pack lives wherever the user
 * hosts it, as an `index.json` next to the images:
 *
 * ```
 * {"logos": {"Milan": "milan.svg", "AC Milan": "milan.svg", "Inter": "inter.svg"}}
 * ```
 *
 * Names go through [ChannelNameNormalizer.groupKeyOf], so they match the way channels group;
 * paths resolve against the index URL. A DAZN team feed (`DAZN SERIE A 3 MILAN`) takes its
 * team's logo even when the list has no bare `Milan` channel to fold it into.
 *
 * [custom] rides along: logos picked by hand for single channels, group key → URL.
 */
class LogoPack(val byKey: Map<String, String>, val custom: Map<String, String> = emptyMap()) {
    /** Every logo URL in the pack, so the UI can tell a pack logo from a provider's. */
    val urls: Set<String> = byKey.values.toSet()

    /**
     * The logo for a channel with this group key, or null: one the user picked for that exact
     * channel (channel menu › Change logo) first, then the pack's.
     */
    fun logoFor(groupKey: String): String? {
        if (groupKey.isEmpty()) return null
        custom[groupKey]?.let { return it }
        if (byKey.isEmpty()) return null
        val base = groupKey.substringBefore('+')
        return byKey[base] ?: ChannelNameNormalizer.teamOf(base)?.let(byKey::get)
    }

    @Serializable
    private class Index(val logos: Map<String, String> = emptyMap())

    companion object {
        val EMPTY = LogoPack(emptyMap())

        private val json = Json { ignoreUnknownKeys = true }

        /** group key → absolute logo URL, from an `index.json` body fetched from [indexUrl]. */
        fun parse(indexUrl: String, body: String): Map<String, String> {
            val base = indexUrl.toHttpUrlOrNull() ?: return emptyMap()
            return json.decodeFromString(Index.serializer(), body).logos.mapNotNull { (name, path) ->
                val key = ChannelNameNormalizer.groupKeyOf(name)
                val url = base.resolve(path.trim())?.toString()
                if (key.isEmpty() || url == null) null else key to url
            }.toMap()
        }

        /**
         * Downloads the pack at [AppSettings.logoPackUrl] and stores it; returns how many names it
         * holds. Throws on failure, leaving the stored pack untouched.
         */
        suspend fun refresh(settings: AppSettings, http: OkHttpClient): Int {
            val url = settings.logoPackUrl.value
            if (url.isEmpty()) return 0
            val byKey = fetch(http, url)
            settings.setLogoPack(byKey)
            return byKey.size
        }

        /** Downloads and parses the pack at [indexUrl]. Throws on network or format errors. */
        suspend fun fetch(http: OkHttpClient, indexUrl: String): Map<String, String> = withContext(Dispatchers.IO) {
            val request = Request.Builder().url(indexUrl).build()
            http.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                parse(indexUrl, response.body?.string().orEmpty())
            }
        }
    }
}
