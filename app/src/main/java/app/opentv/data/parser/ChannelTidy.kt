/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.parser

/**
 * The names channels really have, for the home country: the public channel database's list for it
 * plus the free guides the user has switched on. Built once per regroup.
 *
 * [names] maps a channel's base key (no timeshift) to its official spelling; [wordStyle] is how
 * the dictionary writes single words (`Rai`, `MotoGP`); [acronyms] the all-capital words it
 * keeps that way (`RTV`, `TRM`).
 */
class ChannelDictionary private constructor(
    val names: Map<String, String>,
    val wordStyle: Map<String, String>,
    val acronyms: Set<String>,
) {
    private val byFirst: Map<Char, List<String>> = names.keys.groupBy { it.first() }

    fun startingWith(c: Char): List<String> = byFirst[c].orEmpty()

    val isEmpty: Boolean get() = names.isEmpty()

    companion object {
        val EMPTY = ChannelDictionary(emptyMap(), emptyMap(), emptySet())

        /**
         * [official] (the channel database: properly cased, so it also teaches acronyms) wins over [guides]
         * (XMLTV display names, often shouted). Only Latin-script names: a key made of what's
         * left of `ФЛ` is no name.
         */
        fun of(official: List<String>, guides: List<String> = emptyList()): ChannelDictionary {
            val names = HashMap<String, String>()
            for (name in official + guides) {
                if (name.any { it.code >= LATIN_END }) continue
                val n = ChannelNameNormalizer.normalize(name)
                if (n.groupKey.length >= 2 && '+' !in n.groupKey) names.putIfAbsent(n.groupKey, n.baseName)
            }
            val counts = HashMap<String, Int>()
            for (name in names.values) for (w in name.split(' ')) {
                if (w.length >= 3 && w.first().isUpperCase() && w.any(Char::isLowerCase) && w.all(Char::isLetter)) {
                    counts.merge(w, 1, Int::plus)
                }
            }
            val wordStyle = HashMap<String, String>()
            counts.entries.sortedByDescending { it.value }.forEach { (w, _) -> wordStyle.putIfAbsent(w.lowercase(), w) }
            val acronyms = HashSet<String>()
            for (name in official) {
                if (name.any { it.code >= LATIN_END }) continue
                val words = name.split(' ').filter { it.isNotEmpty() }
                val cased = name.any(Char::isLowerCase)
                for (w in words) {
                    if (w.length in 2..5 && w.any(Char::isLetter) && w.all { it.isUpperCase() || it.isDigit() } &&
                        (cased || w.length <= 4)
                    ) acronyms += w
                }
            }
            return ChannelDictionary(names, wordStyle, acronyms)
        }

        private const val LATIN_END = 0x250
    }
}

/**
 * Grouping and naming on top of [ChannelNameNormalizer], over one source's whole list.
 *
 *  - rows are per country ([ChannelCountry]): a German Sky Nature is not the Italian one
 *  - the home country's rows meet the [ChannelDictionary]: rows naming one channel join
 *    (`History Channel` + `History`, `Sky Series` + `Sky Serie`, `Nicklodeon` + `Nickelodeon`)
 *  - typos outside the dictionary join only when one long word is one letter off (`Fisching`)
 *  - every row gets one tidy name: the list's own spelling, cased like a channel and not a
 *    shout (`EUROSPORT 1` → `Eurosport 1`, `Dmax` → `DMAX`)
 *
 * Timeshift copies are never folded into their channel: `Italia 1 +1` is Italia 1 an hour later.
 * Pure.
 */
object ChannelTidy {

    /** One channel as the tidy pass sees it. */
    data class Item(
        /** `normalize(...)` group key, before aliases, without country. */
        val key: String,
        /** `normalize(...)` base name. */
        val baseName: String,
        /** From [ChannelCountry.of]. */
        val country: String,
        /** Decorative heading rows ('#### UK ####') take no part. */
        val separator: Boolean = false,
    )

    // ---- Keys ---------------------------------------------------------------------------------

