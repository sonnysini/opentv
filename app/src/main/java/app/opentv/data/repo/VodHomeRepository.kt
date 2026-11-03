/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.repo

import app.opentv.data.db.MovieDao
import app.opentv.data.db.SeriesDao
import app.opentv.data.model.Movie
import app.opentv.data.model.Series
import app.opentv.data.parser.ChannelNameNormalizer
import app.opentv.data.parser.VodTitleCleaner
import app.opentv.data.remote.TmdbLists
import app.opentv.data.remote.TmdbLists.Chart
import app.opentv.data.remote.TmdbTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What the chart shelves need of a hit, film or show: TMDB's title, a key, the art, a corner badge. */
interface ChartHit {
    val tmdb: TmdbTitle
    val key: Long
    val posterUrl: String?
    val backdropUrl: String?
    val badge: String?
}

/** A chart entry the provider really has: TMDB's title and art, the catalogue's copies to play. */
@androidx.compose.runtime.Immutable
data class MovieHit(override val tmdb: TmdbTitle, val group: MovieVariantGroup) : ChartHit {
    val movie: Movie get() = group.primary
    /** "4K", "FHD", "HD" — the copies on offer, best first. */
    val qualities: List<String> get() = group.variants.map { it.qualityLabel }.filter { it.isNotBlank() }.distinct()
    override val key: Long get() = movie.id
    override val posterUrl: String? get() = movie.posterUrl ?: tmdb.posterUrl
    override val backdropUrl: String? get() = tmdb.backdropUrl ?: movie.backdropUrl
    override val badge: String? get() = qualities.firstOrNull()
}

/** A TV chart entry the provider has: TMDB's title and art, the catalogue's show to open. */
@androidx.compose.runtime.Immutable
data class SeriesHit(override val tmdb: TmdbTitle, val series: Series) : ChartHit {
    override val key: Long get() = series.id
    override val posterUrl: String? get() = series.posterUrl ?: tmdb.posterUrl
    override val backdropUrl: String? get() = tmdb.backdropUrl ?: series.backdropUrl
    override val badge: String? get() = null
}

/** One turn of a VOD hero: the hit, the title's own lettering when TMDB has it, its genres. */
@androidx.compose.runtime.Immutable
data class HeroHit<T : ChartHit>(val hit: T, val logoUrl: String?, val genres: String)

/** Everything a VOD home shows from TMDB's charts: hero, the week's Top 10, popular, best rated. */
@androidx.compose.runtime.Immutable
data class ChartHome<T : ChartHit>(
    val hero: List<HeroHit<T>>,
    val top10: List<T>,
    val popular: List<T>,
    val topRated: List<T>,
)

typealias MoviesHome = ChartHome<MovieHit>
typealias SeriesHome = ChartHome<SeriesHit>

/**
 * TMDB's charts, cut down to what the user's provider can play. A chart title is found by the TMDB
 * id the provider stamps on most titles; failing that, by name and year. Results are held in memory
 * for the session (TMDB's answers are already on disk for 12 hours, see [TmdbLists]).
 */
