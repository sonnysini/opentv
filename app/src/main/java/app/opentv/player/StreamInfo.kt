/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Turns what the player knows about the stream on screen into short, Kodi-style labels:
 * `1080p` `HEVC` `50fps` `5.1` `HDR10`. Pure so the rules can be unit-tested.
 */
object StreamInfo {

    /** The usual name for a picture size; a letterboxed 1920×800 or anamorphic 1440×1080 is still 1080p. */
    fun resolutionLabel(width: Int, height: Int): String? {
        if (width <= 0 || height <= 0) return null
        val lines = max(height, (width * 9 + 8) / 16)
        return when {
            lines >= 2000 -> "4K"
            lines >= 1300 -> "1440p"
            lines >= 1000 -> "1080p"
            lines >= 700 -> "720p"
            lines >= 560 -> "576p"
            lines >= 470 -> "480p"
            else -> "${height}p"
        }
    }

    fun videoCodecLabel(mime: String?): String? = when (mime) {
        null -> null
        MimeTypes.VIDEO_H264 -> "H.264"
        MimeTypes.VIDEO_H265 -> "HEVC"
        MimeTypes.VIDEO_MPEG2 -> "MPEG-2"
        MimeTypes.VIDEO_AV1 -> "AV1"
        MimeTypes.VIDEO_VP9 -> "VP9"
        MimeTypes.VIDEO_VP8 -> "VP8"
        MimeTypes.VIDEO_DOLBY_VISION -> "Dolby Vision"
        MimeTypes.VIDEO_MP4V -> "MPEG-4"
        MimeTypes.VIDEO_H263 -> "H.263"
        MimeTypes.VIDEO_VC1 -> "VC-1"
        else -> mime.substringAfter('/').uppercase(Locale.ROOT)
    }

    fun audioCodecLabel(mime: String?): String? = when (mime) {
        null -> null
        MimeTypes.AUDIO_AAC -> "AAC"
        MimeTypes.AUDIO_AC3 -> "AC3"
        MimeTypes.AUDIO_E_AC3 -> "E-AC3"
        MimeTypes.AUDIO_E_AC3_JOC -> "E-AC3 Atmos"
        MimeTypes.AUDIO_AC4 -> "AC4"
        MimeTypes.AUDIO_MPEG -> "MP3"
        MimeTypes.AUDIO_MPEG_L2 -> "MP2"
        MimeTypes.AUDIO_OPUS -> "Opus"
        MimeTypes.AUDIO_VORBIS -> "Vorbis"
        MimeTypes.AUDIO_FLAC -> "FLAC"
        MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_EXPRESS -> "DTS"
        MimeTypes.AUDIO_DTS_HD -> "DTS-HD"
        MimeTypes.AUDIO_TRUEHD -> "TrueHD"
        MimeTypes.AUDIO_RAW -> "PCM"
        else -> mime.substringAfter('/').uppercase(Locale.ROOT)
    }

    fun channelsLabel(count: Int): String? = when {
        count <= 0 -> null
        count == 1 -> "Mono"
        count == 2 -> "2.0"
        count == 3 -> "2.1"
        count == 6 -> "5.1"
        count == 8 -> "7.1"
        else -> "${count}ch"
    }

    /** "HDR10", "HLG" or "Dolby Vision"; null for an ordinary SDR picture. */
    fun hdrLabel(colorTransfer: Int, videoMime: String?): String? = when {
        videoMime == MimeTypes.VIDEO_DOLBY_VISION -> "Dolby Vision"
        colorTransfer == C.COLOR_TRANSFER_ST2084 -> "HDR10"
        colorTransfer == C.COLOR_TRANSFER_HLG -> "HLG"
        else -> null
    }

    private val STANDARD_RATES = floatArrayOf(23.976f, 24f, 25f, 29.97f, 30f, 48f, 50f, 59.94f, 60f)