    /** The stored group key of every item: aliases, dictionary and typos applied, country prefixed. */
    fun keys(items: List<Item>, home: String, dictionary: ChannelDictionary): List<String> {
        val out = Array(items.size) { items[it].key }
        items.indices.groupBy { i -> items[i].country.let { if (ChannelCountry.isHome(it, home)) home else it } }
            .forEach { (country, idx) ->
                val resolved = resolve(idx.map { items[it] }, if (country == home) dictionary else ChannelDictionary.EMPTY)
                idx.forEachIndexed { j, i -> out[i] = ChannelCountry.keyFor(resolved[j], items[i].country, home) }
            }
        return out.toList()
    }

    private fun base(k: String) = k.substringBefore('+')
    private fun shift(k: String) = k.substring(base(k).length)

    /** One country's keys. */
    private fun resolve(items: List<Item>, dictionary: ChannelDictionary): List<String> {
        val guide = dictionary.names
        val keys0 = items.map { it.key }
        val live = items.filterNot { it.separator }.mapTo(HashSet()) { it.key }.filterTo(HashSet()) { it.isNotEmpty() }
        val aliases = ChannelNameNormalizer.keyAliases(live)
        // The brand comes off only when the dictionary doesn't know the branded name as a channel of
        // its own: `Sky Sport 1` (Germany) is not `Sport1`.
        val k1 = items.map { item ->
            val key = item.key
            if (item.separator || key.isEmpty()) key else aliases[key]?.takeIf { base(key) !in guide } ?: key
        }
        val count = HashMap<String, Int>()
        val nameByBase = HashMap<String, String>()
        items.indices.forEach { i ->
            val b = base(k1[i])
            if (b.isEmpty() || items[i].separator) return@forEach
            count.merge(b, 1, Int::plus)
            nameByBase.putIfAbsent(b, TRAILING_SHIFT.replace(items[i].baseName, ""))
        }
        fun nameOf(b: String) = guide[b] ?: nameByBase[b] ?: b
        val words = HashMap<String, List<String>>()
        fun wordsOf(name: String) = words.getOrPut(name) { name.lowercase().split(NON_WORD).filter { it.isNotEmpty() } }
        fun typo(a: String, b: String) = typoPair(wordsOf(a), wordsOf(b))

        fun guideOf(b: String): String? {
            if (b in guide) return b
            listOf("sky$b", b.removeSuffix("channel"), b.removeSuffix("tv"))
                .firstOrNull { it != b && it.length >= 3 && it in guide }?.let { return it }
            if (b.length >= TYPO_MIN_KEY) {
                val c = dictionary.startingWith(b.first()).filter { typo(guide.getValue(it), nameOf(b)) }
                if (c.size == 1) return c[0]
            }
            return null
        }

        // Team feeds (`DAZN 5 Serie B Cesena` → `cesena`) are confirmed by the team, never by the
        // dictionary: `Inter` is not `Inter Channel`.
        val teams = keys0.mapNotNullTo(HashSet()) { ChannelNameNormalizer.teamOf(base(it)) }
        val final = HashMap<String, String>()
        if (!dictionary.isEmpty) for (b in count.keys) {
            if (b in teams) continue
            guideOf(b)?.let { final[b] = it }
        }
        // Typos against another row of the same list, for rows the dictionary didn't settle.
        val known = count.keys.mapTo(HashSet()) { final[it] ?: it }
        val knownByFirst = known.groupBy { it.first() }
        for (b in count.keys) {
            if (b in final || b in teams) continue
            if (b.length >= TYPO_MIN_KEY) {
                val c = knownByFirst[b.first()].orEmpty()
                    .filter { it != b && typo(nameOf(it), nameOf(b)) && (count[it] ?: Int.MAX_VALUE) >= (count[b] ?: 0) }
                if (c.size == 1) { final[b] = c[0]; continue }
            }
            // A slip on the brand itself: `Sly Documentari` is Sky's when `Sky Documentari` exists.
            if (b.length > 3 && !b.startsWith("sky")) {
                val sky = "sky" + b.drop(3)
                if (sky in known && lev1(sky.take(3), b.take(3))) final[b] = sky
            }
        }
        return k1.mapIndexed { i, k ->
            if (items[i].separator || k.isEmpty()) k else (final[base(k)] ?: base(k)) + shift(k)
        }
    }

