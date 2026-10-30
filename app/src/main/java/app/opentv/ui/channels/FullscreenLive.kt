/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.channels

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.opentv.R
import app.opentv.data.parser.GroupBadge
import app.opentv.data.model.shownName
import app.opentv.data.repo.TvHighlightsRepository
import app.opentv.ui.ChannelsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import app.opentv.ui.theme.focusLift

/**
 * Live TV on one screen: tonight's big programmes across the top ([TvHero]), the groups down the
 * left, the current group's channels filling the rest with what's on now (and next) against each
 * one. While you browse the channels the groups stay in sight but out of reach and the top shrinks
 * to a strip; LEFT (or Back, or ☰) goes back into the groups, and moving through them swaps the
 * channels beside them. UP from the top of either list reaches the programmes. RIGHT or OK goes back into the channels; LEFT or
 * Back from the groups asks the host to reveal the app's navigation menu.
 */
@Composable
fun FullscreenLive(
    rows: List<ChannelsViewModel.Row>,
    categories: List<ChannelsViewModel.CategoryGroup>,
    selectedCategory: String?,
    favouritesOnly: Boolean,
    highlightedKey: Any?,
    // The guide has been built at least once (and no sync is running), so an empty list is final.
    rowsLoaded: Boolean,
    // Where to start when the highlight isn't in the list yet (rows only just loaded): the last
    // watched channel, so returning from the player lands back on it.
    fallbackKey: (List<ChannelsViewModel.Row>) -> Any?,
    // Channels per group (group key, "all", "fav") and the groups last watched from, newest first.
    groupCounts: Map<String, Int>,
    recentGroups: List<String>,
    nowMillis: Long,
    onSelectCategory: (String?) -> Unit,
    onSelectFavourites: () -> Unit,
    onFocusRow: (ChannelsViewModel.Row) -> Unit,
    onPlay: (ChannelsViewModel.Row) -> Unit,
    // Long-press OK on a channel: the host's channel menu.
    onLongPress: (ChannelsViewModel.Row) -> Unit,
    // While the user picks the channel to merge [mergingName] into, BACK calls this instead.
    mergingName: String?,
    onCancelMerge: () -> Unit,
    // After a merge: the row to put focus back on, the row merged away, and the list as it was
    // when OK was pressed. Regrouping re-emits the list a few times; focus follows the row each
    // time until the merged-away row is gone, then [onRefocusDone].
    refocus: Triple<Any, Any, List<ChannelsViewModel.Row>>?,
    onRefocusDone: () -> Unit,
    onExitLeft: () -> Unit,
    emptyContent: @Composable () -> Unit,
    // Tonight's big programmes for the top of the page; none, no top.
    highlights: List<TvHighlightsRepository.Highlight> = emptyList(),
    onWatchHighlight: (TvHighlightsRepository.Highlight) -> Unit = {},
) {
    // Opens on the groups, so the list starts wide and the user picks a group first.
    var groupsOpen by rememberSaveable { mutableStateOf(true) }
    // Only while focus is in here: once LEFT/Back has handed it to the navigation menu, Back
    // falls through to the screen's usual one (the exit prompt).
    var focusInside by remember { mutableStateOf(false) }
    // Back climbs one level: channels → groups → navigation menu.
    BackHandler(enabled = focusInside) { if (groupsOpen) onExitLeft() else groupsOpen = true }
    // Declared later, so it wins: BACK first leaves "merge with…".
    BackHandler(enabled = mergingName != null) { onCancelMerge() }

    // After picking another group the old group's rows are still on hand for a moment; hold the
    // list back until the new ones arrive, so focus lands on the new group's channels instead of
    // on whatever sat at the old position. The timeout covers a group that comes back identical
    // (two empty groups), which a StateFlow wouldn't re-emit.
    var awaitingRows by remember { mutableStateOf<List<ChannelsViewModel.Row>?>(null) }
    LaunchedEffect(rows) { if (awaitingRows != null && rows !== awaitingRows) awaitingRows = null }
    LaunchedEffect(awaitingRows) {
        if (awaitingRows != null) {
            delay(3_000)
            awaitingRows = null
        }
    }

    // A group that turns out empty (all its channels gone, filtered or hidden) would otherwise sit
    // on the loading screen for good. Once the guide is known to be built, give it a moment, then
    // open the groups so the user can pick another.
    val groupEmpty = awaitingRows == null && rows.isEmpty() && rowsLoaded
    LaunchedEffect(groupEmpty, groupsOpen) {
        if (groupEmpty && !groupsOpen) {
            delay(1_500)
            groupsOpen = true
        }
    }

    // When nothing in the channels can hold focus (loading, empty), park it on the screen itself —
    // with focus nowhere, the remote's keys (☰ included) would go nowhere either.
    val holderFocus = remember { FocusRequester() }
    val needsHolder = !groupsOpen && (awaitingRows != null || rows.isEmpty())
    LaunchedEffect(needsHolder) {
        if (needsHolder) {
            delay(50)
            runCatching { holderFocus.requestFocus() }
        }
    }

    val favouritesLabel = stringResource(R.string.guide_favourites)
    val allLabel = stringResource(R.string.guide_all_channels)
    val title = when {
        favouritesOnly -> favouritesLabel
        selectedCategory == null -> allLabel
        else -> categories.firstOrNull { selectedCategory in it.memberKeys }?.let { g ->
            GroupBadge.of(g.rawName).label
        } ?: ""
    }

    // The top is big while focus is on it or on the groups, a strip while the channels have it.
    var heroFocused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onFocusChanged { focusInside = it.hasFocus }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // The user moved on before the regrouped list came back: don't yank focus later.
                if (refocus != null) onRefocusDone()
                when (e.key) {
                    Key.Menu -> { groupsOpen = !groupsOpen; true }
                    Key.DirectionLeft -> if (needsHolder) { groupsOpen = true; true } else false
                    else -> false
                }
            },
    ) {
        TvHero(
            items = highlights,
            expanded = heroFocused || groupsOpen,
            nowMillis = nowMillis,
            onWatch = onWatchHighlight,
            onExitLeft = onExitLeft,
            modifier = Modifier.onFocusChanged { heroFocused = it.hasFocus },
        )
        Column(Modifier.weight(1f).padding(start = 32.dp, end = 32.dp, top = if (highlights.isEmpty()) 20.dp else 4.dp, bottom = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (rows.isNotEmpty() && awaitingRows == null) {
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.live_channel_count, rows.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f).width(24.dp))
            Text(
                stringResource(if (groupsOpen) R.string.live_menu_hint_categories else R.string.live_menu_hint),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (mergingName != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.live_merge_banner, mergingName),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(12.dp))

        Row(Modifier.weight(1f).fillMaxWidth()) {
            val groupsWidth = 340.dp
            Box(Modifier.width(groupsWidth).fillMaxHeight()) {
                GroupsList(
                    open = groupsOpen,
                    categories = categories,
                    selectedCategory = selectedCategory,
                    favouritesOnly = favouritesOnly,
                    counts = groupCounts,
                    recentGroups = recentGroups,
                    favouritesLabel = favouritesLabel,
                    allLabel = allLabel,
                    onSelectCategory = { key ->
                        awaitingRows = rows
                        onSelectCategory(key)
                    },
                    onSelectFavourites = {
                        awaitingRows = rows
                        onSelectFavourites()
                    },
                    onEnter = { groupsOpen = false },
                    onLeft = onExitLeft,
                )
            }
            Spacer(Modifier.width(20.dp))
            Box(Modifier.weight(1f).fillMaxHeight()) {
                ChannelsPane(
                    rows = rows,
                    active = !groupsOpen,
                    waiting = awaitingRows != null,
                    groupEmpty = groupEmpty,
                    holderFocus = holderFocus,
                    favouritesOnly = favouritesOnly,
                    selectedCategory = selectedCategory,
                    highlightedKey = highlightedKey,
                    fallbackKey = fallbackKey,
                    refocus = refocus,
                    onRefocusDone = onRefocusDone,
                    nowMillis = nowMillis,
                    onFocusRow = {
                        // Down from the top lands here straight, groups open or not.
                        groupsOpen = false
                        onFocusRow(it)
                    },
                    onPlay = onPlay,
                    onLongPress = onLongPress,
                    onLeft = { groupsOpen = true },
                    emptyContent = emptyContent,
                )
            }
        }
        }
    }
}

