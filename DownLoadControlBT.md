# DownLoadControlBT

Experimental first-phase prototype of **download control**: the client
requests pieces of one selected file in a strict order and waits when the
current piece is unavailable, instead of falling back to a later piece.

This is not production-ready. It is not a privacy tool, an anti-tracking
tool, or a complete replacement for BiglyBT's normal picker.

## Upstream starting point

| Item | Value |
| --- | --- |
| Upstream repository | https://github.com/BiglySoftware/BiglyBT |
| Upstream version | BiglyBT 4.1.0.1 Beta B07 (`4.1.0.1_B07`) |
| Base commit | `0e68b708357a86debe1423df05740f6f184fd690` |
| Experiment branch | `DownLoadControlBT` on the `gr88/BiglyBT` fork |
| Pull request base | `master` on the same fork (not upstream) |

The `pom.xml` parent version string (`3.4.0.1-SNAPSHOT`) is unchanged from
upstream and is not the published application version. The runtime version
is `com.biglybt.core.util.Constants.BIGLYBT_VERSION`.

## What phase 1 does

When the experimental toggle is enabled for **exactly one file** in a
torrent:

1. Compute that file's inclusive zero-based piece range
   `[firstPiece, lastPiece]`.
2. Request pieces in this order: `firstPiece`, then `lastPiece`, then
   `firstPiece+1` ... `lastPiece-1`.
3. Keep exactly one **active** piece until that piece is **hash-verified**
   complete (`DiskManagerPiece.isDone()`).
4. Allow multiple outstanding blocks and multiple peers **within** the
   active piece.
5. If the active piece is unavailable, wait indefinitely. Do not skip to a
   later piece, even if it is rarer, hinted, allowed-fast, reserved,
   sequential, high-priority, or an endgame leftover.
6. Download shared file-boundary pieces in full. If the file occupies a
   single piece, that piece is requested once.
7. Skip pieces that are already hash-verified, including after a restart.
   Restart/resume reconstructs the next piece from disk-verified state, not
   from transient received bytes.
8. A failed hash does not advance the queue.

The mode is **off by default**. With the mode off, piece picking is
unchanged.

While the mode remains on, pieces outside the selected file's remaining
plan are not requested. After the selected file's pieces are all
hash-verified, the prototype requests nothing else until you turn the mode
off (which restores normal picking for any remaining torrent pieces).

## What phase 1 does not do

- Peer or speed-based bans
- Randomized piece order
- Speed policy
- 1-2 second retry timers
- Queue demotion of a source
- Multiple selected files, or interleaving more than one file
- Automatic global coordination across several torrents
- Privacy, fingerprinting, or anti-tracking claims
- A public swarm live-download test of copyrighted or user-supplied
  torrent content in this repository

## How to enable it

1. Build and run this branch (see below).
2. Add a torrent as you normally would.
3. In the torrent's file list, right-click **exactly one** file.
4. Check **Experimental: DownLoadControlBT Strict Order**.

The choice is persisted on that download (`dlcbt.enabled` and
`dlcbt.fileidx`). Selecting a different file on the same torrent replaces
the target. Selecting more than one file at once does not enable the mode;
the menu explains that exactly one file is required.

The item is unavailable when the file is skipped ("Do not download") or
has zero length.

Turning the mode on while a download is already running cancels
outstanding requests for pieces other than the new active piece.

## How to build and test

From the repository root, with Java 8+ and Maven (the wrapper is included):

```
./mvnw -pl core test -Dtest=DownloadControlStrictOrderTest,DownloadControlBTEligibilityTest
```

To compile the core module:

```
./mvnw -pl core -am package -DskipTests
```

To run the desktop client from a development checkout, follow the
existing [README.md](README.md) instructions (`com.biglybt.ui.Main` in
the `uis` module). There is no separate installer for this experiment.

Look for log lines prefixed with `DownLoadControlBT:` in BiglyBT's
`pieces` log channel. Useful events include enable/disable, the current
active piece, waiting because the active piece is unavailable, issued
block requests, hash-verified completion, hash failure (no advance),
cancelled off-target requests, and late or off-target incoming blocks.

## Architecture

New request eligibility is a hard gate, not a negative-priority hint:

- `com.biglybt.core.downloadcontrolbt.DownloadControlStrictOrder` —
  pure order and eligibility rules, no BiglyBT runtime.
- `com.biglybt.core.downloadcontrolbt.DownloadControlBT` — persistence,
  file-range lookup, verified-state reconstruction, request cancellation,
  and logging.
- `PiecePickerImpl` consults the gate from normal picking, reserved-piece
  resume, RTA allocation, and endgame chunk selection. While the mode is
  restricting requests, torrent-wide endgame is left so leftover chunks
  cannot start a later piece.
- Incoming `writeBlock` logs late or off-target data. Already-requested
  in-flight blocks may still be written; they are not treated as
  malicious and they do not count as verified completion.

The ordinary sequential/priority picker remains a preference with fallback
when this mode is off. The public plugin API is not used for exclusivity.

## Protocol and restart limitations

- Enabling the mode mid-download sends `cancel` for outstanding
  off-target piece requests. Data that already left a peer can still
  arrive. Those arrivals are logged and may be written if the block is
  not already present. Unverified bytes never advance the plan.
- Duplicate or late blocks for the active piece are handled by existing
  client logic (already-written blocks are discarded).
- Physical network timing cannot be made identical to "the client has
  exactly one outstanding piece." The prototype controls **new
  requests**.
- Availability wait is indefinite; there is no retry timer in this phase.

## Tests

Focused offline tests use synthetic numeric piece ranges only. They do
not include user torrents, artwork, filenames, tracker URLs, or infohashes
from outside fixtures.

The numeric range `3355-3582` appears in tests only as an example of
first / last / interior order. Expected order for that range is
`3355, 3582, 3356, ..., 3581`.

Covered cases include first-last-interior order, single-piece and empty
files, waiting at an unavailable head, hash failure with no advancement,
already-complete boundaries, shared first/last boundary pieces, disabled
mode compatibility, and eligibility rejection of rarest/hint/allowed-fast
/endgame/reserved alternatives.
