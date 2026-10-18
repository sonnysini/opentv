/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.repo

import android.util.Log
import app.opentv.core.AppSettings
import app.opentv.data.db.CategoryDao
import app.opentv.data.db.ChannelDao
import app.opentv.data.db.EpisodeDao
import app.opentv.data.db.MovieDao
import app.opentv.data.db.PlaybackPositionDao
import app.opentv.data.db.SeriesDao
import app.opentv.data.db.SourceDao
import app.opentv.data.model.Category
import app.opentv.data.model.Channel
import app.opentv.data.model.Episode
import app.opentv.data.model.LiveStreamFormat
import app.opentv.data.model.Movie
import app.opentv.data.model.Series
import app.opentv.data.model.Source
import app.opentv.data.model.SourceKind
import app.opentv.data.model.StreamKind
import app.opentv.data.parser.CategoryFamily
import app.opentv.data.parser.ChannelNameNormalizer
import app.opentv.data.parser.M3uParser
import app.opentv.data.parser.MergeSuggester
import app.opentv.data.parser.VodTitleCleaner
import app.opentv.data.remote.StalkerApi
import app.opentv.data.remote.TmdbClient
import app.opentv.data.remote.XtreamApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Keeps one channel per distinct quality signature, preserving order (so "best first" is
 * preserved when the caller has already sorted by rank).
 *
 * Two streams with the same rank AND the same label are treated as the same quality — one is
 * kept, the rest dropped. This is what collapses "RAW / RAW" and "HD / HD" duplicates down to
 * a single option, and turns a falsely-multi-quality channel back into a single one.
 */
internal fun distinctByQuality(channels: List<app.opentv.data.model.Channel>): List<app.opentv.data.model.Channel> {
    val seen = HashSet<String>()
    return channels.filter { seen.add("${it.qualityRank}|${it.qualityLabel.lowercase()}") }
}

/** A home/detail row: a genre label and the titles under it. Generic so movies and series share it. */
@androidx.compose.runtime.Immutable
data class GenreGroup<T>(val genre: String, val items: List<T>)

/**
 * One library entry a person is credited in — a movie or a series — for the Person screen's mixed
 * poster grid. A thin wrapper so the screen can render one grid yet still route a click to the right
 * detail page (movie vs series) without a second lookup.
 */
sealed interface PersonTitle {
    data class MovieItem(val movie: Movie) : PersonTitle
    data class SeriesItem(val series: Series) : PersonTitle
}

/** One quality variant of a film, with the quality parsed from its name by [ChannelNameNormalizer]. */
data class MovieVariant(val movie: Movie, val qualityLabel: String, val qualityRank: Int)

/** One logical film with its switchable quality tiers, best first — the VOD analogue of a channel group. */
@androidx.compose.runtime.Immutable
data class MovieVariantGroup(
    /** Best-quality variant: what a row shows and plays by default. */
    val primary: Movie,
    /** Every variant (including [primary]), best quality first. */
    val variants: List<MovieVariant>,
) {
    val hasMultipleQualities: Boolean get() = variants.size > 1
}

/** A standalone 4-digit release year (19xx/20xx) as it appears inside a VOD title. */
private val VOD_YEAR = Regex("""\b(19|20)\d{2}\b""")

/**
 * Collapses obvious quality variants of the same film — "The Godfather 1972 HD" and
 * "The Godfather 4K" — into one entry with switchable tiers, the VOD analogue of a channel's
 * quality group. Pure and in-memory: movies carry no stored groupKey, so the key is computed here
 * from the normalised, year-stripped name plus the year (the field, else the one embedded in the
 * name). Group order follows first appearance; variants within a group are best-quality first.
 *
 * It folds only genuine quality variants: edition tokens [ChannelNameNormalizer] does not know
 * (IMAX, EXTENDED, 3D) stay in the name and keep those cuts separate — intended. Two same-named
 * films that both lack any year will merge, an accepted edge for a best-effort collapse.
 */
internal fun collapseMovieVariants(movies: List<Movie>): List<MovieVariantGroup> {
    val groups = LinkedHashMap<String, MutableList<MovieVariant>>()
    for (movie in movies) {
        val embeddedYear = VOD_YEAR.find(movie.name)?.value?.toIntOrNull()
        val bareName = VOD_YEAR.replace(movie.name, " ")
        val normalized = ChannelNameNormalizer.normalize(bareName)
        val year = movie.year ?: embeddedYear
        val key = normalized.groupKey + "|" + (year?.toString() ?: "")
        groups.getOrPut(key) { mutableListOf() }
            .add(MovieVariant(movie, normalized.qualityLabel, normalized.qualityRank))
    }
    return groups.values.map { variants ->
        val ordered = variants.sortedWith(
            compareByDescending<MovieVariant> { it.qualityRank }.thenBy { it.movie.name },
        )
        MovieVariantGroup(primary = ordered.first().movie, variants = ordered)
    }
}

/**
 * Channels, movies and series.
 *
 * Same principle as [EpgRepository]: a refresh that fails must never leave the user with less
 * than they started with. Catalogue writes go through [ChannelDao.replaceCatalogue], which
 * merges rather than wipes and carries favourites, hidden flags and manual ordering across.
 */
