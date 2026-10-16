/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.remote

import java.io.FilterInputStream
import java.io.InputStream

/**
 * Caps how fast [upstream] is read, so a background catalogue download leaves bandwidth for the
 * live stream the user is watching. Reading slower makes TCP back off, so the cap really applies
 * on the wire, not just in the app. A non-positive [maxBytesPerSecond] passes reads straight
 * through. Blocking by design: only use it on an IO thread.
 */
class ThrottledInputStream(
    upstream: InputStream,
    private val maxBytesPerSecond: Long,
) : FilterInputStream(upstream) {

    private val startNanos = System.nanoTime()
    private var bytesRead = 0L

    override fun read(): Int {
        val b = super.read()
        if (b >= 0) account(1)
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        // Small reads keep the pacing smooth instead of one big burst then a long sleep.
        val chunk = if (maxBytesPerSecond > 0) minOf(len, MAX_CHUNK) else len
        val n = super.read(b, off, chunk)
        if (n > 0) account(n)
        return n
    }

    private fun account(n: Int) {
        if (maxBytesPerSecond <= 0) return
        bytesRead += n
        val expectedNanos = bytesRead * 1_000_000_000L / maxBytesPerSecond
        val aheadMillis = (expectedNanos - (System.nanoTime() - startNanos)) / 1_000_000L
        if (aheadMillis > 0) Thread.sleep(aheadMillis)
    }

    private companion object {
        const val MAX_CHUNK = 16 * 1024
    }
}