    // ---- Names --------------------------------------------------------------------------------

    /**
     * One name per row. [keys] are the stored keys (country-prefixed, after user merges); channels
     * a user merge [moved] in don't vote while the row has channels of its own.
     */
    fun displayNames(
        items: List<Item>,
        keys: List<String>,
        moved: BooleanArray,
        home: String,
        dictionary: ChannelDictionary,
    ): List<String> {
        val out = Array(items.size) { i -> tidyAlone(items[i].baseName, dictionary) }
        items.indices.filter { keys[it].isNotEmpty() && !items[it].separator }.groupBy { keys[it] }.forEach { (key, idx) ->
            val voters = idx.filterNot { moved[it] }.ifEmpty { idx }
            val dict = if (ChannelCountry.ofKey(key) == null) dictionary else ChannelDictionary.EMPTY
            val name = display(ChannelCountry.bare(key), voters.map { items[it] }, dict)
            idx.forEach { out[it] = name }
        }
        items.indices.filter { items[it].separator }.forEach { out[it] = items[it].baseName }
        return out.toList()
    }

    private fun display(key: String, items: List<Item>, dictionary: ChannelDictionary): String {
        val b = base(key)
        val s = shift(key)
        val names0 = items.map { it.baseName }
        val sameKey = items.filter { it.key == key }.map { it.baseName }
        val branded = items.filter { it.key in BRANDS.map { br -> br + key } }.map { it.baseName }
        val g = dictionary.names[b]
        val names = when {
            g != null && sameKey.isNotEmpty() -> sameKey
            g != null -> return styled(g, g, dictionary) + (if (s.isNotEmpty()) " $s" else "")
            branded.isNotEmpty() -> branded
            sameKey.isNotEmpty() -> sameKey
            else -> names0
        }
        if (names.all(::nonLatin)) return names.groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key
        // The timeshift is the key's, not a word to weigh: compared without, put back at the end.
        val plain = names.map { TRAILING_SHIFT.replace(it, "") }
        val freq = plain.groupingBy { it }.eachCount()
        val gSpaces = g?.count { it == ' ' }
        var pick = freq.keys.maxWithOrNull(
            compareBy<String>(
                { if (gSpaces != null && it.count { c -> c == ' ' } == gSpaces) 1 else 0 },
                { it.count { c -> c == ' ' } },
                { if (tidy(it)) 1 else 0 },
                { freq.getValue(it) },
            ),
        )!!
        // `JuveStabia` where the row's other names write `Juve Stabia`: the spaced one.
        val spaced = names0.joinToString(" | ").lowercase()
        pick = pick.split(' ').joinToString(" ") { w ->
            CAMEL_PAIR.find(w)?.groupValues?.takeIf { "${it[1]} ${it[2]}".lowercase() in spaced }
                ?.let { "${it[1]} ${it[2]}" } ?: w
        }
        val suffix = if (s.isNotEmpty()) " $s" else ""
        // Same letters, other spacing (`Sky Sport Moto GP` / `Sky Sport MotoGP`): the dictionary's —
        // only where its joins are a name's own (`MotoGP`, `TG24`, `BabyTV`), not `Solocalcio`/`RaiRadio2`.
        if (g != null && compact(pick) == compact(g)) {
            val own = pick.lowercase().split(' ').toSet()
            if (g.split(' ').filter { it.lowercase() !in own }.all(::ownJoin)) return styled(g, g, dictionary) + suffix
        }
        return styled(pick, g, dictionary) + suffix
    }

    /** `TG24`, `LA7` (a short acronym and its number) or `MotoGP`, `BabyTV` (an acronym glued on). */
    private fun ownJoin(w: String): Boolean {
        val letters = w.count(Char::isLetter)
        if (w.any(Char::isDigit) && letters <= 3) return true
        return w.any(Char::isLowerCase) && CAPS_RUN.containsMatchIn(w.drop(1))
    }

    /** First LETTER up: `1live` → `1Live`. */
    private fun cap(w: String): String {
        val i = w.indexOfFirst(Char::isLetter)
        return if (i < 0) w else w.substring(0, i) + w[i].uppercaseChar() + w.substring(i + 1)
    }

