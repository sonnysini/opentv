# What this fork changes

This is **sonnysini/opentv**, a fork of [opentvproject/opentv](https://github.com/opentvproject/opentv)
taken at **v0.11.8**. It was built for an Italian household with one large Xtream list watched on
Fire TV sticks. Everything listed here is new in the fork; for the version-by-version detail see
[CHANGELOG.md](CHANGELOG.md) from 0.12.0 on.

The fork stays under the GPL v3 like the original.

## Install and updates

- **Its own package**, `app.opentv.sonny`, so it installs alongside the original (`app.opentv`).
- **Signed with a fixed key**, so each new version installs over the last and keeps your data.
- **Updates from inside the app**: it checks this fork's GitHub Releases every 6 hours, or on
  demand under Settings › About › Check for updates.
- **Back up and restore settings** from the phone page (Settings › Manage on phone or laptop).

## Large lists

- **Big film and series catalogues no longer crash the app.** The original read whole Xtream VOD
  lists into memory and ran out of it on a Fire stick. The fork streams them into the database
  instead, a bit at a time, and pages the grids.
- **The catalogue syncs gradually in the background**, with a progress count in Films and Series.
- **Switch whole categories off** (Live, Films, Series). Switched-off categories are skipped when
  syncing and removed from the database.
- **"Italian only" and "International sport"** switches for live categories. They work from rules,
  so new categories from the provider are handled too.

## Live TV screen

- **Full-screen live layout** (the default): groups on the left, the group's channels beside
  them. While you browse the channels the groups shrink to a strip of logos.
- Each channel row shows a large logo, the programme on now, "Up next" on the same line, and a
  progress bar across the row. No channel numbers, because the provider's numbering jumps about.
- **Group logos** (Sky, DAZN, Discovery, flags…) with a channel count on each group, and the
  groups you watched from recently at the top.
- **Back steps up one level**: channels → groups → navigation menu → leave the app.
- **Reopens on the last group and the last channel you watched.**
- **Empty groups** (event-only feeds between events) are hidden, so you never open one onto
  nothing.

## Channels folded together

- **One row per channel**: FHD/HD/SD/RAW/HEVC copies and the same channel from different restream
  sources (Hermes, SAT, Stellina…) are folded into a single row. You choose the copy in the
  player.
- **The same channel under two names is one row** (`Sky MTV` / `MTV`, `Discovery Giallo` /
  `Giallo`…), but `+1/+2/+3` timeshift channels stay separate.
- **Groups the provider splits by source are folded too** ("Sky Sport (Hermes)" and "Sky Sport
  (SAT)" become one "Sky Sport"). This is a switch in Settings.
- **Merge channels by hand**: hold OK on a channel and choose "Merge with…".
- Tidier names: one spelling per channel, heading rows hidden, and the provider's typos and
  stray tags handled.

## Player

- **Default quality and automatic fallback.** You pick which quality plays first. If that copy
  fails or shows no picture within a wait you set, the next one plays instead. A channel that
  freezes while you watch also switches copy, and the copy that worked is remembered for the
  session.
- **Quality button**: OK steps through the channel's copies (`FHD 1`, `FHD 2 · SAT`…) and holding
  OK opens the full list. A setting can make OK open the list straight away instead.
- **"Channel not available"** instead of an endless spinner when nothing plays.
- **Redesigned bar**: large logo, clock, what's on now and next, time left, an icon-only button
  row, and a backdrop that stays readable on bright pictures.
- **Stream details, Kodi-style**: tags for resolution, codec, frame rate and audio, plus an Info
  panel with decoder, dropped frames, download rate and buffer.
- **☰ in the player** opens the channel list of the current group.

## TV guide and logos

- **Italian guides built in** (iptv-epg.org, epgshare), plus your own guides.
- **Smarter guide matching**: a channel is matched only to a guide that actually has programmes,
  with no double schedules. `+1/+2/+3` channels get their base channel's guide and logo.
- **Channel logos from the guide** for channels whose provider has none. Optionally they can
  replace the provider's logos.
- **Initials** instead of an empty logo.

## Look and settings

- **OwnTV style**: a teal theme with the Plus Jakarta Sans font and a ring-and-lift focus.
  The original look is still available as "Classic".
- **Interface size** setting that zooms the whole app, plus full-width settings screens and a
  language picker.

## Not changed

Recording, Stalker/M3U support, the phone management page and the 30 translations come from the
original project. New text added by the fork is in English and Italian only, so other languages
fall back to English for it.
