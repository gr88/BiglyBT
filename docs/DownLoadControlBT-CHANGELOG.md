# DownLoadControlBT progress

This file tracks the unofficial experimental `DownLoadControlBT` work on
the `gr88/BiglyBT` fork branch of the same name. It is not an official
BiglyBT release, not a privacy tool, and is not intended for merge into
upstream `BiglySoftware/BiglyBT`.

Builds, when published, are GitHub prereleases of an isolated Linux
x86_64 test package. They must not be treated as signed installers or as
replacements for a normal BiglyBT install.

## What the experiment does

DownLoadControlBT optionally restricts piece requests for exactly one
selected file in a torrent:

- **Off** (blank in the Files-view DLC column): normal client behaviour.
- **Sequential (S)**: first containing piece, then the last distinct
  containing piece, then interiors in ascending order.
- **Random (R)**: the same first and last pieces, then a persisted shuffle
  of the interiors. Restart reuses the stored plan (seed + piece list)
  for that file until the mode or selected file is changed.

In both experimental modes the client keeps one active piece until that
piece is hash-verified. Multiple blocks and peers are allowed inside the
active piece. If that piece is unavailable, the picker waits; it does
not skip ahead. Tiny 1-piece and 2-piece files use the same first/last
rules (a single piece appears once; a two-piece file is first then last
with no interiors).

This phase does **not** implement peer bans, speed policy, short retry
timers, or queue demotion.

Legacy downloads that already had the experiment enabled (old
`dlcbt.enabled=true` without a stored mode) keep Sequential behaviour.
No config reset is required.

## 2026-11-07 — Focused UI and restart bug-fix increment

Source and unit tests live on branch `DownLoadControlBT`. Isolated Linux
test archives use an America/Chicago `YYYYMMDDHHMM` version in the
archive name, extracted directory, and `version.txt`. Older prereleases
are retained. Existing isolated `data/` from a previous package of this
series stays usable: copy the old `data` directory beside a fresh
extract, or keep using the same extracted tree after replacing the jars
and scripts. Do not copy `~/.biglybt` into the package.

This increment was **not** GUI-tested in a live desktop session. Fixes
below are labeled as source-fixed, source-mitigated, or unresolved.
No test torrent was placed on the public network. No block-level
control was added. Force Piece still cannot bypass the experimental
allowed-head gate (`getAllowedPieceNumber` is unchanged).

### Status

| Item | Status | Notes |
| --- | --- | --- |
| Wrong-row first right-click (Files tab and add-dialog) | Source-fixed; not GUI-verified | MenuDetect now hit-tests with the event's display coordinates converted via `toControl`, not `getCursorLocation()`. Keyboard menus keep the focused row. Multi-selection is preserved when the pointer is already inside it. `clientArea.y` is added once. Profile-only diagnostic lines log display/control/content Y, hit row, and current selection. |
| DLC column on Open Torrent Options | Source-fixed; not GUI-verified | New `DLC` column shows S/R/blank from pending Off, Auto queue, per-file overrides, and skip/empty. Refreshes after the file menu changes. Existing Files-tab letters are unchanged. Auto-queue tooltip states that selected files download strictly one at a time. Sequential/Random stay single-file; the menu explains why they are disabled for multi-select, skipped, or empty files. |
| Gray expandable add-dialog sections default expanded | Source-fixed; not GUI-verified | Skin defaults all sections expanded. Isolated `InitIsolatedConfig` applies a one-shot `dlcbt.opentorrent.sections.expanded.v1` write so testers who inherited the old saved-collapsed false get the new default once; later collapses persist. |
| Pieces-graphic tiny empty menu | Source-mitigated; not GUI-verified | Upstream already has Force Piece and Reset Piece. The builder no longer returns a zero-item menu: it recovers the piece from the cursor if MenuDetect has not stored one yet (GTK show-before-detect) and otherwise shows a disabled reason item. Force Piece still only sets the picker flag; it does not enlarge the DLCBT allowed head. |
| Column-width drag starts a column move | Source-mitigated; not GUI-verified | Move DND is cancelled if MouseDown already started a resize, or if the last control-relative mouse X *or* the drag event X hits a divider (same 5px slop). Hit-test math is unit-tested. Not visually confirmed. |
| GTK tracker/peer/network checkbox orange, no checkmark | Source-mitigated; not GUI-verified | Add-dialog peer/network groups stop inheriting the dialog's forced white background on Linux. After leaving GTK's inconsistent (grayed) state the selection is rewritten so the native glyph can recover. Stored network and peer-source defaults are unchanged. Not a theme patch. |
| File > Restart exits and does not relaunch | Source-fixed; subprocess-tested | The restarter no longer pipes the child to the dying JVM (`sleep 2; exec` + inherited stdout). It waits for the parent pid (up to 10s) and appends child stdout/stderr to `data/logs/restart-child.log`. The launcher also ignores SIGPIPE and retries a live pid-file or port 26980 before refusing. A dummy-script unit test checks that a marker is written after the watched pid exits, without a hung or leaked child. File > Exit shutdown-hook guard is unchanged. |

