/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.football

import android.util.Log
import app.opentv.data.model.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.Normalizer
import java.util.concurrent.TimeUnit

/**
 * The scan server's rankings ([app.opentv.core.AppSettings.scanServerUrl]): a box at home that,
 * before each kick-off, looks at every feed of the games about to start — the TV is off then, so
 * the one connection the account allows is free. Its looks go into [ScanStore] as if the TV had
 * made them: "Watch now" then plays the best feed at once.
 *
 * A feed is the server's when the provider's stream id is the same (same account) or, on another
 * of the provider's servers, when the provider's name for it is.
 */
object ServerScans {
    data class Look(val streamId: String, val name: String, val probe: AutoScan.Probe?)
    data class MatchLooks(val matchId: Long, val scannedMillis: Long, val looks: List<Look>)

    /** The server's matches, or null when it can't be read. */
    suspend fun fetch(http: OkHttpClient, url: String): List<MatchLooks>? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        runCatching {
            val client = http.newBuilder().callTimeout(4, TimeUnit.SECONDS).build()
            val body = client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code}")
                r.body?.string().orEmpty()
            }
            parse(body)
        }.getOrElse { Log.w(TAG, "fetch: ${it.message}"); null }
    }

    fun parse(body: String): List<MatchLooks> {
        val matches = JSONObject(body).optJSONArray("matches") ?: return emptyList()
        return (0 until matches.length()).map { i ->
            val m = matches.getJSONObject(i)
            val feeds = m.optJSONArray("feeds")
            val looks = (0 until (feeds?.length() ?: 0)).mapNotNull { j ->
                val f = feeds!!.getJSONObject(j)
                // Listed but not looked at (the away team's copy of the home feed).
                if (f.optBoolean("skipped")) return@mapNotNull null
                val probe = if (f.optBoolean("ok") && f.has("height")) {
                    AutoScan.Probe(
                        height = f.getInt("height"),
                        fps = if (f.has("fps")) f.getInt("fps") else null,
                        audioChannels = if (f.has("channels") && !f.isNull("channels")) f.getInt("channels") else null,
                        millis = f.optLong("start_ms", 1000),
                        board = null,
                        audioHd = f.optString("audio") == "eac3",
                        kbps = if (f.has("kbps")) f.getInt("kbps") else null,
                    )
                } else {
                    null
                }
                Look(f.get("stream_id").toString(), f.optString("name"), probe)
            }
            MatchLooks(m.getLong("id"), (m.getDouble("scanned") * 1000).toLong(), looks)
        }
    }

    /**
     * Stores [server]'s looks at the feeds of each match in [feeds] (match id → the TV's feeds).
     * Returns the ids of the matches it had something for.
     */
    fun apply(server: List<MatchLooks>, feeds: Map<Long, List<Channel>>, store: ScanStore): Set<Long> {
        val done = HashSet<Long>()
        for (m in server) {
            val mine = feeds[m.matchId] ?: continue
            val byId = mine.associateBy { it.streamId }
            val byName = mine.groupBy { key(it.name) }
            var hits = 0
            for (look in m.looks) {
                val targets = byId[look.streamId]?.let { listOf(it) } ?: byName[key(look.name)].orEmpty()
                for (c in targets) {
                    store.putServer(m.matchId, c.id, look.probe, m.scannedMillis)
                    hits++
                }
            }
            if (hits > 0) done += m.matchId
            Log.i(TAG, "match ${m.matchId}: ${m.looks.size} looks, $hits feeds of ours")
        }
        if (done.isNotEmpty()) store.flush()
        return done
    }

    private fun key(name: String) =
        Normalizer.normalize(name, Normalizer.Form.NFD).replace(MARKS, "").lowercase().replace(NON_ALNUM, "")

    private val MARKS = Regex("\\p{M}+")
    private val NON_ALNUM = Regex("[^a-z0-9]+")
    private const val TAG = "ServerScans"
}