/**
 * The groups column: Favourites, the groups last watched from, then every group. Open, it is a
 * list of badges, names and channel counts that you move through — the channels beside it follow
 * the focused group after a short pause, so holding a direction doesn't rebuild every group on
 * the way. Closed, the same list can't take focus, the current group marked.
 */
@Composable
private fun GroupsList(
    open: Boolean,
    categories: List<ChannelsViewModel.CategoryGroup>,
    selectedCategory: String?,
    favouritesOnly: Boolean,
    counts: Map<String, Int>,
    recentGroups: List<String>,
    favouritesLabel: String,
    allLabel: String,
    onSelectCategory: (String?) -> Unit,
    onSelectFavourites: () -> Unit,
    onEnter: () -> Unit,
    onLeft: () -> Unit,
) {
    val entries = rememberLiveGroupEntries(
        categories, selectedCategory, favouritesOnly, counts, recentGroups,
        favouritesLabel, allLabel, onSelectCategory, onSelectFavourites,
    )
    // The pick behind the focused group, run after a pause unless focus moves on first.
    var focusedKey by remember { mutableStateOf<String?>(null) }
    // At start-up the categories arrive after the list is drawn: until the remembered group is
    // among the entries, focus sits on the first one (Favourites) only because nothing else is
    // there yet, so following it would throw the remembered group away.
    val hasSelection = entries.any { it.selected }
    LaunchedEffect(focusedKey, open, hasSelection) {
        val entry = entries.firstOrNull { it.key == focusedKey } ?: return@LaunchedEffect
        if (!open || entry.selected || !hasSelection) return@LaunchedEffect
        delay(GROUP_FOLLOW_DELAY_MILLIS)
        entry.pick()
    }
    val selectedIndex = entries.indexOfFirst { it.selected }.coerceAtLeast(0)
    // Once the remembered group shows up, move focus onto it (once — later picks follow focus).
    var landed by remember { mutableStateOf(false) }
    FocusedList(
        count = entries.size,
        startIndex = selectedIndex,
        active = open,
        activeIndex = selectedIndex,
        jumpTo = selectedIndex.takeIf { open && hasSelection && !landed },
        jumpVersion = hasSelection,
        onJumped = { if (hasSelection) landed = true },
    ) { index, requester ->
        val entry = entries[index]
        Column {
            entry.section?.let { GroupsHeader(it, true) }
            CategoryEntry(
                label = entry.label,
                badge = entry.badge,
                selected = entry.selected,
                count = entry.count,
                open = open,
                modifier = requester,
                onFocused = { focusedKey = entry.key },
                onLeft = onLeft,
                onEnter = {
                    if (!entry.selected) entry.pick()
                    onEnter()
                },
            )
        }
    }
}