class CatalogRepository(
    private val sourceDao: SourceDao,
    private val channelDao: ChannelDao,
    private val categoryDao: CategoryDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val episodeDao: EpisodeDao,
    private val positionDao: PlaybackPositionDao,
    private val api: XtreamApi,
    private val stalkerApi: StalkerApi,
    private val http: OkHttpClient,
    private val settings: AppSettings,
) {

    /** TMDB back-fill for VOD detail pages, gated on a user-supplied key. See [TmdbClient]. */
    private val tmdb = TmdbClient(http, settings)

    sealed interface SyncResult {
        data class Success(
            val channelCount: Int,
            val movieCount: Int,
            val seriesCount: Int,
        ) : SyncResult

        data class Failed(val reason: String, val cause: Throwable?) : SyncResult
    }

    /** Titles written so far by the VOD sync that is running right now. */
    @androidx.compose.runtime.Immutable
    data class VodProgress(val movies: Int, val series: Int) {
        val total: Int get() = movies + series
    }

    private val _vodProgress = MutableStateFlow<VodProgress?>(null)

    /** The running VOD sync's progress, or null when none is running. Movies/Shows show it. */
    val vodProgress: StateFlow<VodProgress?> = _vodProgress.asStateFlow()

    /** One VOD sync at a time, whoever asks (launch auto-start, worker, Movies/Shows tab). */
    private val vodMutex = Mutex()

    fun observeChannels(sourceId: Long? = null, categoryId: String? = null): Flow<List<Channel>> =
        channelDao.observe(sourceId, categoryId)

    fun observeChannelsIn(categoryIds: List<String>): Flow<List<Channel>> =
        channelDao.observeInCategories(categoryIds)

    /**
     * Channels in a set of categories INCLUDING hidden ones, optionally scoped to one source — the
     * channel manager's browse feed. Separate from [observeChannelsIn] (which drops `hidden` rows
     * for the guide) because the manager must show hidden channels so they can be un-hidden.
     */
    fun observeChannelsInIncludingHidden(sourceId: Long?, categoryIds: List<String>): Flow<List<Channel>> =
        channelDao.observeInCategoriesIncludingHidden(sourceId, categoryIds)

    fun observeFavouriteChannels(): Flow<List<Channel>> = channelDao.observeFavourites()

    /** Reactive number of visible channels on disk — the UI uses this to tell "guide still
     * building" (channels exist) apart from "nothing loaded" (a failed or empty sync). */
    fun observeChannelCount(): Flow<Int> = channelDao.observeVisibleCount()

    fun observeNonEmptyCategoryIds(): Flow<Set<String>> =
        channelDao.observeNonEmptyCategoryIds().map { it.toSet() }

    /** Categories of [kind] the user has not switched off — what every rail and chip row shows. */
    fun observeCategories(kind: StreamKind): Flow<List<Category>> =
        categoryDao.observe(kind).combine(settings.categoryRules) { categories, rules ->
            if (rules.isEmpty) categories else categories.filter { !rules.isOff(it) }
        }

    /** Every category of [kind], switched-off ones included — the Categories screen's list. */
    fun observeAllCategories(kind: StreamKind): Flow<List<Category>> = categoryDao.observe(kind)

    /**
     * Switch categories (by [categoryKey]) on or off. Off takes effect for every later sync; call
     * [purgeCategories] to drop what is already stored. On only clears the flag: the rows come
     * back with the next sync, so a re-enabled VOD category marks the catalogue stale here.
     */
    fun setCategoriesEnabled(kind: StreamKind, keys: Set<String>, enabled: Boolean) {
        val current = settings.disabledCategories.value
        settings.setDisabledCategories(if (enabled) current - keys else current + keys)
        // A live category switched on by hand stays on even if a language rule would drop it.
        if (kind == StreamKind.LIVE) {
            val forced = settings.categoryRules.value.forcedOn
            settings.setForcedOnCategories(if (enabled) forced + keys else forced - keys)
        }
        if (enabled && kind != StreamKind.LIVE) settings.vodSyncedAtMillis = 0L
    }

    /**
     * Sets the live language rules and purges what they now switch off. Turning a rule on also
     * drops the hand-made "keep on" choices it covers, so "Italian only" means exactly that.
     * Returns true when something may have come back on (the caller schedules a refresh).
     */
    suspend fun setLanguageRules(italianOnly: Boolean, foreignSportOff: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val before = settings.categoryRules.value
            val after = before.copy(italianOnly = italianOnly, foreignSportOff = foreignSportOff)
            val live = categoryDao.allByKind(StreamKind.LIVE)
            val newlyRuled = live.filter { after.ruleHides(it.name) && !before.ruleHides(it.name) }
                .mapTo(HashSet()) { categoryKey(it) }
            settings.setForcedOnCategories(before.forcedOn - newlyRuled)
            settings.setLanguageRules(italianOnly, foreignSportOff)
            val rules = settings.categoryRules.value
            val offNow = live.filter { rules.isOff(it) && !before.isOff(it) }.mapTo(HashSet()) { categoryKey(it) }
            if (offNow.isNotEmpty()) purgeCategories(StreamKind.LIVE, offNow)
            live.any { before.isOff(it) && !rules.isOff(it) }
        }

    /** Deletes the stored channels/titles of the categories behind [keys]. */
    suspend fun purgeCategories(kind: StreamKind, keys: Set<String>) = withContext(Dispatchers.IO) {
        categoryDao.allByKind(kind)
            .filter { categoryKey(it) in keys }
            .groupBy({ it.sourceId }, { it.id })
            .forEach { (sourceId, ids) ->
                ids.chunked(SQL_CHUNK).forEach { chunk ->
                    when (kind) {
                        StreamKind.LIVE -> channelDao.deleteInCategories(sourceId, chunk)
                        StreamKind.MOVIE -> movieDao.deleteInCategories(sourceId, chunk)
                        StreamKind.SERIES -> seriesDao.deleteInCategories(sourceId, chunk)
                    }
                }
            }
    }

    /** Ids among [categories] that are switched off right now. */
    private fun switchedOffIds(categories: List<Category>): Set<String> {
        val rules = settings.categoryRules.value
        if (rules.isEmpty) return emptySet()
        return categories.filter { rules.isOff(it) }.mapTo(HashSet()) { it.id }
    }

    /**
     * One category's films for the browse grid (capped at [BROWSE_LIMIT]); no category → nothing.
     * There is deliberately no "every movie" Flow: on a 300k-title provider that alone is enough to
     * run a low-RAM box out of memory, and it re-emitted on every sync batch.
     */
    fun observeMovies(categoryId: String?): Flow<List<Movie>> =
        if (categoryId == null) flowOf(emptyList())
        else movieDao.observeInCategory(categoryId, BROWSE_LIMIT).distinctUntilChanged()

    /** One category's shows for the browse grid. See [observeMovies]. */
    fun observeSeries(categoryId: String?): Flow<List<Series>> =
        if (categoryId == null) flowOf(emptyList())
        else seriesDao.observeInCategory(categoryId, BROWSE_LIMIT).distinctUntilChanged()

    fun observeEpisodes(sourceId: Long, seriesId: String): Flow<List<Episode>> =
        episodeDao.observeForSeries(sourceId, seriesId)

    fun searchChannels(query: String): Flow<List<Channel>> = channelDao.search(query)

    fun searchChannelsIncludingHidden(query: String): Flow<List<Channel>> =
        channelDao.searchIncludingHidden(query)

    fun searchMovies(query: String): Flow<List<Movie>> = movieDao.search(query)

    fun searchSeries(query: String): Flow<List<Series>> = seriesDao.search(query)

    suspend fun channel(id: Long): Channel? = channelDao.byId(id)

    suspend fun movie(id: Long): Movie? = movieDao.byId(id)

    /**
     * The IMDb id for a film, for Stremio add-on stream lookups. Resolved through the user's TMDB
     * key (using the provider's TMDB id when present, else a title+year search). Null without a key
     * or a match — the add-on feature stays inert rather than guessing.
     */
    suspend fun imdbIdFor(movie: Movie): String? = withContext(Dispatchers.IO) {
        tmdb.imdbId(title = movie.name, year = movie.year, isMovie = true, tmdbId = movie.tmdbId)
    }

    suspend fun episode(id: Long): Episode? = episodeDao.byId(id)

    suspend fun movieByStreamUrl(url: String): Movie? = movieDao.byStreamUrl(url)

    suspend fun episodeByStreamUrl(url: String): Episode? = episodeDao.byStreamUrl(url)

    suspend fun series(id: Long): app.opentv.data.model.Series? = seriesDao.byId(id)

    // ---- Netflix-style home feeds ---------------------------------------------------------------
    // All local: derived from the catalogue already on disk plus the active profile's watch history.
    // Every row is a bounded SQL read — never a scan of the whole library into memory. A provider
    // with 300k titles made the old "load everything, group in Kotlin" approach crash low-RAM boxes.

    /** "Recently Added" movies, newest first. Reactive. */
    fun recentlyAddedMovies(limit: Int = 30): Flow<List<Movie>> = movieDao.observeRecentlyAdded(limit)

    /** "Recently Added" series, newest first. Reactive. */
    fun recentlyAddedSeries(limit: Int = 30): Flow<List<Series>> = seriesDao.observeRecentlyAdded(limit)

    /** How many movies / series are on disk — a cheap COUNT the home screen uses to tell "the
     *  library grew" from "unchanged since last open" without loading every row. */
    suspend fun movieCount(): Int = withContext(Dispatchers.IO) { movieDao.count() }
    suspend fun seriesCount(): Int = withContext(Dispatchers.IO) { seriesDao.count() }

    /**
     * Movies grouped by genre for the by-genre home rows: the [maxGenres] biggest genres, each with
     * up to [perGenre] titles (newest first). A movie appears under every genre it lists — provider
     * genre strings are frequently multi-valued ("Action, Thriller" / "Action|Thriller"), so they
     * are split on comma and pipe (see [splitGenres]). Genre sizes come from a GROUP BY over the
     * raw strings; each row is then one capped query.
     */
    suspend fun moviesByGenre(maxGenres: Int = 12, perGenre: Int = 30): List<GenreGroup<Movie>> =
        withContext(Dispatchers.IO) {
            topGenres(movieDao.genreCounts(), maxGenres).mapNotNull { genre ->
                movieDao.byGenreNewest(genre, perGenre * 2)
                    .filter { genre in splitGenres(it.genre) }
                    .take(perGenre)
                    .takeIf { it.isNotEmpty() }
                    ?.let { GenreGroup(genre, it) }
            }
        }

    /** Series grouped by genre for the by-genre home rows. See [moviesByGenre]. */
    suspend fun seriesByGenre(maxGenres: Int = 12, perGenre: Int = 30): List<GenreGroup<Series>> =
        withContext(Dispatchers.IO) {
            topGenres(seriesDao.genreCounts(), maxGenres).mapNotNull { genre ->
                seriesDao.byGenreNewest(genre, perGenre * 2)
                    .filter { genre in splitGenres(it.genre) }
                    .take(perGenre)
                    .takeIf { it.isNotEmpty() }
                    ?.let { GenreGroup(genre, it) }
            }
        }

    /**
     * "Recommended for you" — a simple, explainable genre-affinity heuristic, no ML.
     *
     * Tallies the genres of the movies this profile has watched or resumed, then returns the
     * highest-scoring UNWATCHED movies, where a movie's score is how many of its genres the profile
     * favours (ties broken by rating, then recency). Candidates are the best-rated titles of the
     * profile's top genres, so only a few hundred rows are read. With no usable history — a fresh
     * profile, or only watched movies that carry no genre — it falls back to top-rated.
     */
    suspend fun recommendedMovies(profileId: Long, limit: Int = 30): List<Movie> =
        withContext(Dispatchers.IO) {
            val watchedIds = watchedMovieIds(profileId)
            val watched = watchedIds.toList().chunked(500).flatMap { movieDao.byIds(it) }

            val affinity = HashMap<String, Int>()
            for (movie in watched) {
                for (genre in splitGenres(movie.genre)) affinity[genre] = (affinity[genre] ?: 0) + 1
            }

            suspend fun topRatedFallback(): List<Movie> =
                movieDao.topRated(limit + watchedIds.size).filter { it.id !in watchedIds }.take(limit)

            if (affinity.isEmpty()) return@withContext topRatedFallback()

            val candidates = LinkedHashMap<Long, Movie>()
            affinity.entries.sortedByDescending { it.value }.take(RECOMMEND_GENRES).forEach { (genre, _) ->
                for (m in movieDao.byGenreTopRated(genre, limit * 3)) candidates.putIfAbsent(m.id, m)
            }
            val ranked = candidates.values
                .filter { it.id !in watchedIds }
                .map { it to genreScore(it.genre, affinity) }
                .filter { it.second > 0 }
                .sortedWith(
                    compareByDescending<Pair<Movie, Int>> { it.second }
                        .thenByDescending { it.first.rating ?: -1.0 }
                        .thenByDescending { it.first.addedMillis },
                )
                .map { it.first }
                .take(limit)

            ranked.ifEmpty { topRatedFallback() }
        }

    /** The movie ids this profile has watched or resumed — the input to the affinity heuristic. */
    private suspend fun watchedMovieIds(profileId: Long): Set<Long> =
        positionDao.forProfile(profileId).mapNotNull { movieIdFromMediaKey(it.mediaKey) }.toSet()

    /**
     * "More Like This" for a movie: other titles sharing a genre, most genres in common first,
     * same-source titles preferred, the film itself excluded. Falls back to other titles in the
     * same source/category when the movie has no genre metadata to match on.
     */
    suspend fun moreLikeThis(movie: Movie, limit: Int = 20): List<Movie> = withContext(Dispatchers.IO) {
        val genres = splitGenres(movie.genre).toSet()
        if (genres.isEmpty()) {
            return@withContext movieDao.similarByCategory(movie.sourceId, movie.categoryId, movie.id, limit)
        }
        val candidates = LinkedHashMap<Long, Movie>()
        genres.take(RECOMMEND_GENRES).forEach { genre ->
            for (m in movieDao.byGenreTopRated(genre, SIMILAR_CANDIDATES)) candidates.putIfAbsent(m.id, m)
        }
        rankSimilar(candidates.values, movie.id, movie.sourceId, genres, limit, Movie::id, Movie::sourceId, Movie::genre, Movie::rating)
    }

    // ---- Related by person (Plex-style "click an actor / director") ----------------------------

    /** Movies a person is billed in, best-rated first. Blank query yields nothing. */
    suspend fun moviesWithActor(name: String, limit: Int = 40): List<Movie> = withContext(Dispatchers.IO) {
        name.trim().takeIf { it.isNotEmpty() }?.let { movieDao.moviesWithActor(it, limit) }.orEmpty()
    }

    /** Movies a person directed, best-rated first. */
    suspend fun moviesByDirector(name: String, limit: Int = 40): List<Movie> = withContext(Dispatchers.IO) {
        name.trim().takeIf { it.isNotEmpty() }?.let { movieDao.moviesByDirector(it, limit) }.orEmpty()
    }

    /** Series a person is billed in, best-rated first. */
    suspend fun seriesWithActor(name: String, limit: Int = 40): List<Series> = withContext(Dispatchers.IO) {
        name.trim().takeIf { it.isNotEmpty() }?.let { seriesDao.seriesWithActor(it, limit) }.orEmpty()
    }

    /**
     * Everything in the library featuring a person — the data behind the Person screen. Merges movies
     * they act in, movies they directed and series they act in, de-duplicating movies that credit the
     * same person as both actor and director. Movies (best-rated first) lead, then series.
     */
    suspend fun titlesWithPerson(name: String, perKind: Int = 40): List<PersonTitle> =
        withContext(Dispatchers.IO) {
            val query = name.trim()
            if (query.isEmpty()) return@withContext emptyList()

            val movies = LinkedHashMap<Long, Movie>()
            for (m in movieDao.moviesWithActor(query, perKind)) movies[m.id] = m
            for (m in movieDao.moviesByDirector(query, perKind)) movies.putIfAbsent(m.id, m)

            val orderedMovies = movies.values.sortedWith(
                compareByDescending<Movie> { it.rating ?: -1.0 }.thenByDescending { it.addedMillis },
            )
            val series = seriesDao.seriesWithActor(query, perKind)

            buildList(orderedMovies.size + series.size) {
                orderedMovies.forEach { add(PersonTitle.MovieItem(it)) }
                series.forEach { add(PersonTitle.SeriesItem(it)) }
            }
        }

    /** "More Like This" for a series. See [moreLikeThis]. */
    suspend fun moreLikeThisSeries(series: Series, limit: Int = 20): List<Series> = withContext(Dispatchers.IO) {
        val genres = splitGenres(series.genre).toSet()
        if (genres.isEmpty()) {
            return@withContext seriesDao.similarByCategory(series.sourceId, series.categoryId, series.id, limit)
        }
        val candidates = LinkedHashMap<Long, Series>()
        genres.take(RECOMMEND_GENRES).forEach { genre ->
            for (s in seriesDao.byGenreTopRated(genre, SIMILAR_CANDIDATES)) candidates.putIfAbsent(s.id, s)
        }
        rankSimilar(candidates.values, series.id, series.sourceId, genres, limit, Series::id, Series::sourceId, Series::genre, Series::rating)
    }

    /**
     * Loads a movie and, if its detail fields are still bare, back-fills them from the provider's
     * `get_vod_info` and persists the merged row before returning it. Safe to call whenever a detail
     * screen opens: it runs off the main thread, only touches the network when something is missing,
     * tolerates any failure (returning the row unchanged), and — because it copies the stored row —
     * preserves the favourite flag. Non-Xtream sources are returned as-is.
     */
    suspend fun movieDetail(id: Long): Movie? = withContext(Dispatchers.IO) {
        val movie = movieDao.byId(id) ?: return@withContext null
        var result = movie
        val source = sourceDao.byId(movie.sourceId)

        // 1) Provider back-fill from get_vod_info, for a still-bare row on an Xtream source.
        if (!result.isEnriched && source?.kind == SourceKind.XTREAM) {
            val info = runCatching { api.movieInfo(source, result.streamId) }.getOrNull()
            if (info != null) {
                result = result.copy(
                    backdropUrl = result.backdropUrl ?: info.backdropUrl,
                    cast = result.cast ?: info.cast,
                    director = result.director ?: info.director,
                    genre = result.genre ?: info.genre,
                    tmdbId = result.tmdbId ?: info.tmdbId,
                    plot = result.plot ?: info.plot,
                    rating = result.rating ?: info.rating,
                    year = result.year ?: info.year,
                    durationSeconds = result.durationSeconds ?: info.durationSeconds,
                )
            }
        }

        // 2) TMDB fallback for whatever the provider still left blank — only when the user set a
        //    key, and only while a headline visual field is missing (so it stops re-fetching once
        //    filled). This is the "match Plex" back-fill for posters, backdrops, synopsis and cast.
        if (tmdb.isConfigured() && (result.backdropUrl == null || result.plot == null || result.cast == null)) {
            val meta = runCatching {
                tmdb.movieMeta(VodTitleCleaner.clean(result.name), result.year, result.tmdbId)
            }.getOrNull()
            if (meta != null) {
                result = result.copy(
                    posterUrl = result.posterUrl ?: meta.posterUrl,
                    backdropUrl = result.backdropUrl ?: meta.backdropUrl,
                    plot = result.plot ?: meta.overview,
                    cast = result.cast ?: meta.cast,
                    director = result.director ?: meta.director,
                    genre = result.genre ?: meta.genre,
                    tmdbId = result.tmdbId ?: meta.tmdbId,
                    rating = result.rating ?: meta.rating,
                    year = result.year ?: meta.year,
                )
            }
        }

        if (result != movie) movieDao.upsertAll(listOf(result))
        result
    }

    /** Loads a series and lazily back-fills its detail fields from `get_series_info`. See [movieDetail]. */
    suspend fun seriesDetail(id: Long): Series? = withContext(Dispatchers.IO) {
        val series = seriesDao.byId(id) ?: return@withContext null
        var result = series
        val source = sourceDao.byId(series.sourceId)

        // 1) Provider back-fill from get_series_info, for a still-bare row on an Xtream source.
        if (!result.isEnriched && source?.kind == SourceKind.XTREAM) {
            val info = runCatching { api.seriesInfo(source, result.seriesId) }.getOrNull()
            if (info != null) {
                result = result.copy(
                    backdropUrl = result.backdropUrl ?: info.backdropUrl,
                    cast = result.cast ?: info.cast,
                    genre = result.genre ?: info.genre,
                    tmdbId = result.tmdbId ?: info.tmdbId,
                    plot = result.plot ?: info.plot,
                    rating = result.rating ?: info.rating,
                    year = result.year ?: info.year,
                )
            }
        }

        // 2) TMDB fallback for anything still missing (see [movieDetail]); TMDB has no director for TV.
        if (tmdb.isConfigured() && (result.backdropUrl == null || result.plot == null || result.cast == null)) {
            val meta = runCatching {
                tmdb.seriesMeta(VodTitleCleaner.clean(result.name), result.year, result.tmdbId)
            }.getOrNull()
            if (meta != null) {
                result = result.copy(
                    posterUrl = result.posterUrl ?: meta.posterUrl,
                    backdropUrl = result.backdropUrl ?: meta.backdropUrl,
                    plot = result.plot ?: meta.overview,
                    cast = result.cast ?: meta.cast,
                    genre = result.genre ?: meta.genre,
                    tmdbId = result.tmdbId ?: meta.tmdbId,
                    rating = result.rating ?: meta.rating,
                    year = result.year ?: meta.year,
                )
            }
        }

        if (result != series) seriesDao.upsertAll(listOf(result))
        result
    }

    /**
     * Collapses a list of movies (e.g. one category's titles) into logical films with switchable
     * quality tiers — see [collapseMovieVariants]. Pure; hand it whatever list the UI is about to
     * show. Kept here as the discoverable entry point for the VOD UI.
     */
    fun collapseVariants(movies: List<Movie>): List<MovieVariantGroup> = collapseMovieVariants(movies)

    // -- feed helpers --

    /** A movie counts as "already enriched" once any headline detail field is set, so [movieDetail]
     *  re-fetches only truly-bare rows. A movie the provider has no metadata for re-fetches each open;
     *  acceptable, and it self-limits the moment anything comes back. */
    private val Movie.isEnriched: Boolean
        get() = backdropUrl != null || cast != null || genre != null || director != null

    private val Series.isEnriched: Boolean
        get() = backdropUrl != null || cast != null || genre != null

    /** Splits a provider genre string ("Action, Thriller" / "Action|Thriller") into clean genres. */
    private fun splitGenres(raw: String?): List<String> =
        raw?.split(',', '|')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            .orEmpty()

    /** The [max] biggest genres, tallied from the raw (possibly multi-valued) genre strings. */
    private fun topGenres(counts: List<app.opentv.data.db.GenreCount>, max: Int): List<String> {
        val tally = HashMap<String, Int>()
        for (c in counts) for (genre in splitGenres(c.genre)) tally[genre] = (tally[genre] ?: 0) + c.count
        return tally.entries.sortedByDescending { it.value }.take(max).map { it.key }
    }

    /** The More-Like-This ranking: most shared genres, then same source, then rating. */
    private fun <T> rankSimilar(
        candidates: Collection<T>,
        selfId: Long,
        selfSourceId: Long,
        genres: Set<String>,
        limit: Int,
        idOf: (T) -> Long,
        sourceOf: (T) -> Long,
        genreOf: (T) -> String?,
        ratingOf: (T) -> Double?,
    ): List<T> = candidates.asSequence()
        .filter { idOf(it) != selfId }
        .map { it to sharedGenreCount(genreOf(it), genres) }
        .filter { it.second > 0 }
        .sortedWith(
            compareByDescending<Pair<T, Int>> { it.second }
                .thenByDescending { sourceOf(it.first) == selfSourceId }
                .thenByDescending { ratingOf(it.first) ?: -1.0 },
        )
        .map { it.first }
        .take(limit)
        .toList()

    /** How strongly a title matches a profile's genre affinity: sum of its genres' tallies. */
    private fun genreScore(genre: String?, affinity: Map<String, Int>): Int =
        splitGenres(genre).sumOf { affinity[it] ?: 0 }

    /** How many of a title's genres are in the wanted set — the More-Like-This overlap. */
    private fun sharedGenreCount(genre: String?, wanted: Set<String>): Int =
        splitGenres(genre).count { it in wanted }

    /** Extracts a movie's local id from a playback-position mediaKey ("movie:42" → 42). */
    private fun movieIdFromMediaKey(mediaKey: String): Long? {
        val parts = mediaKey.split(":", limit = 2)
        return if (parts.size == 2 && parts[0] == "movie") parts[1].toLongOrNull() else null
    }

    suspend fun setChannelFavourite(id: Long, favourite: Boolean) =
        channelDao.setFavourite(id, favourite)

    suspend fun setChannelHidden(id: Long, hidden: Boolean) = channelDao.setHidden(id, hidden)

    /** Sets (or clears, on blank) a channel's manual rename. Trimmed; a blank name clears it. */
    suspend fun setChannelCustomName(id: Long, name: String?) =
        channelDao.setCustomName(id, name?.trim()?.takeIf { it.isNotBlank() })

    suspend fun setChannelSortIndex(id: Long, sortIndex: Int) = channelDao.setSortIndex(id, sortIndex)

    // --- Web channel manager: one-shot reads for the socket-thread server ------------------------
    // The manager server handles one HTTP request at a time off the UI thread and wants plain
    // lists, not Flows. These are read-only pass-throughs; every mutation still goes through the
    // suspend setters above (setChannelHidden/Favourite/CustomName/SortIndex).

    /** Enabled providers, for the manager's source selector. */
    suspend fun enabledSources(): List<Source> = sourceDao.enabled()

    /** Every live category (raw, un-folded), for the manager's category list. */
    suspend fun liveCategories(): List<Category> {
        val rules = settings.categoryRules.value
        return categoryDao.allByKind(StreamKind.LIVE).filter { !rules.isOff(it) }
    }

    /** Channel counts per (source, category) — the number shown next to each category. */
    suspend fun categoryChannelCounts(): List<app.opentv.data.db.CategoryChannelCount> =
        channelDao.channelCountsByCategory()

    /** One category's channels, hidden included, optionally scoped to a single source. */
    suspend fun channelsInCategoryForManager(sourceId: Long?, categoryId: String): List<Channel> =
        channelDao.channelsInCategoryIncludingHidden(sourceId, categoryId)

    suspend fun setMovieFavourite(id: Long, favourite: Boolean) =
        movieDao.setFavourite(id, favourite)

    // --- Sync helpers: read/apply curation by stable stream URL ---
    suspend fun favouriteChannelUrls(): List<String> = channelDao.favouriteUrls()
    suspend fun hiddenChannelUrls(): List<String> = channelDao.hiddenUrls()
    suspend fun markChannelFavouriteByUrl(url: String) = channelDao.markFavouriteByUrl(url)
    suspend fun markChannelHiddenByUrl(url: String) = channelDao.markHiddenByUrl(url)
    suspend fun favouriteMovieUrls(): List<String> = movieDao.favouriteUrls()
    suspend fun markMovieFavouriteByUrl(url: String) = movieDao.markFavouriteByUrl(url)
    suspend fun favouriteSeriesIds(): List<String> = seriesDao.favouriteSeriesIds()
    suspend fun markSeriesFavouriteBySeriesId(seriesId: String) =
        seriesDao.markFavouriteBySeriesId(seriesId)

    /** Series episodes are fetched lazily — panels are slow and most series are never opened. */
    suspend fun ensureEpisodes(source: Source, seriesId: String) {
        if (source.kind != SourceKind.XTREAM) return
        runCatching { api.episodes(source, seriesId) }
            .onSuccess { if (it.isNotEmpty()) episodeDao.upsertAll(it) }
            .onFailure { Log.w(TAG, "Episode fetch failed for series $seriesId", it) }
    }

    /**
     * Live catalogue for one source, honouring the Live TV toggle. Used by the periodic worker and
     * by a manual refresh; VOD follows separately through [syncVodCatalogue].
     */
    suspend fun sync(source: Source, nowUtcMillis: Long): SyncResult = withContext(Dispatchers.IO) {
        // Skip fetching a content type the user has switched off — that's the whole speed-up.
        // Live is gated here (not in syncLive) so onboarding's direct syncLive still loads channels.
        // Already-synced rows are left untouched: turning a type back on and refreshing restores it.
        // Live only: VOD is refreshed separately by [syncVodCatalogue], once per pass over all
        // sources, gated on its own TTL and rate-capped so it never starves the live stream.
        if (settings.liveEnabled.value) syncLive(source, nowUtcMillis)
        else SyncResult.Success(0, 0, 0)
    }

    /** Whether a stored VOD catalogue is older than [VOD_TTL_MILLIS] (or was never fetched). */
    fun vodStale(nowUtcMillis: Long): Boolean {
        val last = settings.vodSyncedAtMillis
        return last <= 0L || nowUtcMillis - last >= VOD_TTL_MILLIS
    }

    /** Live channels only — the fast path so the guide can show before VOD and the guide load. */
    suspend fun syncLive(source: Source, nowUtcMillis: Long): SyncResult = withContext(Dispatchers.IO) {
        try {
            when (source.kind) {
                SourceKind.XTREAM -> syncXtreamLive(source, nowUtcMillis)
                SourceKind.M3U -> syncM3u(source, nowUtcMillis)
                SourceKind.STALKER -> syncStalkerLive(source, nowUtcMillis)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Catalogue sync failed for source ${source.id}", e)
            SyncResult.Failed(e.message ?: "The catalogue could not be downloaded.", e)
        }
    }

    /**
     * Movies + series for every Xtream source in [sources] — best-effort and gradual: titles land
     * in the DB batch by batch, so the Movies/Shows rows fill in while it runs, and [vodProgress]
     * counts them. Skipped when the catalogue is still fresh unless [force]. A call that arrives
     * while another sync runs waits for it, then normally finds the catalogue fresh and returns.
     * [throttled] caps the download at [VOD_BACKGROUND_BYTES_PER_SECOND] — for every run the user
     * is not actively waiting on. Returns true when a sync ran and stamped the catalogue.
     */
    suspend fun syncVodCatalogue(
        sources: List<Source>,
        nowUtcMillis: Long,
        force: Boolean = false,
        throttled: Boolean = true,
    ): Boolean = withContext(Dispatchers.IO) {
        vodMutex.withLock {
            if (!force && !vodStale(nowUtcMillis)) return@withLock false
            val xtream = sources.filter { it.kind == SourceKind.XTREAM }
            if (xtream.isEmpty()) return@withLock false
            val rate = if (throttled) VOD_BACKGROUND_BYTES_PER_SECOND else 0L
            _vodProgress.value = VodProgress(0, 0)
            try {
                var ok = false
                for (source in xtream) {
                    runCatching { syncXtreamVod(source, nowUtcMillis, rate) }
                        .onSuccess { if (it) ok = true }
                        .onFailure {
                            if (it is CancellationException) throw it
                            Log.w(TAG, "VOD sync failed for source ${source.id}", it)
                        }
                }
                // Stamp only when something synced, so a failure retries on the next trigger
                // instead of being remembered as "fresh" with no catalogue behind it.
                if (ok) settings.vodSyncedAtMillis = nowUtcMillis
                ok
            } finally {
                _vodProgress.value = null
            }
        }
    }

    private suspend fun syncXtreamLive(source: Source, nowUtcMillis: Long): SyncResult {
        // Authenticate first so a wrong password produces a clear message rather than
        // four separate confusing failures further down.
        api.authenticate(source)

        val liveCategories = api.liveCategories(source)
        val channels = api.liveStreams(source)
        if (channels.isEmpty()) {
            return SyncResult.Failed(
                "The server returned no channels. The account may have no package assigned.",
                null,
            )
        }

        categoryDao.upsertAll(liveCategories)
        val stored = storeLive(source.id, channels, liveCategories, nowUtcMillis)
        sourceDao.markCatalogSynced(source.id, nowUtcMillis)
        return SyncResult.Success(stored, 0, 0)
    }

    /**
     * Writes a freshly downloaded live list, minus the switched-off categories. If every channel is
     * in a switched-off category the source's stored channels are simply cleared —
     * [ChannelDao.replaceCatalogue] ignores an empty list so a broken download can't wipe the app.
     */
    private suspend fun storeLive(
        sourceId: Long,
        channels: List<Channel>,
        categories: List<Category>,
        nowUtcMillis: Long,
    ): Int {
        val off = switchedOffIds(categories)
        val kept = if (off.isEmpty()) channels else channels.filter { it.categoryId !in off }
        if (kept.isEmpty()) {
            channelDao.deleteStale(sourceId, nowUtcMillis)
        } else {
            val categoryNames = categories.associate { it.id to it.name }
            channelDao.replaceCatalogue(sourceId, normalized(kept, categoryNames), nowUtcMillis)
        }
        return kept.size
    }

    /**
     * Stalker/Ministra live sync. Mirrors [syncXtreamLive] but the channels it stores carry a
     * [Channel.cmd] instead of a directly-playable URL — [resolvePlaybackUrl] mints the real URL at
     * play time. The handshake happens inside [StalkerApi] on first call; a bad MAC or URL throws
     * here with a clear message, caught by [syncLive].
     */
    private suspend fun syncStalkerLive(source: Source, nowUtcMillis: Long): SyncResult {
        val liveCategories = stalkerApi.liveCategories(source)
        val channels = stalkerApi.liveChannels(source)
        if (channels.isEmpty()) {
            return SyncResult.Failed(
                "The portal returned no channels. The MAC may not be authorised, or its package is empty.",
                null,
            )
        }
        categoryDao.upsertAll(liveCategories)
        val stored = storeLive(source.id, channels, liveCategories, nowUtcMillis)
        sourceDao.markCatalogSynced(source.id, nowUtcMillis)
        return SyncResult.Success(stored, 0, 0)
    }

    /**
     * The URL to actually feed the player for [channel]. Xtream/M3U channels already carry a playable
     * [Channel.streamUrl]; a Stalker channel's real URL is short-lived, so it's minted now via
     * create_link from the channel's [Channel.cmd]. Falls back to the stored streamUrl if resolution
     * fails, so the player surfaces an error rather than silently doing nothing.
     */
    suspend fun resolvePlaybackUrl(channel: Channel, source: Source?): String {
        if (source?.kind != SourceKind.STALKER) return channel.streamUrl
        val cmd = channel.cmd?.takeIf { it.isNotBlank() } ?: return channel.streamUrl
        return runCatching { stalkerApi.createLink(source, cmd) }.getOrNull() ?: channel.streamUrl
    }

    /** Returns false when an enabled content type failed to download (so the TTL is not stamped). */
    private suspend fun syncXtreamVod(source: Source, nowUtcMillis: Long, maxBytesPerSecond: Long): Boolean {
        var failed = false
        // VOD is optional: plenty of accounts have live TV only, and a 404 on get_vod_streams
        // must not cost the user their channel list. Movies and series are gated independently so
        // a user who only turned off, say, Series still gets their movie library refreshed.
        //
        // Titles are streamed straight from the response into the database in batches; the full
        // list never exists in memory. Stale titles are pruned only after a stream that completed —
        // a cut connection leaves the previous catalogue (plus whatever new arrived) intact.
        //
        // Titles in switched-off categories are dropped per batch (read live, so switching one off
        // mid-sync takes hold straight away); never stamped, they are pruned with the stale ones.
        if (settings.moviesEnabled.value) {
            val categories = runCatching { api.movieCategories(source) }.getOrNull().orEmpty()
            if (categories.isNotEmpty()) categoryDao.upsertAll(categories)
            try {
                val count = api.movies(source, maxBytesPerSecond = maxBytesPerSecond) { batch ->
                    val off = switchedOffIds(categories)
                    val kept = if (off.isEmpty()) batch else batch.filter { it.categoryId !in off }
                    movieDao.mergeBatch(source.id, kept, nowUtcMillis)
                    _vodProgress.update { it?.copy(movies = it.movies + kept.size) }
                }
                if (count > 0) movieDao.deleteStale(source.id, nowUtcMillis)
                Log.i(TAG, "VOD: $count movies for source ${source.id}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Movie sync failed for source ${source.id}", e)
                failed = true
            }
        }
        if (settings.seriesEnabled.value) {
            val categories = runCatching { api.seriesCategories(source) }.getOrNull().orEmpty()
            if (categories.isNotEmpty()) categoryDao.upsertAll(categories)
            try {
                val count = api.series(source, maxBytesPerSecond = maxBytesPerSecond) { batch ->
                    val off = switchedOffIds(categories)
                    val kept = if (off.isEmpty()) batch else batch.filter { it.categoryId !in off }
                    seriesDao.mergeBatch(source.id, kept, nowUtcMillis)
                    _vodProgress.update { it?.copy(series = it.series + kept.size) }
                }
                if (count > 0) seriesDao.deleteStale(source.id, nowUtcMillis)
                Log.i(TAG, "VOD: $count series for source ${source.id}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Series sync failed for source ${source.id}", e)
                failed = true
            }
        }
        return !failed
    }

    private suspend fun syncM3u(source: Source, nowUtcMillis: Long): SyncResult {
        val request = Request.Builder()
            .url(source.url)
            .header("User-Agent", source.userAgent)
            .build()

        val parsed = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return SyncResult.Failed("Playlist download failed (HTTP ${response.code}).", null)
            }
            val stream = response.body?.byteStream()
                ?: return SyncResult.Failed("The playlist was empty.", null)
            M3uParser.parse(stream, source.id)
        }

        if (parsed.channels.isEmpty()) {
            return SyncResult.Failed(
                "No channels found in that playlist. Check the URL points at an M3U file.",
                null,
            )
        }

        // Synthesise categories from group-title so the UI has something to group by.
        val categories = parsed.channels
            .mapNotNull { it.categoryId }
            .distinct()
            .sorted()
            .mapIndexed { index, name ->
                Category(
                    id = name,
                    sourceId = source.id,
                    name = name,
                    kind = StreamKind.LIVE,
                    sortIndex = index,
                )
            }

        categoryDao.upsertAll(categories)
        val stored = storeLive(source.id, parsed.channels, categories, nowUtcMillis)

        // If the playlist declared its own guide URL and the user did not set one, adopt it.
        if (source.epgUrl.isNullOrBlank() && !parsed.declaredEpgUrl.isNullOrBlank()) {
            sourceDao.update(source.copy(epgUrl = parsed.declaredEpgUrl))
        }

        sourceDao.markCatalogSynced(source.id, nowUtcMillis)
        return SyncResult.Success(stored, 0, 0)
    }

    /**
     * Stamps every channel with its normalised identity before it is stored.
     *
     * This one pass powers both headline features: [Channel.groupKey] folds quality
     * variants of a channel into a single row and is the join key for EPG matching, and
     * [Channel.displayName] is the cleaned name the UI shows instead of `UK| BBC ONE FHD`.
     */
    private fun normalized(
        channels: List<Channel>,
        categoryNames: Map<String, String>,
    ): List<Channel> {
        val sourceTags = CategoryFamily.learnSourceTags(categoryNames.values)
        val stamped = channels.map { channel ->
            val n = ChannelNameNormalizer.normalize(channel.name, sourceTags)
            var rank = n.qualityRank
            var label = n.qualityLabel
            if (label.isEmpty() || rank == 0) {
                // Some providers put the quality in the CATEGORY, not the channel:
                // 'UK| GENERAL HD/RAW' and 'UK| GENERAL hevc' holding identically named
                // channels. Without this, the player's switch is four buttons all
                // reading 'Standard' — grouped correctly, labelled uselessly.
                val categoryName = channel.categoryId?.let { categoryNames[it] }
                if (categoryName != null) {
                    val c = ChannelNameNormalizer.normalize(categoryName)
                    if (label.isEmpty()) label = c.qualityLabel
                    if (rank == 0) rank = c.qualityRank
                }
            }
            // A source written into the name ('… FHD STELLINA') reads like a category's: 'FHD · Stellina'.
            if (n.source != null && !label.contains(n.source, ignoreCase = true)) {
                label = listOf(label, n.source).filter { it.isNotEmpty() }.joinToString(" · ")
            }
            channel.copy(
                displayName = n.baseName,
                groupKey = n.groupKey,
                qualityRank = rank,
                qualityLabel = label,
                // Providers ship decorative separator rows ('#### UK GENERAL ####') as
                // channels. They are headings, not channels — hide them on import.
                hidden = channel.hidden || isSeparatorRow(channel.name),
            )
        }

        // Separator rows are headings: '---- Basket Serie A2 ----' must not pass for a bare channel.
        val aliases = ChannelNameNormalizer.keyAliases(
            stamped.filterNot { isSeparatorRow(it.name) }.mapTo(HashSet()) { it.groupKey },
        )
        val folded = if (aliases.isEmpty()) stamped
        else stamped.map { c -> aliases[c.groupKey]?.let { c.copy(groupKey = it) } ?: c }
        return withTidiestNames(withUserMerges(folded))
    }

    /** The merges the user confirmed in Settings › Channel grouping, chains followed (a → b → c). */
    private fun withUserMerges(channels: List<Channel>): List<Channel> {
        val merges = settings.groupMerges.value.associate { it.fromKey to it.intoKey }
        if (merges.isEmpty()) return channels
        fun target(key: String): String {
            var k = key
            repeat(MAX_MERGE_HOPS) { k = merges[k] ?: return k }
            return k
        }
        // A timeshift copy follows its channel: merging `Sky Cinema Suspence` also takes `…+2`.
        fun follow(key: String): String? {
            if (key in merges) return target(key)
            val base = key.substringBefore('+')
            return if (base != key && base in merges) target(base) + key.substring(base.length) else null
        }
        return channels.map { c -> follow(c.groupKey)?.let { c.copy(groupKey = it) } ?: c }
    }

    /**
     * Rows that are probably one channel under two names, for Settings › Channel grouping to offer.
     * Hidden channels are left out; pairs already answered "Ignore" too.
     */
    suspend fun mergeSuggestions(): List<MergeSuggester.Suggestion> = withContext(Dispatchers.Default) {
        val groups = channelDao.allForMatching()
            .filter { !it.hidden && it.groupKey.isNotEmpty() }
            .groupBy { it.groupKey }
            .map { (key, chans) ->
                MergeSuggester.Group(
                    key = key,
                    name = chans.first().displayName,
                    channels = chans.size,
                    // The provider's guide id or the user's own pick only: the name matcher's guesses
                    // ('Disney Plus Film' → Discovery's guide) would suggest its own mistakes.
                    guideIds = chans.mapNotNullTo(HashSet()) { c ->
                        (c.epgOverrideId ?: c.epgChannelId)?.takeIf { it.isNotBlank() }
                    },
                )
            }
        MergeSuggester.suggest(groups, settings.ignoredMergePairs.value)
    }

    /**
     * One display name per channel: the variants of a group can be spelled `Sky Uno`, `SKY UNO`
     * and `Sky UNO` by different lists, and whichever plays first used to name the row. Prefer
     * the most common spelling that isn't all capitals.
     */
    private fun withTidiestNames(channels: List<Channel>): List<Channel> {
        val names = channels.groupBy { it.groupKey }.mapValues { (_, group) ->
            val spellings = group.map { it.displayName }
            spellings.filter { n -> n.any { it.isLowerCase() } }.ifEmpty { spellings }
                .groupingBy { it }.eachCount()
                .maxByOrNull { it.value }!!.key
        }
        return channels.map { c ->
            val name = names[c.groupKey] ?: return@map c
            if (c.groupKey.isEmpty() || name == c.displayName) c else c.copy(displayName = name)
        }
    }

    /**
     * Decorative list headings: starts AND ends with a run of banner characters (`#### UK ####`,
     * `--Sky Sport (Hermes)--`, `== DAZN AMICHEVOLI ==`), or is wrapped in a single ornament
     * (`◈ DAZN KALI ◈`).
     */
    private fun isSeparatorRow(rawName: String): Boolean {
        val t = rawName.trim()
        if (t.length < 6) return false
        if (t.first() in ORNAMENT_CHARS && t.last() in ORNAMENT_CHARS) return true
        return t.take(2).all { it in SEPARATOR_CHARS } && t.takeLast(2).all { it in SEPARATOR_CHARS }
    }

    /**
     * Re-cleans every stored channel with the current normaliser, no network needed.
     *
     * displayName, groupKey, quality and separator-hiding are computed at import time, so a
     * change to the normaliser (a new superscript char, a new junk pattern) does not reach
     * channels already in the database until the next full catalogue sync — which can be
     * hours away. This runs the same pass over existing rows locally, so a code fix shows up
     * on the next launch instead of the next sync. Bump [NORMALIZER_VERSION] to trigger it.
     */
    suspend fun renormalizeAll(): Int = withContext(Dispatchers.IO) {
        val existing = channelDao.allForMatching()
        if (existing.isEmpty()) return@withContext 0

        val bySource = existing.groupBy { it.sourceId }
        var changed = 0
        for ((_, channels) in bySource) {
            val names = channelCategoryNames(channels)
            val renamed = normalized(channels, names)
            // Only write rows that actually changed, to keep the write small.
            val diff = renamed.filterIndexed { i, c ->
                val old = channels[i]
                c.displayName != old.displayName || c.groupKey != old.groupKey ||
                    c.qualityLabel != old.qualityLabel || c.qualityRank != old.qualityRank ||
                    c.hidden != old.hidden
            }
            if (diff.isNotEmpty()) {
                diff.chunked(500).forEach { channelDao.upsertAll(it) }
                changed += diff.size
            }
        }
        Log.i(TAG, "Re-normalised $changed channels with the current normaliser")
        changed
    }

    /** Best-effort category-id → name map for a set of channels. */
    private suspend fun channelCategoryNames(channels: List<Channel>): Map<String, String> {
        val ids = channels.mapNotNull { it.categoryId }.toSet()
        if (ids.isEmpty()) return emptyMap()
        return categoryDao.namesFor(ids).associate { it.id to it.name }
    }

    /**
     * The switchable quality variants of a channel, best first, ONE per distinct quality.
     *
     * A provider often lists the same stream in several categories — PRIME American Crimes
     * shows up twice, both RAW. Those are not "qualities"; offering a switch between two
     * identical (and sometimes one dead) feeds is worse than useless. So variants are
     * de-duplicated by their quality signature: only genuinely different qualities survive,
     * and a channel that is really single-quality gets no switch at all.
     */
    suspend fun variants(channel: Channel): List<Channel> {
        if (channel.groupKey.isEmpty()) return listOf(channel)
        val all = channelDao.variantsInGroup(channel.groupKey)
        if (!settings.mergeCategorySources.value) return distinctByQuality(all).ifEmpty { listOf(channel) }
        // The same quality from different restream sources ('Sky Sport (Hermes)' vs '(SAT)') is a
        // real alternative — one may be down while the other plays — so it stays, labelled
        // "FHD · Hermes", and the fallback walks through it. Plain dupes still collapse.
        val ids = all.mapNotNullTo(HashSet()) { it.categoryId }
        val sources = if (ids.isEmpty()) emptyMap() else categoryDao.namesFor(ids)
            .associate { it.id to CategoryFamily.of(it.name).source }
        val seen = HashSet<String>()
        return all.mapNotNull { v ->
            val source = sources[v.categoryId]
            // "4K · 4K" says nothing twice: a source tag that is already the quality stays off.
            val labelled = if (source == null || v.qualityLabel.contains(source, ignoreCase = true)) v
            else v.copy(qualityLabel = listOf(v.qualityLabel, source).filter { it.isNotEmpty() }.joinToString(" · "))
            // One entry per label: 'HD · Hermes' from the name and from the category is one feed choice.
            labelled.takeIf { seen.add("${it.qualityRank}|${it.qualityLabel.lowercase()}") }
        }.ifEmpty { listOf(channel) }
    }

    /**
     * The record menu's "record from which provider" options: one channel per source that carries
     * this logical channel, each that source's best-quality copy, the current source first. With two
     * providers this lets a recording run on one account while the user keeps watching on the other —
     * the only real way around a provider's single-connection limit (which even TiviMate can't dodge).
     */
    suspend fun recordSourceOptions(channel: Channel): List<Channel> = withContext(Dispatchers.IO) {
        if (channel.groupKey.isEmpty()) return@withContext listOf(channel)
        channelDao.variantsInGroup(channel.groupKey)
            .groupBy { it.sourceId }
            .map { (_, chans) -> chans.maxByOrNull { it.qualityRank } ?: chans.first() }
            .sortedByDescending { it.sourceId == channel.sourceId }
    }

    /**
     * Switches an Xtream source's live-stream container (HLS ↔ MPEG-TS) and rewrites its live
     * channels' playback URLs in place, so the change takes effect immediately without a re-sync.
     *
     * Xtream only: an M3U source's channel URLs come straight from its playlist and must not be
     * rebuilt from credentials. The `channels` table is live-only (movies and series have their own
     * tables), so every row here is a live channel whose URL derives from [XtreamApi.liveStreamUrl].
     */
    suspend fun setLiveFormat(sourceId: Long, format: LiveStreamFormat) = withContext(Dispatchers.IO) {
        val source = sourceDao.byId(sourceId) ?: return@withContext
        if (source.kind != SourceKind.XTREAM || source.liveFormat == format) return@withContext
        val updated = source.copy(liveFormat = format)
        sourceDao.update(updated)
        // Rebuild each live channel's URL for the new container.
        channelDao.forSource(sourceId).forEach { channel ->
            val newUrl = api.liveStreamUrl(updated, channel.streamId)
            if (newUrl != channel.streamUrl) channelDao.updateStreamUrl(channel.id, newUrl)
        }
    }

    suspend fun deleteSource(sourceId: Long) = withContext(Dispatchers.IO) {
        channelDao.deleteForSource(sourceId)
        categoryDao.deleteForSource(sourceId)
        movieDao.deleteForSource(sourceId)
        seriesDao.deleteForSource(sourceId)
        episodeDao.deleteForSource(sourceId)
        sourceDao.delete(sourceId)
    }

    companion object {
        private const val TAG = "CatalogRepository"

        /** Ids per `IN (...)` statement, well under SQLite's bound-variable cap. */
        private const val SQL_CHUNK = 500

        /**
         * Stable identity of a category for the on/off switch, scoped by kind. Live categories fold
         * like the guide rail does ('UK| GENERAL HD' and 'UK| GENERAL hevc' are one switch); VOD
         * categories are shown verbatim, so they key on their plain name — folding would merge
         * shelves like 'Film 4k UHD' and 'Film FHD 1080p' that are genuinely different.
         */
        fun categoryKey(category: Category): String {
            val identity = if (category.kind == StreamKind.LIVE) {
                ChannelNameNormalizer.normalize(category.name).groupKey.ifEmpty { category.id }
            } else {
                category.name.trim().lowercase().ifEmpty { category.id }
            }
            return "${category.kind}:$identity"
        }

        /** What the Categories screen calls [category] — the same label its rail/chip shows. */
        fun categoryLabel(category: Category): String =
            if (category.kind == StreamKind.LIVE) {
                ChannelNameNormalizer.normalize(category.name).baseName.ifBlank { category.name }
            } else {
                category.name.trim()
            }

        /** Most titles a category grid holds at once. Bigger categories show their newest. */
        const val BROWSE_LIMIT = 3000

        /** How many of the profile's / title's genres seed Recommended and More-Like-This. */
        private const val RECOMMEND_GENRES = 5

        /** Candidates read per genre for More-Like-This before ranking. */
        private const val SIMILAR_CANDIDATES = 120

        /** How long a fetched VOD catalogue is trusted before a background sync refreshes it. */
        const val VOD_TTL_MILLIS = 12L * 60 * 60 * 1000

        /** Download cap for VOD syncs nobody is waiting on (~6 Mbit/s): leaves room for live HD. */
        const val VOD_BACKGROUND_BYTES_PER_SECOND = 768L * 1024
        val SEPARATOR_CHARS = setOf('#', '*', '=', '~', '-', '_', '•', '█', '▓', '|', '─', '━', '═', '▬')
        val ORNAMENT_CHARS = setOf('◈', '◆', '◇', '►', '◄', '▶', '◀', '★', '☆', '✦', '❖')

        /**
         * Bump this whenever the normaliser changes in a way that should re-process
         * already-imported channels. The app compares it against a stored value on launch
         * and runs [renormalizeAll] once when it moves.
         */
        const val NORMALIZER_VERSION = 8
        private const val MAX_MERGE_HOPS = 8
    }
}
