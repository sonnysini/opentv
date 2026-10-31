package app.opentv.ui.channels

import android.graphics.Paint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentv.R
import app.opentv.data.parser.GroupBadge
import app.opentv.ui.ChannelsViewModel
import app.opentv.ui.ChannelsViewModel.Companion.LAST_GROUP_ALL
import app.opentv.ui.ChannelsViewModel.Companion.LAST_GROUP_FAVOURITES
import coil.compose.AsyncImage

/** One entry in a live groups list — the full-screen list's column and the player's side menu. */
data class LiveGroupEntry(
    val key: String,
    val label: String = "",
    val selected: Boolean = false,
    val count: Int? = null,
    val badge: GroupBadge.Badge? = null,
    // The broadcaster or country above the name, small (the full-screen list's tabs).
    val kicker: String? = null,
    // A section title ("Recent", "All groups") drawn above this group, in the same list item
    // so every item can take focus.
    val section: String? = null,
    val pick: () -> Unit = {},
)

/** Favourites, the groups last watched from, then All and every group, in that order. */
@Composable
fun rememberLiveGroupEntries(
    categories: List<ChannelsViewModel.CategoryGroup>,
    selectedCategory: String?,
    favouritesOnly: Boolean,
    counts: Map<String, Int>,
    recentGroups: List<String>,
    favouritesLabel: String,
    allLabel: String,
    onSelectCategory: (String?) -> Unit,
    onSelectFavourites: () -> Unit,
): List<LiveGroupEntry> {
    val external = LocalExternalGroupLogos.current
    fun shown(name: String) = GroupBadge.of(name, external)
    val recentTitle = stringResource(R.string.live_groups_recent)
    val allTitle = stringResource(R.string.live_groups_all)
    // The remembered entries' picks go through these, so they always reach the latest callbacks.
    val selectCategory by rememberUpdatedState(onSelectCategory)
    val selectFavourites by rememberUpdatedState(onSelectFavourites)
    // Naming and badging every group is slow on a Fire stick (hundreds of groups): done only when
    // the groups change. Moving the selection then only re-marks the entries, so picking another
    // group no longer stalls the screen for half a second.
    val built = remember(categories, counts, recentGroups, external, favouritesLabel, allLabel) {
        fun all(prefix: String) = Marked(LiveGroupEntry(prefix + "all", allLabel, false, counts[LAST_GROUP_ALL]) { selectCategory(null) }) { cat, favs -> !favs && cat == null }
        fun group(prefix: String, g: ChannelsViewModel.CategoryGroup): Marked {
            val s = shown(g.rawName)
            val entry = LiveGroupEntry(prefix + "cat-" + g.key, s.label, false, counts[g.key], s.badge) { selectCategory(g.key) }
            return Marked(entry) { cat, favs -> !favs && cat in g.memberKeys }
        }
        buildList {
            val fav = shown(favouritesLabel)
            add(Marked(LiveGroupEntry("fav", fav.label, false, counts[LAST_GROUP_FAVOURITES], fav.badge) { selectFavourites() }) { _, favs -> favs })
            val recent = recentGroups.mapNotNull { key ->
                when (key) {
                    LAST_GROUP_FAVOURITES -> null
                    LAST_GROUP_ALL -> all("recent-")
                    else -> categories.firstOrNull { key in it.memberKeys }?.let { group("recent-", it) }
                }
            }
            addAll(recent.mapIndexed { i, m -> if (i == 0) m.copy(entry = m.entry.copy(section = recentTitle)) else m })
            add(all("").let { m -> if (recent.isNotEmpty()) m.copy(entry = m.entry.copy(section = allTitle)) else m })
            categories.forEach { add(group("", it)) }
        }
    }
    return remember(built, selectedCategory, favouritesOnly) {
        built.map { m -> m.entry.copy(selected = m.isSelected(selectedCategory, favouritesOnly)) }
    }
}

/** An entry and how to tell whether it is the current one. */
private data class Marked(val entry: LiveGroupEntry, val isSelected: (String?, Boolean) -> Boolean)

/** A group's badge — logo, flag or big emoji — sized by [modifier]; its initials when asked and there's none. */
@Composable
fun GroupBadgeSlot(badge: GroupBadge.Badge?, label: String, initialsIfBlank: Boolean, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            badge == null -> if (initialsIfBlank) Initials(label)
            badge is GroupBadge.Image -> AsyncImage(
                model = badge.url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)),
            )
            // Old Fire OS fonts lack newer emoji (🧸 draws as bars): leave the slot empty then.
            badge is GroupBadge.Emoji -> if (remember(badge.text) { Paint().hasGlyph(badge.text) }) {
                Text(badge.text, fontSize = 26.sp, maxLines = 1)
            } else if (initialsIfBlank) {
                Initials(label)
            }
            else -> Unit
        }
    }
}

/** A badge-less group: the start of its name. */
@Composable
private fun Initials(label: String) {
    Text(
        label.take(3),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}