    /** A measured frame rate snapped to the broadcast rate it is close to (within 3%), or as is. */
    fun snapFrameRate(fps: Float): Float {
        if (fps <= 0f) return fps
        val near = STANDARD_RATES.minBy { abs(it - fps) }
        return if (abs(near - fps) / near <= 0.03f) near else fps
    }

    /** `25`, `29.97`, `50`. */
    fun frameRateLabel(fps: Float): String? {
        if (fps <= 0f) return null
        val rounded = fps.roundToInt()
        return if (abs(fps - rounded) < 0.01f) "$rounded" else String.format(Locale.ROOT, "%.2f", fps)
    }

    /** `8.4 Mb/s`, `850 kb/s`. */
    fun bitrateLabel(bitsPerSecond: Long): String? = when {
        bitsPerSecond <= 0 -> null
        bitsPerSecond >= 1_000_000 -> String.format(Locale.ROOT, "%.1f Mb/s", bitsPerSecond / 1_000_000.0)
        else -> "${(bitsPerSecond / 1000.0).roundToInt()} kb/s"
    }

    /** `850 KB`, `12.4 MB`, `1.2 GB`. */
    fun sizeLabel(bytes: Long): String? = when {
        bytes <= 0 -> null
        bytes >= 1_000_000_000 -> String.format(Locale.ROOT, "%.1f GB", bytes / 1e9)
        bytes >= 1_000_000 -> String.format(Locale.ROOT, "%.1f MB", bytes / 1e6)
        else -> "${(bytes / 1000.0).roundToInt()} KB"
    }

    /** `48 kHz`, `44.1 kHz`. */
    fun sampleRateLabel(hz: Int): String? {
        if (hz <= 0) return null
        val khz = hz / 1000.0
        return if (hz % 1000 == 0) "${hz / 1000} kHz" else String.format(Locale.ROOT, "%.1f kHz", khz)
    }

    /** Google's and AOSP's own codecs run on the CPU; anything else is the chip's hardware decoder. */
    fun isSoftwareDecoder(name: String): Boolean {
        val n = name.lowercase(Locale.ROOT)
        return SOFTWARE_DECODER_PREFIXES.any { n.startsWith(it) }
    }

    private val SOFTWARE_DECODER_PREFIXES = listOf(
        "omx.google.", "c2.android.", "c2.google.", "ffmpeg", "libgav1", "libvpx", "libopus", "libflac",
    )

    /** One look at the stream, already turned into labels. Any field is null while still unknown. */
    data class Snapshot(
        val resolution: String?,
        val size: String?,
        val videoCodec: String?,
        val frameRate: String?,
        val hdr: String?,
        val videoBitrate: String?,
        val audioCodec: String?,
        val channels: String?,
        val sampleRate: String?,
        val audioBitrate: String?,
        val audioLanguage: String?,
        val container: String?,
        val videoDecoder: String?,
        val videoDecoderSoftware: Boolean,
        val audioDecoder: String?,
        val renderedFrames: Long,
        val droppedFrames: Int,
        val download: String?,
        val downloaded: String?,
        val bufferedSeconds: Float,
        val liveDelaySeconds: Float?,
    ) {
        /** The short Kodi-style row for the bar: `1080p` `HEVC` `50fps` `HDR10` `AC3 5.1`. */
        val badges: List<String>
            get() = listOfNotNull(
                resolution,
                videoCodec,
                frameRate?.let { "${it}fps" },
                hdr,
                listOfNotNull(audioCodec, channels).joinToString(" ").ifEmpty { null },
            )
    }

    /**
     * Turns [PlayerController.Stats] taken about once a second into a [Snapshot]. Frame rate and
     * download speed are measured from what moved between two samples: live MPEG-TS rarely
     * declares either.
     */
    @OptIn(UnstableApi::class)
    class Meter {
        private class Point(val at: Long, val frames: Long, val bytes: Long)

