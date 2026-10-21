/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.parser

/**
 * Turns the channel names IPTV providers actually ship into something usable.
 *
 * A real provider list contains entries like:
 *
 *     UK| BBC ONE FHD
 *     UK - BBC One HD
 *     [UK] BBC 1 ᵁᴴᴰ
 *     ### PRIME RAW 60fps ####
 *
 * while an XMLTV guide calls the same channel `BBC One`. Nothing in either format joins
 * them. This normaliser produces, for any name:
 *
 *  - a **base name** with country prefixes, quality tags and decorations stripped
 *  - a **group key** (lowercased alphanumerics of the base) used to fold quality variants
 *    of one channel into a single row, and to match against guide data
 *  - a **quality rank and label**, so the app can pick the best variant by default and
 *    offer the rest as a switch during playback
 *
 * This one object is the foundation of both headline features: EPG matching and quality
 * grouping. It is pure and heavily tested — when a channel matches the wrong guide entry,
 * the fix almost certainly starts here, in a test case named after the offending channel.
 */
object ChannelNameNormalizer {

    data class Normalized(
        /** Human-readable name with the junk removed: `BBC One`. */
        val baseName: String,
        /** Join/group key: lowercase alphanumerics only: `bbcone`. */
        val groupKey: String,
        /** Higher is better. 0 when the name carries no quality information. */
        val qualityRank: Int,
        /** What to show on the quality switch: `FHD`, `HD`, `RAW 60fps`, or `""`. */
        val qualityLabel: String,
        /** Country hint from a stripped prefix (`UK`, `US`…), or null. */
        val region: String?,
        /** Restream source taken out of the name, or null. */
        val source: String? = null,
    )

    /** Quality/codec tokens, matched as whole words, case-insensitively. */
    private val QUALITY_RANKS = mapOf(
        "8K" to 500,
        "4K" to 400, "UHD" to 400, "FUHD" to 400, "2160P" to 400,
        "FHD" to 300, "1080" to 300, "1080P" to 300, "FULLHD" to 300,
        "HD" to 200, "720P" to 200, "720" to 200,
        "SD" to 100, "LQ" to 100, "480P" to 100, "576P" to 100,
    )

    private const val UHD_RANK = 400

    /** Group keys of brands whose UHD feed is a separate channel (`Rai 4K`), not a quality copy. */
    private val BRANDS_WITH_UHD_CHANNEL = setOf("rai", "skysport")

    /** Tokens that describe the stream but not its resolution. Stripped, kept in the label. */
    private val EXTRA_TOKENS = setOf(
        "RAW", "HEVC", "H265", "H.265", "H264", "H.264", "AV1",
        "50FPS", "60FPS", "25FPS", "30FPS", "FPS",
        "HDR", "DOLBY", "VISION",
    )

    /**
     * Restream sources written INTO channel names: `[SRC] DAZN-LIGUE-WOMEN-1`,
     * `Sky Sport F1 HD (SRC)`, `SKY SPORT BUSINESS 1 FHD SRC`, `Src ComoTV 1`. Same channel, other
     * feed — so they are stripped from the name and kept as [Normalized.source]. The list grows per
     * provider with tags learned from its category names (see
     * [app.opentv.data.parser.CategoryFamily.learnSourceTags]).
     */
    val BUILTIN_SOURCE_TAGS = SourceTags.CHANNEL

    private val BRACKET_SOURCE = Regex("""^\s*\[\s*([A-Za-z][A-Za-z+\-]{1,11})\s*]\s*""")
    private val PAREN_SOURCE = Regex("""\(\s*([A-Za-z][A-Za-z+\-]{1,11})\s*\)""")
    /** `Sky Sport F1 FHD (Q)` / `(S)`: a single letter in brackets at the end names a feed. */
    private val PAREN_LETTER = Regex("""\(\s*([A-Z])\s*\)\s*$""")
    /** `[V] BOX 01`: a single letter in square brackets at the start names a feed too. */
    private val BRACKET_LETTER = Regex("""^\s*\[\s*([A-Za-z])\s*]\s*""")
    /** Bandwidth notes some lists append: `Sky Sport F1 4K RAW Richiede 30 Mbps`. */
    private val BANDWIDTH_NOTE = Regex("""\b(?:richiede|requires?)\s*\d+\s*mbps\b""", RegexOption.IGNORE_CASE)
    /** Timeshift: `+1`, `+ 1`, `+24`, also glued to the name (`Food Network+1`). */
    private val TIMESHIFT = Regex("""\+\s*(\d{1,2})(?!\d)""")
    private val STRONG_MARKERS = setOf("HDR", "DOLBY", "VISION")

