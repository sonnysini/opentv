/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.football

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * What "Watch now" found out about each feed, kept per feed rather than per match: a scan cut
 * short picks up where it stopped, two games sharing a channel look at it once, and leaving the
 * football home (or the app) loses nothing. Kept for [TTL_MILLIS] — streams come and go before a
 * game — on disk with a small picture of each feed.
 */
class ScanStore private constructor(private val dir: File) {
    /** A look at one feed: [probe] null when it gave nothing. */
    data class Entry(val at: Long, val probe: AutoScan.Probe?)

    private val entries = ConcurrentHashMap<Long, Entry>()
    private val file = File(dir, "scans.json")
    private val frames = File(dir, "frames")

    init {
        load()
    }

    /** The feed's look when still fresh. */
    fun fresh(feedId: Long, now: Long = System.currentTimeMillis()): Entry? =
        entries[feedId]?.takeIf { now - it.at < TTL_MILLIS }

    fun put(feedId: Long, probe: AutoScan.Probe?, preview: Bitmap?) {
        entries[feedId] = Entry(System.currentTimeMillis(), probe)
        val f = frameFile(feedId)
        if (preview != null) {
            runCatching {
                frames.mkdirs()
                f.outputStream().use { preview.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            }
            preview.recycle()
        } else {
            f.delete()
        }
        save()
    }

    /** The feed's picture from its last look, if fresh and one was decoded. */
    fun frame(feedId: Long): File? = frameFile(feedId).takeIf { fresh(feedId) != null && it.exists() }

    /** Drops [feedIds]' looks: "Scan again". */
    fun forget(feedIds: Collection<Long>) {
        feedIds.forEach { entries.remove(it); frameFile(it).delete() }
        save()
    }

    private fun frameFile(feedId: Long) = File(frames, "$feedId.jpg")

    @Synchronized
    private fun save() {
        val now = System.currentTimeMillis()
        val arr = JSONArray()
        entries.forEach { (id, e) ->
            if (now - e.at >= TTL_MILLIS) return@forEach
            arr.put(
                JSONObject().apply {
                    put("id", id)
                    put("at", e.at)
                    e.probe?.let { p ->
                        put("h", p.height)
                        p.fps?.let { put("fps", it) }
                        p.audioChannels?.let { put("ch", it) }
                        if (p.audioHd) put("eac3", true)
                        put("ms", p.millis)
                        p.board?.let { put("board", JSONArray(it.codes)) }
                    }
                },
            )
        }
        runCatching {
            dir.mkdirs()
            val tmp = File(dir, "scans.json.tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(file)
        }.onFailure { Log.w(TAG, "save failed", it) }
    }

    private fun load() {
        val now = System.currentTimeMillis()
        runCatching {
            if (!file.exists()) return
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val at = o.getLong("at")
                if (now - at >= TTL_MILLIS) continue
                val probe = if (o.has("h")) {
                    AutoScan.Probe(
                        height = o.getInt("h"),
                        fps = if (o.has("fps")) o.getInt("fps") else null,
                        audioChannels = if (o.has("ch")) o.getInt("ch") else null,
                        millis = o.getLong("ms"),
                        board = o.optJSONArray("board")?.let { b -> AutoScan.Board((0 until b.length()).map { b.getString(it) }) },
                        audioHd = o.optBoolean("eac3"),
                    )
                } else {
                    null
                }
                entries[o.getLong("id")] = Entry(at, probe)
            }
        }.onFailure { Log.w(TAG, "load failed", it) }
        // Pictures of looks gone stale.
        frames.listFiles()?.forEach { f ->
            val id = f.nameWithoutExtension.toLongOrNull()
            if (id == null || entries[id] == null) f.delete()
        }
    }

    companion object {
        private const val TAG = "ScanStore"
        const val TTL_MILLIS = 20 * 60_000L

        @Volatile private var instance: ScanStore? = null

        fun get(context: Context): ScanStore = instance ?: synchronized(this) {
            instance ?: ScanStore(File(context.applicationContext.filesDir, "football_scans")).also { instance = it }
        }
    }
}
