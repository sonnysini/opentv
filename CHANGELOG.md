# Changelog

## 0.54.0 — 8 November 2026

- **Film player: times beside the bar, and when it ends.** Where you are sits at the left end of
  the seek bar and the length at the right, in place of "1:02:13 / 2:46:12 −1:43:59" by the
  buttons. With the bar up, the top right corner says when the film ends ("Ends at 23:14").
- **The title's own lettering in the player.** Films and shows show their TMDB logo above the bar,
  as on their pages (the name when there's none); an episode adds "S4 · E7 · name" under it.
- **Stats for nerds.** A new i button in the film player opens the stream read-out of the live
  player, plus the file (container and size, e.g. "MKV · 22.8 GB") and the screen's mode
  ("3840×2160 · 60 Hz"). It stays up with the bar hidden; i again or Back closes it.

## 0.53.1 — 8 November 2026

- **One look for the selected card.** Every card with a picture (films, shows, Continue
  watching, episodes, football games, search results) now shows the selection with the same
  white 3dp outline; before, films and shows used a green one and football a thinner white one.
  Rows, buttons and the guide stay green.
- **The Live TV groups look like the menu.** The focused group is a solid green pill (still
  sliding from group to group) with dark text; the open group has a light green veil and green
  text, instead of two near-identical greys and a thin outline. The broadcaster before a group's
  name ("SKY", "DAZN", "DIFFERITA") is no longer green, just the same colour as the name.

## 0.53.0 — 8 November 2026

- **A new Search.** The bar on top opens the TV's own keyboard straight away (on a Fire TV, the
  full-screen one with "type from your phone"); its Search key closes it and puts the cursor on
  the first result, and up to the bar and OK types again. The results come one row per page of
  the menu, in the menu's order: **Live TV** (the channels by name, with what's on, then the
  programmes in the guide from now to the next two days, LIVE or with day and time, ● REC when
  booked), **Football** (today's and the week's games of the team), **Films**, **Shows** and
  **Recordings**. Rows with nothing are left out. A live programme plays its channel; one still
  to come opens the panel to record it, set a reminder or watch the channel now; a game goes to
  the Football page and plays as from there.
- The guide search is quick on a big guide: the titles are indexed after each guide download
  (a couple of seconds, in the background), so a search takes milliseconds instead of a second.
  It finds words by how they start ("juve" finds Juventus).
- The search's own on-screen keyboard is gone.

## 0.52.1 — 7 November 2026

- **OK on a menu entry takes you into the page, everywhere.** Live TV and Football already moved
  the cursor into the page and closed the menu; Films, Shows and Recordings left it on the menu,
  waiting for RIGHT. Now every page does the same, and so do Search, Profiles, Settings and the
  screens inside them: the cursor starts on the first item, no press needed to wake it.

## 0.52.0 — 6 November 2026

- **No more zoom on the selected card.** Posters, Continue Watching, episodes and football games
  no longer grow when the cursor lands on them, so rows don't bounce as you move: the selection
  is the ring alone (white on Football, teal on Films and Shows).
- **Opening the menu slides the page instead of squeezing it.** On Football, Films and Shows the
  cards kept resizing as the side menu opened and closed; now the page keeps its size and moves
  right, as on Live TV.
- **Football: five games to a row** instead of six, a little bigger.
- **Left into the menu lands on the page you're on**, not on Recordings when you came from a
  row low on the screen.

## 0.51.0 — 6 November 2026