    /**
     * Unicode modifier/superscript letters folded to ASCII before tokenising.
     *
     * Providers love these as anti-copy decoration: `ᴴᴰ`, `ʰᵉᵛᶜ`, `ᴿᴬᵂ`, `ᵁᴴᴰ`, `⁸ᴷ`,
     * `³⁸⁴⁰ᴾ`. Both cases exist in the wild — often in the SAME list — so this covers the
     * full A–Z in both the uppercase modifier block and the lowercase superscript block,
     * plus superscript digits. Without the lowercase half, `ʰᵉᵛᶜ`/`ᴿᴬᵂ` survive into the
     * channel name and read as junk, which is the single biggest source of a "messy" list.
     */
    private val SUPERSCRIPT_MAP: Map<Char, Char> = buildMap {
        // Uppercase modifier letters (U+1D2C block, plus strays).
        val upper = "ᴬ:A,ᴮ:B,ꟲ:C,ᴰ:D,ᴱ:E,ꟳ:F,ᴳ:G,ᴴ:H,ᴵ:I,ᴶ:J,ᴷ:K,ᴸ:L,ᴹ:M,ᴺ:N," +
            "ᴼ:O,ᴾ:P,ꟴ:Q,ᴿ:R,ᵀ:T,ᵁ:U,ⱽ:V,ᵂ:W,ᔆ:S"
        // Lowercase superscript/modifier letters.
        val lower = "ᵃ:a,ᵇ:b,ᶜ:c,ᵈ:d,ᵉ:e,ᶠ:f,ᵍ:g,ʰ:h,ᶦ:i,ʲ:j,ᵏ:k,ˡ:l,ᵐ:m,ⁿ:n," +
            "ᵒ:o,ᵖ:p,ʳ:r,ˢ:s,ᵗ:t,ᵘ:u,ᵛ:v,ʷ:w,ˣ:x,ʸ:y,ᶻ:z"
        val digits = "⁰:0,¹:1,²:2,³:3,⁴:4,⁵:5,⁶:6,⁷:7,⁸:8,⁹:9"
        for (chunk in "$upper,$lower,$digits".split(',')) {
            if (chunk.length >= 3) put(chunk[0], chunk[2])
        }
    }

    /**
     * Leading country tag: `UK|`, `UK -`, `UK:`, `[UK]`, `(US)`, `USA |` and so on.
     * Two or three capitals only — long enough for ISO codes and `USA`, short enough that
     * `BEIN| SPORTS` is left alone.
     *
     * Two alternatives on purpose. Brackets are themselves the delimiter, so `[UK] ITV`
     * needs no separator after them; a bare code **must** have one, or `BBC ONE` would
     * lose its BBC.
     */
    private val COUNTRY_PREFIX = Regex(
        """^\s*(?:[\[(]([A-Z]{2,3})[\])]\s*[|:\-–—]?|([A-Z]{2,3})\s*[|:\-–—])\s*"""
    )

    /**
     * Decorations providers use as separators or eye-catchers — stripped from the display
     * name. Broad on purpose: anything outside letters, digits, spaces and the handful of
     * punctuation marks that appear inside real names (& - + . / : | ( )) is decoration.
     * The groupKey ignores all of it anyway, so matching is unaffected either way; this is
     * purely so the name on screen reads like a channel and not a ransom note.
     */
    private val DECORATION = Regex("""[^\p{L}\p{Nd} &+.\-/:|()']+""")

    private val MULTI_SPACE = Regex("""\s+""")

