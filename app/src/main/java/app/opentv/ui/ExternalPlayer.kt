package app.opentv.ui

import app.opentv.R
import app.opentv.core.findActivity

/**
 * Hands the stream to another installed player (VLC, Kodi, Vimu…), asking which one every time.
 * The User-Agent rides along as the extras VLC and Vimu read; Kodi ignores it.
 */
fun openInExternalPlayer(context: android.content.Context, url: String, userAgent: String?, title: String) {
    val view = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
        setDataAndType(android.net.Uri.parse(url), "video/*")
        if (userAgent != null) {
            putExtra("http-user-agent", userAgent)
            putExtra("User-Agent", userAgent)
            putExtra("headers", arrayOf("User-Agent", userAgent))
        }
    }
    // Started from our own task (no NEW_TASK), so Back in the other player comes home to us instead
    // of to the launcher.
    val activity = context.findActivity()
    val chooser = android.content.Intent.createChooser(view, title)
        .apply { if (activity == null) addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
        // Fire OS lists a placeholder gallery that can't play anything.
        .putExtra(
            android.content.Intent.EXTRA_EXCLUDE_COMPONENTS,
            arrayOf(android.content.ComponentName("com.amazon.dummy.gallery", "com.amazon.dummy.gallery.DummyGalleryActivity")),
        )
    try {
        (activity ?: context).startActivity(chooser)
    } catch (_: android.content.ActivityNotFoundException) {
        android.widget.Toast.makeText(context, R.string.player_no_external, android.widget.Toast.LENGTH_LONG).show()
    }
}
