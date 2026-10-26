/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Text in the scoreboard corner of a frame: [ScoreboardMask] here, Tesseract in [OcrService]'s own
 * process. About a tenth of a second a picture on a Fire Stick. If that process dies the reader
 * gives up for the rest of its life and every read is null — the scan goes on on quality alone.
 */
class ScoreboardReader(private val context: Context) {
    private var connection: ServiceConnection? = null
    private var binder: IBinder? = null
    private var broken = false

    /** The text read; "" when the corner has nothing letter-like; null when OCR isn't available. */
    suspend fun read(corner: Bitmap): String? {
        if (broken) return null
        val ink = withContext(Dispatchers.Default) {
            val w = corner.width
            val h = corner.height
            val px = IntArray(w * h)
            corner.getPixels(px, 0, w, 0, 0, w, h)
            // The corner is the top 15 % of the frame.
            ScoreboardMask.letters(px, w, h, frameHeight = h * 100 / CORNER_PERCENT)
        } ?: return ""
        val b = connect() ?: return null
        return withContext(Dispatchers.IO) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInt(corner.width)
                data.writeInt(corner.height)
                data.writeByteArray(ink)
                b.transact(OcrService.READ, data, reply, 0)
                reply.readString()
            } catch (t: Throwable) {
                // DeadObjectException: the OCR process crashed.
                Log.w(TAG, "OCR process gone, reading no more scoreboards", t)
                broken = true
                null
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
    }

    private suspend fun connect(): IBinder? {
        binder?.let { return it }
        val ready = CompletableDeferred<IBinder?>()
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                binder = service
                ready.complete(service)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                binder = null
                broken = true
                ready.complete(null)
            }
        }
        connection = conn
        val bound = runCatching { context.bindService(Intent(context, OcrService::class.java), conn, Context.BIND_AUTO_CREATE) }
            .getOrDefault(false)
        if (!bound) {
            broken = true
            return null
        }
        return withTimeoutOrNull(CONNECT_TIMEOUT_MILLIS) { ready.await() }.also { if (it == null) broken = true }
    }

    fun close() {
        connection?.let { runCatching { context.unbindService(it) } }
        connection = null
        binder = null
    }

    private companion object {
        const val TAG = "ScoreboardReader"
        const val CORNER_PERCENT = 15
        const val CONNECT_TIMEOUT_MILLIS = 5_000L
    }
}