class VodHomeRepository(
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val lists: TmdbLists,
) {
    private val lock = Mutex()
    private var moviesHome: Pair<Int, MoviesHome>? = null
    private var seriesHome: Pair<Int, SeriesHome>? = null
    private val movieGenres = HashMap<Int, Pair<Int, List<MovieHit>>>()
    private val seriesGenres = HashMap<Int, Pair<Int, List<SeriesHit>>>()

    fun isConfigured(): Boolean = lists.isConfigured()

    /**
     * The Movies home, or null with no TMDB key. [catalogueSize] keys the memory cache: a sync that
     * changes the library rebuilds it.
     */
    suspend fun moviesHome(catalogueSize: Int): MoviesHome? = withContext(Dispatchers.IO) {
        if (!lists.isConfigured()) return@withContext null
        lock.withLock {
            moviesHome?.takeIf { it.first == catalogueSize }?.let { return@withContext it.second }
            val home = buildHome(TmdbLists.Kind.MOVIE, TmdbLists.MOVIE_GENRE_NAMES) { movieChart(it) }
            moviesHome = catalogueSize to home
            home
        }
    }

    /** The Shows home, as [moviesHome] for TV. */
    suspend fun seriesHome(catalogueSize: Int): SeriesHome? = withContext(Dispatchers.IO) {
        if (!lists.isConfigured()) return@withContext null
        lock.withLock {
            seriesHome?.takeIf { it.first == catalogueSize }?.let { return@withContext it.second }
            val home = buildHome(TmdbLists.Kind.TV, TmdbLists.TV_GENRE_NAMES) { seriesChart(it) }
            seriesHome = catalogueSize to home
            home
        }
    }

    /** A title's lettering for its page (see [TmdbLists.logoUrl]); null with no key or none on TMDB. */
    suspend fun logoUrl(kind: TmdbLists.Kind, tmdbId: String?): String? = withContext(Dispatchers.IO) {
        val id = tmdbId?.takeIf { it.isNotBlank() && it != "0" } ?: return@withContext null
        runCatching { lists.logoUrl(kind, id) }.getOrNull()
    }

    /** One genre tab: TMDB's most popular films of [genreId] that the provider has. */
    suspend fun movieGenre(genreId: Int, catalogueSize: Int): List<MovieHit> = withContext(Dispatchers.IO) {
        if (!lists.isConfigured()) return@withContext emptyList()
        lock.withLock {
            movieGenres[genreId]?.takeIf { it.first == catalogueSize }?.let { return@withContext it.second }
            val hits = movieChart(Chart.Genre(genreId), pages = 5).take(GENRE_SIZE)
            movieGenres[genreId] = catalogueSize to hits
            hits
        }
    }

    /** One genre tab of the Shows page. See [movieGenre]. */
    suspend fun seriesGenre(genreId: Int, catalogueSize: Int): List<SeriesHit> = withContext(Dispatchers.IO) {
        if (!lists.isConfigured()) return@withContext emptyList()
        lock.withLock {
            seriesGenres[genreId]?.takeIf { it.first == catalogueSize }?.let { return@withContext it.second }
            val hits = seriesChart(Chart.Genre(genreId), pages = 5).take(GENRE_SIZE)
            seriesGenres[genreId] = catalogueSize to hits
            hits
        }
    }

    private suspend fun <T : ChartHit> buildHome(
        kind: TmdbLists.Kind,
        genreNames: Map<Int, String>,
        chart: suspend (Chart) -> List<T>,
    ): ChartHome<T> {
        val trending = chart(Chart.Trending)
        val hero = trending.filter { it.backdropUrl != null }
            .take(HERO_SIZE)
            .map { hit ->
                HeroHit(
                    hit = hit,
                    logoUrl = runCatching { lists.logoUrl(kind, hit.tmdb.id) }.getOrNull(),
                    genres = hit.tmdb.genreIds.mapNotNull { genreNames[it] }.take(2).joinToString(", "),
                )
            }
        return ChartHome(
            hero = hero,
            top10 = trending.take(10),
            popular = chart(Chart.PopularInItaly).take(ROW_SIZE),
            topRated = chart(Chart.TopRated).take(ROW_SIZE),
        )
    }

    private suspend fun movieChart(chart: Chart, pages: Int = 3): List<MovieHit> {
        val titles = runCatching { lists.chart(TmdbLists.Kind.MOVIE, chart, pages) }.getOrDefault(emptyList())
        if (titles.isEmpty()) return emptyList()
        val byId = titles.map { it.id }.chunked(BIND_CHUNK)
            .flatMap { movieDao.byTmdbIds(it) }
            .groupBy { it.tmdbId }
        var lookups = TITLE_LOOKUPS
        val seen = HashSet<Long>()
        return titles.mapNotNull { title ->
            val copies = byId[title.id]
                ?: if (lookups-- > 0) movieByTitle(title) else null
            val group = copies?.takeIf { it.isNotEmpty() }?.let { collapseMovieVariants(it).first() }
                ?: return@mapNotNull null
            if (!seen.add(group.primary.id)) return@mapNotNull null
            MovieHit(title, group)
        }
    }

    /**
     * A TV chart cut to the provider's shows. A show often sits in more than one category (dubbed,
     * subtitled); the newest copy stands for it, one card per title.
     */
    private suspend fun seriesChart(chart: Chart, pages: Int = 3): List<SeriesHit> {
        val titles = runCatching { lists.chart(TmdbLists.Kind.TV, chart, pages) }.getOrDefault(emptyList())
        if (titles.isEmpty()) return emptyList()
        val byId = titles.map { it.id }.chunked(BIND_CHUNK)
            .flatMap { seriesDao.byTmdbIds(it) }
            .groupBy { it.tmdbId }
        var lookups = TITLE_LOOKUPS
        val seen = HashSet<Long>()
        return titles.mapNotNull { title ->
            val copies = byId[title.id]
                ?: if (lookups-- > 0) seriesByTitle(title) else null
            val show = copies?.maxByOrNull { it.addedMillis } ?: return@mapNotNull null
            if (!seen.add(show.id)) return@mapNotNull null
            SeriesHit(title, show)
        }
    }

    /** The provider's copies of [title] found by name, for films it stamped no TMDB id on. */
    private suspend fun movieByTitle(title: TmdbTitle): List<Movie> =
        byTitle(title) { probe -> movieDao.nameContains(probe, NAME_LIMIT).filter { matchesTitle(it.name, it.year, it.tmdbId, title) } }

    private suspend fun seriesByTitle(title: TmdbTitle): List<Series> =
        byTitle(title) { probe -> seriesDao.nameContains(probe, NAME_LIMIT).filter { matchesTitle(it.name, it.year, it.tmdbId, title) } }

    private suspend fun <T> byTitle(title: TmdbTitle, find: suspend (String) -> List<T>): List<T> {
        for (name in listOfNotNull(title.title, title.originalTitle)) {
            val probe = name.trim().takeIf { it.length >= MIN_PROBE } ?: continue
            val found = find(probe)
            if (found.isNotEmpty()) return found
        }
        return emptyList()
    }

    companion object {
        private const val HERO_SIZE = 6
        private const val ROW_SIZE = 20
        private const val GENRE_SIZE = 40
        private const val BIND_CHUNK = 500
        // Name lookups scan the table; a chart only gets a few for the titles the id missed.
        private const val TITLE_LOOKUPS = 12
        private const val NAME_LIMIT = 40
        private const val MIN_PROBE = 2
    }
}

