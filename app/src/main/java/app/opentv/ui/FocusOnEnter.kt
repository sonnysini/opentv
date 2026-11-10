package app.opentv.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay

/**
 * True while focus is in the app's menu. A page opened from the menu by resting on its entry must
 * not pull the focus out of the menu as it comes up: pages that put focus somewhere by themselves
 * when they open check this first (the menu hands focus over when the user moves into the page).
 */
val LocalMenuHasFocus = androidx.compose.runtime.staticCompositionLocalOf<() -> Boolean> { { false } }

/**
 * A screen opened from a menu takes the focus itself, so the remote works on it straight away:
 * if nothing inside it holds focus shortly after it opens, focus goes to its first item.
 * Screens that place focus themselves (a Play button, the selected setting) win, since this only
 * steps in when they haven't. It keeps trying for a couple of seconds, for pages whose content
 * arrives a moment after the screen does.
 */
@Composable
fun Modifier.focusOnEnter(
    key: Any? = Unit,
    enabled: Boolean = true,
    // Tried first: a page with its own idea of where focus should land. True if it took it.
    enter: () -> Boolean = { false },
): Modifier {
    val requester = remember { FocusRequester() }
    var inside by remember { mutableStateOf(false) }
    LaunchedEffect(key, enabled) {
        if (!enabled) return@LaunchedEffect
        repeat(20) {
            delay(100)
            if (inside) return@LaunchedEffect
            // On a focus group requestFocus() hands focus to its first focusable child.
            if (!enter()) runCatching { requester.requestFocus() }
        }
    }
    return this
        .onFocusChanged { inside = it.hasFocus }
        .focusRequester(requester)
        .focusGroup()
}
