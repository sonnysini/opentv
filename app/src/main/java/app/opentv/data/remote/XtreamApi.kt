/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.remote

import app.opentv.data.model.Category
import app.opentv.data.model.Channel
import app.opentv.data.model.Episode
import app.opentv.data.model.Movie
import app.opentv.data.model.Series
import app.opentv.data.model.Source
import app.opentv.data.model.StreamKind
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeToSequence
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import app.opentv.core.AppText
import app.opentv.R

/**
 * Client for the Xtream Codes "player API".
 *
 * ## Why this is hand-rolled instead of using typed `@Serializable` models
 *
 * Xtream panels are not a standard. Different builds return the same field as a number, a
 * quoted number, an empty string, `null`, or omit it entirely — and `stream_id` in particular
 * flips between `12345` and `"12345"` depending on the panel. Strict deserialisation into a
 * data class means one unusual field aborts the parse of the *entire* catalogue, which the
 * user experiences as "the app won't load my channels" with no way to diagnose it.
 *
 * So we read [JsonElement] defensively and coerce. Every accessor below tolerates the wrong
 * type and returns null rather than throwing. Ugly; correct.
 */
class XtreamApi(
    private val http: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {

    class XtreamException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /** Verifies credentials and returns the account's expiry, if the panel reports one. */
    suspend fun authenticate(source: Source): AccountInfo = withContext(Dispatchers.IO) {
        val body = getJson(source, action = null).jsonObject
        val userInfo = body["user_info"]?.jsonObjectOrNull
            ?: throw XtreamException(AppText.get(R.string.xt_no_account))

        val status = userInfo["status"].asStringOrNull
        if (status != null && !status.equals("Active", ignoreCase = true)) {
            throw XtreamException(AppText.get(R.string.xt_account_status, status))
        }
        if (userInfo["auth"].asIntOrNull == 0) {
            throw XtreamException(AppText.get(R.string.xt_bad_login))
        }

        AccountInfo(
            username = userInfo["username"].asStringOrNull,
            expiryMillis = userInfo["exp_date"].asLongOrNull?.times(1000),
            maxConnections = userInfo["max_connections"].asIntOrNull,
            activeConnections = userInfo["active_cons"].asIntOrNull,
            timezone = body["server_info"]?.jsonObjectOrNull?.get("timezone").asStringOrNull,
        )
    }

    suspend fun liveCategories(source: Source): List<Category> =
        categories(source, "get_live_categories", StreamKind.LIVE)

    suspend fun movieCategories(source: Source): List<Category> =
        categories(source, "get_vod_categories", StreamKind.MOVIE)

    suspend fun seriesCategories(source: Source): List<Category> =
        categories(source, "get_series_categories", StreamKind.SERIES)

    private suspend fun categories(
        source: Source,
        action: String,
        kind: StreamKind,
    ): List<Category> = withContext(Dispatchers.IO) {
        getJson(source, action).arrayOrEmpty.mapIndexedNotNull { index, element ->
            val obj = element.jsonObjectOrNull ?: return@mapIndexedNotNull null
            val id = obj["category_id"].asStringOrNull ?: return@mapIndexedNotNull null
            Category(
                id = id,
                sourceId = source.id,
                name = obj["category_name"].asStringOrNull ?: id,
                kind = kind,
                sortIndex = index,
            )
        }
    }

    suspend fun liveStreams(source: Source): List<Channel> = withContext(Dispatchers.IO) {
        getJson(source, "get_live_streams").arrayOrEmpty.mapIndexedNotNull { index, element ->
            val obj = element.jsonObjectOrNull ?: return@mapIndexedNotNull null
            val streamId = obj["stream_id"].asStringOrNull ?: return@mapIndexedNotNull null
            val name = obj["name"].asStringOrNull ?: return@mapIndexedNotNull null
            Channel(
                sourceId = source.id,
                streamId = streamId,
                name = name,
                categoryId = obj["category_id"].asStringOrNull,
                logoUrl = obj["stream_icon"].asStringOrNull?.takeIf { it.isNotBlank() },
                // epg_channel_id is what joins to XMLTV; it is frequently blank, in which
                // case the channel simply has no guide rather than the guide being broken.
                epgChannelId = obj["epg_channel_id"].asStringOrNull?.takeIf { it.isNotBlank() },
                tvArchive = obj["tv_archive"].asIntOrNull == 1,
                tvArchiveDays = obj["tv_archive_duration"].asIntOrNull ?: 0,
                number = obj["num"].asIntOrNull,
                streamUrl = liveStreamUrl(source, streamId),
                sortIndex = obj["num"].asIntOrNull ?: index,
            )
        }
    }

    /**
     * Every movie on the panel, delivered in batches of [batchSize] to [onBatch] as the response is
     * read. `get_vod_streams` on a big provider is tens of megabytes of JSON (300k titles is not
     * rare); reading it into one String and parsing a tree of it is what ran an old Fire Stick out
     * of memory. Here the body is decoded one array element at a time, so peak memory is one batch.
     * Returns the number of titles delivered. Throws if the stream is cut or is not JSON — the
     * caller must then treat what it received as partial.
     */
    suspend fun movies(
        source: Source,
        batchSize: Int = STREAM_BATCH,
        maxBytesPerSecond: Long = 0,
        onBatch: suspend (List<Movie>) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        streamArray(source, "get_vod_streams", batchSize, maxBytesPerSecond, onBatch) { obj -> parseMovie(source, obj) }
    }

    /** Every series on the panel, in batches. Same streaming contract as [movies]. */
    suspend fun series(
        source: Source,
        batchSize: Int = STREAM_BATCH,
        maxBytesPerSecond: Long = 0,
        onBatch: suspend (List<Series>) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        streamArray(source, "get_series", batchSize, maxBytesPerSecond, onBatch) { obj -> parseSeries(source, obj) }
    }

    private fun parseMovie(source: Source, obj: JsonObject): Movie? {
        val streamId = obj["stream_id"].asStringOrNull ?: return null
        val name = obj["name"].asStringOrNull ?: return null
        val extension = obj["container_extension"].asStringOrNull?.takeIf { it.isNotBlank() }
        return Movie(
            sourceId = source.id,
            streamId = streamId,
            name = name,
            categoryId = obj["category_id"].asStringOrNull,
            posterUrl = obj["stream_icon"].asStringOrNull?.takeIf { it.isNotBlank() },
            rating = obj["rating"].asDoubleOrNull,
            year = obj["year"].asIntOrNull,
            plot = obj["plot"].asStringOrNull,
            durationSeconds = obj["duration_secs"].asIntOrNull,
            containerExtension = extension,
            streamUrl = vodStreamUrl(source, streamId, extension),
            addedMillis = obj["added"].asLongOrNull?.times(1000) ?: 0L,
            // Rich metadata is best-effort here: the streams list carries it on some panels
            // and not others. Whatever is missing is back-filled from get_vod_info on the
            // first detail open. See asBackdropUrl for the array-or-string handling.
            backdropUrl = obj.asBackdropUrl("movie_image", "cover_big"),
            cast = obj["cast"].asStringOrNull ?: obj["actors"].asStringOrNull,
            director = obj["director"].asStringOrNull,
            genre = obj["genre"].asStringOrNull,
            tmdbId = obj["tmdb_id"].asStringOrNull ?: obj["tmdb"].asStringOrNull,
        )
    }

    private fun parseSeries(source: Source, obj: JsonObject): Series? {
        val seriesId = obj["series_id"].asStringOrNull ?: return null
        val name = obj["name"].asStringOrNull ?: return null
        return Series(
            sourceId = source.id,
            seriesId = seriesId,
            name = name,
            categoryId = obj["category_id"].asStringOrNull,
            posterUrl = obj["cover"].asStringOrNull?.takeIf { it.isNotBlank() },
            rating = obj["rating"].asDoubleOrNull,
            year = obj["year"].asIntOrNull ?: obj["releaseDate"].asStringOrNull?.take(4)?.toIntOrNull(),
            plot = obj["plot"].asStringOrNull,
            addedMillis = obj["last_modified"].asLongOrNull?.times(1000) ?: 0L,
            // get_series carries most of this inline on the majority of panels; anything
            // missing is back-filled from get_series_info on the first detail open.
            backdropUrl = obj.asBackdropUrl("cover_big", "cover"),
            cast = obj["cast"].asStringOrNull ?: obj["actors"].asStringOrNull,
            genre = obj["genre"].asStringOrNull,
            tmdbId = obj["tmdb_id"].asStringOrNull ?: obj["tmdb"].asStringOrNull,
        )
    }

    /**
     * Episode list for one series.
     *
     * `episodes` is an object keyed by season number whose values are arrays — except on some
     * panels, where it is an array of arrays, and on others where a season with a single
     * episode collapses to a bare object. All three shapes are handled.
     */
    suspend fun episodes(source: Source, seriesId: String): List<Episode> =
        withContext(Dispatchers.IO) {
            val body = getJson(source, "get_series_info") { it.addQueryParameter("series_id", seriesId) }
            val episodesNode = body.jsonObjectOrNull?.get("episodes") ?: return@withContext emptyList()

            val seasonBuckets: List<Pair<Int?, JsonElement>> = when (episodesNode) {
                is JsonObject -> episodesNode.entries.map { it.key.toIntOrNull() to it.value }
                is JsonArray -> episodesNode.mapIndexed { index, value -> index to value }
                else -> emptyList()
            }

            seasonBuckets.flatMap { (seasonHint, bucket) ->
                val items: List<JsonElement> = when (bucket) {
                    is JsonArray -> bucket.toList()
                    is JsonObject -> listOf(bucket)
                    else -> emptyList()
                }
                items.mapNotNull { element ->
                    val obj = element.jsonObjectOrNull ?: return@mapNotNull null
                    val episodeId = obj["id"].asStringOrNull ?: return@mapNotNull null
                    val info = obj["info"].jsonObjectOrNull
                    val extension = obj["container_extension"].asStringOrNull
                        ?.takeIf { it.isNotBlank() }
                    Episode(
                        sourceId = source.id,
                        seriesId = seriesId,
                        episodeId = episodeId,
                        season = obj["season"].asIntOrNull ?: seasonHint ?: 1,
                        episodeNumber = obj["episode_num"].asIntOrNull ?: 0,
                        title = obj["title"].asStringOrNull ?: "Episode",
                        plot = info?.get("plot").asStringOrNull,
                        durationSeconds = info?.get("duration_secs").asIntOrNull,
                        stillUrl = info?.get("movie_image").asStringOrNull?.takeIf { it.isNotBlank() },
                        streamUrl = seriesStreamUrl(source, episodeId, extension),
                    )
                }
            }
        }

    /**
     * Rich metadata for a single movie, from `get_vod_info`'s `info` object.
     *
     * The streams list ([movies]) is deliberately lightweight; the heavy fields (backdrop art,
     * cast, director, genre) live behind this per-title call, which the detail screen makes lazily
     * on open. Every field is best-effort — a panel that omits `info` entirely yields null, not an
     * error, so the caller can fall back to whatever the list already had.
     */
    suspend fun movieInfo(source: Source, streamId: String): VodInfo? = withContext(Dispatchers.IO) {
        val body = runCatching {
            getJson(source, "get_vod_info") { it.addQueryParameter("vod_id", streamId) }
        }.getOrNull() ?: return@withContext null
        val info = body.jsonObjectOrNull?.get("info").jsonObjectOrNull ?: return@withContext null
        VodInfo(
            backdropUrl = info.asBackdropUrl("movie_image", "cover_big"),
            cast = info["cast"].asStringOrNull ?: info["actors"].asStringOrNull,
            director = info["director"].asStringOrNull,
            genre = info["genre"].asStringOrNull,
            tmdbId = info["tmdb_id"].asStringOrNull ?: info["tmdb"].asStringOrNull,
            plot = info["plot"].asStringOrNull ?: info["description"].asStringOrNull,
            rating = info["rating"].asDoubleOrNull,
            year = info["year"].asIntOrNull ?: info["releasedate"].asStringOrNull?.take(4)?.toIntOrNull(),
            durationSeconds = info["duration_secs"].asIntOrNull,
        )
    }

    /**
     * Rich metadata for a single series, from `get_series_info`'s `info` object. Same contract as
     * [movieInfo]; series carry no director. This shares the `get_series_info` endpoint with
     * [episodes] but is a separate call — the detail screen fetches episodes and info independently,
     * and most series are never opened, so paying for both only when needed is the point.
     */
    suspend fun seriesInfo(source: Source, seriesId: String): SeriesInfo? = withContext(Dispatchers.IO) {
        val body = runCatching {
            getJson(source, "get_series_info") { it.addQueryParameter("series_id", seriesId) }
        }.getOrNull() ?: return@withContext null
        val info = body.jsonObjectOrNull?.get("info").jsonObjectOrNull ?: return@withContext null
        SeriesInfo(
            backdropUrl = info.asBackdropUrl("cover_big", "cover"),
            cast = info["cast"].asStringOrNull ?: info["actors"].asStringOrNull,
            genre = info["genre"].asStringOrNull,
            tmdbId = info["tmdb_id"].asStringOrNull ?: info["tmdb"].asStringOrNull,
            plot = info["plot"].asStringOrNull ?: info["description"].asStringOrNull,
            rating = info["rating"].asDoubleOrNull,
            year = info["year"].asIntOrNull ?: info["releaseDate"].asStringOrNull?.take(4)?.toIntOrNull(),
        )
    }

    /** Opens the XMLTV guide as a stream. The caller must close it. */
    suspend fun openEpgStream(source: Source): InputStream = withContext(Dispatchers.IO) {
        val url = source.epgUrl?.toHttpUrlOrNull() ?: xmltvUrl(source)
        val response = http.newCall(request(source, url)).execute()
        if (!response.isSuccessful) {
            response.close()
            throw XtreamException(AppText.get(R.string.xt_guide_http, response.code))
        }
        response.body?.byteStream()
            ?: throw XtreamException(AppText.get(R.string.xt_guide_empty))
    }

    // ---- URL construction ----------------------------------------------------------------

    fun liveStreamUrl(source: Source, streamId: String): String =
        "${source.url}/live/${source.username}/${source.password}/$streamId.${source.liveFormat.extension}"

    fun vodStreamUrl(source: Source, streamId: String, extension: String?): String =
        "${source.url}/movie/${source.username}/${source.password}/$streamId.${extension ?: "mp4"}"

    fun seriesStreamUrl(source: Source, episodeId: String, extension: String?): String =
        "${source.url}/series/${source.username}/${source.password}/$episodeId.${extension ?: "mp4"}"

    /**
     * Catch-up / archive stream for a past programme. Uses the widely-supported path form
     * `/timeshift/{user}/{pass}/{durationMinutes}/{yyyy-MM-dd:HH-mm}/{streamId}.ts`, which returns a
     * seekable MPEG-TS just like a recording. The start time is formatted in the device's local
     * zone, matching how the guide shows programme times.
     */
    fun catchupUrl(source: Source, streamId: String, startUtcMillis: Long, durationMinutes: Int): String {
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd:HH-mm", java.util.Locale.US)
            .format(java.util.Date(startUtcMillis))
        val minutes = durationMinutes.coerceAtLeast(1)
        return "${source.url}/timeshift/${source.username}/${source.password}/$minutes/$stamp/$streamId.ts"
    }

    private fun xmltvUrl(source: Source): HttpUrl =
        baseUrl(source).newBuilder()
            .encodedPath("/xmltv.php")
            .addQueryParameter("username", source.username.orEmpty())
            .addQueryParameter("password", source.password.orEmpty())
            .build()

    private fun baseUrl(source: Source): HttpUrl =
        source.url.toHttpUrlOrNull()
            ?: throw XtreamException("\"${source.url}\" is not a valid server address.")

    // ---- Plumbing ------------------------------------------------------------------------

    private fun getJson(
        source: Source,
        action: String?,
        extra: (HttpUrl.Builder) -> Unit = {},
    ): JsonElement {
        val builder = baseUrl(source).newBuilder()
            .encodedPath("/player_api.php")
            .addQueryParameter("username", source.username.orEmpty())
            .addQueryParameter("password", source.password.orEmpty())
        if (action != null) builder.addQueryParameter("action", action)
        extra(builder)

        http.newCall(request(source, builder.build())).execute().use { response ->
            if (!response.isSuccessful) {
                throw XtreamException(describeHttpFailure(response.code))
            }
            val text = response.body?.string().orEmpty()
            if (text.isBlank()) throw XtreamException(AppText.get(R.string.xt_empty_response))
            return try {
                json.parseToJsonElement(text)
            } catch (e: Exception) {
                // Panels behind a captive portal or Cloudflare return HTML here. Saying
                // "not valid JSON" is useless to a user; say what it probably means.
                throw XtreamException(
                    AppText.get(R.string.xt_not_catalogue),
                    e,
                )
            }
        }
    }

    /**
     * Streams a top-level JSON array from [action], decoding one element at a time and handing
     * parsed rows to [onBatch] every [batchSize]. Nothing but the current batch is held in memory.
     * A panel that answers with an object instead of an array (some do, for "no VOD") yields zero
     * rows rather than an error, matching what the tree-based [getJson] path did.
     * A positive [maxBytesPerSecond] caps the download rate (see [ThrottledInputStream]).
     */
    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun <T> streamArray(
        source: Source,
        action: String,
        batchSize: Int,
        maxBytesPerSecond: Long,
        onBatch: suspend (List<T>) -> Unit,
        parse: (JsonObject) -> T?,
    ): Int {
        val url = baseUrl(source).newBuilder()
            .encodedPath("/player_api.php")
            .addQueryParameter("username", source.username.orEmpty())
            .addQueryParameter("password", source.password.orEmpty())
            .addQueryParameter("action", action)
            .build()

        http.newCall(request(source, url)).execute().use { response ->
            if (!response.isSuccessful) throw XtreamException(describeHttpFailure(response.code))
            val body = response.body ?: throw XtreamException(AppText.get(R.string.xt_empty_response))
            var total = 0
            val batch = ArrayList<T>(batchSize)
            try {
                val elements = json.decodeToSequence(
                    ThrottledInputStream(body.byteStream(), maxBytesPerSecond).buffered(),
                    JsonElement.serializer(),
                    DecodeSequenceMode.AUTO_DETECT,
                )
                for (element in elements) {
                    val row = element.jsonObjectOrNull?.let(parse) ?: continue
                    batch += row
                    if (batch.size >= batchSize) {
                        onBatch(ArrayList(batch))
                        total += batch.size
                        batch.clear()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SerializationException) {
                throw XtreamException(
                    AppText.get(R.string.xt_not_catalogue),
                    e,
                )
            } catch (e: IllegalArgumentException) {
                // kotlinx reports some malformed input (e.g. an HTML error page) this way.
                throw XtreamException(AppText.get(R.string.xt_catalogue_unreadable), e)
            }
            if (batch.isNotEmpty()) {
                onBatch(ArrayList(batch))
                total += batch.size
            }
            return total
        }
    }

    private fun request(source: Source, url: HttpUrl): Request =
        Request.Builder()
            .url(url)
            .header("User-Agent", source.userAgent)
            .build()

    private fun describeHttpFailure(code: Int): String = when (code) {
        401, 403 -> AppText.get(R.string.xt_http_auth, code)
        404 -> AppText.get(R.string.xt_http_404)
        405 -> AppText.get(R.string.xt_http_405)
        429 -> AppText.get(R.string.xt_http_429)
        in 500..599 -> AppText.get(R.string.xt_http_5xx, code)
        else -> AppText.get(R.string.perr_http_other, code)
    }

    companion object {
        /** Rows per streamed batch: one DB transaction each, and the most held in memory at once. */
        const val STREAM_BATCH = 1000
    }

    data class AccountInfo(
        val username: String?,
        val expiryMillis: Long?,
        val maxConnections: Int?,
        val activeConnections: Int?,
        val timezone: String?,
    )

    /**
     * Rich per-movie metadata from `get_vod_info`. Every field is nullable — the caller merges it
     * over the (possibly sparse) row it already has, only filling blanks. `plot`/`rating`/`year`/
     * `durationSeconds` are included because `get_vod_info` often carries a fuller version than the
     * streams list did.
     */
    data class VodInfo(
        val backdropUrl: String?,
        val cast: String?,
        val director: String?,
        val genre: String?,
        val tmdbId: String?,
        val plot: String?,
        val rating: Double?,
        val year: Int?,
        val durationSeconds: Int?,
    )

    /** Rich per-series metadata from `get_series_info`. Series have no director. See [VodInfo]. */
    data class SeriesInfo(
        val backdropUrl: String?,
        val cast: String?,
        val genre: String?,
        val tmdbId: String?,
        val plot: String?,
        val rating: Double?,
        val year: Int?,
    )
}

// ---- Defensive JSON accessors ------------------------------------------------------------
// Every one of these returns null rather than throwing. See the class doc for why.

private val JsonElement?.jsonObjectOrNull: JsonObject?
    get() = this as? JsonObject

private val JsonElement.arrayOrEmpty: List<JsonElement>
    get() = (this as? JsonArray)?.toList() ?: emptyList()

private val JsonElement?.asPrimitiveOrNull: JsonPrimitive?
    get() = this as? JsonPrimitive

private val JsonElement?.asStringOrNull: String?
    get() = asPrimitiveOrNull?.contentOrNull?.takeIf { it.isNotEmpty() && it != "null" }

/**
 * A field that is a plain URL string on some panels and a JSON array of URLs on others — which is
 * exactly how Xtream ships `backdrop_path`. Returns the first non-blank element of an array, or the
 * string itself otherwise. `asStringOrNull` already drops empties and literal "null".
 */
private val JsonElement?.firstStringOrNull: String?
    get() = when (this) {
        is JsonArray -> firstNotNullOfOrNull { it.asStringOrNull }
        else -> asStringOrNull
    }

/**
 * The best backdrop URL from an `info`/stream object: `backdrop_path` (array-aware) first, then the
 * given fallback keys (e.g. `movie_image`, `cover_big`) as plain strings. Keeps the array handling
 * in one place so [movies], [series], [movieInfo] and [seriesInfo] stay consistent.
 */
private fun JsonObject.asBackdropUrl(vararg fallbackKeys: String): String? {
    this["backdrop_path"].firstStringOrNull?.let { return it }
    this["backdrop"].firstStringOrNull?.let { return it }
    for (key in fallbackKeys) this[key].asStringOrNull?.takeIf { it.isNotBlank() }?.let { return it }
    return null
}

private val JsonElement?.asIntOrNull: Int?
    get() = asStringOrNull?.substringBefore('.')?.toIntOrNull()

private val JsonElement?.asLongOrNull: Long?
    get() = asStringOrNull?.substringBefore('.')?.toLongOrNull()

private val JsonElement?.asDoubleOrNull: Double?
    get() = asStringOrNull?.toDoubleOrNull()