    /**
     * Folds unicode superscript/modifier decorations to ASCII (`ᴴᴰ` → `HD`, `ᴿᴬᵂ` → `RAW`).
     *
     * Exposed so other cleaners — notably [app.opentv.data.parser.VodTitleCleaner] for VOD titles —
     * reuse the exact same decoration table instead of maintaining a second, drifting copy. Returns
     * the input untouched (and without allocating) when it carries no such characters.
     */
    fun foldSuperscripts(raw: String): String {
        if (raw.none { it in SUPERSCRIPT_MAP }) return raw
        return buildString(raw.length) {
            for (c in raw) append(SUPERSCRIPT_MAP[c] ?: c)
        }
    }

    /**
     * The quality rank of a single token if it is a known resolution/quality tag (`4K`, `FHD`, `HD`,
     * `SD`, `UHD`…), matched whole-word and case-insensitively, else null. Exposed so the VOD title
     * cleaner strips exactly the tags this normaliser recognises rather than reinventing the list.
     */
    fun qualityRankOfToken(token: String): Int? = QUALITY_RANKS[token.uppercase()]

    /** Whether a single token is a non-resolution stream marker (`RAW`, `HEVC`, `60FPS`, `HDR`…). */
    fun isStreamMarker(token: String): Boolean = token.uppercase() in EXTRA_TOKENS

    fun normalize(
        raw: String,
        sourceTags: Set<String> = BUILTIN_SOURCE_TAGS,
        leadingTags: Set<String> = SourceTags.LEADING,
    ): Normalized {
        // Fold superscript decorations (ᴴᴰ → HD) so the token pass can see them.
        var working = foldSuperscripts(raw.trim())
        var source: String? = null
        fun takeSource(tag: String) {
            if (source == null) source = CategoryFamily.sourceLabel(tag)
        }

        working = BANDWIDTH_NOTE.replace(working, " ")
        // Source tags before the country prefix: `[SRC]` would otherwise pass for a country.
        BRACKET_SOURCE.find(working)?.let { m ->
            if (m.groupValues[1].uppercase() in sourceTags) {
                takeSource(m.groupValues[1])
                working = working.removeRange(m.range)
            }
        }
        working = PAREN_SOURCE.replace(working) { m ->
            if (m.groupValues[1].uppercase() in sourceTags) {
                takeSource(m.groupValues[1])
                " "
            } else {
                m.value
            }
        }
        PAREN_LETTER.find(working)?.let { m ->
            takeSource(m.groupValues[1])
            working = working.removeRange(m.range)
        }
        BRACKET_LETTER.find(working)?.let { m ->
            takeSource(m.groupValues[1].uppercase())
            working = working.removeRange(m.range)
        }
        // Timeshift is part of the identity (`Rai 1 +1` is not `Rai 11`): pulled out here and
        // put back as a `+N` suffix the key keeps, so aliases can work on the bare channel.
        var timeshift: String? = null
        TIMESHIFT.find(working)?.let { m ->
            timeshift = m.groupValues[1].trimStart('0').ifEmpty { "0" }
            working = working.removeRange(m.range)
        }

        // Country prefix BEFORE decoration stripping: the brackets in `[UK]` are the
        // delimiter, and the decoration pass would eat them and lose the tag.
        var region: String? = null
        // Only when a name is left after it: in `VH- 1` the "prefix" is the channel.
        COUNTRY_PREFIX.find(working)?.takeIf { m -> working.removeRange(m.range).any { it.isLetter() } }?.let { match ->
            region = match.groupValues[1].ifEmpty { match.groupValues[2] }
            working = working.removeRange(match.range)
        }

        working = DECORATION.replace(working, " ")

        // Tokenise and pull out quality/extra markers wherever they appear.
        var bestRank = 0
        val labelParts = ArrayList<String>(2)
        val keptTokens = ArrayList<String>()

        val tokens = working.split(' ', '\t', '/')
            .flatMap(::splitDashedTags)
            .map { it.trim('.', ',', ':', ';', '|', '-', '(', ')', '[', ']') }
            .filter { it.isNotEmpty() }
        var i = 0
        while (i < tokens.size) {
            val bare = tokens[i++]
            var upper = bare.uppercase()
            // `Full HD` written as two words is FHD, not a channel called "… Full" in HD.
            if (upper == "FULL" && tokens.getOrNull(i)?.uppercase() == "HD") {
                upper = "FHD"
                i++
            }

            val rank = QUALITY_RANKS[upper]
            when {
                // `HDR PLUS` is a stream marker; `Disney Plus` / `Boing Plus` are channels.
                upper == "PLUS" && labelParts.lastOrNull()?.uppercase() in STRONG_MARKERS -> labelParts += bare
                // `Sky Sport 256 FHD Duplicate`: a second copy of the same feed.
                upper == "DUPLICATE" && keptTokens.isNotEmpty() -> if (source == null) source = "Alt"
                // Trailing `RS` marks one restream source (`Sky Uno HD RS`): a label, not part of the name.
                upper == "RS" && i == tokens.size && keptTokens.isNotEmpty() -> labelParts += upper
                rank != null -> {
                    if (rank > bestRank) bestRank = rank
                    labelParts += upper
                }
                upper in EXTRA_TOKENS -> labelParts += bare
                else -> keptTokens += bare
            }
        }

        bestRank = withCodecBonus(bestRank, labelParts)

        // Source tags at the edges of what's left: `… Src`, `… SRC`, `Src ComoTV 1`.
        repeat(2) {
            val last = keptTokens.lastOrNull()?.uppercase()
            if (keptTokens.size > 1 && last != null && last in sourceTags) {
                takeSource(keptTokens.removeAt(keptTokens.lastIndex))
            }
        }
        if (keptTokens.size > 1 && keptTokens.first().uppercase() in leadingTags) {
            takeSource(keptTokens.removeAt(0))
        }

        var base = MULTI_SPACE.replace(keptTokens.joinToString(" "), " ").trim()
        // `Rai 4K` / `Sky Sport 4K` are channels of their own: stripping the 4K leaves just the brand.
        if (bestRank >= UHD_RANK && groupKeyOf(base) in BRANDS_WITH_UHD_CHANNEL) base = "$base 4K"
        val key = groupKeyOf(base)
        val shift = timeshift

        return Normalized(
            baseName = (base.ifEmpty { raw.trim() }).let { if (shift != null && base.isNotEmpty()) "$it +$shift" else it },
            groupKey = key.ifEmpty { groupKeyOf(raw) }.let { if (shift != null && key.isNotEmpty()) "$it+$shift" else it },
            qualityRank = bestRank,
            qualityLabel = labelParts.joinToString(" "),
            region = region,
            source = source,
        )
    }

