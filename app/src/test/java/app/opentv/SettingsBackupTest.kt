/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.core.SettingsBackup
import app.opentv.core.SettingsBackup.Kind
import app.opentv.data.model.GroupMerge
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SettingsBackupTest {

    private val kinds = mapOf(
        "italian_only" to Kind.BOOL,
        "ui_scale" to Kind.INT,
        "language_tag" to Kind.STRING,
        "disabled_categories" to Kind.SET,
    )

    @Test
    fun roundTripsEveryKind() {
        val values = mapOf(
            "italian_only" to true,
            "ui_scale" to 95,
            "language_tag" to "it",
            "disabled_categories" to setOf("LIVE:spain", "VOD:xxx - 3d"),
        )
        val file = SettingsBackup.File(
            app = "0.16.3",
            settings = SettingsBackup.toJson(values),
            guides = listOf(SettingsBackup.Feed("IT", "https://example.org/it.xml.gz", true, builtIn = true)),
            merges = listOf(GroupMerge("skymtv", "mtv", "Sky MTV", "MTV")),
            ignoredPairs = listOf("a|b"),
        )
        val back = SettingsBackup.decode(SettingsBackup.encode(file))!!
        assertThat(SettingsBackup.fromJson(back.settings!!, kinds)).isEqualTo(values)
        assertThat(back.guides).isEqualTo(file.guides)
        assertThat(back.merges).isEqualTo(file.merges)
        assertThat(back.ignoredPairs).containsExactly("a|b")
    }

    @Test
    fun dropsUnknownKeysAndWrongTypes() {
        val json = SettingsBackup.toJson(mapOf("italian_only" to "yes", "ui_scale" to 90, "smb_password" to "secret"))
        assertThat(SettingsBackup.fromJson(json, kinds)).isEqualTo(mapOf("ui_scale" to 90))
    }

    @Test
    fun rejectsOtherFiles() {
        assertThat(SettingsBackup.decode("""{"format":"something-else"}""")).isNull()
        assertThat(SettingsBackup.decode("not json")).isNull()
    }
}
