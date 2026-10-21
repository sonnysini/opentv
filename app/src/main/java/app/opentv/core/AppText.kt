/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.core

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import java.util.Locale

/**
 * Strings for code that has no UI [Context] of its own — repositories, network clients, servers,
 * workers — so the messages they produce (errors, sync status) follow the app language too.
 *
 * Resolves against the saved in-app language every time, so a language change made in Settings
 * applies without restarting the process (the application context keeps its startup locale).
 */
object AppText {

    @Volatile private var app: Context? = null
    @Volatile private var cached: Pair<String, Context>? = null

    fun init(context: Context) {
        app = context.applicationContext ?: context
    }

    private fun context(): Context? {
        val base = app ?: return null
        val tag = AppSettings.savedLanguageTag(base)
        cached?.let { (t, ctx) -> if (t == tag) return ctx }
        val ctx = if (tag.isNotBlank()) {
            LocaleUtils.wrap(base, tag)
        } else {
            // "Follow the device": the application context may still carry a language picked
            // earlier in this process, so ask the system for its own.
            val config = Configuration(base.resources.configuration)
            @Suppress("DEPRECATION")
            config.setLocale(Resources.getSystem().configuration.locale)
            base.createConfigurationContext(config)
        }
        cached = tag to ctx
        return ctx
    }

    /**
     * A context in the language of a browser's `Accept-Language` header, for the pages the TV
     * serves to a phone: they follow the phone's language, not the TV's. English if absent.
     */
    fun forBrowser(acceptLanguage: String?): Context? {
        val base = app ?: return null
        val tag = acceptLanguage?.substringBefore(',')?.substringBefore(';')?.trim()
            ?.takeIf { it.isNotEmpty() && it != "*" } ?: "en"
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(config)
    }

    /** The string in the app language; English (the default resources) before [init]. */
    fun get(@StringRes id: Int, vararg args: Any): String {
        val ctx = context() ?: return "#$id"
        return if (args.isEmpty()) ctx.getString(id) else ctx.getString(id, *args)
    }
}