    /**
     * `SERIE-A1-FHD`: words glued with dashes hide the quality tag from the token pass. Split
     * only when a part is a quality/stream tag, so `X-Men` or `Brixen-PALLAMANO` read as before.
     */
    private fun splitDashedTags(token: String): List<String> {
        if ('-' !in token) return listOf(token)
        val parts = token.split('-').filter { it.isNotEmpty() }
        val tagged = parts.any { it.uppercase().let { u -> u in QUALITY_RANKS || u in EXTRA_TOKENS } }
        return if (tagged) parts else listOf(token)
    }

    /**
     * Ranks HEVC and RAW feeds within their resolution tier: HEVC above RAW above plain.
     *
     * Measured on a real Italian list: "X HEVC" and "X RAW" with no resolution tag were all
     * 1080p, yet scored 0 and sorted below SD. So a bare HEVC/RAW is taken as FHD, and at equal
     * resolution HEVC (better picture per bit) beats RAW (untouched feed) beats a plain H.264.
     * The bonus stays under 100, so `rank / 100` is still the resolution tier.
     */
    private fun withCodecBonus(rank: Int, labelParts: List<String>): Int {
        val upper = labelParts.map { it.uppercase() }
        val hevc = upper.any { it == "HEVC" || it == "H265" || it == "H.265" }
        val raw = "RAW" in upper
        if (!hevc && !raw) return rank
        val base = if (rank == 0) FHD_RANK else rank
        return base + if (hevc) HEVC_BONUS else RAW_BONUS
    }

