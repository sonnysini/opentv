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
 * What "Watch now" found out about each feed of each match: a scan cut short picks up where it
 * stopped, and leaving the football home (or the app) loses nothing. A match's looks hold for the
 * whole game ([TTL_MILLIS]) — only "Scan again" looks afresh — on disk with a small picture of each
 * feed, along with the feed last watched for the match ([picked]), which goes first from then on.
 */
class ScanStore private constructor(private val dir: File) {
    /** A look at one feed: [probe] null when it gave nothing. */
    data class Entry(val at: Long, val probe: AutoScan.Probe?)

    private data class Key(val matchId: Long, val feedId: Long)

    private val entries = ConcurrentHashMap<Key, Entry>()
    /** Per match: the feed last watched, and when. */
    private val picks = ConcurrentHashMap<Long, Pair<Long, Long>>()
    private val file = File(dir, "scans.json")
    private val frames = File(dir, "frames")

    init {
        load()
    }

    /** The feed's look for the match, while it holds. */
    fun fresh(matchId: Long, feedId: Long, now: Long = System.currentTimeMillis()): Entry? =
        entries[Key(matchId, feedId)]?.takeIf { now - it.at < TTL_MILLIS }

    fun put(matchId: Long, feedId: Long, probe: AutoScan.Probe?, preview: Bitmap?) {
        entries[Key(matchId, feedId)] = Entry(System.currentTimeMillis(), probe)
        val f = frameFile(matchId, feedId)
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
    fun frame(matchId: Long, feedId: Long): File? =
        frameFile(matchId, feedId).takeIf { fresh(matchId, feedId) != null && it.exists() }

    /** Drops [feedIds]' looks for the match: "Scan again". The feed watched stays first. */
    fun forget(matchId: Long, feedIds: Collection<Long>) {
        feedIds.forEach { entries.remove(Key(matchId, it)); frameFile(matchId, it).delete() }
        save()
    }

    /** The feed last watched for the match, while it holds. */
    fun picked(matchId: Long, now: Long = System.currentTimeMillis()): Long? =
        picks[matchId]?.takeIf { now - it.second < TTL_MILLIS }?.first

    fun pick(matchId: Long, feedId: Long) {
        if (picks[matchId]?.first == feedId) return
        picks[matchId] = feedId to System.currentTimeMillis()
        save()
    }

    private fun frameFile(matchId: Long, feedId: Long) = File(frames, "${matchId}_$feedId.jpg")

    @Synchronized
    private fun save() {
        val now = System.currentTimeMillis()
        val arr = JSONArray()
        entries.forEach { (k, e) ->
            if (now - e.at >= TTL_MILLIS) return@forEach
            arr.put(
                JSONObject().apply {
                    put("m", k.matchId)
                    put("id", k.feedId)
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
        val picked = JSONArray()
        picks.forEach { (m, p) ->
            if (now - p.second < TTL_MILLIS) picked.put(JSONObject().put("m", m).put("id", p.first).put("at", p.second))
        }
        runCatching {
            dir.mkdirs()
            val tmp = File(dir, "scans.json.tmp")
            tmp.writeText(JSONObject().put("looks", arr).put("picks", picked).toString())
            tmp.renameTo(file)
        }.onFailure { Log.w(TAG, "save failed", it) }
    }

    private fun load() {
        val now = System.currentTimeMillis()
        runCatching {
            if (!file.exists()) return
            val text = file.readText()
            // Before 0.31 the file was a bare array of per-feed looks: nothing to keep from it.
            if (!text.trimStart().startsWith("{")) return
            val root = JSONObject(text)
            val arr = root.optJSONArray("looks") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val at = o.getLong("at")
                if (now - at >= TTL_MILLIS || !o.has("m")) continue
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
                entries[Key(o.getLong("m"), o.getLong("id"))] = Entry(at, probe)
            }
            val picked = root.optJSONArray("picks") ?: JSONArray()
            for (i in 0 until picked.length()) {
                val o = picked.getJSONObject(i)
                val at = o.getLong("at")
                if (now - at < TTL_MILLIS) picks[o.getLong("m")] = o.getLong("id") to at
            }
        }.onFailure { Log.w(TAG, "load failed", it) }
        // Pictures of looks gone stale.
        frames.listFiles()?.forEach { f ->
            val parts = f.nameWithoutExtension.split('_')
            val key = if (parts.size == 2) parts[0].toLongOrNull()?.let { m -> parts[1].toLongOrNull()?.let { Key(m, it) } } else null
            if (key == null || entries[key] == null) f.delete()
        }
    }

    companion object {
        private const val TAG = "ScanStore"
        /** Longer than any game, extra time and a late kick-off included. */
        const val TTL_MILLIS = 12 * 60 * 60_000L

        @Volatile private var instance: ScanStore? = null

        fun get(context: Context): ScanStore = instance ?: synchronized(this) {
            instance ?: ScanStore(File(context.applicationContext.filesDir, "football_scans")).also { instance = it }
        }
    }
}
