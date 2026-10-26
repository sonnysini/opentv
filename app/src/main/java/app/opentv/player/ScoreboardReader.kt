/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.player

import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Text in a picture, on the device: ML Kit's bundled recogniser (no Google Play services, which
 * Fire TV hasn't got). Used on the scoreboard corner of a frame; a few hundred ms on a Fire Stick.
 */
class ScoreboardReader {
    private var recognizer: TextRecognizer? = null

    /** The text read, lines joined by spaces; null when the recogniser isn't available or fails. */
    suspend fun read(bitmap: Bitmap): String? = withContext(Dispatchers.Default) {
        runCatching {
            val r = recognizer ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).also { recognizer = it }
            val result = Tasks.await(r.process(InputImage.fromBitmap(bitmap, 0)), TIMEOUT_SECONDS, TimeUnit.SECONDS)
            result.textBlocks.flatMap { b -> b.lines.map { it.text } }.joinToString(" ")
        }.getOrElse {
            // Includes UnsatisfiedLinkError on a device whose ABI the library wasn't shipped for.
            Log.w(TAG, "OCR failed", it)
            null
        }
    }

    fun close() {
        runCatching { recognizer?.close() }
        recognizer = null
    }

    private companion object {
        const val TAG = "ScoreboardReader"
        const val TIMEOUT_SECONDS = 5L
    }
}