    /** The resolution tier of a rank (`320` HEVC 1080p → `300`), dropping the codec bonus. */
    fun resolutionTier(rank: Int): Int = rank / 100 * 100

    private const val FHD_RANK = 300
    private const val HEVC_BONUS = 20
    private const val RAW_BONUS = 10

    /**
     * The canonical join key: lowercase letters and digits only.
     *
     * Aggressive on purpose. `BBC One`, `BBC1`, `bbc-one` and `B B C One` should all meet in
     * the middle — false negatives here mean channels with no guide, which is the failure
     * users actually notice. The word-number folding handles `BBC ONE` vs `BBC 1`.
     */
    fun groupKeyOf(name: String): String {
        // Split CamelCase first so `BbcOne` sees the same words as `BBC One`, then fold
        // number WORDS to digits — whole words only, never substrings, or `money` becomes
        // `m1y` on one side of a match and not the other.
        // Accents fold to the bare letter (`Südtirol` = `SudTirol`) instead of vanishing.
        val plain = COMBINING_MARKS.replace(java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD), "")
        val spaced = CAMEL_BOUNDARY.replace(plain, "$1 $2")
        return spaced.lowercase()
            .split(NON_ALNUM)
            .filter { it.isNotEmpty() }
            .joinToString("") { word -> NUMBER_WORDS[word] ?: word }
    }

    /**
     * Folds the group keys of one list so the same channel under two names lands in one row.
     *
     * Italian resellers ship `Sky MTV` next to `MTV` and `Discovery Giallo` next to `Giallo`.
     * Typos (`Nicklodeon`) are not fixed here: [MergeSuggester] offers them to the user.
     * Returns old key → new key for the keys that change. A `Sky`/
     * `Discovery` prefix is dropped only when the bare channel exists in the same list, so `Sky Uno`
     * stays `Sky Uno`. `Sky Cinema X` is left alone: `Cinema X` is often a different 24/7 loop.
     * `DAZN` / `IT DAZN|` work the same way, and so do DAZN team feeds (`DAZN Serie A Milan`,
     * `DAZN SERIE A 3 MILAN`, `DAZN 5 SERIE B WEB CESENA`): they join the team's bare channel
     * (`Milan`) when the list has one. No team list — the bare channel is the proof.
     */
    fun keyAliases(keys: Set<String>): Map<String, String> {
        // Timeshift copies (`skycomedycentral+1`) follow their channel: aliases work on the
        // part before `+`, and the bare-channel check looks at every channel, shifted or not.
        val bases = keys.mapTo(HashSet()) { it.substringBefore('+') }
        val out = HashMap<String, String>()
        for (key in keys) {
            val base = key.substringBefore('+')
            val shift = key.substring(base.length)
            val candidates = BRAND_PREFIXES.map { base.removePrefix(it) }.filter {
                // `DAZN 1` is not channel `1`: a bare number left over is no channel name.
                it != base && it.length >= 2 && !it.startsWith("cinema") && !it.all(Char::isDigit)
            } + listOfNotNull(teamOf(base))
            val alias = candidates.firstOrNull { it in bases }
            if (alias != null && alias != base) out[key] = alias + shift
        }
        return out
    }

    /** The team in a DAZN team-feed key (`dazn5seriebwebcesena` → `cesena`), or null. */
    fun teamOf(base: String): String? =
        TEAM_FEED.matchEntire(base)?.groupValues?.get(1)?.takeIf { !it.startsWith("women") }

    private val BRAND_PREFIXES = listOf("sky", "discovery", "itdazn", "dazn")

    /** `[dazn][N]serie a|b[N][web]<team>`, on group keys. */
    private val TEAM_FEED = Regex("""^(?:dazn)?\d*serie[ab]\d*(?:web)?([a-z].{2,})$""")

    private val COMBINING_MARKS = Regex("""\p{Mn}+""")
    private val CAMEL_BOUNDARY = Regex("""([a-z])([A-Z])""")
    private val NON_ALNUM = Regex("""[^a-zA-Z0-9]+""")
    private val NUMBER_WORDS = mapOf(
        "one" to "1", "two" to "2", "three" to "3", "four" to "4", "five" to "5",
        "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9",
    )
}
