/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.core

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * A small, always-on diary of what the app does: screens opened, channels tuned, the screen kept
 * awake or let go, the app going to the background, the box's screensaver, player errors, crashes.
 *
 * Events only — a few lines a minute, never per frame — written on one background thread, so the
 * UI never waits on it. It lives in the app's own external files folder (survives "Clear cache",
 * gone with "Clear data" or an uninstall) and turns over at [MAX_BYTES]: events.log plus the
 * previous events.1.log, so at most twice that on disk. Read it over adb:
 * `adb shell cat /sdcard/Android/data/<package>/files/events.log`.
 */
object EventLog {
    private const val TAG = "MyTV-Event"
    private const val MAX_BYTES = 512L * 1024

    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "event-log").apply { isDaemon = true } }
    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    @Volatile private var file: File? = null

    fun init(context: Context) {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        file = File(dir, "events.log")
        installCrashHandler()
    }

    /** One line: when, which area of the app, what happened. */
    fun log(area: String, message: String) {
        val line = "${stamp.format(Date())} [$area] $message"
        Log.i(TAG, line)
        val target = file ?: return
        writer.execute { append(target, line) }
    }

    /**
     * A keep-awake change, with where the screen stands right after it: the window's own flag and
     * the content view's keepScreenOn. Either one being set keeps the screensaver away.
     */
    fun keepAwake(who: String, action: String, activity: Activity?) {
        log("awake", "$who $action -> ${awakeState(activity)}")
    }

    fun awakeState(activity: Activity?): String {
        val window = activity?.window ?: return "no window"
        val windowFlag = window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val viewFlag = content?.let { anyKeepsScreenOn(it) } ?: false
        return "window=${onOff(windowFlag)} view=${onOff(viewFlag)}"
    }

    private fun anyKeepsScreenOn(view: View): Boolean {
        if (view.keepScreenOn) return true
        if (view is ViewGroup) for (i in 0 until view.childCount) if (anyKeepsScreenOn(view.getChildAt(i))) return true
        return false
    }

    private fun onOff(b: Boolean) = if (b) "ON" else "off"

    private fun append(target: File, line: String) {
        runCatching {
            if (target.length() > MAX_BYTES) {
                val old = File(target.parentFile, "events.1.log")
                old.delete()
                target.renameTo(old)
            }
            FileWriter(target, true).use { it.write(line); it.write("\n") }
        }
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Written right here, not on the writer thread: the process is about to die.
            runCatching {
                val line = "${stamp.format(Date())} [crash] ${thread.name}: ${Log.getStackTraceString(error).take(4000)}"
                file?.let { append(it, line) }
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