/** A standalone 4-digit year inside a provider title. */
private val NAME_YEAR = Regex("""\b(19|20)\d{2}\b""")

/** A title folded for comparison: provider junk, year and quality off, then the channel-name key. */
internal fun vodTitleKey(raw: String): String =
    ChannelNameNormalizer.normalize(NAME_YEAR.replace(VodTitleCleaner.clean(raw), " ")).groupKey

/**
 * True when the provider's film [name] (with its [year] field and [tmdbId]) is TMDB's [title]: the
 * same folded name, as the Italian or the original title, and a year no more than one off when both
 * sides have one. A copy the provider tied to a different TMDB id is never taken.
 */
internal fun matchesTitle(name: String, year: Int?, tmdbId: String?, title: TmdbTitle): Boolean {
    if (!tmdbId.isNullOrBlank() && tmdbId != "0" && tmdbId != title.id) return false
    val key = vodTitleKey(name)
    if (key.isEmpty()) return false
    val sameName = key == vodTitleKey(title.title) || (title.originalTitle != null && key == vodTitleKey(title.originalTitle))
    if (!sameName) return false
    val ours = year ?: NAME_YEAR.find(name)?.value?.toIntOrNull()
    val theirs = title.year
    return ours == null || theirs == null || kotlin.math.abs(ours - theirs) <= 1
}
