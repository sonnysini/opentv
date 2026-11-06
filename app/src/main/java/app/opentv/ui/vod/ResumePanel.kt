package app.opentv.ui.vod

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.opentv.R
import app.opentv.data.model.Series
import app.opentv.ui.ActionLabel
import app.opentv.ui.ActionPanel
import app.opentv.ui.ActionRow
import app.opentv.ui.VodViewModel
import app.opentv.ui.openInExternalPlayer
import app.opentv.ui.startFocus
import kotlinx.coroutines.launch

/**
 * Hold OK on a Continue Watching card: resume or start again, the next episode, another copy to
 * play, the show's episodes, mark it watched, take it off the shelf, or another player.
 * A film has no show and no next episode, so those entries aren't there.
 */
@Composable
internal fun ResumePanel(
    item: VodViewModel.ResumeItem,
    viewModel: VodViewModel,
    onResume: (mediaKey: String, url: String, title: String) -> Unit,
    onOpenSeries: (Series) -> Unit,
    onPickSource: (List<VodViewModel.ResumeSource>) -> Unit,
    /** The card is about to leave the shelf (removed, or marked watched). */
    onLeaveShelf: () -> Unit,
    onClose: () -> Unit,
) {
    val extras by produceState<VodViewModel.ResumeExtras?>(null, item.mediaKey) { value = viewModel.resumeExtras(item) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val ep = item.episode
    val kicker = if (ep != null) {
        "S${ep.season} E${ep.episodeNumber}" + ep.title.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
    } else {
        listOfNotNull(stringResource(R.string.panel_film), item.movie?.year?.toString()).joinToString(" · ")
    }
    val title = if (ep != null) item.seriesName ?: item.title else item.title
    val left = (item.durationMillis - item.positionMillis).coerceAtLeast(0)
    val leftLabel = if (item.durationMillis <= 0) null else {
        val mins = ((left + 59_999) / 60_000).toInt()
        stringResource(
            R.string.panel_remaining,
            if (mins >= 60) stringResource(R.string.panel_hours_minutes, mins / 60, mins % 60)
            else stringResource(R.string.panel_minutes, mins),
        )
    }
    val started = item.positionMillis >= 1_000
    ActionPanel(
        kicker = kicker,
        title = title,
        onDismiss = onClose,
        progress = item.progress.takeIf { item.durationMillis > 0 },
        progressLabel = leftLabel,
    ) { first ->
        item(key = "resume") {
            ActionRow(
                if (started) stringResource(R.string.panel_resume_from, clockOf(item.positionMillis))
                else stringResource(R.string.panel_watch),
                Modifier.startFocus(first),
                icon = R.drawable.ic_act_play,
            ) {
                onClose()
                onResume(item.mediaKey, item.streamUrl, item.title)
            }
        }
        if (started) item(key = "restart") {
            ActionRow(stringResource(R.string.panel_restart), icon = R.drawable.ic_act_restart) {
                scope.launch {
                    viewModel.restart(item)
                    onClose()
                    onResume(item.mediaKey, item.streamUrl, item.title)
                }
            }
        }
        extras?.next?.let { next ->
            item(key = "next") {
                ActionRow(
                    stringResource(R.string.panel_next_episode, next.season, next.episodeNumber),
                    icon = R.drawable.ic_act_next,
                ) {
                    onClose()
                    onResume("ep:${next.id}", next.streamUrl, next.title.ifBlank { "S${next.season} E${next.episodeNumber}" })
                }
            }
        }
        extras?.sources?.takeIf { it.size > 1 }?.let { sources ->
            item(key = "source") {
                ActionRow(stringResource(R.string.panel_pick_source), icon = R.drawable.ic_act_source) { onPickSource(sources) }
            }
        }
        if (ep != null) {
            item(key = "label:series") { ActionLabel(stringResource(R.string.panel_series)) }
            extras?.series?.let { series ->
                item(key = "episodes") {
                    ActionRow(stringResource(R.string.panel_go_episodes), icon = R.drawable.ic_act_episodes) {
                        onClose()
                        onOpenSeries(series)
                    }
                }
            }
            item(key = "watched") {
                ActionRow(stringResource(R.string.panel_mark_watched), icon = R.drawable.ic_act_check) {
                    onLeaveShelf()
                    viewModel.markWatched(item, extras?.next)
                    onClose()
                }
            }
        }
        item(key = "label:shelf") { ActionLabel(stringResource(R.string.vod_continue_watching)) }
        item(key = "remove") {
            ActionRow(stringResource(R.string.panel_remove), icon = R.drawable.ic_act_remove) {
                onLeaveShelf()
                viewModel.removeResume(item)
                onClose()
            }
        }
        item(key = "label:more") { ActionLabel(stringResource(R.string.panel_more)) }
        item(key = "external") {
            ActionRow(stringResource(R.string.guide_open_external), icon = R.drawable.ic_act_external) {
                scope.launch {
                    val ua = viewModel.userAgentFor(item)
                    onClose()
                    openInExternalPlayer(context, item.streamUrl, ua, title)
                }
            }
        }
    }
}

/** The copies of a Continue Watching title, to play another one from the same point. */
@Composable
internal fun ResumeSourcePanel(
    item: VodViewModel.ResumeItem,
    sources: List<VodViewModel.ResumeSource>,
    onResume: (mediaKey: String, url: String, title: String) -> Unit,
    onClose: () -> Unit,
) {
    val start = sources.indexOfFirst { it.current }.coerceAtLeast(0)
    ActionPanel(
        kicker = stringResource(R.string.panel_pick_source),
        title = item.seriesName ?: item.title,
        onDismiss = onClose,
    ) { first ->
        items(sources.withIndex().toList(), key = { "src:" + it.index }) { (i, src) ->
            // Played under the card's own key, so it starts where the card left off.
            ActionRow(
                src.label,
                Modifier.startFocus(first, on = i == start),
                icon = if (src.current) R.drawable.ic_act_check else R.drawable.ic_act_play,
            ) {
                onClose()
                onResume(item.mediaKey, src.url, item.title)
            }
        }
    }
}

/** 6:40, or 1:06:40 past the hour. */
private fun clockOf(millis: Long): String {
    val s = millis / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
