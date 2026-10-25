/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * Turns a YouTube video id into a URL our own player can open, so highlights play inside the app
 * (and Back comes straight back) instead of handing off to the YouTube app.
 *
 * Extraction is NewPipeExtractor's. YouTube changes its player every so often and breaks
 * extractors until the library catches up, so every failure here returns null and the caller
 * falls back to the YouTube app.
 */
class YouTubeStreams(private val http: OkHttpClient) {

    @Volatile private var ready = false

    /**
     * The best stream for [videoId]: YouTube's adaptive HLS manifest (all qualities, the player
     * picks), else the single muxed MP4. Null when YouTube can't be read right now.
     */
    suspend fun resolve(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            if (!ready) synchronized(this@YouTubeStreams) {
                if (!ready) { NewPipe.init(OkHttpDownloader(http)); ready = true }
            }
            val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
            info.hlsUrl.takeIf { it.isNotBlank() }
                ?: info.videoStreams.maxByOrNull { it.height }?.content?.takeIf { it.isNotBlank() }
        } catch (e: Throwable) {
            // Throwable, not Exception: a stale extractor can trip over YouTube's player script in
            // ways that surface as errors from the bundled JavaScript engine.
            Log.w(TAG, "no stream for $videoId", e)
            null
        }
    }

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
