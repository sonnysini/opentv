package app.opentv.ui.channels

import android.content.Context
import android.util.Log
import app.opentv.data.model.Channel
import app.opentv.data.model.Programme
import app.opentv.ui.ChannelsViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * What the live list last showed: the groups, their counts and the open group's rows.
 *
 * Building the list means reading every channel from the database, folding the quality variants
 * and joining the guide — seconds on a big provider, and it used to happen on every launch and
 * every return through "Exit". The snapshot is shown straight away instead and the real list
 * replaces it as soon as it is built. Kept in memory for the life of the process and on disk for
 * the next cold start.
 */
@Serializable
data class LiveSnapshot(
    val version: Int = VERSION,
    val category: String?,
    val favourites: Boolean,
    val categoryGroups: List<ChannelsViewModel.CategoryGroup>,
    val nonEmptyGroups: List<ChannelsViewModel.CategoryGroup>,
    val groupCounts: Map<String, Int>,
    val rows: List<SnapshotRow>,
) {
    @Serializable
    data class SnapshotRow(
        val primary: Channel,
        val variants: List<Channel>,
        val programmes: List<Programme>,
    )

    /** The rows as the list wants them, with "now" and "next" worked out again for [nowMillis]. */
    fun rowsAt(nowMillis: Long): List<ChannelsViewModel.Row> = rows.map { r ->
        val list = r.programmes.filter { it.endUtcMillis > nowMillis }
        ChannelsViewModel.Row(
            primary = r.primary,
            variants = r.variants,
            now = list.firstOrNull { it.isLiveAt(nowMillis) },
            next = list.firstOrNull { it.startUtcMillis > nowMillis },
            programmes = r.programmes,
        )
    }

    companion object {
        /** Bump when the shape changes: an old file is then ignored rather than misread. */
        const val VERSION = 1
    }
}

object LiveSnapshotStore {
    private const val TAG = "LiveSnapshot"
    private const val FILE = "live_snapshot.json"
    /** Rows re-emit in bursts while a guide sync lands; write once things settle. */
    private const val SAVE_DELAY_MILLIS = 3_000L

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loaded = CompletableDeferred<LiveSnapshot?>()
    private var saveJob: Job? = null

    /** Set as soon as the disk copy is read (or a fresh one saved): what a new screen starts from. */
    @Volatile
    var current: LiveSnapshot? = null
        private set

    /** Read the disk copy in the background. Called once from Application.onCreate. */
    fun preload(context: Context) {
        val file = File(context.filesDir, FILE)
        scope.launch {
            val snapshot = runCatching {
                if (!file.exists()) null
                else json.decodeFromString<LiveSnapshot>(file.readText()).takeIf { it.version == LiveSnapshot.VERSION }
            }.onFailure { Log.w(TAG, "Unreadable snapshot, ignoring", it) }.getOrNull()
            if (current == null) current = snapshot
            loaded.complete(snapshot)
        }
    }

    /** The snapshot once the disk read is done — immediately when it already is. */
    suspend fun await(): LiveSnapshot? = current ?: loaded.await().let { current ?: it }

    /** Keep [snapshot] in memory now and write it to disk after a short pause. */
    fun save(context: Context, snapshot: LiveSnapshot) {
        current = snapshot
        val file = File(context.filesDir, FILE)
        synchronized(this) {
            saveJob?.cancel()
            saveJob = scope.launch {
                delay(SAVE_DELAY_MILLIS)
                runCatching {
                    val tmp = File(file.path + ".tmp")
                    tmp.writeText(json.encodeToString(LiveSnapshot.serializer(), snapshot))
                    tmp.renameTo(file)
                }.onFailure { Log.w(TAG, "Could not save snapshot", it) }
            }
        }
    }
}