    private fun compact(n: String) = n.lowercase().filter { it.isLetterOrDigit() }

    /** A name with no row to vote in (no key): cased, nothing else. */
    private fun tidyAlone(name: String, dictionary: ChannelDictionary): String =
        if (nonLatin(name)) name else smartCase(name, dictionary)

    private fun tidy(n: String) = n.any(Char::isUpperCase) && n.any(Char::isLowerCase) &&
        n.split(' ').none { w -> w.length > 3 && w == w.uppercase() }

    private fun nonLatin(n: String) = n.any { it.isLetter() && it.code >= 0x250 }

    /** [name] cased, then any word the dictionary's name for it writes with inner capitals (`MotoGP`). */
    private fun styled(name: String, g: String?, dictionary: ChannelDictionary): String {
        val cased = smartCase(name, dictionary)
        if (g == null) return cased
        val gw = g.split(' ').associateBy { it.lowercase() }
        return cased.split(' ').joinToString(" ") { w ->
            gw[w.lowercase()]?.takeIf { x -> x.drop(1).any(Char::isUpperCase) && x.any(Char::isLowerCase) } ?: w
        }
    }

    /**
     * Channel casing: `ITALIA 1` → `Italia 1`, `Warner Tv` → `Warner TV`, `RAI SPORT` → `Rai Sport`,
     * `TV 8` stays as the normaliser left it. Known acronyms stay capitals, so do the dictionary's
     * own (`RTV`) and short ones inside a name that isn't shouted (`Sky TG24`); a deliberate mix
     * (`iCarly`) is kept; two words glued in camel case (`JuveStabia`) come apart.
     */
    fun smartCase(name: String, dictionary: ChannelDictionary = ChannelDictionary.EMPTY): String {
        val hasLower = name.any(Char::isLowerCase)
        val hasUpper = name.any(Char::isUpperCase)
        fun word(w: String, idx: Int): String {
            // `PALLAMANO-FEMMINILE`: each part on its own.
            if ('-' in w && w.length > 1) return w.split('-').joinToString("-") { if (it.isEmpty()) it else word(it, 1) }
            val u = w.uppercase()
            val l = w.lowercase()
            return when {
                u in UPPER -> u
                w == u && w.length <= 4 && dictionary.wordStyle[l] != null -> dictionary.wordStyle.getValue(l)
                u in dictionary.acronyms && w == u -> u
                hasLower && w == u && w.length in 2..3 && w.all(Char::isLetter) -> u
                // No vowel, no word: `RTTR`, `TVRS`.
                w == u && w.length in 2..5 && w.all(Char::isLetter) && w.none { it in VOWELS } -> u
                INITIALS.matches(w) -> u
                ORDINAL.matches(w) || DECADE.matches(w) -> l
                w.any(Char::isDigit) && w.any(Char::isLetter) ->
                    if (w.count(Char::isLetter) <= 3) u else cap(l)
                idx > 0 && l in SMALL -> l
                ROMAN.matches(l) -> if (l == "lll") "III" else u
                // Mixed case written on purpose (iCarly, eXtra): kept, unless the whole name shouts.
                hasUpper && hasLower && w != u && w != l && !UP_THEN_LOW.containsMatchIn(w) -> w
                else -> cap(l)
            }
        }
        return name.split(' ').filter { it.isNotEmpty() }.mapIndexed { idx, w -> word(w, idx) }.joinToString(" ")
    }

    // ---- Typos --------------------------------------------------------------------------------

