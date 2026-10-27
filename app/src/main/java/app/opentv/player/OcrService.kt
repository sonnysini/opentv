/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.player

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

/**
 * Tesseract, in a process of its own (":ocr" in the manifest): native code that goes wrong on
 * some device takes this process down, never the app — the scan just carries on without reading
 * scoreboards. [ScoreboardReader] is the other end.
 *
 * One call: [READ] with width, height and a [ScoreboardMask] (1 = ink); replies with the text.
 */
class OcrService : Service() {
    private var api: TessBaseAPI? = null

    private val binder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != READ) return super.onTransact(code, data, reply, flags)
            val w = data.readInt()
            val h = data.readInt()
            val ink = data.createByteArray() ?: return false
            reply?.writeString(synchronized(this@OcrService) { read(w, h, ink) })
            return true
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    private fun read(w: Int, h: Int, ink: ByteArray): String {
        val t = api ?: open().also { api = it }
        val px = IntArray(w * h) { if (ink[it].toInt() != 0) BLACK else WHITE }
        val mask = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        // Twice the size reads better: the letters are small.
        val big = Bitmap.createScaledBitmap(mask, w * 2, h * 2, true)
        mask.recycle()
        t.setImage(big)
        val text = t.utF8Text.orEmpty()
        t.clear()
        big.recycle()
        return text.replace(Regex("\\s+"), " ").trim()
    }

    private fun open(): TessBaseAPI {
        val dir = File(noBackupFilesDir, "tess")
        val data = File(dir, "tessdata/$LANG.traineddata")
        if (!data.exists()) {
            data.parentFile!!.mkdirs()
            val tmp = File(data.path + ".tmp")
            assets.open("tessdata/$LANG.traineddata").use { i -> tmp.outputStream().use { i.copyTo(it) } }
            tmp.renameTo(data)
        }
        return TessBaseAPI().apply {
            check(init(dir.path, LANG)) { "Tesseract init failed" }
            pageSegMode = TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT
            setVariable("tessedit_char_whitelist", "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789:-")
            Log.i(TAG, "Tesseract ready")
        }
    }

    override fun onDestroy() {
        synchronized(this) { api?.recycle(); api = null }
        super.onDestroy()
    }

    companion object {
        const val READ = IBinder.FIRST_CALL_TRANSACTION
        private const val TAG = "OcrService"
        private const val LANG = "eng"
        private const val BLACK = 0xFF000000.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
    }
}