### Tests

Unit tests: 55 run, 0 failed (previously 47). Added 8: pending-file
letters (1), pointer/menu/column-hit helpers (5), detached restart
transport (2). Retained FileQueue (13+1), Eligibility (12), Strict (12),
and Random (10).

## Next increment — isolated file queue, profile defaults, Force Start add-mode


Source and unit tests live on branch `DownLoadControlBT`. Isolated Linux
test archives use an America/Chicago `YYYYMMDDHHMM` version in the
archive name, extracted directory, and `version.txt` (plus a newline).
`SOURCE.txt` still records the upstream BiglyBT version and full git
commit. See the matching GitHub prerelease for the archive and SHA256
sidecar. Older prereleases are retained.

This increment was not GUI-tested in a live desktop session. Column
defaults, add-dialog behaviour, Force Start menu visibility, and the
library Advanced List were verified at source and with unit tests / an
isolated `--init-only` inventory. The user will test the build. No test
torrent was placed on the public network.

### Experiment behaviour

- Isolated profile only (`dlcbt.profile.enabled`). Regular BiglyBT
  config and a non-profile run of this fork keep stock defaults.
- New torrents added through Open Torrent Options (not a restored
  library item, not a magnet metadata placeholder, not a seeding-only
  add) default to auto file queue with Random interiors when the
  profile is on. Auto queue can be unchecked. Off disables every
  DownLoadControlBT gate for that torrent and restores normal piece
  picking; it is not a file-skip.
- Auto queue orders selected unskipped nonempty files by numeric
  priority descending, then file index. Exactly one file is active
  until all of its containing pieces are hash-verified. The active file
  stays pinned if the user later edits priorities. After that file
  finishes, or if the user skips it, the remaining queue is recomputed.
  Restart keeps the pinned file and its random plan. Explicit Off is
  persisted and is not reactivated on restore.
- Pre-add Sequential/Random choices are stored per file and carried
  onto the added torrent. In auto queue they are per-file piece-mode
  overrides, not a second concurrent file. Legacy torrents that already
  had explicit Sequential/Random stay in one-file manual mode unless
  the user enables Auto file queue.
- Files-view DLC letters show `S` or `R` for controlled queued files in
  auto mode (and still only the selected file in manual mode). Skipped
  files and Off stay blank. No extra glyph was added.
- Shared whole-boundary pieces still count as verified for every file
  that contains them. There is still no block-level option and no
  request-policy, retry, or ban work.

### Isolated profile defaults (set only if missing; nonempty schedules kept)

- Always show the add dialog (`ui.addtorrent.openoptions=always`).
- Add mode defaults to Force Started unless the user already chose
  “Default Start Torrents Stopped”.
- All peer-source defaults remain on. Public, I2P, and Tor network
  selection defaults are on. I2P/Tor plugins are not installed and this
  is not a privacy feature. Private-torrent mandatory restrictions are
  unchanged.
- Empty or missing speed schedules get exactly:
  `peer_set UpPeers=All,peer_up=25.1k` and
  `peer_set DownPeers=All,peer_down=251.1k` (per-peer limits, not
  session totals).
- Library Advanced List (`Library*.viewmode=1`) and details tabs.
- Default-hidden library columns, remaining available in column setup:
  Order (`#`), Play Now (`TorrentStream`), Subscribe, Health, Comment
  Icon, and Seeding Rank. Hidden Order remains the default sort.
  Saved custom layouts are left alone; only an exact previous stock
  layout is migrated.

### Force Start parity

- Force Start is already present beside Queue in the main library
  context menu (and the toolbar) when user mode is Intermediate+ or
  `Always Show Force Start` is on. The isolated profile turns that
  flag on if it was unset.
- Force Start was added next to Queue on the download mini-bar and the
  category menu, and the tag menu no longer hides it when
  `Always Show Force Start` is on. The meaning of Force Start is
  unchanged.

### Known limitations

- Column drag after the earlier 5-pixel header hit-test change is still
  reported broken. Size Column to Fit works. No further GUI loop was
  spent; this remains unresolved.
- Magnet metadata placeholders are not given auto queue. If a magnet
  later becomes a real torrent without going through Open Torrent
  Options again, auto queue is not silently applied.
- No live GUI verification of the new defaults, menus, or column hides.
- No public-network test torrent was used.

## 2026-11-07 — Sequential + Random, Files-view letter, isolated package fixes

Source and unit tests live on branch `DownLoadControlBT`. Isolated Linux
test archives are published as GitHub **prereleases** from this branch
when a build is packaged. See the matching prerelease for the archive
and its SHA256 sidecar.

### User-visible experiment changes

- Files view gains a default-visible **DLC** column (`S`, `R`, or blank).
  The letter is the experimental mode of the selected file only. It is
  not BiglyBT's ordinary sequential-from-file flag. Other files in the
  same torrent stay blank. Disabled stays blank.