    /** One letter apart (substitution, insertion or deletion). */
    fun lev1(a: String, b: String): Boolean {
        if (a == b || kotlin.math.abs(a.length - b.length) > 1) return false
        var i = 0
        var j = 0
        var d = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { i++; j++; continue }
            if (++d > 1) return false
            when {
                a.length > b.length -> i++
                a.length < b.length -> j++
                else -> { i++; j++ }
            }
        }
        return d + (a.length - i) + (b.length - j) <= 1
    }

    /** Same words but one, and that one a real word (5+ letters, no digits) one letter off: never `Serie A`/`B`. */
    private fun typoPair(x: List<String>, y: List<String>): Boolean {
        if (x.size != y.size) return false
        var diff = -1
        for (i in x.indices) if (x[i] != y[i]) { if (diff >= 0) return false; diff = i }
        if (diff < 0) return false
        val u = x[diff]
        val v = y[diff]
        return u.length >= 5 && v.length >= 5 && u.none(Char::isDigit) && v.none(Char::isDigit) && lev1(u, v)
    }

    // ---- Name prefixes --------------------------------------------------------------------------

    private val SOURCE_PREFIX = Regex("""^\s*([A-Za-z]{2,6})\s*:\s+""")
    private val ANGLE = Regex("""《[^》]*》""")

    /**
     * What the normaliser can't tell from a name: a source tag in front (`Abc: Sky Uno` — any short
     * word and a colon that isn't a country) and a `《 C 》` box. Returns the name without them and
     * the tag, which reads as a source (`HD · Abc`).
     */
    fun preClean(name: String): Pair<String, String?> {
        var s = ANGLE.replace(name, " ")
        var tag: String? = null
        SOURCE_PREFIX.find(s)?.let { m ->
            val code = m.groupValues[1]
            if (code.uppercase() !in ChannelCountry.ISO && s.length > m.range.last + 1) {
                tag = code
                s = s.removeRange(m.range)
            }
        }
        return s.trim() to tag
    }

    private val UPPER = setOf(
        "DAZN", "MTV", "HGTV", "TV", "TV8", "TV2000", "RTL", "BBC", "CNN", "HD", "UFC", "NBA", "NFL", "F1", "GP",
        "ACI", "QVC", "RSI", "SRF", "AMC", "TLC", "DMAX", "VH1", "XXX", "UHD", "4K", "MGM", "TCM", "AXN", "CNBC",
        "LA7", "RDS", "RTV", "TVA", "TG", "TGR", "ESPN", "ACL", "PPV", "TRC", "TRL", "UEFA", "FIFA", "WWE", "MCM",
        "ZDF", "ARD", "ORF", "NHK", "CGTN", "RMC", "UK", "USA", "BFM", "TF1", "M6", "LCI", "HBO", "PBS", "FX",
        "TNT", "SBS", "KBS", "SNAI", "ISS", "RTI", "RTP", "RTS", "TVE", "NOS", "NPO", "SVT", "DR", "NRK", "YLE",
        "PGA", "USGA", "ATP", "WTA", "NHL", "MLB", "NCAA", "ITV", "CBS", "NBC", "ABC", "FOX", "HSE", "N24", "NTV", "TRT", "ERT", "TVP", "RTR",
    )
    /** Little words inside a name: `Il Trono di Spade`, `Men in Black`. Never single letters: `Serie A`. */
    private val SMALL = setOf(
        "di", "e", "del", "della", "delle", "dei", "degli", "dello", "da", "dal", "dalla", "per", "con", "il", "lo",
        "gli", "in", "nel", "nella", "al", "alla", "ai", "agli", "su", "sul", "tra", "fra", "and", "of", "the", "by",
    )
    private val BRANDS = listOf("sky", "discovery", "dazn")
    private val UP_THEN_LOW = Regex("[A-Z]{2,}[a-z]")
    private val ORDINAL = Regex("""\d+(st|nd|rd|th)""", RegexOption.IGNORE_CASE)
    private val CAPS_RUN = Regex("[A-Z]{2,}")
    private val ROMAN = Regex("ii|iii|iv|vi|vii|viii|ix|xi|xii|lll")
    private val DECADE = Regex("""\d0s""")
    private val INITIALS = Regex("""[A-Za-z](?:\.[A-Za-z])+\.?""")
    private const val VOWELS = "AEIOUYÀÈÉÌÒÙ"
    private val CAMEL_PAIR = Regex("""^([A-Z][a-z]{3,})([A-Z][a-z]{3,})$""")
    private val TRAILING_SHIFT = Regex("""\s*\+\d+$""")
    private val NON_WORD = Regex("""[^a-z0-9]+""")
    private const val TYPO_MIN_KEY = 7
}
