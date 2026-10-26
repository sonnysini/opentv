/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.remote

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File

/**
 * Turns a YouTube video id into a URL our own player can open, so highlights play inside the app
 * (and Back comes straight back) instead of handing off to the YouTube app.
 *
 * Extraction is NewPipeExtractor's. YouTube changes its player every so often and breaks
 * extractors until the library catches up, so every failure here returns null and the caller
 * falls back to the YouTube app.
 */
class YouTubeStreams(private val http: OkHttpClient, private val cacheDir: File) {

    @Volatile private var ready = false

    private fun init() {
        if (!ready) synchronized(this) {
            if (!ready) { NewPipe.init(OkHttpDownloader(http)); ready = true }
        }
    }

    /**
     * The latest uploads of [channelId] as (video id, title), newest first, [pages] pages of
     * about twenty. Unlike the channel's RSS feed (fifteen videos) this reaches back weeks.
     * Empty when YouTube can't be read now.
     */
    suspend fun uploads(channelId: String, pages: Int): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            init()
            val channel = ChannelInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/channel/$channelId")
            val tab = channel.tabs.firstOrNull { ChannelTabs.VIDEOS in it.contentFilters } ?: return@withContext emptyList()
            val first = ChannelTabInfo.getInfo(ServiceList.YouTube, tab)
            val items = ArrayList<InfoItem>(first.relatedItems)
            var next = first.nextPage
            repeat(pages - 1) {
                val page = next ?: return@repeat
                val more = ChannelTabInfo.getMoreItems(ServiceList.YouTube, tab, page)
                items += more.items
                next = more.nextPage
            }
            items.filterIsInstance<StreamInfoItem>().mapNotNull { v ->
                val id = v.url.substringAfter("v=", "").substringBefore('&').ifEmpty { null } ?: return@mapNotNull null
                id to v.name
            }
        } catch (e: Throwable) {
            Log.w(TAG, "no uploads for $channelId", e)
            emptyList()
        }
    }

    /**
     * The best stream for [videoId]: YouTube's adaptive HLS manifest (all qualities, the player
     * picks) cut down to H.264, else the single muxed MP4. Null when YouTube can't be read now.
     */
    suspend fun resolve(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            init()
            val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
            info.hlsUrl.takeIf { it.isNotBlank() }?.let { h264Only(it) }
                ?: info.videoStreams.maxByOrNull { it.height }?.content?.takeIf { it.isNotBlank() }
        } catch (e: Throwable) {
            // Throwable, not Exception: a stale extractor can trip over YouTube's player script in
            // ways that surface as errors from the bundled JavaScript engine.
            Log.w(TAG, "no stream for $videoId", e)
            null
        }
    }

    /**
     * YouTube's HLS manifest also offers VP9, which TV boxes claim to decode and then don't (the
     * Fire TV's MediaTek decoder fails outright). Keep only the H.264 variants, in a local copy of
     * the manifest — its playlist URLs are absolute, so it plays from a file. Falls back to the
     * original when there's no H.264 to keep.
     */
    private fun h264Only(masterUrl: String): String {
        val master = http.newCall(okhttp3.Request.Builder().url(masterUrl).header("User-Agent", USER_AGENT).build())
            .execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return masterUrl
        val kept = StringBuilder()
        var variants = 0
        var skipNext = false
        for (line in master.lineSequence()) {
            when {
                skipNext -> skipNext = false
                line.startsWith("#EXT-X-STREAM-INF") ->
                    if (isH264(line)) { kept.appendLine(line); variants++ } else skipNext = true
                line.startsWith("#EXT-X-I-FRAME-STREAM-INF") -> if (isH264(line)) kept.appendLine(line)
                else -> kept.appendLine(line)
            }
        }
        if (variants == 0) return masterUrl
        val file = File(cacheDir, "youtube.m3u8")
        file.writeText(kept.toString())
        return Uri.fromFile(file).toString()
    }

    private fun isH264(streamInf: String): Boolean =
        streamInf.substringAfter("CODECS=\"", "").substringBefore('"').startsWith("avc1")

    private class OkHttpDownloader(private val http: OkHttpClient) : Downloader() {
        override fun execute(request: Request): Response {
            val builder = okhttp3.Request.Builder()
                .url(request.url())
                .method(request.httpMethod(), request.dataToSend()?.toRequestBody())
                .header("User-Agent", USER_AGENT)
            request.headers().forEach { (name, values) ->
                builder.removeHeader(name)
                values.forEach { builder.addHeader(name, it) }
            }
            http.newCall(builder.build()).execute().use { res ->
                return Response(
                    res.code,
                    res.message,
                    res.headers.toMultimap(),
                    res.body?.string(),
                    res.request.url.toString(),
                )
            }
        }
    }

    private companion object {
        const val TAG = "YouTubeStreams"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
    }
}