        // The last few seconds of samples: HLS arrives a segment (6–10 s of video) at a time, so a
        // short window would often see nothing downloaded at all.
        private val window = ArrayDeque<Point>()
        private var fps = 0f
        private var bitsPerSecond = 0L

        fun sample(nowMillis: Long, stats: PlayerController.Stats): Snapshot {
            val last = window.lastOrNull()
            // The counters restart with a new stream: a step backwards means "start over".
            if (last != null && (stats.renderedFrames < last.frames || stats.bytesLoaded < last.bytes)) window.clear()
            window.addLast(Point(nowMillis, stats.renderedFrames, stats.bytesLoaded))
            while (window.size > 2 && nowMillis - window[1].at >= WINDOW_MILLIS) window.removeFirst()
            val first = window.first()
            val dt = nowMillis - first.at
            if (dt >= MIN_SPAN_MILLIS) {
                val frames = stats.renderedFrames - first.frames
                val bytes = stats.bytesLoaded - first.bytes
                fps = if (frames > 0) snapFrameRate(frames * 1000f / dt) else 0f
                bitsPerSecond = if (bytes > 0) bytes * 8_000 / dt else 0
            } else {
                fps = 0f
                bitsPerSecond = 0
            }
            return snapshot(stats)
        }

        private fun snapshot(stats: PlayerController.Stats): Snapshot {
            val v = stats.video
            val a = stats.audio
            val rate = v?.frameRate?.takeIf { it > 0f }?.let { snapFrameRate(it) } ?: fps.takeIf { it > 0f }
            return Snapshot(
                resolution = resolutionLabel(stats.width, stats.height),
                size = if (stats.width > 0 && stats.height > 0) "${stats.width}×${stats.height}" else null,
                videoCodec = videoCodecLabel(v?.sampleMimeType),
                frameRate = rate?.let { frameRateLabel(it) },
                hdr = v?.let { hdrLabel(it.colorInfo?.colorTransfer ?: Format.NO_VALUE, it.sampleMimeType) },
                videoBitrate = v?.let { bitrateLabel(it.bitrate.toLong()) },
                audioCodec = audioCodecLabel(a?.sampleMimeType),
                channels = a?.let { channelsLabel(it.channelCount) },
                sampleRate = a?.let { sampleRateLabel(it.sampleRate) },
                audioBitrate = a?.let { bitrateLabel(it.bitrate.toLong()) },
                audioLanguage = a?.language?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED },
                container = containerLabel(stats.streamMime ?: v?.containerMimeType ?: a?.containerMimeType),
                videoDecoder = stats.videoDecoder,
                videoDecoderSoftware = stats.videoDecoder?.let { isSoftwareDecoder(it) } ?: false,
                audioDecoder = stats.audioDecoder,
                renderedFrames = stats.renderedFrames,
                droppedFrames = stats.droppedFrames,
                download = bitrateLabel(bitsPerSecond),
                downloaded = sizeLabel(stats.bytesLoaded),
                bufferedSeconds = stats.bufferedMillis / 1000f,
                liveDelaySeconds = stats.liveOffsetMillis.takeIf { it != C.TIME_UNSET && it >= 0 }?.let { it / 1000f },
            )
        }

        private companion object {
            const val MIN_SPAN_MILLIS = 500L
            const val WINDOW_MILLIS = 15_000L
        }
    }

    fun containerLabel(mime: String?): String? = when (mime) {
        null -> null
        MimeTypes.VIDEO_MP2T -> "MPEG-TS"
        MimeTypes.APPLICATION_M3U8 -> "HLS"
        MimeTypes.APPLICATION_MPD -> "DASH"
        MimeTypes.VIDEO_MP4, MimeTypes.AUDIO_MP4, MimeTypes.APPLICATION_MP4 -> "MP4"
        MimeTypes.VIDEO_MATROSKA, MimeTypes.AUDIO_MATROSKA, MimeTypes.APPLICATION_MATROSKA -> "MKV"
        else -> mime.substringAfter('/').uppercase(Locale.ROOT)
    }
}
