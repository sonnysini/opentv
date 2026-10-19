/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import app.opentv.player.PlayerController
import app.opentv.player.StreamInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The Kodi-style labels for the stream on screen, and the frame rate / speed measured between samples. */
class StreamInfoTest {

    @Test
    fun `resolution names the usual picture sizes`() {
        assertThat(StreamInfo.resolutionLabel(1920, 1080)).isEqualTo("1080p")
        assertThat(StreamInfo.resolutionLabel(1920, 800)).isEqualTo("1080p")
        assertThat(StreamInfo.resolutionLabel(1440, 1080)).isEqualTo("1080p")
        assertThat(StreamInfo.resolutionLabel(1280, 720)).isEqualTo("720p")
        assertThat(StreamInfo.resolutionLabel(720, 576)).isEqualTo("576p")
        assertThat(StreamInfo.resolutionLabel(3840, 2160)).isEqualTo("4K")
        assertThat(StreamInfo.resolutionLabel(0, 0)).isNull()
    }

    @Test
    fun `frame rate snaps to broadcast rates`() {
        assertThat(StreamInfo.snapFrameRate(49.2f)).isEqualTo(50f)
        assertThat(StreamInfo.snapFrameRate(29.9f)).isEqualTo(29.97f)
        assertThat(StreamInfo.snapFrameRate(40f)).isEqualTo(40f)
        assertThat(StreamInfo.frameRateLabel(25f)).isEqualTo("25")
        assertThat(StreamInfo.frameRateLabel(29.97f)).isEqualTo("29.97")
    }

    @Test
    fun `software decoders are told apart from hardware ones`() {
        assertThat(StreamInfo.isSoftwareDecoder("OMX.google.h264.decoder")).isTrue()
        assertThat(StreamInfo.isSoftwareDecoder("c2.android.avc.decoder")).isTrue()
        assertThat(StreamInfo.isSoftwareDecoder("OMX.MTK.VIDEO.DECODER.AVC")).isFalse()
    }

    @Test
    fun `meter measures frame rate and download between samples`() {
        val meter = StreamInfo.Meter()
        val video = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H265)
            .setContainerMimeType(MimeTypes.VIDEO_MP2T)
            .setWidth(1920).setHeight(1080)
            .build()
        val audio = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AC3).setChannelCount(6).setSampleRate(48_000).build()

        val first = meter.sample(0, stats(video, audio, frames = 0, bytes = 0))
        assertThat(first.frameRate).isNull()
        assertThat(first.badges).containsExactly("1080p", "HEVC", "AC3 5.1").inOrder()
        assertThat(first.container).isEqualTo("MPEG-TS")

        val second = meter.sample(1_000, stats(video, audio, frames = 50, bytes = 1_000_000))
        assertThat(second.frameRate).isEqualTo("50")
        assertThat(second.download).isEqualTo("8.0 Mb/s")
        assertThat(second.badges).containsExactly("1080p", "HEVC", "50fps", "AC3 5.1").inOrder()
    }

    @Test
    fun `a counter that restarts does not give a negative rate`() {
        val meter = StreamInfo.Meter()
        val video = Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).setWidth(1280).setHeight(720).build()
        meter.sample(0, stats(video, null, frames = 500, bytes = 5_000_000))
        val after = meter.sample(1_000, stats(video, null, frames = 10, bytes = 100))
        assertThat(after.frameRate).isNull()
        assertThat(after.download).isNull()
    }

    @Test
    fun `download is averaged so segment-at-a-time streams still show a speed`() {
        val meter = StreamInfo.Meter()
        meter.sample(0, stats(null, null, frames = 0, bytes = 0))
        meter.sample(1_000, stats(null, null, frames = 0, bytes = 2_000_000))
        // Nothing new this second (between HLS segments), but the window still has the segment.
        val quiet = meter.sample(2_000, stats(null, null, frames = 0, bytes = 2_000_000))
        assertThat(quiet.download).isEqualTo("8.0 Mb/s")
        assertThat(quiet.downloaded).isEqualTo("2.0 MB")
    }

    @Test
    fun `hdr comes from the colour transfer`() {
        val hdr = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H265)
            .setWidth(3840).setHeight(2160)
            .setColorInfo(
                ColorInfo.Builder()
                    .setColorSpace(C.COLOR_SPACE_BT2020)
                    .setColorRange(C.COLOR_RANGE_LIMITED)
                    .setColorTransfer(C.COLOR_TRANSFER_ST2084)
                    .build(),
            )
            .build()
        val snap = StreamInfo.Meter().sample(0, stats(hdr, null, frames = 0, bytes = 0))
        assertThat(snap.badges).containsExactly("4K", "HEVC", "HDR10").inOrder()
    }

    private fun stats(video: Format?, audio: Format?, frames: Long, bytes: Long) = PlayerController.Stats(
        video = video,
        audio = audio,
        width = video?.width ?: 0,
        height = video?.height ?: 0,
        videoDecoder = null,
        audioDecoder = null,
        renderedFrames = frames,
        droppedFrames = 0,
        bufferedMillis = 0,
        liveOffsetMillis = C.TIME_UNSET,
        bytesLoaded = bytes,
        streamMime = null,
    )
}
