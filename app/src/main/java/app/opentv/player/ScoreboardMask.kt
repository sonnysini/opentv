/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.player

/**
 * The scoreboard corner cleaned up for OCR: only light, letter-sized blobs sitting in a row with
 * another like them are kept — the team codes. The boxes they sit on, logos, the crowd and the
 * dark-on-white bits (clock, score) go. Without this Tesseract misreads mixed boards like DAZN's
 * (white on blue next to dark on white).
 */
object ScoreboardMask {
    /**
     * [argb] is the [width]×[height] corner of a frame [frameHeight] lines tall (sizes are relative
     * to it). Returns 1 for ink, 0 for paper; null when nothing letter-like is there.
     */
    fun letters(argb: IntArray, width: Int, height: Int, frameHeight: Int): ByteArray? {
        val n = width * height
        val on = BooleanArray(n) { i ->
            val p = argb[i]
            ((p shr 16 and 255) * 299 + (p shr 8 and 255) * 587 + (p and 255) * 114) / 1000 > LIGHT
        }
        val seen = BooleanArray(n)
        val stack = IntArray(n)
        val comps = ArrayList<Blob>()
        for (i in 0 until n) {
            if (!on[i] || seen[i]) continue
            var sp = 0
            stack[sp++] = i
            seen[i] = true
            val pts = ArrayList<Int>()
            var y0 = height; var y1 = -1; var x0 = width; var x1 = -1
            while (sp > 0) {
                val p = stack[--sp]
                pts.add(p)
                val y = p / width
                val x = p % width
                if (y < y0) y0 = y
                if (y > y1) y1 = y
                if (x < x0) x0 = x
                if (x > x1) x1 = x
                if (x > 0 && on[p - 1] && !seen[p - 1]) { seen[p - 1] = true; stack[sp++] = p - 1 }
                if (x < width - 1 && on[p + 1] && !seen[p + 1]) { seen[p + 1] = true; stack[sp++] = p + 1 }
                if (y > 0 && on[p - width] && !seen[p - width]) { seen[p - width] = true; stack[sp++] = p - width }
                if (y < height - 1 && on[p + width] && !seen[p + width]) { seen[p + width] = true; stack[sp++] = p + width }
            }
            val h = y1 - y0 + 1
            val w = x1 - x0 + 1
            if (h >= MIN_H * frameHeight && h <= MAX_H * frameHeight && w <= MAX_W * frameHeight && pts.size >= MIN_FILL * h * w) {
                comps += Blob(y0, y1, x0, x1, pts.toIntArray())
            }
        }
        val out = ByteArray(n)
        var any = false
        for (a in comps) {
            val ah = a.y1 - a.y0 + 1
            val ac = (a.y0 + a.y1) / 2.0
            val inRow = comps.any { b ->
                if (b === a) return@any false
                val bh = b.y1 - b.y0 + 1
                val gap = maxOf(b.x0 - a.x1, a.x0 - b.x1)
                Math.abs(bh - ah) <= ROW_TOLERANCE * ah &&
                    Math.abs((b.y0 + b.y1) / 2.0 - ac) <= ROW_TOLERANCE * ah &&
                    gap <= MAX_GAP * ah
            }
            if (inRow) {
                any = true
                for (p in a.pts) out[p] = 1
            }
        }
        return if (any) out else null
    }

    private class Blob(val y0: Int, val y1: Int, val x0: Int, val x1: Int, val pts: IntArray)

    private const val LIGHT = 160
    // A scoreboard letter is 1.2–4 % of the frame's height.
    private const val MIN_H = 0.012
    private const val MAX_H = 0.04
    private const val MAX_W = 0.04
    private const val MIN_FILL = 0.15
    private const val ROW_TOLERANCE = 0.3
    private const val MAX_GAP = 1.2
}
