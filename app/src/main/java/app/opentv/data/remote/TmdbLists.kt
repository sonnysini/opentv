/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.remote

import android.util.Log
import app.opentv.core.AppSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** One title off a TMDB list: enough for a hero, a poster card and matching against the catalogue. */
data class TmdbTitle(
    val id: String,
    val title: String,
    val originalTitle: String?,
    val year: Int?,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val rating: Double?,
    val genreIds: List<Int>,
)

/**
 * The charts that fill the Movies and Shows home: what's trending this week, what's popular on
 * Italian streaming, the best rated, and the same per genre. TMDB knows nothing of the user's
 * provider — the caller keeps only the titles the catalogue really has.
 *
 * Every answer is kept on disk for [TTL_MILLIS], so opening the tab is instant and costs TMDB a
 * handful of calls a day. Like [TmdbClient] it runs on the user's own key and blocks the calling
 * thread (callers are on IO); with no key every call is an empty list.
 */
class TmdbLists(
    private val http: OkHttpClient,
    private val settings: AppSettings,
    private val cacheDir: File,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    enum class Kind(val path: String) { MOVIE("movie"), TV("tv") }

    /** Which chart. [GENRE] takes a TMDB genre id. */
    sealed interface Chart {
        data object Trending : Chart
        data object PopularInItaly : Chart
        data object TopRated : Chart
        data class Genre(val id: Int) : Chart
    }

    fun isConfigured(): Boolean = settings.tmdbApiKey.value.isNotBlank()

    /** Up to [pages] pages (20 titles each) of [chart], in TMDB's order. */
    fun chart(kind: Kind, chart: Chart, pages: Int = 3): List<TmdbTitle> {
        val key = settings.tmdbApiKey.value.trim()
        if (key.isEmpty()) return emptyList()
        val seen = HashSet<String>()
        return (1..pages).flatMap { page ->
            val url = chartUrl(kind, chart, page, key)
            val results = cached(url)?.get("results")?.jsonArray ?: return@flatMap emptyList()
            results.mapNotNull { parse(it.jsonObject, kind) }
        }.filter { seen.add(it.id) }
    }

    /**
     * The title's logo (the film's own lettering, transparent PNG) for the hero: Italian first, then
     * English, then one with no language. Null when TMDB has none — the hero writes the title then.
     */
    fun logoUrl(kind: Kind, id: String): String? {
        val key = settings.tmdbApiKey.value.trim()
        if (key.isEmpty()) return null
        val url = BASE.newBuilder()
            .addPathSegment(kind.path).addPathSegment(id).addPathSegment("images")
            .addQueryParameter("api_key", key)
            .addQueryParameter("include_image_language", "it,en,null")
            .build()
        val logos = cached(url)?.get("logos")?.jsonArray ?: return null
        val byLang = logos.map { it.jsonObject }.sortedBy {
            when (it["iso_639_1"]?.jsonPrimitive?.contentOrNull) { "it" -> 0; "en" -> 1; null -> 2; else -> 3 }
        }
        return byLang.firstOrNull()?.get("file_path")?.jsonPrimitive?.contentOrNull?.let { IMG_LOGO + it }
    }

    private fun chartUrl(kind: Kind, chart: Chart, page: Int, key: String): HttpUrl {
        val b = BASE.newBuilder()
        when (chart) {
            Chart.Trending -> b.addPathSegment("trending").addPathSegment(kind.path).addPathSegment("week")
            Chart.PopularInItaly -> b.addPathSegment("discover").addPathSegment(kind.path)
                .addQueryParameter("sort_by", "popularity.desc")
                .addQueryParameter("watch_region", "IT")
                .addQueryParameter("with_watch_monetization_types", "flatrate")
            Chart.TopRated -> b.addPathSegment("discover").addPathSegment(kind.path)
                .addQueryParameter("sort_by", "vote_average.desc")
                .addQueryParameter("vote_count.gte", if (kind == Kind.MOVIE) "3000" else "1000")
            is Chart.Genre -> b.addPathSegment("discover").addPathSegment(kind.path)
                .addQueryParameter("sort_by", "popularity.desc")
                .addQueryParameter("with_genres", chart.id.toString())
                .addQueryParameter("vote_count.gte", "100")
        }
        return b.addQueryParameter("api_key", key)
            .addQueryParameter("language", "it-IT")
            .addQueryParameter("include_adult", "false")
            .addQueryParameter("page", page.toString())
            .build()
    }

    private fun parse(o: JsonObject, kind: Kind): TmdbTitle? {
        val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val title = (o["title"] ?: o["name"])?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val original = (o["original_title"] ?: o["original_name"])?.jsonPrimitive?.contentOrNull
        val date = (if (kind == Kind.MOVIE) o["release_date"] else o["first_air_date"])?.jsonPrimitive?.contentOrNull
        return TmdbTitle(
            id = id,
            title = title,
            originalTitle = original?.takeIf { it != title },
            year = date?.take(4)?.toIntOrNull(),
            overview = o["overview"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
            posterUrl = o["poster_path"]?.jsonPrimitive?.contentOrNull?.let { IMG_POSTER + it },
            backdropUrl = o["backdrop_path"]?.jsonPrimitive?.contentOrNull?.let { IMG_BACKDROP + it },
            rating = o["vote_average"]?.jsonPrimitive?.doubleOrNull?.takeIf { it > 0.0 },
            genreIds = o["genre_ids"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull }.orEmpty(),
        )
    }

    /** The answer for [url] from disk while it's fresh, else from TMDB (falling back to a stale copy). */
    private fun cached(url: HttpUrl): JsonObject? {
        // The key is in the URL; the file name must not carry it.
        val name = url.newBuilder().removeAllQueryParameters("api_key").build().toString().hashCode().toUInt().toString(16)
        val file = File(cacheDir, "$name.json")
        val now = System.currentTimeMillis()
        if (file.exists() && now - file.lastModified() < TTL_MILLIS) {
            read(file)?.let { return it }
        }
        val body = runCatching {
            http.newCall(Request.Builder().url(url).header("User-Agent", "OpenTV").build()).execute().use { r ->
                if (r.isSuccessful) r.body?.string() else null
            }
        }.onFailure { Log.w(TAG, "TMDB chart request failed", it) }.getOrNull()
        val parsed = body?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
        if (parsed != null) {
            runCatching { cacheDir.mkdirs(); file.writeText(body) }
            return parsed
        }
        return if (file.exists()) read(file) else null
    }

    private fun read(file: File): JsonObject? =
        runCatching { json.parseToJsonElement(file.readText()).jsonObject }.getOrNull()

    companion object {
        private const val TAG = "TmdbLists"
        private val BASE: HttpUrl = "https://api.themoviedb.org/3".toHttpUrl()
        private const val IMG_POSTER = "https://image.tmdb.org/t/p/w342"
        private const val IMG_BACKDROP = "https://image.tmdb.org/t/p/w1280"
        private const val IMG_LOGO = "https://image.tmdb.org/t/p/w500"
        const val TTL_MILLIS = 12L * 60 * 60 * 1000

        /** The genre tabs on the Movies page: TMDB id → Italian label, in tab order. */
        val MOVIE_GENRES = linkedMapOf(
            28 to "Azione", 35 to "Commedia", 18 to "Drammatico", 27 to "Horror",
            53 to "Thriller", 878 to "Fantascienza", 16 to "Animazione", 10751 to "Famiglia",
        )

        /** Every TMDB movie genre's Italian name, for the hero's "Horror, Thriller" line. */
        val MOVIE_GENRE_NAMES = mapOf(
            28 to "Azione", 12 to "Avventura", 16 to "Animazione", 35 to "Commedia", 80 to "Crime",
            99 to "Documentario", 18 to "Dramma", 10751 to "Famiglia", 14 to "Fantasy", 36 to "Storia",
            27 to "Horror", 10402 to "Musica", 9648 to "Mistero", 10749 to "Romance",
            878 to "Fantascienza", 10770 to "Film TV", 53 to "Thriller", 10752 to "Guerra", 37 to "Western",
        )
    }
}