@Composable
private fun GroupsHeader(title: String, open: Boolean) {
    if (open) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 2.dp),
        )
    } else {
        Spacer(Modifier.height(10.dp))
    }
}

/**
 * The channels of the current group, or what stands in for them while they load or when there
 * are none. Only [active] (groups closed) does it take focus; while the groups are open it just
 * shows what the focused group holds.
 */
@Composable
private fun ChannelsPane(
    rows: List<ChannelsViewModel.Row>,
    active: Boolean,
    waiting: Boolean,
    groupEmpty: Boolean,
    holderFocus: FocusRequester,
    favouritesOnly: Boolean,
    selectedCategory: String?,
    highlightedKey: Any?,
    fallbackKey: (List<ChannelsViewModel.Row>) -> Any?,
    refocus: Triple<Any, Any, List<ChannelsViewModel.Row>>?,
    onRefocusDone: () -> Unit,
    nowMillis: Long,
    onFocusRow: (ChannelsViewModel.Row) -> Unit,
    onPlay: (ChannelsViewModel.Row) -> Unit,
    onLongPress: (ChannelsViewModel.Row) -> Unit,
    onLeft: () -> Unit,
    emptyContent: @Composable () -> Unit,
) {
    when {
        waiting -> Spacer(Modifier.fillMaxSize().focusRequester(holderFocus).focusable())
        rows.isEmpty() -> Box(Modifier.fillMaxSize().focusRequester(holderFocus).focusable()) {
            if (groupEmpty) {
                Text(
                    stringResource(R.string.live_group_empty),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                emptyContent()
            }
        }
        else -> {
            val startIndex = rows.indexOfFirst { it.key == highlightedKey }.takeIf { it >= 0 }
                ?: fallbackKey(rows)?.let { key -> rows.indexOfFirst { it.key == key } }?.coerceAtLeast(0)
                ?: 0
            // Keyed on the group so switching category re-focuses the new list's first row.
            val jumpTo = refocus?.takeIf { rows !== it.third }
                ?.let { (key, _, _) -> rows.indexOfFirst { it.key == key }.takeIf { it >= 0 } }
            val refocusSettled = refocus != null && jumpTo != null && rows.none { it.key == refocus.second }
            // Regrouping a big list takes several seconds on a Fire stick.
            LaunchedEffect(refocus) {
                if (refocus != null) {
                    delay(30_000)
                    onRefocusDone()
                }
            }
            androidx.compose.runtime.key(favouritesOnly, selectedCategory) {
                FocusedList(
                    count = rows.size,
                    startIndex = startIndex,
                    active = active,
                    jumpTo = jumpTo,
                    jumpVersion = rows,
                    onJumped = { if (refocusSettled) onRefocusDone() },
                ) { index, requester ->
                    val row = rows[index]
                    FullscreenChannelRow(
                        row = row,
                        nowMillis = nowMillis,
                        modifier = requester,
                        onFocus = { onFocusRow(row) },
                        onPlay = { onPlay(row) },
                        onLongPress = { onLongPress(row) },
                        onLeft = onLeft,
                    )
                }
            }
        }
    }
}

/**
 * A lazy list that, on first composition, scrolls to [startIndex] and puts focus on that item —
 * so opening the categories lands on the current one and coming back lands on the channel you
 * were on. [item] gets a modifier to attach to its focusable root.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FocusedList(
    count: Int,
    startIndex: Int,
    // Only an active list takes focus; becoming active puts it on [activeIndex] when given, else on
    // the row last focused.
    active: Boolean = true,
    activeIndex: Int? = null,
    // Move focus to this row now (the list changed under it), then call [onJumped].
    jumpTo: Int? = null,
    // A new value re-runs the jump even to the same index (the list changed under it).
    jumpVersion: Any? = null,
    onJumped: () -> Unit = {},
    item: @Composable (index: Int, requester: Modifier) -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (startIndex - 3).coerceAtLeast(0))
    val focusRequester = remember { FocusRequester() }
    var listHasFocus by remember { mutableStateOf(false) }
    // The requester follows focus, so coming back into the list (RIGHT from the navigation menu)
    // returns to the row you left rather than to whichever row sits nearest the menu.
    var focusedIndex by remember { mutableIntStateOf(startIndex) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        if (activeIndex != null && activeIndex in 0 until count) focusedIndex = activeIndex
        // The item has to be laid out — and, at app start, the window focused — before it can take
        // focus, and a request made too early is silently dropped. Keep asking until it sticks.
        repeat(40) {
            delay(75)
            if (listHasFocus) return@LaunchedEffect
            // The row may have been scrolled out of the composed window (rows arriving after the
            // first frame); bring it back so its requester is attached before asking.
            if (count > 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == focusedIndex }) {
                focusedIndex = focusedIndex.coerceIn(0, count - 1)
                listState.scrollToItem((focusedIndex - 3).coerceAtLeast(0))
            }
            runCatching { focusRequester.requestFocus() }
        }
    }
    val jumped by rememberUpdatedState(onJumped)
    LaunchedEffect(jumpTo, jumpVersion) {
        val target = jumpTo ?: return@LaunchedEffect
        if (target !in 0 until count) return@LaunchedEffect jumped()
        focusedIndex = target
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == target }) {
            listState.scrollToItem((target - 3).coerceAtLeast(0))
        }
        // The requester moves to the target row on the next frame; ask until it takes.
        for (attempt in 0 until 20) {
            delay(50)
            if (runCatching { focusRequester.requestFocus() }.isSuccess) break
        }
        jumped()
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            // Only redirect into the remembered row while it is composed: an unattached requester
            // throws ("FocusRequester is not initialized") and took the app down on a key press.
            .focusProperties {
                enter = {
                    if (active && listState.layoutInfo.visibleItemsInfo.any { it.index == focusedIndex }) focusRequester
                    else FocusRequester.Default
                }
            }
            .onFocusChanged { listHasFocus = it.hasFocus },
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(List(count) { it }, key = { i, _ -> i }) { index, _ ->
            val tracked = Modifier.onFocusChanged { if (it.isFocused) focusedIndex = index }
            item(index, if (index == focusedIndex) tracked.focusRequester(focusRequester) else tracked)
        }
    }
}

@Composable
private fun CategoryEntry(
    label: String,
    badge: GroupBadge.Badge?,
    selected: Boolean,
    count: Int?,
    // Closed: drawn the same, but can't take focus.
    open: Boolean,
    modifier: Modifier,
    onFocused: () -> Unit,
    onLeft: () -> Unit,
    onEnter: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .focusProperties { canFocus = open }
            .leftReveals({ focused }, onLeft)
            .onPreviewKeyEvent { e ->
                if (focused && e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight) { onEnter(); true } else false
            }
            .fillMaxWidth()
            .focusLift(focused)
            .clip(shape)
            .background(
                when {
                    focused -> MaterialTheme.colorScheme.primaryContainer
                    selected -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.surface
                },
            )
            .then(if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(interactionSource = null, indication = null, onClick = onEnter)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Without a badge a closed strip still needs something to tell the groups apart.
        GroupBadgeSlot(badge, label, initialsIfBlank = false, modifier = Modifier.size(width = 80.dp, height = 40.dp))
        run {
            Spacer(Modifier.width(14.dp))
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (count != null) {
                Spacer(Modifier.width(10.dp))
                Text(
                    "$count",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * One channel across the pane: logo and name, then the programme on now with what follows it on
 * the same line, and the now programme's progress across the whole row underneath.
 */
@Composable
private fun FullscreenChannelRow(
    row: ChannelsViewModel.Row,
    nowMillis: Long,
    modifier: Modifier,
    onFocus: () -> Unit,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
    onLeft: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .leftReveals({ focused }, onLeft)
            .longPressOk({ focused }, onLongPress)
            .fillMaxWidth()
            .focusLift(focused)
            .clip(shape)
            .background(if (focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .then(if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable(interactionSource = null, indication = null, onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // No channel number: the provider's numbers jump about and mean nothing. Its room goes
        // to the logo, as tall as the two text lines beside it.
        ChannelLogo(
            logoUrl = row.primary.shownLogo(),
            name = row.primary.shownName,
            modifier = Modifier.size(width = 128.dp, height = 64.dp).clip(RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.primary.shownName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val nowProg = row.now
            val nextProg = row.next
            Spacer(Modifier.height(2.dp))
            val upNext = nextProg?.let { stringResource(R.string.live_up_next, slot(it.startUtcMillis, it.endUtcMillis, it.title)) }
            val dim = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            Text(
                text = buildAnnotatedString {
                    append(nowProg?.let { slot(it.startUtcMillis, it.endUtcMillis, it.title) } ?: stringResource(R.string.guide_no_info))
                    if (upNext != null) withStyle(SpanStyle(color = dim)) { append("     "); append(upNext) }
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (nowProg != null) {
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(nowProg.progressAt(nowMillis).coerceIn(0f, 1f))
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        // Only shows the state: a focusable star inside the row is where focus lands when a dialog
        // closes, so OK would favourite instead of play. Favourites are set from the channel menu.
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (row.primary.favourite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = if (row.primary.favourite) stringResource(R.string.common_remove_favourite)
                else stringResource(R.string.common_favourite),
                tint = if (row.primary.favourite) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "11:00 – 12:30 · Title", the way the list writes both the programme on now and the next one. */
private fun slot(start: Long, end: Long, title: String) =
    "${clock.format(Date(start))} – ${clock.format(Date(end))} · $title"

/**
 * LEFT on the row itself (not on a child like the star button, which LEFT should just leave)
 * calls [onLeft] — the host's "reveal the navigation menu".
 */
private fun Modifier.leftReveals(isRowFocused: () -> Boolean, onLeft: () -> Unit): Modifier =
    onPreviewKeyEvent { e ->
        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft && isRowFocused()) {
            onLeft()
            true
        } else {
            false
        }
    }

/**
 * Holding OK on the row (not on the star) calls [onLongPress] once; the rest of that press —
 * repeats and the release that would play the channel — is swallowed.
 */
private fun Modifier.longPressOk(isRowFocused: () -> Boolean, onLongPress: () -> Unit): Modifier = composed {
    var held by remember { mutableStateOf(false) }
    onPreviewKeyEvent { e ->
        val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
        when {
            !ok -> false
            // A fresh press: the last one may have ended in a dialog that took its release.
            e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0 -> { held = false; false }
            e.type == KeyEventType.KeyDown && e.nativeKeyEvent.isLongPress && isRowFocused() -> {
                held = true
                onLongPress()
                true
            }
            held && e.type == KeyEventType.KeyUp -> { held = false; true }
            else -> held
        }
    }
}

private val clock = SimpleDateFormat("HH:mm", Locale.getDefault())

// How long the focus rests on a group before the channels beside it switch to that group.
private const val GROUP_FOLLOW_DELAY_MILLIS = 400L