- **Hold OK: one panel down the right, everywhere.** The menus for a channel and for a programme
  in the guide now slide in from the right, like the Football feed picker: a small line on top
  (what's on and until when, or the day and time of the programme), the name in big letters, a
  thin bar with the time left for what's on, then the choices with an icon each, in groups —
  *Later* (the next programmes, by time), *Channel* (favourites, merge, logo, guide), *Reminder*,
  *More* (external player). The qualities to record in are small chips in a row. Back closes the
  panel, so there's no "Cancel" any more. The warning before watching live during a recording
  stays in the middle of the screen, restyled, with the focus on "Keep recording".
- **Continue Watching: hold OK on a card.** The same panel: the episode and the show (or the
  film), a bar with the time to go, then *Resume from 6:40*, *Start again*, *Next episode*,
  *Choose the source* (another provider or quality, from the same point); for a show *Go to the
  episodes* and *Mark as watched* (the next episode takes its place on the shelf); *Remove from
  the list*; *Open in an external player*.
- **Films and episodes remember where you stopped, to the second.** Leaving the player used to
  lose the last point (only the saves every 15 seconds stuck), so an episode watched for a few
  seconds didn't reach Continue Watching and resume could start up to 15 seconds back.

## 0.50.0 — 6 November 2026

- **Cleaner pages: the same top everywhere, whole cards, bigger posters.**
  The top banner is the same height on Live, Football, Movies and Shows. Rows now hold an exact
  number of cards with the same margin on both sides — no sliver of a next card at the right edge:
  six games to a row on Football (two whole rows fit), four in Continue Watching, five in the
  Top 10 and seven posters in every other row. Posters are about 1.4× bigger, like in the usual
  streaming apps. The card in focus grows a little more, keeping its usual ring. The bottom of the
  pages with rows (and the Live lists) fades into the background instead of ending on a cut card.

## 0.49.0 — 6 November 2026

- **Football: the "Watch" button tells you where the game is, with no line of text above it.**
  Before 15 minutes to kick-off it is grey with a clock and "From 20:30"; then it is "Watch",
  filling up in white while the app looks for the best feed by itself, and solid white once the
  best one is found. A game on with no channel to play it: grey, "Not available". Pressing OK on a
  grey button only shakes it; holding OK still lets you pick a channel by hand. The "12/30"
  counters on the cards and the thin scanning line under the button are gone.

## 0.48.2 — 6 November 2026

- **Event log.** The app keeps a small diary of what it does (screens opened, channels tuned,
  the screen kept awake or let go, going to the background, the box's screensaver, player errors,
  crashes) in its own files folder, at most about 1 MB. Nothing changes on screen: it is there to
  find out what went wrong when something odd happens.

## 0.48.1 — 6 November 2026

- **"Watch on …" in tonight's highlights works again after a list is synced or a provider is
  turned back on.** The highlight remembers the channel by name, not by its id, and OK now does
  exactly what pressing that channel in the list does (your preferred quality, then the fallback).

## 0.48.0 — 5 November 2026

- **Turn a provider off without removing it.** Settings › Providers has a Turn off / Turn on
  button on each list. Off: its channels, films and series disappear and it is no longer synced,
  but the login stays saved. On: it syncs again (films and series included).

## 0.47.1 — 5 November 2026

- **The side menu's focus matches the rest of the app again:** the solid green pill, as in the
  guide, Film and settings, instead of a white ring. The open page keeps its faint green wash.

## 0.47.0 — 5 November 2026

- **New side-menu icons.** The rail uses Phosphor's thin-line icons instead of Material's solid
  ones. The open page shows its icon filled, in green, on a faint green wash.

## 0.46.3 — 5 November 2026

- **Tonight's programmes are there with the channels.** The top of the live page is kept on disk
  and the TV guide is read once a day, the first time the app opens: from then on a cold start
  shows it at once, together with the groups and the list, instead of a few seconds later with
  the list jumping down. What has ended drops off by the clock; a failed read is tried again a
  few minutes later. Its pictures are fetched at start too.
- **On the day's first opening the top slides in** instead of pushing the channels down in one go.
- **The tabs and their groups are there at start too.** Italy, Football… were worked out from every
  channel on each launch, and the open tab showed no groups until then; now they come from the
  saved copy with the rest of the list.
- **Film and Shows open straight away.** Their homes are built in the background a few seconds
  after launch, while the live page is up, instead of when the page is first opened.

## 0.46.2 — 4 November 2026

- **Leaving the app lets go of the stream.** Home, the box going to standby or another app now stop
  the channel (or pause the film) and free the account's one connection; coming back puts the
  channel on again, or the film at the same point, paused. Before, a box in standby kept streaming
  in the background and another TV's channel got cut every few seconds.
- **No automatic picture-in-picture on Home.** The floating window opens only from its button in
  the player bar.

## 0.46.1 — 4 November 2026

- **The scan screen's left side, reordered.** On top the competition's logo with its name and
  matchday ("CHAMPIONS LEAGUE · 4ª GIORNATA"), then the teams one under the other, each with its
  crest, then "Finding the best source" in the theme's colour.

## 0.46.0 — 4 November 2026

- **One button on the football page: "▶ Watch".** OK finds the best feed and plays it, as before;
  holding OK picks the source by hand. "Pick source" and ↻ are gone from the top of the page; left
  and right on Watch turn the carousel.
- **The source picker is a panel down the right.** The page darkens behind it; the match on top,
  then "Check sources again" with the time of the last check and how many were checked, then the
  feeds that play — best first, marked "recommended" — with the same badges as the player's bar
  (1080p, 50fps, 8.4 Mb/s, AUDIO HD 5.1, ✓ right game), and the ones that don't under
  "Unavailable", with why. Feeds not checked yet show just their name.
- **A new scan screen, animated.** While the best feed is looked for, the left shows the game,
  the last picture a look caught with the feed being checked, how many are done and a thin bar;
  the right lists the feeds as the picker does. Each one that plays gets its badges one after
  another and slides to its place in the ranking, the best on top; one that won't play says so
  and leaves to the right. At the end "Found": the best feed lights up on top, the rest fades and
  its picture grows to the whole screen, where the player picks it up — the same picture held
  until the game's first frame, so there's no jump. The screen is full screen on black, over the
  side menu too. Back cancels, as before. Start-up times and scoreboard codes are no longer shown.
- **New game cards.** Big crests and a chip top left: the time ("OGGI 21:00", "SAB 15:00"), the
  minute in red while live, "FINITA" once over, a white "▶ HIGHLIGHTS" when they're ready. The
  teams under the card, centred; the ready-feeds count and the line under finished games are gone.
- **One Diretta Gol card per competition.** Instead of one per kick-off: the competition's logo
  and "DIRETTA GOL" on its colour, no chip. Watch opens the slot on now, or the next one.
- **Competition logos drawn for the dark page**, without the white box, over each row.
- **A wider source picker**: long badges such as "AUDIO HD 5.1" fit whole.

## 0.45.0 — 4 November 2026

- **Today's games in a carousel.** The top of the football page now turns through all of today's
  games that matter — Serie A and the Italian teams in the cups, with their goal shows — every
  8 seconds, live ones first, dots underneath. Left off "Watch now" and right off ↻ step through
  them by hand; it holds still while a game plays muted behind it. The tabs are gone, and the
  games on the carousel stay in their rows below too.
- **Diretta Gol for the cups.** Champions League nights get their goal show as a card of its own,
  and so do Europa and Conference League ones (Sky's "Diretta Gol Uel/Uecl" shows both): found
  through the guide on whichever Sky Sport channel carries it — one for each kick-off (18:45 and
  21:00).
- **Champions League first on Champions nights.** The rows go by the day a competition plays next,
  then Champions, Europa, Conference, Serie A: an odd Europa game kicking off earlier on a
  Wednesday no longer puts its row on top.
- **Team channels wear their club's crest.** "Milan", "DAZN Serie A 3 Milan" and the like now
  show the Serie A team's crest from FotMob — the same ones as on the football page, on a clear
  background — ahead of
  the provider's logo and of a logo pack, whenever outside logos are on (Settings › Logos).
- **Back up to the top shows the whole card again.** Coming back up from the rows, the page
  scrolled only as far as the buttons and left the top of the game's card off screen.

## 0.44.1 — 4 November 2026

- **Cup games found under their Italian names.** Guides write "Atletico M. - Bayern M." or
  "FC Barcellona - Aston Villa": a game is now matched by any word of each team's name and by the
  Italian one (Barcellona, Lipsia, Stoccarda, Praga…), so these games get their channels and play.
  A game with no channel found says so instead of doing nothing on OK.
- **Changing game in the player goes like a TV channel.** Channel up/down to another game starts
  from its first channel, with the servers chosen in the settings ahead of the rest. The game
  started with OK keeps the best feed the scan found.
- **Closing "Pick source" goes back to the game.** Back out of the list of a game's feeds and
  focus returns to its card, not to the menu on the left.

## 0.44.0 — 3 November 2026

- **Film player bar with icons.** Round icon-only buttons in place of the labelled chips: a white
  disc marks the focused one, a light one the picker that's open. The seek bar runs the full width;
  the time (position / length, and what's left) sits on the right of the buttons. Up from the
  buttons goes back to the seek bar, down puts the bar away. The picture-format button shows the
  new format's name for a moment. Look taken from NuvioTV's player (GPL-3.0).

- **A proper film player.** With the bar hidden the remote works like a TV player: left/right jump
  10 s at a time, faster while held (30 s, a minute, two), with the target time shown over the bar
  before the jump happens; OK pauses; the media keys work everywhere. The bar opens on its seek bar,
  down reaches play/pause, and the time on the right is what's left.
- **Resuming a film is quick again.** Providers redirect each request to one of several servers
  that don't all hold the same file; a resume or a seek used to mix two of them and stall for half a
  minute or fail ("must be in a Cues"). The player now sticks to one server for the whole film.
- **Films with DTS or TrueHD audio play with sound** on boxes that can't decode them (Fire TV): the
  FFmpeg audio decoder steps in when the box has none. A codec that fails to start hands over to the
  next one instead of stopping playback.
- After a dropped connection a film carries on from where it stopped, not from where it was opened.
  The film buffer is deeper (up to two minutes) but capped at 64 MB so a 4K file can't fill the
  memory of a stick.
- Audio and subtitle pickers name each track in your language with its format ("Italiano · Dolby
  Digital+ · 5.1", forced subtitles marked), list your language first, and remember the pick for the
  next film. Subtitles have a size (small, medium, large) and a background (shadow, box, outline).
- A picture-format button (Fit, Fill, Stretch) in the film player, remembered apart from live TV.

## 0.42.0 — 3 November 2026

- **Movies, redone in the live page's style.** A hero at the top turns over the week's most
  watched films that are in your list — backdrop, the film's own title art, year, genres, running
  time, TMDB rating and the copies on offer (4K/FHD/HD), Watch and Details. Under it, genre tabs in
  Oswald (Featured, Action, Comedy, Drama, Horror, Thriller, Sci-fi, Animation, Family, All) and
  shelves: Continue watching, the week's Top 10 with big numbers, Recently added, Popular in
  Italy, Top rated of all time. Charts come from TMDB and keep only the titles your provider has
  (matched by TMDB id, else by name and year); they are cached for 12 hours. All is the provider's
  own categories, as before. Without a TMDB key the hero shows the latest arrivals and the shelves
  are the catalogue's own.
- **Shows, the same way.** The week's trending series in the hero (Details opens the show), TMDB's
  TV genres as tabs (Crime, Drama, Comedy, Sci-Fi & Fantasy, Animation, Action & Adventure,
  Mystery, All), and shelves: Continue watching, New episodes, Top 10, Recently added, Popular in
  Italy, Top rated. New episodes lists the shows you're watching whose furthest episode you
  finished and that have more after it: it shows the next one and how many are left. Their episode
  lists are refreshed once a session.
- Charts and the film page's quality buttons use the Italian copy of a title when the provider also
  has it in a foreign-language category ("🇩🇪 DE Netflix").
- **New film and show pages.** The same hero as the home: backdrop, the title's own lettering,
  year, genres, running time or seasons, rating and the copies on offer, the plot. A film has
  Watch/Resume plus a button for each other copy (FHD, HD…), favourite and add-on sources, then cast
  and similar films. A show has Resume S3 · E2 (or Watch the next episode), then tabs: one per
  season, Cast, Similar. Seasons are rows of episode cards with the still, a bar of how far you got,
  the length and "✓ Seen" or the minutes left; the page opens on the season you're in. Episode
  titles drop the show name and code the provider puts in front.
- Without a TMDB key the Movies hero no longer goes missing: a film with no wide art shows its
  poster instead.
- **TMDB key from the remote.** The key field now saves on the TV keyboard's Done and DOWN moves
  on to Save; before, a key typed with the remote could never be saved.
- Posters no longer show the providers' fake ★ 10.0.

## 0.41.0 — 3 November 2026

- **OpenTV is now MyTV.** New name, new teal logo (a screen with a play button) and a new wide
  banner with the two-tone MyTV wordmark, matching My Launcher. The app id is unchanged, so it
  updates in place and keeps every setting. About notes that MyTV is based on OpenTV.

## 0.39.1 — 2 November 2026

- **Football: the scan server is asked first.** With a scan server set, the TV reads its rankings
  before looking at any feed itself, so a game the server already scanned is never scanned again
  on the TV. Games not started yet are left to the server; the TV only scans a game on now that
  the server has no ranking for. Without a scan server nothing changes.

## 0.39.0 — 2 November 2026

- **Download rate in the player bar.** Next to the frame rate, the Mb/s the channel is actually
  using, measured live.
- **Football: one Serie A matchday at a time.** Next matchday's games appear once the current
  one is over, on the Football page and on the launcher row. The cups are unchanged.
- **Football: feeds checked closer to kick-off.** The best feed is looked for 15 minutes before
  the game instead of 35, when the real pictures are already on.
- **Football: still pictures ruled out.** Feeds above 50 fps (a frozen logo, never the game) are
  no longer offered.

## 0.38.2 — 31 October 2026

- **Lighter in the background.** Screens stop following their data while the app is in the
  background or hidden behind the player, and pick it up again when they come back.

## 0.38.1 — 31 October 2026

- **Football: lighter.** The page is read when you open it (unless read in the last five minutes)
  and again only when a game kicks off or ends; nothing runs while a channel plays or the app is
  in the background. LIVE now comes from the clock: from kick-off for two hours (two and a half
  for the cups), with no minute.
- **Updates keep your data.** A schema change can no longer silently wipe favourites, hidden
  channels and other personal settings.

## 0.38.0 — 31 October 2026

- **Football: the European cups.** Champions League, Europa League and Conference League join
  Serie A, one row per competition. The competition that plays next comes first; inside a row the
  games follow kick-off time, Italian teams first when two start together.
- **Football: day and time on every card.** "OGGI 18:00", "MAR 21:00", so no day separators.
- **Football: no score anywhere.** Live games show only LIVE and the minute, on the top, the cards
  and the launcher. Finished cup games are gone.
- **Football: Italian teams only for the extras.** The top, the background scan and the launcher
  cover Serie A and the cup games with an Italian team; the other cup games are scanned when you
  open them. Highlights stay Serie A only.
- **Football: the scan starts 35 minutes before kick-off**, as the scan server, which now also
  scans the Italian teams' cup games.

## 0.37.3 — 31 October 2026

- **Live TV: groups switch faster.** Moving onto another group no longer freezes the screen for
  half a second, the channels follow after a much shorter pause, and the last groups you opened
  come back at once.
- **Live TV: the focus slides.** The box around the focused group glides from one group to the
  next instead of jumping.
- **Live TV: a fixed way up and down.** UP from the first channel goes to the tabs; from the top's
  button DOWN goes to the tabs and then the groups, and quick presses no longer skip the tabs.

## 0.37.2 — 30 October 2026

- **Live TV: group names on one line.** "SKY INTRATTENIMENTO", with the broadcaster in the accent,
  so more groups fit down the column. The dot beside the selected group is gone.
- **Live TV: the top folds away in the channels.** Tonight's programmes show when the page opens,
  hide once you move into the channels and come back with UP onto the tabs. The group name and
  channel count beside the tabs are gone.
- **Live TV: no menu flash on RIGHT.** Moving from a group into its channels no longer opens the
  menu for a moment on the way.

## 0.37.1 — 30 October 2026

- **Live TV: the menu stays beside the list.** The full-screen channel list keeps the navigation
  menu as a slim strip of icons down the left, as on the other pages; LEFT from the groups opens
  it with its labels, RIGHT goes straight back to the group you were on.
- **Live TV: rows show only what's on now.** What follows is in the guide and the player.

## 0.37.0 — 30 October 2026

- **Live TV: tonight's big programmes across the top.** The full-screen channel list opens with a
  carousel of tonight's prime-time highlights from the TV guide (films, shows, series — no sport or
  news), one card per channel you have, moving on by itself with dots below. OK on a card opens
  that channel; going down into the list shrinks the carousel to a strip, UP from the first channel
  brings it back. When a programme's still won't load, the channel's logo stands in.
- **Live TV: tabs above the groups.** Groups are now gathered under tabs — ★, Italy, Football,
  Sport, Entertainment, Cinema, Kids, Music, Abroad (one shelf per country, biggest first, hidden
  with "Italian only") and All. Group names are drawn as tall capitals with the brand small above.
  Time-shifted channels (+1, +2, +3) sit together in their own group inside each tab, with the
  guide shifted to match. Your merges and ordering from the phone page still win. The last tab you
  used is the one that opens next time.

## 0.36.2 — 29 October 2026

- **Football: the game at the top bigger.** The live preview (and the still before it) is again
  as tall as the top of the page, against the right edge, melting into the page on the left and
  below — still the whole 16:9 picture, nothing cut off.

## 0.36.1 — 29 October 2026

- **Football: the game at the top of the page whole.** The live preview (and the still before it)
  now shows the full 16:9 picture as broadcast, as tall as the top of the page and melting into it
  on the left and below, instead of zoomed in with the top and sides cut off.

## 0.36.0 — 29 October 2026

- **Football: the game plays at the top of the page.** Once a game is on (or a quarter of an hour
  from kick-off) and its best feed is known, the top of the football home plays it, muted, in place
  of the still — with "Watch now" in focus, nothing being scanned and no recording running. It stops
  when you move down to the rows or open a feed, and after three minutes untouched, so the
  account's one connection isn't held. Off together with the guide's video preview in Settings.
- **60 fps feeds go last.** Italian football is broadcast at 50 fps; the 60 fps feeds seen were a
  frozen DAZN logo. They now rank below every other feed that plays, on the TV and on the scan
  server, and stay in "Pick source" to choose by hand.

## 0.35.0 — 29 October 2026

- **Football: every channel the guide says shows the game.** A guide channel showing the match
  now brings in every channel of yours with its name, from every source — "DAZN 1" from all the
  provider's sections, not only the one copy the guide got matched to. Other countries' copies
  ("LA: DAZN 1") stay out. Same channels the scan server finds.
- **Bitrate measured on the TV too.** The football scan works out each feed's real bitrate from
  the piece it already fetches (no extra time); the heavier feed wins between feeds alike in frame
  rate and lines, and the scan screen shows it ("8,0 Mbps").
- The feed watched last no longer stays first once a later scan finds it dead.

## 0.34.0 — 29 October 2026

- **Football: rankings from a scan server.** Settings › Playback › Football scan server takes the
  address of a server that, before each kick-off, looks at every feed of the games about to start
  (while the TV is off and the account's one connection is free) and ranks them. The football home
  reads it: "Watch now" then plays the best feed at once, with no scan on the TV, and nothing is
  looked at in the background for a game the server has ranked. Feeds are matched by the
  provider's stream id, or by the provider's channel name on another of its servers. Without a
  server, or when it has nothing for a game, the TV scans as before.
- **Bitrate in the ranking.** When a feed's bitrate is known (the scan server measures it), the
  heavier feed wins between feeds alike in frame rate and lines.

## 0.33.0 — 29 October 2026

- **Football: a new look, streaming-app style.** The match on now (or next) fills the top of the
  page: its best feed's picture behind (the two teams' colours before any scan), the teams in tall
  capitals, one line on the feed that will play, and "Watch now", "Pick source" and ↻ buttons. The
  source list moved into "Pick source". Games are 16:9 tiles in the two teams' colours with the
  time, LIVE minute or Highlights on a chip and the feeds ready in a corner; coming games are
  grouped by day. Up and down scroll only as far as needed, so the top of the page shows whole.

## 0.32.0 — 28 October 2026

- **Football: the goal channel is a game of its own.** When two or more Serie A games kick off
  together, "Diretta Gol" gets its own card (last in the row, and a tab on the big card when the
  games are on): Watch now, its feeds best first, scan, channel up/down between it and the games.
  Its scan doesn't read the scoreboard, which jumps from game to game. It's no longer listed
  under each match.

## 0.31.1 — 28 October 2026

- **Football: the Serie A goal channel** ("Diretta Gol Serie A") is listed under every Serie A
  match, last. It has no guide, so it was never found; goal channels of other competitions stay
  out.

## 0.31.0 — 28 October 2026

- **Football: a match's scan holds for the whole game.** Watch now used to look at the feeds again
  once 20 minutes had passed; now a match is looked at once (before kick-off or as it starts) and
  plays straight away after that. Scan again still looks afresh.
- **The feed you watched comes first.** Back on a match, Watch now opens the feed you were on,
  ahead of the scan's ranking.
- **Channel up/down moves between the games on now**, each opening on its own feed (the one you
  watched there, else the best). The match's other feeds are on ☰ and in Quality.

## 0.30.0 — 28 October 2026

- **Rows for a home-screen launcher.** A launcher signed with the same key can read the channels
  played lately, the favourites with what is on now, and the Football matches (live, today,
  finished with highlights, coming up) — through a provider no other app can open.
- **A launcher can open Football**: the tab, a match with Watch automatically, or its highlights.

## 0.29.4 — 28 October 2026

- **Football: holding OK on a match lets you pick the channel again.** Letting go of OK used to
  play the first source in the picker straight away; the picker now waits for a fresh press.

## 0.29.3 — 28 October 2026

- **More channels find their guide, and fewer get the wrong one.** The guide matcher was checked
  against a real Italian list, the same list without the provider's guide ids, and a public
  playlist:
  - a guide that lists two channels under one name no longer blocks the other guides from
    matching it;
  - a `+` that is not a timeshift is part of the name (`Sky Uno +` is Sky Uno Plus, not Sky Uno);
  - small typos on long names are forgiven (`Suspence`, `Documentaris`, `Fisching`), never
    across a number (`27 Mediaset` is not `20 Mediaset`) or a timeshift;
  - `+2` no longer takes the guide of `+24`, and a `+1` no longer shows its base channel's guide
    when the provider gives it the base's id;
  - the provider's guide id is checked against the channel's name (`Discovery Channel` carrying
    `Disney Plus Animazione` is turned down when the name finds the right guide);
  - a channel filed under a shorter name (`Sky Nature` → `Nature`) is also looked up by its
    full name.
- **`+1`/`+2` channels prefer their base channel's guide moved later** over the provider's own
  copy, which can list the same slot twice an hour apart.
- **A guide id with only finished programmes no longer hides the next one**: the guide row
  moves on to the channel's next candidate.
- `(540p)`, `(1080i)`, `[Not 24/7]` and `[Geo-blocked]` in channel names are recognised.

## 0.29.2 — 27 October 2026

- **Faster football scan.** The home and away team channels carry the same DAZN feed, so only
  the home team's feeds are looked at; the away team's stay out of the list and come in only
  when none of the home team's plays the game.
- **The scan starts as soon as Calcio opens**, instead of sometimes waiting up to 30 seconds.
- **Games on at the same time are all scanned**: the other tabs of the big card were skipped.

## 0.29.1 — 27 October 2026

- **Cleaner feed list on the football home.** Feed names are short ("Lazio", "Zona DAZN SA1"),
  the restream source sits small at the end ("DVS", "HQ", "SAT · HEVC"), and outlined tags in
  the theme's colours show what the scan measured: ✓ right game, resolution, 50/60 FPS,
  Audio HD, 5.1. Feeds not looked at yet show the quality their name claims, dashed; feeds that
  don't start or show another game fade at the bottom.
- **Audio HD.** E-AC3 (Dolby Digital Plus) audio is recognised and ranks a feed a little higher
  (less than 5.1 does).

## 0.29.0 — 27 October 2026

- **New football home.** The big card shows the next or live match with "⚡ Watch now" (it says
  which feed is ready and how good it is), the match's feeds listed best first, and a preview
  picture of the feed under the cursor. Games on at the same time sit as tabs on the big card.
- **Feeds looked at in the background.** While the football home is open, the feeds of live
  matches and of those starting within 45 minutes are checked one at a time; results are kept
  for 20 minutes and survive a restart. "Watch now" then plays at once, with no ranking pause.
- **⚡ on every match card.** OK watches the best feed; hold OK to pick a channel by hand. Up/down
  in the player now zaps through that match's feeds only, numbered as ranked.
- **Quality list.** OK on Quality always opens the numbered list with full names (☰ still steps).
- **Football home opens at once.** The calendar, the match channels and the highlights found are
  kept on disk: FotMob is asked again only on a new day, an hour before a kick-off (a game put
  off) and every minute while a game is on; channels are found again only when the channel list
  or the guide changes, in one pass for all matches. About 10 s → under a second on a Fire TV.

## 0.28.2 — 26 October 2026

- **Watch automatically no longer crashes on the Fire TV Stick 4K Max.** The scoreboard is now
  read with Tesseract instead of ML Kit, whose kernels a Cortex-A55 on 32-bit Fire OS rejects
  (SIGILL). Same result on every Fire, about a tenth of a second a picture.
- **OCR in its own process.** If reading a scoreboard ever crashes, the app stays up and the scan
  goes on ranking feeds on quality alone.
- **Simpler check.** Only the two team codes on the scoreboard are read: the right game is
  confirmed, another game is dropped. Clock, score and delay are no longer looked at.

## 0.28.1 — 26 October 2026

- **"▶ Watch now" picks the feed by itself.** On the football home the main button now runs Watch
  automatically, at any time: every feed of the match is looked at and the best one plays. Below,
  "Or watch on" lists all the match channels, to pick one by hand.

## 0.28.0 — 26 October 2026

- **⚽ Football home.** A new ⚽ tab in the side rail (shown when Live is on) for Serie A: the match
  being played is the big hero with crests, score and minute, and "▶ Watch now" starts it on the
  best channel; the other sources sit beside it. Rows below for other live games, today's
  finished ones, the last matchday and the next fixtures.
- **Match channels.** The two team channels come first, then the guide's channels showing the
  match; club channels, radio commentaries, boxing feeds and replays are left out.
- **Highlights in the app.** Finished matches open the official Serie A highlights straight in the
  player (H.264 only, so the Fire TV decodes them); Back returns to the football home. Older ones
  are found in the Serie A channel's video list; last season's "classic" highlights are skipped.
- **⚡ Watch automatically.** From an hour before kick-off, a second button looks at every feed of
  the match for a moment — real frame rate, resolution, audio — and reads its scoreboard on the
  device: the wrong game or a replay is dropped, the best live feed starts, and the ranking
  becomes the fallback order.

## 0.27.0 — 25 October 2026

- **The TV goes to 50 Hz as soon as OpenTV opens.** With "Match the channel's frame rate" on, the
  switch happens once at launch, while the list loads, instead of on every channel: 25/50 fps
  channels (nearly all Italian ones) now start without the second of black. Leaving the player
  keeps 50 Hz; only a 24 or 60 fps channel still switches, and back.

## 0.26.2 — 23 October 2026

- **☰ moves on to the next source again, in the Quality list.** Each press of the remote's menu key
  switches straight to the channel's next source and shows it highlighted in the player's Quality
  list; press ☰ again for the next one, or use up/down and OK to pick one. Left alone, the list
  goes away after the same time as the player bar (Settings).

## 0.26.1 — 23 October 2026

- **☰ in the player opens the Quality list.** The remote's menu key brings up the player's own
  Quality picker; press it again to close it. The sources list that popped up top-right is gone,
  also on automatic source switches (the notice at the top still says which source took over).

## 0.26.0 — 23 October 2026

- **Preferred sources.** Settings › Playback › Preferred sources lists every restream source in
  the live categories: select the ones to try first, in order. A channel starts on the first
  listed source it has, before any quality rule; the other sources follow as fallback.
- **The source picked by hand is remembered.** A feed picked with the Quality button or its list
  becomes that channel's first try from then on, even after a restart. If it does not play, the
  usual fallback takes over and it is tried first again next time. On by default
  (Settings › Playback › Remember the source picked for each channel), with a button to reset
  every pick.

## 0.25.0 — 23 October 2026

- **The live list opens straight away.** OpenTV remembers the last list it showed and opens on it,
  on launch and when coming back after Exit, then swaps in the fresh one once it is built. It no
  longer flashes "All channels" before the group you were on.
- **The screen follows the channel's frame rate.** A 25/50 fps channel switches the TV to 50 Hz
  (any mode at the same resolution whose refresh is a whole multiple), and back on leaving the
  player. On by default: Settings › Playback › Match the channel's frame rate. On Fire TV
  it needs the system's match-frame-rate setting turned on.
- Back from an external player no longer closes OpenTV.

## 0.24.0 — 22 October 2026

- **Open in another player.** A new button at the end of the player's bar hands the channel to
  another video app (Vimu, VLC, Kodi…), asking which one each time. OpenTV stops its own stream
  first so the provider's connection is free, and coming back lands on the guide, on that channel.

## 0.23.1 — 22 October 2026

- **Live channels start faster and stop freezing after the first frame.** Playback no longer sits
  just 10 s behind live, where it began on the segment the provider was still writing and waited
  for it at real-time speed. The delay is now automatic (about 30 s with 10 s segments).
  Settings › Playback › Delay behind live sets it by hand, from 5 to 60 s.

## 0.23.0 — 22 October 2026

- **A source you pick stays picked.** After changing source with ☰, the Quality button or its
  list, the player gives it the time it needs to start and rides out a single freeze instead of
  moving on by itself. Settings › Playback › Source picked by hand: stay on it, switch only if it
  keeps stuttering (default), or treat it like any other. Changing channel goes back to automatic.
- **Switch stream if it stutters.** While watching, a stream that keeps pausing for a moment —
  4 times in a minute by default, adjustable from 2 to 10 or off — moves to the channel's next
  stream, with a notice saying so.
- Every automatic switch now shows the list of sources top right, the highlight sliding onto the
  new one.

## 0.22.2 — 22 October 2026

- Favourites are set from the channel menu: hold OK on a channel and pick Add to favourites (or
  Remove from favourites). The star on the row now only shows the state.
- After closing a dialog opened from a channel (find guide, merge…), OK plays the channel again
  instead of favouriting it.

## 0.22.1 — 22 October 2026

- The full-screen live list opens on the group you last watched again; at start-up it could fall
  back to Favourites before the groups had loaded.
- The player's list of sources no longer goes blank past the ninth source.

## 0.22.0 — 21 October 2026

- **A better side menu while you watch.** LEFT shows the group's channels with what is on now —
  title, times and a progress bar — in a narrower panel. LEFT again slides the groups in, in the
  channels' place: OK on one brings up its channels without stopping what is playing, RIGHT or
  Back goes back. Playing from another group makes it the one channel up/down steps through, and
  the guide opens on it when you leave the player.
- **☰ changes source.** While watching, the menu key moves on to the channel's next source and
  shows the list of sources top right, the highlight sliding onto the new one; it fades after a
  few seconds. The channel list now opens with LEFT only.
- The dot beside the current group is gone; its lighter background marks it.

## 0.21.0 — 21 October 2026

- **Find a channel's guide by hand.** Hold OK on a channel › Find guide manually: a search over
  your guide feeds, prefilled with the channel's name, showing each match with its logo, guide id,
  feed, how many programmes it carries and what is on now. Pick one and the channel (every quality
  of it) uses that guide straight away, through every sync; Automatic puts it back on the
  automatic match. Handy for the provider's "EPG not available" placeholders.

## 0.20.0 — 21 October 2026

- **One place for logos.** Settings › Logos: channel logos from the provider only (the default),
  the provider's then outside ones, or outside ones first — outside meaning your logo pack, then
  the free TV guides'. Group logos from outside (broadcaster logos, flags) is a switch of its
  own; off, each group shows the provider's emoji, enlarged. A logo picked by hand for a channel
  still wins. Your old choices carry over; the phone page has the same card.
- **Settings in sections.** Sources, Channels, Watching and System. Display & playback is split
  into Appearance and Playback, the TMDB key has its own page, and the live/movies/series
  switches moved to Providers.
- Italian now says "fornitore" throughout; the sync hint names the real menu entry.

## 0.19.0 — 21 October 2026

- **Everything follows your language.** Messages that were always in English — sync status,
  playback and provider errors, the update dialog, TV-to-TV sharing — now come in the app's
  language, and Italian covers every screen.
- **The phone setup page speaks the phone's language.** The page behind the QR code on first start
  used to be English only; now it follows the language the phone is set to.

## 0.18.3 — 21 October 2026

- **Change a channel's logo.** Hold OK on a channel › Change logo: a grid with every logo it could
  wear — each merged source's, the guide's, the logo pack's and those of similarly named channels
  (the red Sky Calcio for the blue HEVC feed). The pick beats everything else and survives list
  refreshes and backups; Automatic goes back to the default.

## 0.18.2 — 21 October 2026

- **Logo packs.** Point the app at an `index.json` of your own logos (team crests, say) and they
  replace the provider's and the guide's on the channels they name, team feeds such as
  `DAZN SERIE A 3 MILAN` included. Settings › TV guide › Logo pack, or the "Logo pack" card on
  the phone page. The app ships no logos; the pack is re-read at every launch.
- **SVG logos** now load.

## 0.18.1 — 20 October 2026

- **Drag to reorder on the phone page.** Hold the ≡ handle and drag a group or a channel where you
  want it; the page scrolls by itself near the screen edges.
- **The phone page speaks Italian** on a phone set to Italian.

## 0.18.0 — 20 October 2026

- **The phone page shows your live list as the TV does.** Groups come folded and in the TV's
  order, and each channel is one row with its qualities; hide, favourite and rename act on every
  copy.
- **Reorder groups and channels from the phone.** Arrows, or ⇅ then tap the spot. The order
  survives refreshes and goes in the settings backup. Favourites can be reordered too.
- **Merge groups from the phone**, and separate them again. Switch a whole group off, and turn
  switched-off groups back on.
- **Merge channels from the phone**: search any channel to merge with, plus a "Duplicate
  channels" list with Merge/Ignore and the merges already made.

## 0.17.3 — 20 October 2026

- **Live opens on the groups.** The full-screen live list starts with focus on the groups, so the
  list opens wide; OK or → goes into the channels.
- **Recent groups can be switched off.** New switch in Settings › Grouping, "Recent groups" (on by
  default).
- **Holding OK on Quality opens the list properly.** The held press no longer falls into the list
  and picks the first stream; choose with a fresh OK.
- **Player bar timeout.** Settings › Display & playback, "Player bar hides after": 1 to 10 seconds
  (5 by default).

## 0.17.2 — 20 October 2026

- **Plainer source examples.** The grouping settings explain source folding in general terms
  instead of quoting names from one list, and the source tags the app recognises now live in one
  place (`SourceTags.kt`).

## 0.17.1 — 20 October 2026

- **No "last watched" tag** on channel rows in the live list.
- **FORK.md** explains what this fork does differently from the original OpenTV.

## 0.17.0 — 20 October 2026

- **Groups and channels side by side.** The full-screen live list now shows the groups down the
  left and the current group's channels beside them. Moving through the groups swaps the
  channels after a short pause; → or OK goes into the channels, and the groups fold to a strip
  of logos so the channels get the room. ← or Back opens the groups again.
- **Channel counts and Recent groups.** Each group shows how many channels it holds, and the
  groups you last watched from sit at the top under Favourites.
- **Up next on the same line.** Each channel reads "11:00 – 12:30 · now on   Up next: 12:30 –
  14:30 · next", with the progress bar across the whole row. The channel last watched is marked.
- **Back no longer quits from the groups.** Back goes channels → groups → navigation menu; only
  Back from the menu asks whether to leave.

## 0.16.9 — 20 October 2026

- **Choose what OK on Quality does.** New switch in Settings › Display & playback, "OK on
  Quality switches stream" (on by default): on, OK steps to the next feed and holding OK opens
  the list; off, OK opens the list straight away and you pick the server.

## 0.16.8 — 20 October 2026

- **One-press feed switching.** In the player, OK on the Quality button now steps to the
  channel's next feed (wrapping round), and a notice says which one is on. Hold OK to open the
  full list as before. The button shows the quality playing (`4K`, `FHD 1`, `FHD 2`, `HD`…):
  feeds of the same quality are numbered, and the list and notices name them the same way
  (`FHD 2 · B`).
- **Right opens the playback bar.** In full screen, → now brings up the playback bar instead of
  the quality list.

## 0.16.7 — 19 October 2026

- **Right guide logo when guides disagree.** If two TV guides give a channel different logos,
  the one whose image address names the channel wins — iptv-epg.org shows Sky Sport Max with
  the Sky Sport Football logo; epgshare's correct one is now used.

## 0.16.6 — 19 October 2026

- **Logos beside channel groups.** The category list (☰) shows a badge next to each group:
  the broadcaster's logo when the name starts with one (Sky Sport, Sky Calcio, Sky Cinema,
  Sky Primafila, DAZN, Discovery, Disney+, Eleven Sports, F1 — loaded from tv-logos, nothing
  bundled), the country flag for a flag emoji, otherwise the provider's own leading emoji, shown
  big and dropped from the name. Emoji the device's font can't draw leave the slot empty.
  Turn it off in **Settings › Channel grouping › Logos beside groups**.

## 0.16.5 — 19 October 2026

- **Channel logos from the guide.** Channels your provider ships without a logo now take the one
  from their matched TV guide (the `<icon>` in XMLTV), instead of showing just their initials.
  The provider's logo wins unless you turn on **Settings › TV guide › Logos from the guide**,
  which shows the guide's logos instead. Logos appear after the next guide refresh.
  `+1`/`+2`/`+3` copies show their base channel's logo.
- **Bigger logos, no channel numbers.** The channel list and the player's channel list drop the
  provider's channel numbers (they jump about and mean nothing) and give the room to the logos.
  Typing a number on the remote still jumps to that channel.

## 0.16.4 — 19 October 2026

- **New player bar layout.** The channel logo sits large on the left and the rest of the bar runs
  to the right edge of the screen: channel name, clock, the programme with its progress bar and
  the minutes left, a wider synopsis and what's on next.
- **Stream specs, Kodi-style.** Next to the channel name, small tags show what's actually on
  screen: resolution, video codec, frame rate, HDR and the audio format (e.g. `1080p` `HEVC`
  `50fps` `AC3 5.1`).
- **Info button** in the bar: opens a technical panel (top right) with video and audio details,
  hardware or software decoder, frames shown and dropped, download speed, buffer and delay
  behind live, updated every second. It stays up while you zap until you turn it off.

## 0.16.3 — 19 October 2026

- **Back up and restore your settings.** On the phone page (Settings › Manage on phone or laptop),
  *Backup & restore settings* downloads one file with your settings, guide feeds and channel
  merges, and imports it back on this or another TV. On import you choose what to bring in:
  channel merges (added to the ones already on the TV) and/or settings (they replace the TV's).
  Your provider login, NAS password, TMDB key and Stremio add-ons are never put in the file.
- Fixed: large or non-English request bodies from the phone page could arrive cut short.

## 0.16.2 — 19 October 2026

- **Wait-time sliders you can actually use with the remote.** "Wait before switching" and
  "Frozen for" are now a bar: move onto it and press ←/→ to change the seconds. The old −/+
  buttons lost focus when one of them hit its limit, so changing the value was hit-and-miss.
- **Same range for both waits:** 1 to 10 s in half-second steps (the freeze wait used to be
  1–15 s in whole seconds; values above 10 s become 10 s).

## 0.16.1 — 19 October 2026

- **Switch stream if it freezes.** While you watch, a channel whose picture freezes or keeps
  loading moves on its own to the channel's next stream ("HEVC · A froze → HEVC · B").
  Streams take turns, so one that only hiccuped comes back later; if every other one fails to
  start, it goes back to the one that was playing. A channel with a single stream restarts it.
  Settings › Display & playback › Playback: on by default, wait 1–15 s (default 3). Pause,
  rewind and fast-forward never count as a freeze.

## 0.16.0 — 19 October 2026

- **Italian TV guides built in.** Settings › TV guide lists *Italia — iptv-epg.org* (~300
  channels, 10 days, descriptions) and *Italia — epgshare01* (~200 channels, 4 days). On a
  device set to Italian they switch on by themselves the first time; after that your toggle rules.
- **Guide matching picks the guide that has programmes.** A channel is matched only to guide
  entries that are actually filled, and the same channel published by two guides is no longer
  "ambiguous": the fuller one wins. Adding a second guide no longer takes channels away.
- **No double schedules.** When two guides use the same channel id, one guide is shown per
  channel instead of both overlapping.
- **+1 / +2 / +3 channels get a guide and a logo.** With no guide of their own (or the provider's
  pointing at the original channel), they show the original's schedule moved 1, 2 or 3 hours
  later, and borrow its logo when they have none.
- **Initials instead of an empty logo.** Channels with no logo, or one that fails to load, show
  their initials on a tile.
- **Switching a guide off removes it.** Its programmes leave the guide straight away; switching it
  back on downloads it again.
- **"Add your guide" works with a remote.** The form takes focus when it opens, up/down move
  between the fields and buttons, and Cancel clears it.

## 0.15.9 — 18 October 2026

- **Group names without the source.** A group that comes from a single restream source drops the
  tag from its name: `Bambini (A)` reads "Bambini", `Basket LNP | Serie A2 (A)` reads
  "Basket LNP | Serie A2". Quality and timing (`4K`, `RAW`, `LIVE`) stay. Turn it off in
  Settings › Channel grouping › Hide source in group names.

## 0.15.8 — 18 October 2026

- **Focus stays on the merged row.** After Merge with…, focus lands on the merged channel instead
  of the row below it, even when regrouping moves it in the list.

## 0.15.7 — 18 October 2026

- **Merge channels by hand.** Hold OK on a channel in the full-screen live list: the channel menu
  (Watch, Record…) opens there too, with **Merge with…**. Pick the other channel — switching
  category with ☰ if needed — and press OK: the two become one row, named after the one you
  picked. BACK cancels; undo it in Settings › Channel grouping › Merges done.
- **No more built-in typo list.** Provider misspellings (`Nicklodeon`, `HGVTV`…) are no longer
  fixed silently: they show up in Possible duplicates for you to merge. New suggestion: a
  timeshift that lost its plus (`Sky Cinema Uno 24` next to `Sky Cinema Uno +24`).
- **Merges carry timeshifts.** Merging a channel takes its `+1`/`+2` copies along.
- **Merged rows keep the right name.** The row is named after the channel merged into.

## 0.15.6 — 18 October 2026

- **Settings › Channel grouping.** A new screen for how channels fold together:
  - **Possible duplicates**: rows that look like one channel under two names (same provider guide
    id, the same words in another order, or a one-letter typo) — **Merge** puts them in one row
    with every feed in the Quality panel, **Ignore** stops suggesting the pair.
  - **Merges done**: every merge listed, each with **Undo**.
  - Choices are applied on the spot and kept through every sync.
  - "Merge groups by source" moved here from Categories.

## 0.15.5 — 18 October 2026

- **DAZN team channels in one row.** `DAZN Serie A Milan`, `DAZN SERIE A 3 MILAN` and `Milan`
  are now one channel with all feeds in the Quality picker; same for every Serie A/B team (also
  the `WEB` feeds) whenever the list has the team's plain channel. No team list is built in.
- **DAZN prefix folds.** `DAZN UFC 1`, `DAZN Amichevoli 01`, `IT DAZN| Eurosport 1`, Inter TV,
  Milan TV, Red Bull TV… join the same channel without the prefix, when the list has it.
- **More quality copies found.** `SERIE-A1-FHD/HD/SD` (dashes) and `[V] BOX 01` fold into one row;
  `VH- 1` joins VH1.
- **Heading rows** (`---- Basket Serie A2 ----`) no longer pull channels into their name.

## 0.15.4 — 18 October 2026

- **Rai 4K keeps its name.** `Rai 4K RAW` was listed as plain "Rai"; brand-only UHD channels
  (Rai 4K, Sky Sport 4K) now keep the 4K in the name and stay a channel of their own.

## 0.15.3 — 18 October 2026

- **Fewer duplicate channels.** Restream source tags written into channel names (`[A]`, `B`,
  `QUALITY`, `(Q)`, `Duplicate`…) are taken out of the name: the copies fold into one row and the
  source shows in the Quality picker (`FHD · B`). Tags are also learned from the list's own
  category names, so new sources fold with no update.
- **Timeshift channels kept apart.** `Discovery +2` no longer merges with `Discovery 2`, and the
  name fixes (Sky prefix, typos) now apply to +1/+2/+3 channels too.
- **Accents and typos.** `Südtirol` = `SudTirol`; Suspence/Suspance, Trasformers, Nick Junior,
  HGVTV fold into the right channel; the MotoGP satellite feed joins Sky Sport MotoGP.
- **Heading rows hidden.** `--Sky Sport (A)--`, `== … ==`, `◈ DAZN B ◈` are no longer
  listed as channels.
- **"Plus" stays in the name** (Disney Plus, Boing Plus) instead of reading as a quality.

## 0.15.2 — 18 October 2026

- **Player pickers keep focus.** With Quality, Audio, Subtitles or Aspect open, left/right no longer
  jump out of the list onto the bar buttons.

## 0.15.1 — 18 October 2026

- **Icon-only player bar.** The buttons are just icons now; the focused one shows its name above
  it. New order: rewind · pause · forward, then quality, audio, subtitles, aspect, and record set
  apart on the right.
- **One Back hides the bar.** The first press used to only clear the button's focus; now each Back
  closes one layer (channel list, picker, bar) and leaves the player only when nothing is on screen.

## 0.15.0 — 18 October 2026

- **OwnTV-style focus.** Plain rows (settings, options, the language list) show a teal ring and a
  soft teal fill instead of the grey highlight; channels, categories, the menu, posters, player
  buttons and keyboard keys grow slightly when focused; buttons, switches and radios get a teal
  focus glow. The Classic style keeps the old look.
- **Fixed a crash on a key press in the live list** when it opened on a channel scrolled out of
  view: focus now scrolls back to that row instead of pointing at a row that isn't there.

## 0.14.9 — 18 October 2026

- **OwnTV style.** The app now uses OwnTV's look: a teal accent over a near-black with a green
  undertone, in dark and light, and the Plus Jakarta Sans typeface. Prefer the original indigo?
  Settings › Display & playback › Appearance › Style › Classic.

## 0.14.8 — 18 October 2026

- **Language is one row in Settings.** Display & playback shows the current language on a single
  line; OK opens the list in a dialog, starting on the language in use, so the rest of the screen
  is no longer pushed below thirty-odd options.

## 0.14.7 — 18 October 2026

- **The same channel under two names is one row.** `Sky MTV` and `MTV`, `Discovery Giallo` and
  `Giallo`, `Sky Nickelodeon` and `Nickelodeon` now fold together (only when both exist in your
  list; `Sky Cinema …` channels are left apart). Common provider typos are folded too
  (`Nicklodeon`, `Italian Fisching`, `EQUTV`, `Sly Documentari`, `History Channel`, `Sky Series`…),
  and a trailing `RS` is shown as a source label instead of making a second channel. Existing
  lists are re-folded on the next launch.
- **"Channel not available" instead of an endless spinner.** When a channel has only one feed,
  or every feed has failed, the player says so as soon as the fallback wait runs out. OK tries
  again, up/down changes channel as usual.

## 0.14.6 — 18 October 2026

- **Channel up/down after a quality fallback** now moves to the next channel instead of jumping
  back to the first one of the list (same for the in-player list and "Last channel").
- **The quality that worked is remembered.** Zapping back to a channel whose preferred feed is
  down starts straight on the one that played (or the one you picked by hand). Forgotten when the
  app is reopened, so a better feed that comes back gets tried again.
- **Interface size: the old 80% is the new 100%.** Your setting is converted automatically.
- **Settings use the full screen width** at any interface size.

## 0.14.5 — 18 October 2026

- **Interface size.** Settings › Display & playback › Appearance › "Interface size" zooms the whole
  app from 60% to 130% in 5% steps: lists, guide, menus, settings and the player bar (the video
  always fills the screen). Shrink it to fit more channels on screen; changes apply instantly.

## 0.14.4 — 18 October 2026

- **Updates in place, from the app.** The fork now has its own package (`app.opentv.sonny`, next to
  the official OpenTV) and a fixed signing key: new versions install over the old one and keep your
  provider and settings. The app checks this fork's GitHub releases and offers the update itself.
- **Better quality order.** HEVC and RAW feeds with no resolution in the name are 1080p (measured),
  so they now rank as FHD instead of below SD; within a resolution HEVC comes first, then RAW.
  Order: 4K › HEVC › RAW › FHD › HD › SD.
- **"Full HD" written in two words** is FHD: no more separate "Sky Atlantic Full" rows.
- **One spelling per channel:** "Sky Uno", not "SKY UNO", whichever variant plays.
- **Back opens the categories** from the full-screen channel list (like ☰); from the categories,
  Back asks to leave.
- **Quality fallback wait** from 0.5 to 10 s in half-second steps.

## 0.14.3 — 18 October 2026

- **Categories folded by source.** Groups split by restream source — "Sky Sport (A)",
  "(B)", "DAZN C/D/…" — show as one "Sky Sport", "DAZN"… in the
  category list (141 → ~89 on a big Italian list). Inside, each channel appears once.
- **Sources in the Quality panel.** The same quality from different sources is offered separately
  ("FHD · A", "FHD · B"), and the automatic quality fallback tries the other sources too.
- **Switch:** Settings › Categories › Live › "Merge groups by source" (on by default) brings the
  separate groups back.

## 0.14.2 — 17 October 2026

- **No more dead ends on empty groups.** The ☰ category list only shows groups that have channels
  right now (event-only feeds such as NBA/NHL Pass are empty between events). If a group still turns
  out empty, the screen says so and returns to the categories.
- **The remote always responds** on the full-screen list, even while it's loading or empty — ☰ and
  LEFT work from anywhere.

## 0.14.1 — 17 October 2026

- **Readable player bar on bright pictures.** The dark backdrop now covers every line of the bar
  (title, times, synopsis), not just the bottom; the button row scrolls instead of wrapping labels.
- **Navigation menu fix (full-screen list).** Moving down the menu opened with LEFT no longer drops
  focus back into the channel list, so Films, Series and Settings are reachable again.

## 0.14.0 — 17 October 2026

- **Full-screen channel list** (new default layout; Grid and Simple list stay in Settings). The
  current group's channels fill the screen: number, logo, name, the programme on now with times and
  progress, and what's next on the right. No preview pane or menus — LEFT on a row brings the menu back.
- **☰ for categories.** The remote's menu key swaps the list for the categories in the same style,
  with the provider's own names (flags and emoji included); OK opens that group.
- **Reopens where you left off:** the last group and the last channel watched.
- **☰ in the player** opens and closes the channel list of the current group.

## 0.13.0 — 17 October 2026

- **Choose which quality plays first.** Settings → Display & playback → Playback → Default quality
  (Best / FHD / HD / SD / RAW / HEVC, FHD by default). If a channel lacks it, the next lower one is
  used; the guide row and preview follow the choice too.
- **Automatic quality fallback.** If a variant errors or shows no picture within a set wait (3–10 s,
  4 s by default), the player moves on to the next quality down — then RAW/HEVC, then higher — with
  a short "X not available → Y" notice. Can be switched off.

## 0.12.0 — 16 October 2026

- **Big VOD catalogues no longer crash the app.** The Xtream film/series lists are streamed straight
  into the database in batches instead of being parsed whole in memory, home rows are built with
  bounded SQL queries, and titles the provider dropped are pruned. Tested with ~70k films and ~7.5k
  series on a Fire TV Stick (heap stays around 150–200 MB).
- **Gradual background catalogue sync.** The VOD catalogue refreshes 30 s after start when stale
  (12 h), one sync at a time, with a bandwidth cap so live TV keeps playing; Films/Series show a live
  "updating catalogue" counter.
- **Switch whole categories off.** Settings → Categories (Live / Films / Series tabs, on-screen
  filter, bulk on/off). A switched-off category is skipped at sync and removed from the library.
- **"Italian only" and "International sport" switches** for live categories, rule-based so they
  also apply to categories the provider adds later; a category switched back on by hand wins.
- **What's on now in the player.** The channel banner shows the current programme (times, title,
  progress, short synopsis) and what's on next, on open and on every channel change.

## 0.11.8

- **Edit a saved provider.** Settings → Providers now has an **Edit** button on each provider, so you
  can fix a server address, password, MAC or name in place instead of removing and re-adding it —
  your favourites, hidden channels and per-provider settings are kept.
- **Stalker portals: fewer dropped sessions.** A portal that occasionally rejected a call
  mid-session (channels loading only "sometimes") now gets one automatic re-handshake and retry, so
  an intermittent refusal becomes a silent re-auth rather than an empty channel list or a channel
  that won't tune.
- **Player controls stay clear of the system bar off-TV.** On phones and tablets the bottom control
  bar (subtitles, audio, quality, aspect ratio) could be drawn under the navigation bar and cut off;
  it's now inset above it. No change on Android TV.

## 0.11.7

- **Stalker portals: closer to a real box.** Building on v0.11.6, OpenTV now sends the rest of the
  identity a real MAG set-top box presents at login — the auth token in the session cookie (not just
  the header), a hardware-version hash, a timestamp and the box's API signature — and it probes a
  couple more portal paths. Some Ministra/Stalker panels only return the channel list when they see
  all of this, so lines that connected but showed no channels have a better chance of loading. If a
  portal still comes up empty, please open an issue and say which panel software it runs.

## 0.11.6

- **Stalker portals: full set-top-box identity.** OpenTV now authenticates to Stalker/Ministra
  portals the way a real MAG box does — sending the device serial, `device_id`, a signature and a
  metrics blob derived from your MAC, instead of the MAC on its own. Many portals won't hand over
  the channel list or stream links to a box that only presents a MAC, which is why some lines that
  worked in other apps came up empty in OpenTV. This lets those portals authorise OpenTV. If a
  portal still fails, please open an issue and say which one — it helps a lot.

## 0.11.5

- **Add a provider from your phone or laptop.** The "Manage on phone or laptop" web page now has an
  **Add a provider** section — pick Xtream, M3U or Stalker portal, fill it in with a real keyboard,
  then Test and Add. Channels load straight onto the TV, so you never have to type a server address
  or a MAC on the remote again.
- **Fixed the "camera won't scan it?" web address.** The manage screen was printing that link without
  its access token, so typing it into a browser hit a dead "Not found". It now shows the full working
  URL, and the token is short enough to type.


## 0.11.4

The big DVR release. Recordings behave like Sky Q, you can record straight to a NAS, watch a
recording while it's still taping, and the whole interface now speaks 30 languages.

### Recording & DVR

- **Watch a recording while it's still recording.** Start playing something the moment it begins
  taping — OpenTV reads the growing file straight off disk, so it costs **zero extra connections**
  to your provider. On a single-stream account that's the difference between "wait until it's
  finished" and "watch now". Fast-forward and rewind work within whatever's been taped so far.
- **Record straight to a NAS.** Point recordings at a network share over SMB (Settings →
  Recordings → NAS, or set it up from the phone/laptop web manager) so a cheap box isn't boxed in
  by its own storage — and every OpenTV in the house can reach the same recordings.
- **Single-connection auto-switch.** On a one-stream provider, if a recording is due to start on
  another channel, OpenTV moves the screen onto that recording as it begins — with a **30-second
  warning** first and a *Keep watching* button if you'd rather not. No more "why did my live stream
  just cut out?" (Recordings → Recording behaviour → *Auto-switch when recording starts*.)
- **Clash handling with multi-provider fallback.** Book two overlapping recordings and, if more
  than one of your providers carries that channel, OpenTV records each from a **different provider**
  so neither is cut. On a single provider it flags the clash instead.
- **Recording padding.** Start each recording a minute early and run a few minutes past the listed
  end, so a late kick-off or an overrun isn't clipped. Adjustable per side.
- **Series links show the next episode.** A series link now tells you exactly when it next records.
- **Storage readout.** See how much space recordings use and how much is left.

### Live TV

- **Pause & rewind live TV** *(experimental, opt-in).* Holds the last couple of minutes so you can
  pause and jump back. Off by default — it uses more memory.

### Languages

- **30 languages.** The whole interface is translated into Spanish, French, German, Italian,
  Portuguese, Dutch, Polish, Russian, Turkish, Arabic, Simplified Chinese, Japanese, Korean, Hindi,
  Swedish, Danish, Finnish, Norwegian, Czech, Greek, Romanian, Hungarian, Ukrainian, Indonesian,
  Thai, Vietnamese, Bulgarian, Slovak, Croatian and Persian. Settings → Language (each listed in
  its own name), or leave it on *System* to follow your device.

### Fixes & polish

- Smoother watch-while-recording, especially from a NAS: playback now keeps a buffer behind the
  live edge so a network share doesn't stutter at the write head.
- Back never lands you on a stray live channel or drops you out by accident — coming back from a
  recording returns to your recordings, and Back on the home screen asks before it exits.
- Password fields are masked, with a show/hide toggle.
- Recording stability: NAS write timeouts and reconnect handling, so a quiet network doesn't
  silently freeze a capture.

### Notes

- Everything stays on your device — provider logins never leave the box.
- Free and open source (GPL-3.0): https://github.com/opentvproject/opentv


## 0.10.0

- **Movies & Shows, redesigned.** A Plex-style layout with big artwork, cast & director, "more with
  this cast" rows, and cleaned-up titles (no more "NF -" / "(KR)" junk). Optional: add your own free
  TMDB key in Settings → Metadata and OpenTV fills in any posters, backdrops, cast or synopses your
  provider left blank — the key stays on your device.
- **No more stuck "Loading movies & shows".** The movies/series catalogue is cached, so it loads
  instantly on later launches instead of re-downloading every time — and the live preview no longer
  stutters while it loads.
- **Record to a USB / external drive.** Settings → Recordings → USB, pick a folder on a plugged-in
  drive, and recordings write straight there (no storage permission needed) and play back in-app.
- **Recordings screen overhaul.** Scheduled recordings show *when* they'll record and sit in their
  own "Scheduled" section; failed ones have a **Retry**; each is tagged to a profile; and bookings
  re-arm on every launch so a force-stop or app update can't quietly drop them.

## 0.9.0

- **Manage your channels from a phone or laptop.** A new "Manage on phone or laptop" screen in
  Settings shows a QR code and a link — open it on any device on your wifi and you get a proper web
  page to browse, **rename**, hide, favourite and **reorder** channels with a real keyboard and
  mouse. Changes apply to the TV instantly. Local-only: the TV is the server, nothing touches a
  cloud.
- **Recording keeps going when you leave the app or the box sleeps.** OpenTV can now ask Android to
  exempt it from battery optimisation (Settings → Recording → "Recording in the background"), which
  is what keeps recordings running in standby and when you switch away — swiping OpenTV out of
  recents no longer stops a recording either.
- **Rename channels.** Give a channel your own name; it sticks and survives a guide refresh (from
  the web manager for now).
- **Tidier update notes.** The "update available" prompt shows a clean summary of what's new instead
  of build metadata.

## 0.8.0

- **No more endless "Loading channels".** If a provider fails to load or comes back empty (a wrong
  login, a dead server), the guide now shows a clear error with **Retry** and **Provider setup**
  buttons instead of spinning forever.
- **Set up on a phone without the QR dance.** Installing on a phone now shows the login form
  directly so you can type your details in; the QR code is only offered on a TV, where typing is the
  painful part.
- **Paste into the setup fields.** The URL, username and password fields — and the on-screen
  keyboard — now have a **Paste** button, so a copied Xtream line drops straight in.
- **MPEG-TS or HLS, your choice.** Each Xtream provider now has a **Stream format** toggle. If your
  panel only serves `.ts` and channels wouldn't play, switch to MPEG-TS and they will. Defaults to
  HLS, so nothing changes unless you need it.
- **Parental controls scrolls.** The hidden-categories list is reachable to the bottom now, however
  many categories your provider has.
- **A real Channel Manager.** Settings → Channels is now a browsable, two-pane manager: pick a
  category on the left, see its channels on the right, hide or favourite each one — including
  hidden channels so you can bring them back. With more than one provider, a **source filter** keeps
  them separate instead of merged into one list.

## 0.3.0

- **Profiles.** Add local profiles (just a name — no accounts) from the person icon in the top
  menu, and switch between them. Each profile keeps its own resume points and watched state, so
  your half-watched film doesn't show as watched on someone else's profile. Existing resume points
  become the default "Me" profile's — nothing is lost.
- **Continue Watching.** Movies and Shows now open with a "Continue watching" shelf for the active
  profile — pick up your last film or episode where you left off, with a progress bar on each.
- **Local sync (no servers).** Settings → Sync copies your continue-watching between two OpenTV
  devices over your own wifi: one device shares behind a six-digit code, the other receives. It's
  local-only — nothing leaves the house — the household answer to the cloud sync that died with
  Viewella.

- **Proper player controls.** A single control bar slides up from the bottom with play/pause,
  rewind/forward (where the stream allows it), and pickers for **subtitles, audio track, quality
  and aspect ratio**. It hides after a few seconds and any remote button brings it back —
  nothing is ever left painted permanently over the picture.
- **Captions that actually show.** Subtitles are now chosen from the real tracks in the stream
  (and can be turned off), instead of a blind on/off that often left the renderer enabled but
  nothing on screen. Audio-track switching works the same way for multi-language streams.
- **Page through the guide.** Earlier / Now / Later buttons above the guide move the timeline
  forward and back, so you can see what's on later this evening with a remote.
- **Live preview in the guide.** The highlighted channel now plays, muted, inside the preview
  pane. It uses a single player that stops the instant you go full-screen or leave the guide —
  no second decoder — and it can be turned off in Display & playback for older boxes.
- **Channel manager.** Settings → Channels: search for a channel and hide it from the guide (or
  favourite it). Hidden channels drop out of Live TV and search but stay here, greyed, so you can
  bring them back. Hiding covers every quality variant of a channel at once.
- **Parental controls.** Set a 4-digit PIN and mark categories as hidden — they drop out of the
  guide, All channels and search until you unlock them (and with a PIN set, the parental screen
  itself is locked). In Settings → Parental controls.
- **Unified search with an on-screen keyboard.** Search now has its own screen with a d-pad
  keyboard (no more "type on your phone") and covers **channels, movies and shows** together, with
  live results as you type. Reachable from the top menu.
- **Loading indicators for Movies and Shows.** Both now show a spinner while the catalogue is
  syncing instead of a premature "nothing here".
- **Settings and Search in the top menu.** Both now live top-right, next to Live TV / Movies /
  Shows, instead of being buried behind the guide's icons.
- **A single Settings screen.** One Settings hub — Providers (add / remove / re-test sources), TV
  guide, Display & playback, Parental controls, and About (version, a manual update check,
  licence and links).
- **Dark / light / follow-system.** A new appearance setting. TV still defaults to dark; pick
  Dark or Light to force it on any device.
- **Quality picker only when there's a choice.** No more "Quality: Standard" on single-quality
  channels; the picker appears only when a channel actually has multiple qualities.
- New **Display & playback** settings screen (the sliders icon in the guide).

## 0.2.0

- **Guide detail pane** — highlighting a channel now shows its logo, what's on now with
  start/end times and a live progress bar, a synopsis when the guide carries one, and what's
  on next. Press to watch full-screen.
- **Live progress fill** on the current programme in the guide grid, and a focus highlight on
  the highlighted channel.
- **Fix: never asks for your provider again.** A cold launch could briefly decide "first run"
  before your saved sources had loaded and drop you on the setup screen — it now waits for the
  sources to load before deciding what to show.
- **Fix: no second video decoder on the guide**, which was locking up low-end boxes (a
  Chromecast could freeze). The detail pane is logo + guide info; the one decoder lives
  full-screen.

## 0.1.0

First public release.

- Xtream Codes and M3U/M3U8 sources
- Streaming XMLTV guide parsing with incremental, non-destructive sync
- Live TV playback on Media3/ExoPlayer with IPTV-tuned retry behaviour
- Movies and series catalogue
- Favourites, categories, search
- In-app self-update for sideloaded installs
- Single APK for Android TV, Fire TV, phones and tablets
