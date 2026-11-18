# DownLoadControlBT checkpoint — 2026-11-18 (America/Chicago)

Increment starts at c19602379 / release 202611101559. See the 2026-11-18
changelog section for details.

## Implemented

- S (Sequential) default piece mode for all files without a stored
  override; stored per-file modes kept.
- Open Torrent Options multi-select S/R applying to all selected
  nonempty files, checking unchecked ones.
- Display-only `DownLoadControlBT<version>` in status bar and About from
  version.txt; Constants.BIGLYBT_VERSION untouched.
- "User Mode"=2 (Advanced) by InitIsolatedConfig when missing or still
  BiglyBT's auto-written Beginner 0 (one-shot dlcbt.usermode.advanced.v1).
- ~30 s Connecting kick (disconnect, no ban) for outgoing peers.
- hash-fail DLCBT temporary ban 210–270 s, separate reason, upstream
  hash-fail rules unchanged, honours dlcbt.ban.enabled.
- Peers "%Bad/Good" column, visible by default when DLCBT is on.

## Verification and limits

- Full Maven core + UI reactor: 172 tests, zero failures/errors/skips
  (98 DLCBT tests, 8 new in DownloadControlBTNov18Test).
- No GUI or live-swarm verification of the status bar, About, OTO
  multi-select, Peers column, connecting kick or hash-fail ban.
- Hash fails with several writers and no later block proof are not
  attributed by BiglyBT, so they do not ban (they do count in %Bad/Good).
- %Bad/Good is per connection; reconnects start at zero.
- A column layout saved earlier that explicitly hides %Bad/Good keeps it
  hidden; new layouts show it.

## Unresolved, non-blocking (carried)

Doubled window after resolution/display changes; old column-width drag,
first Files right-click jump, native file chooser placement, checkbox
rendering — unproven on the laptop.

No upstream changes. PR #1 never merged. No new cloud agents. Never
commit packaging/dlcbt/stage/, logs, fixture payloads or credentials.