- The file context menu is now a mutually exclusive submenu:
  Off / Sequential (S) / Random (R).
- Random mode persists a versioned plan and seed on the download
  (`dlcbt.mode`, `dlcbt.plan`, `dlcbt.plan.seed`, `dlcbt.plan.ver`).
  Re-enabling the same file in Random reuses a still-valid plan.
  Switching mode generates a new plan as appropriate and still skips
  only disk-verified pieces.

### Isolated package / launcher changes (source-level)

- `start-dlcbt.sh` prefers `JAVA_HOME/bin/java` when that binary exists,
  otherwise `java` on `PATH`.
- Default compact SWT scaling is `-Dswt.autoScale=100`, appended to
  existing `JAVA_TOOL_OPTIONS` rather than replacing them. Pass
  `--no-compact` to skip the default. An already-present
  `-Dswt.autoScale=...` is left unchanged. `--compact` keeps the default.
- File > Restart is allowed without the official `azupdater` plugin by
  setting `azureus.restart.cmd` to this package's `start-dlcbt.sh` and
  using a package-owned `DownloadControlBTRestarter`. That restarter
  only re-executes the isolated launcher after a short delay. It does
  not launch a non-isolated `com.biglybt.ui.Main` and does not apply
  updater payloads.
- The core shutdown hook no longer re-enters `stop()` when a stop has
  already completed or is already in progress (it waits for the in-progress
  stop instead). This is a narrow source fix for a logged double-stop
  during File > Exit.
- Table header divider hit-testing uses a shared 5-pixel boundary check
  for both resize and to refuse column-move drag-and-drop, instead of
  comparing SWT cursor object identity. This is a source-level change
  only.

### Verified in this increment (automated)

The following unit tests were run against the commit that produced the
matching prerelease (see that prerelease and `SOURCE.txt` inside the
archive):

- `DownloadControlStrictOrderTest` (existing sequential planner tests)
- `DownloadControlBTEligibilityTest` (existing eligibility/status tests)
- `DownloadControlRandomOrderTest` (new): first/last stay fixed; interiors
  are a permutation with no repeats; same seed plus serialize/restore is
  deterministic; an unavailable head is not skipped; a hash failure does
  not advance; 1- and 2-piece files are correct; legacy enabled-without-mode
  maps to Sequential; the Files-view letter is blank except on the selected
  file; invalid stored plans are rejected; sequential and random plans
  differ for the same range.

Packaging verification, when a build is published:

- Archive inventory and SHA256 sidecar
- Isolated launcher `--init-only` (no raw `Main` launch, no live torrent)

### Not verified in this increment

- GUI behaviour was not exercised on a desktop session.
- No live BitTorrent / public-swarm test was run.
- File > Restart through the GUI was not clicked.
- File > Exit hang reproduction was not repeated on a live GUI.
- Column-boundary drag-to-resize was not repeated on a live GUI under
  compact scaling. **Do not treat the header-hit-test change as a
  confirmed GUI fix.** "Size Column to Fit" was already known to work.
- Java versions newer than the Temurin 17 used to compile the package
  were not re-validated here.

### Isolation, dependencies, and known limitations

- Linux x86_64 / GTK SWT only. Java is not bundled; use a Java 17 JRE or
  JDK (`JAVA_HOME` or `PATH`). The package was built with Temurin 17.
- The launcher uses a dedicated config tree under the extracted folder,
  `MULTI_INSTANCE=true`, control port 26980, listen ports 26981, and
  refuses to start if that control port or pid file is already live. It
  is designed not to read or write `~/.biglybt` and not to contact stock
  BiglyBT port 6880.
- Starting the GUI still initializes a BitTorrent client and may open
  the isolated listen port. Do not add or start torrents until you
  intend to. Use only legal test files you already have the right to
  download.
- The official updater plugin is intentionally not shipped. Restart, if
  it works, is package-owned and stays inside this isolated tree.
- Enabling a mode mid-download cancels off-target requests; already
  in-flight blocks can still arrive.
- Prior separate UI notes that are **not** claimed fixed here include
  unclickable Tracker / peer-connection checkboxes and hardcoded piece
  picker box sizes.

### Privacy note for published artifacts

Published source, progress notes, and build archives must not contain
user torrent files, media, live logs, peer addresses, or other private
identifiers from a tester's machine.

## 2026-11-06 — First-phase Sequential prototype

Initial isolated Linux x86_64 test build of Sequential selected-file
order (first, last, then interiors ascending; wait if the active piece
is unavailable). Source commit `5cdae84c646ec94ed2322374ef384229b183e80a`
on branch `DownLoadControlBT`, open review at
https://github.com/gr88/BiglyBT/pull/1 (not merged, not sent upstream).

That package documented the same isolation rules (dedicated config,
ports 26980/26981, no `~/.biglybt`, no bundled torrent). GUI testing of
that prototype, when it happened, was done by the tester on their own
machine and is not restated with private filenames or log contents here.
