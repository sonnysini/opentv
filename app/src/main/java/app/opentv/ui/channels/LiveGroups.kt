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
    return remember(categories, selectedCategory, favouritesOnly, counts, recentGroups, external, favouritesLabel, allLabel) {
        val allSelected = !favouritesOnly && selectedCategory == null
        fun all(prefix: String) = LiveGroupEntry(prefix + "all", allLabel, allSelected, counts[LAST_GROUP_ALL]) { selectCategory(null) }
        fun group(prefix: String, g: ChannelsViewModel.CategoryGroup): LiveGroupEntry {
            val s = shown(g.rawName)
            return LiveGroupEntry(prefix + "cat-" + g.key, s.label, !favouritesOnly && selectedCategory in g.memberKeys, counts[g.key], s.badge) {
                selectCategory(g.key)
            }
        }
        buildList {
            val fav = shown(favouritesLabel)
            add(LiveGroupEntry("fav", fav.label, favouritesOnly, counts[LAST_GROUP_FAVOURITES], fav.badge) { selectFavourites() })
            val recent = recentGroups.mapNotNull { key ->
                when (key) {
                    LAST_GROUP_FAVOURITES -> null
                    LAST_GROUP_ALL -> all("recent-")
                    else -> categories.firstOrNull { key in it.memberKeys }?.let { group("recent-", it) }
                }
            }
            addAll(recent.mapIndexed { i, e -> if (i == 0) e.copy(section = recentTitle) else e })
            add(all("").let { if (recent.isNotEmpty()) it.copy(section = allTitle) else it })
            categories.forEach { add(group("", it)) }
        }
    }
}

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
