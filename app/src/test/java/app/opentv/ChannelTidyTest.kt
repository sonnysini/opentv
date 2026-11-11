/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.data.parser.ChannelCountry
import app.opentv.data.parser.ChannelDictionary
import app.opentv.data.parser.ChannelNameNormalizer
import app.opentv.data.parser.ChannelTidy
import app.opentv.data.repo.ChannelNamesRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChannelTidyTest {

    /** (raw name, category) → (stored key, shown name), the way the catalogue does it. */
    private fun tidy(
        channels: List<Pair<String, String>>,
        home: String = "IT",
        dictionary: ChannelDictionary = ChannelDictionary.EMPTY,
    ): List<Pair<String, String>> {
        val items = channels.map { (name, category) ->
            val n = ChannelNameNormalizer.normalize(ChannelTidy.preClean(name).first)
            ChannelTidy.Item(n.groupKey, n.baseName, ChannelCountry.of(name, category, null, home))
        }
        val keys = ChannelTidy.keys(items, home, dictionary)
        val names = ChannelTidy.displayNames(items, keys, BooleanArray(items.size), home, dictionary)
        return keys.zip(names)
    }

    private fun names(vararg raw: String, dictionary: ChannelDictionary = ChannelDictionary.EMPTY) =
        tidy(raw.map { it to "Italia" }, dictionary = dictionary).map { it.second }

    // ---- Casing -------------------------------------------------------------------------------

    @Test
    fun `shouted and whispered names read like channels`() {
        assertThat(names("EUROSPORT 1 FHD", "ITALIA 2 HD", "rete oro 2", "SKY CINEMA ILLUMINATION"))
            .containsExactly("Eurosport 1", "Italia 2", "Rete Oro 2", "Sky Cinema Illumination").inOrder()
    }

    @Test
    fun `acronyms stay capitals`() {
        assertThat(names("Dmax HD", "Warner Tv", "Zona Dazn", "RTTR Trentino", "WWE WRESTLING"))
            .containsExactly("DMAX", "Warner TV", "Zona DAZN", "RTTR Trentino", "WWE Wrestling").inOrder()
    }

    @Test
    fun `little words inside a name stay small, never a single letter`() {
        assertThat(ChannelTidy.smartCase("IL TRONO DI SPADE")).isEqualTo("Il Trono di Spade")
        assertThat(ChannelTidy.smartCase("Men In Black")).isEqualTo("Men in Black")
        assertThat(ChannelTidy.smartCase("ZONA DAZN SERIE A")).isEqualTo("Zona DAZN Serie A")
    }

    @Test
    fun `dashed words, initials, numbers and roman numerals`() {
        assertThat(ChannelTidy.smartCase("Brixen-PALLAMANO-FEMMINILE")).isEqualTo("Brixen-Pallamano-Femminile")
        assertThat(ChannelTidy.smartCase("DAZN 7 SERIE B L.R Vicenza")).isEqualTo("DAZN 7 Serie B L.R Vicenza")
        assertThat(ChannelTidy.smartCase("1LIVE")).isEqualTo("1Live")
        assertThat(ChannelTidy.smartCase("NOW 80s")).isEqualTo("NOW 80s")
        assertThat(ChannelTidy.smartCase("Lupin lll Channel")).isEqualTo("Lupin III Channel")
    }

    @Test
    fun `a deliberate mix is kept`() {
        assertThat(ChannelTidy.smartCase("iCarly")).isEqualTo("iCarly")
        assertThat(ChannelTidy.smartCase("DeA Kids")).isEqualTo("DeA Kids")
    }

    @Test
    fun `non-latin names are left alone`() {
        assertThat(names("ФЛ")).containsExactly("ФЛ")
    }

    @Test
    fun `the dictionary's spelling wins for its own joins only`() {
        val d = ChannelDictionary.of(listOf("Sky Sport MotoGP", "Sky TG24", "Solocalcio"))
        assertThat(names("Sky Sport Moto GP FHD", dictionary = d)).containsExactly("Sky Sport MotoGP")
        assertThat(names("Sky TG 24 HD", dictionary = d)).containsExactly("Sky TG24")
        assertThat(names("Solo Calcio HD", dictionary = d)).containsExactly("Solo Calcio")
    }

    @Test
    fun `a glued team name comes apart when the row also writes it spaced`() {
        val rows = names("JuveStabia FHD", "DAZN 10 SERIE B JUVE STABIA")
        assertThat(rows.toSet()).containsExactly("Juve Stabia")
    }

    // ---- Rows ---------------------------------------------------------------------------------

    @Test
    fun `a foreign copy gets a row of its own`() {
        val rows = tidy(listOf("Sky Nature FHD" to "🇮🇹 Documentari", "DE: Sky Nature HD" to "Dokus"))
        assertThat(rows.map { it.first }).containsExactly("skynature", "de:skynature").inOrder()
        assertThat(rows[1].second).isEqualTo("Sky Nature")
    }

    @Test
    fun `country comes from the name first, then the category`() {
        assertThat(ChannelCountry.of("IT: DAZN 1", "🌍 World Mix", null, "IT")).isEqualTo("IT")
        assertThat(ChannelCountry.of("Big Brother", "🇦🇱 Albania", null, "IT")).isEqualTo("AL")
        assertThat(ChannelCountry.of("Some Channel", "🌍 World Mix", null, "IT")).isEqualTo(ChannelCountry.WORLD)
        assertThat(ChannelCountry.of("Some Channel", null, "SomeChannel.de", "IT")).isEqualTo("DE")
        assertThat(ChannelCountry.of("Some Channel", null, null, "IT")).isEqualTo("IT")
        assertThat(ChannelCountry.of("UK| BBC One", null, null, "GB")).isEqualTo("GB")
    }

    @Test
    fun `rows naming one channel in the dictionary join`() {
        val d = ChannelDictionary.of(listOf("History", "Sky Serie", "Sky Cinema Suspense"))
        val rows = tidy(
            listOf("History Channel HD", "History FHD", "Sky Series HD", "Sky Serie FHD", "Sky Cinema Suspence")
                .map { it to "Italia" },
            dictionary = d,
        )
        assertThat(rows.map { it.first })
            .containsExactly("history", "history", "skyserie", "skyserie", "skycinemasuspense").inOrder()
        assertThat(rows.map { it.second }.toSet()).containsExactly("History", "Sky Serie", "Sky Cinema Suspense")
    }

    @Test
    fun `a timeshift copy never joins its channel`() {
        val rows = tidy(listOf("Italia 1 HD" to "Italia", "Italia 1 +1 HD" to "Italia"))
        assertThat(rows.map { it.first }).containsExactly("italia1", "italia1+1").inOrder()
        assertThat(rows[1].second).isEqualTo("Italia 1 +1")
    }

    @Test
    fun `the brand stays when the dictionary knows the branded channel`() {
        val d = ChannelDictionary.of(listOf("Sky Sport 1", "Sport1"))
        val rows = tidy(listOf("Sky Sport 1 HD" to "Sport", "SPORT1 HD" to "Sport"), home = "DE", dictionary = d)
        assertThat(rows.map { it.first }).containsExactly("skysport1", "sport1").inOrder()
    }

    @Test
    fun `without the dictionary the brand comes off as before`() {
        val rows = tidy(listOf("Sky MTV HD" to "Italia", "MTV FHD" to "Italia"))
        assertThat(rows.map { it.first }.toSet()).containsExactly("mtv")
    }

    @Test
    fun `one-letter typos join only on long real words`() {
        val rows = tidy(listOf("ITALIAN FISCHING TV +3", "ITALIAN FISHING TV +3", "Italian Fishing TV +3", "Serie A", "Serie B").map { it to "Italia" })
        assertThat(rows.map { it.first })
            .containsExactly("italianfishingtv+3", "italianfishingtv+3", "italianfishingtv+3", "seriea", "serieb").inOrder()
    }

    @Test
    fun `a slip on the brand letter joins the brand's row`() {
        val rows = tidy(listOf("Sly Documentari HD" to "Italia", "Sky Documentari FHD" to "Italia"))
        assertThat(rows.map { it.first }.toSet()).containsExactly("skydocumentari")
    }

    @Test
    fun `a source tag in front comes off, a country does not`() {
        assertThat(ChannelTidy.preClean("Abc: Sky Uno HD")).isEqualTo("Sky Uno HD" to "Abc")
        assertThat(ChannelTidy.preClean("DE: Sky Uno HD")).isEqualTo("DE: Sky Uno HD" to null)
        assertThat(ChannelTidy.preClean("Sky Nature 《 C 》 HD").first).isEqualTo("Sky Nature   HD".trim())
    }

    @Test
    fun `keys without and with a country`() {
        assertThat(ChannelCountry.bare("de:skynature+1")).isEqualTo("skynature+1")
        assertThat(ChannelCountry.bare("skynature")).isEqualTo("skynature")
        assertThat(ChannelCountry.ofKey("de:skynature")).isEqualTo("DE")
        assertThat(ChannelCountry.ofKey("skynature")).isNull()
        assertThat(ChannelCountry.keyFor("rai1", "SM", "IT")).isEqualTo("rai1")
    }

    // ---- Channel database ---------------------------------------------------------------------

    @Test
    fun `reads one country's names from the channel database, alternatives included`() {
        val json = """
            [
              {"id":"Rai1.it","name":"Rai 1","alt_names":["Rai Uno"],"country":"IT","closed":null},
              {"id":"BBCOne.uk","name":"BBC One","alt_names":[],"country":"UK"},
              {"id":"SkyUno.it","name":"Sky Uno","alt_names":[],"country":"IT","extra":{"x":1}}
            ]
        """.trimIndent()
        assertThat(ChannelNamesRepository.parseNames(json.byteInputStream(), "IT"))
            .containsExactly("Rai 1", "Rai Uno", "Sky Uno").inOrder()
        assertThat(ChannelNamesRepository.parseNames(json.byteInputStream(), "GB")).containsExactly("BBC One")
    }
}
