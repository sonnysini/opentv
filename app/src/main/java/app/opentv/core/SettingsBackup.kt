/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.core

import android.content.SharedPreferences
import app.opentv.data.model.GroupMerge
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * The settings backup file: one JSON document holding the app settings, the guide feeds and the
 * channel-grouping choices, downloaded and uploaded from the phone page ([app.opentv.pairing.ManagerServer]).
 * Pure encode/decode; [AppSettings] decides which prefs go in and applies them.
 */
object SettingsBackup {

    const val FORMAT = "opentv-settings"
    const val VERSION = 1

    /** How a backed-up pref is stored, so a restore writes it back with the right type. */
    enum class Kind { BOOL, INT, STRING, SET }

    @Serializable
    data class Feed(val name: String, val url: String, val enabled: Boolean, val builtIn: Boolean = false)

    @Serializable
    data class File(
        val format: String = FORMAT,
        val version: Int = VERSION,
        val app: String = "",
        val exportedAt: Long = 0,
        val settings: JsonObject? = null,
        val guides: List<Feed>? = null,
        val merges: List<GroupMerge>? = null,
        val ignoredPairs: List<String>? = null,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun encode(file: File): String = json.encodeToString(File.serializer(), file)

    /** Null when [text] isn't an OpenTV settings file. */
    fun decode(text: String): File? =
        runCatching { json.decodeFromString(File.serializer(), text) }.getOrNull()?.takeIf { it.format == FORMAT }

    /** Pref values ([Boolean]/[Int]/[String]/[Set]) as JSON. */
    fun toJson(values: Map<String, Any>): JsonObject = JsonObject(
        values.mapValues { (_, v) ->
            when (v) {
                is Boolean -> JsonPrimitive(v)
                is Number -> JsonPrimitive(v)
                is Set<*> -> JsonArray(v.map { JsonPrimitive(it.toString()) })
                else -> JsonPrimitive(v.toString())
            }
        },
    )

    /** The other way round, typed by [kinds]; unknown keys and values of the wrong type are dropped. */
    fun fromJson(obj: JsonObject, kinds: Map<String, Kind>): Map<String, Any> = buildMap {
        for ((key, element) in obj) {
            val value = kinds[key]?.let { read(element, it) } ?: continue
            put(key, value)
        }
    }

    private fun read(element: JsonElement, kind: Kind): Any? = runCatching {
        when (kind) {
            Kind.BOOL -> element.jsonPrimitive.booleanOrNull
            Kind.INT -> element.jsonPrimitive.intOrNull
            Kind.STRING -> element.jsonPrimitive.takeIf { it.isString }?.content
            Kind.SET -> (element as? JsonArray)?.map { it.jsonPrimitive.content }?.toSet()
        }
    }.getOrNull()

    /** Writes one value read by [fromJson]. */
    fun put(edit: SharedPreferences.Editor, key: String, kind: Kind, value: Any) {
        when (kind) {
            Kind.BOOL -> (value as? Boolean)?.let { edit.putBoolean(key, it) }
            Kind.INT -> (value as? Int)?.let { edit.putInt(key, it) }
            Kind.STRING -> (value as? String)?.let { edit.putString(key, it) }
            Kind.SET -> (value as? Set<*>)?.let { set -> edit.putStringSet(key, set.map { it.toString() }.toSet()) }
        }
    }
}
