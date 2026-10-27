/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.player

import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * A quick look at a live stream without playing it: a second or so of video off the wire, then
 * what it really is — resolution, frames per second (counted from the timestamps, provider labels
 * lie), audio channels — and one picture for reading the scoreboard.
 *
 * HLS: the newest segment of the playlist (a master playlist's richest variant). MPEG-TS: the head
 * of the endless stream. Either way at most [MAX_BYTES], and the connection is closed straight
 * after, so the account's one connection is free for the next stream.
 */
class StreamProbe(http: OkHttpClient, private val dir: File) {
    data class Result(
        val width: Int,
        val height: Int,
        val fps: Int?,
        val audioChannels: Int?,
        /** An E-AC3 (Dolby Digital Plus) audio track. */
        val audioHd: Boolean = false,
        /** Time to the video in hand, the stream's start-up cost. */
        val millis: Long,
        /** The top-left corner of a frame, where the scoreboard is. Null when no frame decoded. */
        val corner: Bitmap?,
        /** The same frame small, for showing what the feed is on. */
        val preview: Bitmap? = null,
    )

    private val http = http.newBuilder()
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /** Null when the stream gave no video in time. */
    suspend fun probe(url: String, userAgent: String): Result? = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val file = File(dir, "probe.ts")
        runCatching {
            if (!fetch(url, userAgent, file, depth = 0)) return@runCatching null
            val fetchedAt = System.currentTimeMillis()
            val info = inspect(file) ?: return@runCatching null
            val (corner, preview) = frame(file)
            Result(
                width = info.width,
                height = info.height,
                fps = info.fps,
                audioChannels = info.audioChannels,
                audioHd = info.audioHd,
                millis = fetchedAt - t0,
                corner = corner,
                preview = preview,
            )
        }.getOrElse { Log.w(TAG, "probe failed", it); null }.also { file.delete() }
    }

    /** Writes a piece of video to [out]; follows playlists down to a segment. */
    private fun fetch(url: String, userAgent: String, out: File, depth: Int): Boolean {
        if (depth > 2) return false
        val req = Request.Builder().url(url).header("User-Agent", userAgent).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return false
            val body = r.body ?: return false
            val peek = body.source().peek()
            val head = if (peek.request(7)) peek.readUtf8(7) else ""
            if (head == "#EXTM3U") {
                val src = body.source()
                src.request(PLAYLIST_BYTES)
                val text = src.buffer.readUtf8(minOf(src.buffer.size, PLAYLIST_BYTES))
                val base = r.request.url
                val next = nextUri(text) ?: return false
                val resolved = base.resolve(next)?.toString() ?: next.toHttpUrlOrNull()?.toString() ?: return false
                return fetch(resolved, userAgent, out, depth + 1)
            }
            out.outputStream().use { o ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                val input = body.byteStream()
                while (total < MAX_BYTES) {
                    val n = input.read(buf)
                    if (n < 0) break
                    o.write(buf, 0, n)
                    total += n
                }
            }
            return out.length() > MIN_BYTES
        }
    }

    /** A master playlist's richest variant, or a media playlist's newest segment. */
    private fun nextUri(playlist: String): String? {
        val lines = playlist.lines().map { it.trim() }
        if (lines.any { it.startsWith("#EXT-X-STREAM-INF") }) {
            var best: Pair<Long, String>? = null
            lines.forEachIndexed { i, l ->
                if (l.startsWith("#EXT-X-STREAM-INF")) {
                    val bw = Regex("BANDWIDTH=(\\d+)").find(l)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    val uri = lines.drop(i + 1).firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: return@forEachIndexed
                    if (best == null || bw > best!!.first) best = bw to uri
                }
            }
            return best?.second
        }
        return lines.lastOrNull { it.isNotEmpty() && !it.startsWith("#") }
    }

    private data class Info(val width: Int, val height: Int, val fps: Int?, val audioChannels: Int?, val audioHd: Boolean)

    private fun inspect(file: File): Info? {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(file.path)
            var video = -1
            var width = 0
            var height = 0
            var audio: Int? = null
            var audioHd = false
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/") && video < 0) {
                    video = i
                    width = f.intOr(MediaFormat.KEY_WIDTH)
                    height = f.intOr(MediaFormat.KEY_HEIGHT)
                } else if (mime.startsWith("audio/")) {
                    val ch = f.intOr(MediaFormat.KEY_CHANNEL_COUNT)
                    if (ch > (audio ?: 0)) audio = ch
                    if (mime.startsWith(MediaFormat.MIMETYPE_AUDIO_EAC3)) audioHd = true
                }
            }
            if (video < 0 || height == 0) return null
            ex.selectTrack(video)
            val times = ArrayList<Long>()
            while (times.size < FPS_SAMPLES) {
                val t = ex.sampleTime
                if (t < 0) break
                times += t
                if (!ex.advance()) break
            }
            return Info(width, height, fpsOf(times), audio, audioHd)
        } finally {
            ex.release()
        }
    }

    /** The scoreboard corner of a frame and the frame shrunk to [PREVIEW_WIDTH]; nulls when none decoded. */
    private fun frame(file: File): Pair<Bitmap?, Bitmap?> {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(file.path)
            val frame = mmr.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null to null
            val corner = Bitmap.createBitmap(frame, 0, 0, frame.width * 4 / 10, frame.height * 15 / 100)
            val preview = Bitmap.createScaledBitmap(frame, PREVIEW_WIDTH, PREVIEW_WIDTH * frame.height / frame.width, true)
            if (corner !== frame && preview !== frame) frame.recycle()
            corner to preview
        } catch (t: Throwable) {
            Log.w(TAG, "no frame", t)
            null to null
        } finally {
            runCatching { mmr.release() }
        }
    }

    private fun MediaFormat.intOr(key: String): Int = if (containsKey(key)) getInteger(key) else 0

    companion object {
        private const val TAG = "StreamProbe"
        private const val CALL_TIMEOUT_SECONDS = 3L
        private const val MAX_BYTES = 1_500_000L
        private const val MIN_BYTES = 50_000L
        private const val PLAYLIST_BYTES = 256_000L
        private const val FPS_SAMPLES = 40
        private const val PREVIEW_WIDTH = 480

        /** Frames per second from sample timestamps (decode order, so sorted first). */
        fun fpsOf(timesUs: List<Long>): Int? {
            val sorted = timesUs.distinct().sorted()
            val gaps = sorted.zipWithNext { a, b -> b - a }.filter { it > 0 }.sorted()
            if (gaps.size < 4) return null
            val median = gaps[gaps.size / 2]
            return Math.round(1_000_000.0 / median).toInt()
        }
    }
}
