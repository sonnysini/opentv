/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.repo

import android.content.Context
import android.util.Log
import app.opentv.core.AppSettings
import app.opentv.data.db.EpgChannelAliasDao
import app.opentv.data.parser.ChannelCountry
import app.opentv.data.parser.ChannelDictionary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeToSequence
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream

/**
 * The [ChannelDictionary] for the home country: the public channel database's names for that
 * country (downloaded by the app itself, weekly, kept on the device — there is no server of ours)
 * plus the channel names of the free guides the user has switched on.
 *
 * The download is read as a stream and only the home country's names are kept (a few hundred
 * lines out of ~30k channels). Offline or before the first download: the last copy, else the one
 * bundled for Italy, else guides only.
 */
class ChannelNamesRepository(
    private val context: Context,
    private val http: OkHttpClient,
    private val aliasDao: EpgChannelAliasDao,
    private val settings: AppSettings,
) {
    private val dir: File get() = File(context.filesDir, DIR)

    @Volatile
    private var cached: Pair<String, ChannelDictionary>? = null

    fun home(): String = settings.effectiveHomeCountry()

    /** The dictionary for [home], rebuilt only when its inputs moved. */
    suspend fun dictionary(home: String = home()): ChannelDictionary = withContext(Dispatchers.IO) {
        val file = fileFor(home)
        val guides = runCatching { aliasDao.freeGuideNames() }.getOrDefault(emptyList())
            .filter { g -> ChannelCountry.ofGuideId(g.epgId)?.let { ChannelCountry.isHome(it, home) } == true }
            .map { it.displayName }
        val stamp = "$home|${file.lastModified()}|${guides.size}|${guides.hashCode()}"
        cached?.takeIf { it.first == stamp }?.let { return@withContext it.second }
        ChannelDictionary.of(officialNames(home, file), guides).also { cached = stamp to it }
    }

    /**
     * Downloads [home]'s names when the copy on the device is older than a week (or missing, or
     * [force]). True when the names changed: the caller regroups.
     */
    suspend fun refreshIfStale(nowMillis: Long, force: Boolean = false, home: String = home()): Boolean =
        withContext(Dispatchers.IO) {
            val file = fileFor(home)
            if (!force && file.exists() && nowMillis - file.lastModified() < REFRESH_MILLIS) return@withContext false
            try {
                val names = http.newCall(Request.Builder().url(URL).build()).execute().use { response ->
                    if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                    val body = response.body ?: throw IllegalStateException("empty body")
                    parseNames(body.byteStream(), home)
                }
                if (names.isEmpty()) return@withContext false
                val text = names.joinToString("\n")
                val before = if (file.exists()) file.readText() else null
                dir.mkdirs()
                val tmp = File(dir, "${file.name}.tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(file)) {
                    file.writeText(text)
                    tmp.delete()
                }
                file.setLastModified(nowMillis)
                Log.i(TAG, "Channel names for $home: ${names.size}")
                before != text
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Channel names for $home not refreshed: ${e.message}")
                false
            }
        }

    private fun fileFor(home: String) = File(dir, "$home.txt")

    private fun officialNames(home: String, file: File): List<String> {
        if (file.exists()) runCatching { return file.readLines().filter { it.isNotBlank() } }
        return runCatching {
            context.assets.open("$DIR/$home.txt").bufferedReader().use { r -> r.readLines().filter { it.isNotBlank() } }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val TAG = "ChannelNames"
        private const val DIR = "channel_names"
        private const val URL = "https://iptv-org.github.io/api/channels.json"
        private const val REFRESH_MILLIS = 7L * 24 * 60 * 60 * 1000

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /**
         * [home]'s channel names (name, then alternative names) from the database's `channels.json`,
         * read element by element so the whole file is never in memory.
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun parseNames(input: InputStream, home: String): List<String> {
            val out = LinkedHashSet<String>()
            val elements = json.decodeToSequence(input.buffered(), JsonElement.serializer(), DecodeSequenceMode.ARRAY_WRAPPED)
            for (element in elements) {
                val o = element as? JsonObject ?: continue
                val country = (o["country"] as? JsonPrimitive)?.content ?: continue
                if (!ChannelCountry.isHome(ChannelCountry.canon(country), home)) continue
                (o["name"] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }?.let(out::add)
                (o["alt_names"] as? JsonArray)?.forEach { alt ->
                    (alt as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }?.let(out::add)
                }
            }
            return out.toList()
        }
    }
}
