# Changelog

## 0.16.5 — 19 October 2026

- **Channel logos from the guide.** Channels your provider ships without a logo now take the one
  from their matched TV guide (the `<icon>` in XMLTV), instead of showing just their initials.
  A logo from the provider always wins. Logos appear after the next guide refresh.

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
  loading moves on its own to the channel's next stream ("HEVC · SAT froze → HEVC · Stel-dvs").
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
  tag from its name: `Bambini (Hermes)` reads "Bambini", `Basket LNP | Serie A2 (Hermes)` reads
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

- **Fewer duplicate channels.** Restream sources written into channel names (`[STEL]`, `[KALI]`,
  `Kaly`, `SAT`, `STELLINA`, `HERMES`, `QUALITY`, `(Q)`, `Duplicate`…) are taken out of the name:
  the copies fold into one row and the source shows in the Quality picker (`FHD · SAT`). Tags are
  also learned from the provider's own category names, so new sources fold with no update.
- **Timeshift channels kept apart.** `Discovery +2` no longer merges with `Discovery 2`, and the
  name fixes (Sky prefix, typos) now apply to +1/+2/+3 channels too.
- **Accents and typos.** `Südtirol` = `SudTirol`; Suspence/Suspance, Trasformers, Nick Junior,
  HGVTV fold into the right channel; the MotoGP satellite feed joins Sky Sport MotoGP.
- **Heading rows hidden.** `--Sky Sport (Hermes)--`, `== … ==`, `◈ DAZN KALI ◈` are no longer
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

- **Categories folded by source.** Groups the provider splits by restream source — "Sky Sport
  (Hermes)", "(SAT)", "(DVS)", "DAZN KALI/STELLINA/…" — show as one "Sky Sport", "DAZN"… in the
  category list (141 → ~89 on a big Italian list). Inside, each channel appears once.
- **Sources in the Quality panel.** The same quality from different sources is offered separately
  ("FHD · Hermes", "FHD · SAT"), and the automatic quality fallback tries the other sources too.
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
