DownLoadControlBT portable Linux x86_64 test build
===================================================

This is an unofficial experimental, isolated test build of a BiglyBT fork
with the DownLoadControlBT selected-file piece-order experiment
(Sequential and Random).

It is NOT production-ready. It is not a privacy tool. It is not an
official BiglyBT release or installer for your regular BiglyBT copy.
It is not intended to be merged upstream.

Source
------
- Fork branch: DownLoadControlBT on https://github.com/gr88/BiglyBT
- Pull request: https://github.com/gr88/BiglyBT/pull/1 (review only; not merged)
- Source commit: SOURCE_COMMIT
- Progress / changelog: docs/DownLoadControlBT-CHANGELOG.md on that branch
  (also copied into this folder when present)
- Upstream project: https://github.com/BiglySoftware/BiglyBT
- Upstream version: BiglyBT 4.1.0.1 Beta B07 (4.1.0.1_B07)
- Upstream base commit: 0e68b708357a86debe1423df05740f6f184fd690
- Architecture: Linux x86_64 (amd64) only
- SWT natives: GTK x86_64. swt.jar is upstream's current Linux SWT 4.968
  (uis/lib/swt-linux-4968r13.jar, needs Java 17+). The previously bundled
  SWT 4.922 is kept as swt-legacy-4922.jar (use --legacy-swt)
- Runtime jars were built with Temurin 17 from the source commit above

The SHA256 of the .tar.gz that contains this folder is recorded next to the
archive as a .sha256 sidecar (an archive cannot contain its own hash).
version.txt next to LICENSE contains the America/Chicago timestamp used as
this package version. SOURCE.txt keeps the upstream BiglyBT version and
full git commit metadata separately.

What this package will not do
-----------------------------
- It does not read or write ~/.biglybt
- It does not import your normal settings or torrent list
- It does not install file associations or replace stock BiglyBT
- It does not contact the stock single-instance port 6880
- It does not include a user torrent or media files
- Isolated first-run defaults (only when a setting is missing or a
  speed schedule is empty) enable the DownLoadControlBT profile: new
  torrents added through the always-shown Open Torrent Options dialog
  default to Force Started and the strict one-file-at-a-time queue with
  Sequential (S) as the default piece mode for every file (choose R per
  file; multi-select works in the add dialog; no separate Auto file queue
  switch). User Proficiency defaults to Advanced when unset. The status
  bar and Help > About show DownLoadControlBT<version> from version.txt.
  Off in that dialog disables all experimental gates. Isolated defaults
  also set upload slots 80 (seeding alternate 80), connections 200 per
  torrent (seeding alternate 200) and 500 global, leave global speeds at
  0, require encrypted transport, turn UPnP and NAT-PMP off, leave DHT,
  PEX and trackers on, and uncheck upload bias, piece re-check on
  completion, and hide-padding. Listen ports default to 26981 once
  (dlcbt.listen.ports.v1) and then stick. Regular ~/.biglybt is never
  written. I2P/Tor network checkboxes default on here; this package does
  not install I2P or Tor plugins and does not provide privacy.
  Private-torrent DHT/PEX restrictions remain as in BiglyBT.

Requirements
------------
- A Linux x86_64 system with GTK 3 libraries for the SWT GUI
  (typically already present on Ubuntu Desktop)
- Java 17+ JRE or JDK (required by the default SWT). The launcher uses
  $JAVA_HOME/bin/java when executable; otherwise java from PATH.
  Built and tested with Temurin 17.0.21.
  Example: sudo apt install openjdk-17-jre
- This package will refuse to start on non-x86_64 CPUs because it ships
  Linux x86_64 SWT natives

Extract
-------
Extract this folder to a normal directory you own, not inside ~/.biglybt.
Do not replace the data/ directories with symlinks.

    tar -tzf DownLoadControlBT-linux-x86_64-VERSION_STAMP.tar.gz
    tar -xzf DownLoadControlBT-linux-x86_64-VERSION_STAMP.tar.gz
    cd DownLoadControlBT-linux-x86_64-VERSION_STAMP

How to launch
-------------
    ./start-dlcbt.sh

Optional:

    ./start-dlcbt.sh --init-only    # write isolated settings only
    ./start-dlcbt.sh --help         # BiglyBT CLI help, then exit
    ./start-dlcbt.sh --no-compact   # do not add default -Dswt.autoScale=100
    ./start-dlcbt.sh --compact      # keep the default compact SWT scale
    ./start-dlcbt.sh --legacy-swt   # use the older bundled SWT 4.922
                                    # (also DLCBT_LEGACY_SWT=1; kept for
                                    # File > Restart)
    ./stop-dlcbt.sh                 # stop only this package's recorded PID

If JAVA_HOME points at a JRE/JDK that contains bin/java, that binary is
used. Otherwise the first java on PATH is used.

By default the launcher appends -Dswt.autoScale=100 to JAVA_TOOL_OPTIONS
so the GTK UI stays compact. Existing JAVA_TOOL_OPTIONS are preserved.
If JAVA_TOOL_OPTIONS already contains -Dswt.autoScale=..., that value is
left unchanged. Use --no-compact to skip adding the default. In compact
mode the launcher also sets GDK_SCALE=1 when that variable is unset, so
SWT 4.968 is less likely to re-layout on an HDMI dpiChanged; an already
set GDK_SCALE is left alone.

The launcher sets these JVM properties before any BiglyBT class loads:

    -DMULTI_INSTANCE=true
    -Dazureus.instance.port=26980
    -Dazureus.config.path=<this-package>/data/config
    -Dazureus.install.path=<this-package>
    -Dazureus.doc.path=<this-package>/data
    -Dazureus.platform.manager.disable=true
    -Dazureus.internal.browser.disable=true
    -Dazureus.overridelog=1
    -Dazureus.overridelogdir=<this-package>/data/logs
    -Dazureus.restart.cmd=<this-package>/start-dlcbt.sh
    -Daz.factory.ClientRestarter.impl=com.biglybt.core.downloadcontrolbt.DownloadControlBTRestarter

Meaning, from BiglyBT source:
- azureus.config.path is read first by SystemProperties.getUserPath() and
  replaces ~/.biglybt.
- azureus.install.path is read by SystemProperties.getApplicationPath()
  (otherwise user.dir).
- azureus.doc.path is used by SystemProperties.getDocPath() for the default
  save-path calculation.
- MULTI_INSTANCE=true makes com.biglybt.ui.Main skip StartServer and skip
  CoreSingleInstanceClient.sendArgs, so a second launch cannot forward
  commands to another instance.
- azureus.instance.port overrides Constants.INSTANCE_PORT (default 6880)
  if single-instance code still reads the port.
- azureus.platform.manager.disable=true avoids desktop/integration hooks.
- azureus.overridelog / overridelogdir force file logging to data/logs.
- azureus.restart.cmd plus the package-owned restarter allow File >
  Restart without shipping the official updater plugin. The restarter
  waits up to 150 s for the old JVM pid (logging "waiting" and
  "gave up" to data/logs/restart-child.log) and starts that waiter
  with setsid so a hangup of the dying parent does not kill the child.
  start-dlcbt.sh also waits up to 150 s for a lingering pid-file or
  port 26980, holds a real flock on data/dlcbt.lock for the whole time
  java runs, prints the real TCP/UDP listen ports, and removes the pid
  file only if it still holds this wrapper's own pid. It does not
  launch a non-isolated Main and does not apply updater payloads. The
  spawn transport is covered by a dummy subprocess unit test; File >
  Restart itself was not GUI-clicked in the packaging run.

If 127.0.0.1:26980 is already in use, or this package's pid file still
points at a live process, the launcher refuses to start. It does not try
to talk to port 6880 and it does not kill other processes.

Temporary peer bans (DownLoadControlBT profile only)
----------------------------------------------------
Separate from BiglyBT's own IP filter / bad-data bans: in memory only (a
restart clears them), never written to the 7-day ban file, and never
widened to a /24 or /64. Each ban lasts a random 54-64 whole minutes and
applies to every torrent in this isolated client: incoming and outgoing
connections from that exact address are refused and any connected peer
with that address is closed on all torrents.

- Address: exact IPv4 (/32) or exact IPv6 (/128). IPv4-mapped IPv6
  (::ffff:a.b.c.d) counts as the IPv4 address. Loopback, LAN, I2P/Tor,
  web seeds and plugin peers are never speed-banned.
- Speed: after 60 s of active sending, the peer's incoming payload
  average over 30 s of active sending must be at least 10k (10 KiB/s)
  and at most the speed schedule's per-peer peer_down (251.1k by
  default). Active sending means it has unchoked us and we have
  outstanding requests to it. Idle time, choked time, DLCBT one-piece
  gate waits and hash-check waits pause the clock instead of counting
  as zero. Time while a torrent or global download limit is set is not
  measured (a cap could push every peer under 10k).
- Handshake: Waiting for Handshake for >3 seconds (not TCP Connecting)
  gets the same temporary ban.
- Hash fail: any hash fail attributed to a peer bans that exact address
  for a random 3.5-4.5 minutes (reason hash-fail), in addition to
  BiglyBT's own ban after the 3rd hash fail. The Peers "%Bad/Good"
  column shows hash-failed bytes / good bytes per peer.
- Connecting: outgoing peers stuck in Connecting about 30 s are
  disconnected (never banned).
- Idle peer: once Fully Established, a peer with no data downloaded
  from it and no data uploaded to it for more than about 5 seconds
  (including while seeding) gets the usual 54-64 minute ban (reason
  idle-peer). A recent rarest-only reject in that window suppresses
  the idle ban.
- Duplicates: the 3rd rejected incoming duplicate connection from the
  same address on the same torrent within 60 s bans it. Different
  torrents do not add up; outgoing dials are never counted.
- Log lines start with "DLCBT temporary ban reason=" in data/logs.
- Settings (biglybt.config, seeded only when missing):
  dlcbt.ban.enabled=true, dlcbt.ban.duration.min.minutes=54,
  dlcbt.ban.duration.max.minutes=64, dlcbt.ban.speed.min=10k,
  dlcbt.ban.speed.max= (blank = follow peer_down), dlcbt.ban.ramp.seconds=60,
  dlcbt.ban.window.seconds=30, dlcbt.ban.duplicate.window.seconds=60,
  dlcbt.ban.duplicate.threshold=3. Set dlcbt.ban.enabled=false in BiglyBT's
  advanced (raw) config view to turn bans off.
  Restarting the client clears all current DLCBT bans.

Rarest-only upload and the DownLoadControlBT Options page
---------------------------------------------------------
dlcbt.upload.rarestonly (default on) is a checkbox on Options >
DownLoadControlBT. Per file, availability includes our own copy. A
file's first and last pieces are always allowed (a piece that spans
two files is therefore always allowed). Only pieces at that file's
current lowest availability among the pieces we have may be sent,
while downloading and while seeding. Non-rarest pieces are hidden
from bitfield and Have announcements; requests for them are rejected
(including a re-check of queued requests before the disk read).
Super-seeding is limited to allowed pieces. When the per-file minimum
rises, Have is sent for newly allowed pieces. A peer that keeps
asking only for non-rarest pieces is disconnected after 8 rejects,
with no ban.

The same Options page has "Automatically size all table columns to
fit" in seconds (default 360, 0 = off). View > All Peers is opened
once on first start. After the sidebar is up, a NAT/firewall test
runs; if both TCP and UDP explicitly fail, a popup asks for the
forwarded port and saves it as both listen ports.

Upload: this build sets overall/session upload to unlimited once
(dlcbt.upload.unlimited.v1). The per-peer schedule (peer_up=25.1k per
peer) is unchanged. Later changes you make are kept.

Isolated paths (all owned by this package)
------------------------------------------
    data/config/       isolated BiglyBT settings (biglybt.config)
    data/downloads/    default save path for downloaded files
    data/torrents/     saved .torrent copies created by this instance
    data/logs/         biglybt.log (also used via azureus.overridelogdir);
                      restart-child.log for File > Restart child output
    data/dlcbt.pid     live process id while the client is running

First-run isolated settings also set TCP/UDP listen ports to 26981 so this
client does not share stock BiglyBT's default 6881.

If you already used an earlier isolated package from this experiment,
keep using the same extracted data/ tree (or copy data/config) so the
persisted download state, including any previous Sequential enablement,
is reused. A config reset is not required. Sequential-enabled downloads
keep Sequential (S) after this upgrade.

This increment: quick tests
--------------------------
Use a legal test torrent and keep your normal BiglyBT untouched.

1. In Open Torrent Options, uncheck a nonempty file: its stored S/R
   should stay visible. Select unchecked files and set Numeric Auto
   Decreasing (or another priority): they should become checked and
   show the priority. Repeat in the Files tab.
2. First piece / Last piece columns should be visible in the add dialog.
   Numbers are torrent-global and zero-based, matching Pieces view.
   Library should have no DLC torrent column and no Play Now column.
3. During a download, raise another file's priority. The current piece
   must finish AND hash-verify before the queue switches files.
4. Files > Experimental: DownLoadControlBT > Start from this piece and
   go forward sequentially... accepts a piece number inside that file.
   It checks the file, starts there, then requests the ascending suffix
   one verified piece at a time. At the suffix end it queues the next
   file. Earlier pieces stay missing until you choose ordinary S/R again
   (clears that start point) or Off. The choice persists across restart.
5. Automatic Rerequest Blocks now runs on a fully covered active piece
   (received blocks plus requested/promised blocks). It clears only
   outstanding request marks, exactly like the manual Pieces action,
   immediately and then at most every 2 seconds when fully covered
   again. It stops after all block data arrives; hash verification still
   gates the next piece. It never resets received data.
6. HTTP/External Seeds get their own per-peer download limiter from
   peer_down (251.1k by default), not an aggregate limit. Check their
   observed speeds. They remain excluded from speed bans.
7. A regular peer shown as Waiting for Handshake for >3 seconds is
   temporarily banned (same 54-64 min, exact address, all torrents).
   TCP Connecting / Pending time does NOT count. Log reason:
   DLCBT temporary ban reason=handshake-timeout.
8. Peers: Ctrl+click rows; Ctrl+Up/Down moves focus without clearing
   selection; Ctrl+Space toggles the focused row. Sources should first
   sort Seeds highest first. Later user sort changes are remembered.
9. Existing partial/complete data: select the folder holding it, add
   stopped, then Force Recheck before starting. This build does not
   change BiglyBT's disk hash/recheck engine. Automated local fixtures
   verified plain and .incomplete partial/complete files in moved folders,
   including a core restart with saved download state. Back up your
   existing data/config before testing; do not copy regular ~/.biglybt.

File defaults are seeded only if unset (saved explicit preferences win):
  Rename Incomplete Files=true
  Rename Incomplete Files Extension=.incomplete
  DefaultDir.AutoSave.AutoRename=false
  def.deletetorrent=false
No per-torrent/global download caps are added and custom schedules are
never rewritten.

Known issues: display doubling after resolution/monitor moves remains
unresolved. SWT 4.968 is the default; compare --legacy-swt if needed.
Launching the terminal/client on the intended monitor was reported to
help, but is not a proven fix. Ctrl-selection and new menus/columns are
source-tested/build-tested, not GUI-verified on the reporting laptop.

How to stop (keeps experiment files)
------------------------------------
- Prefer File > Exit / close the window, or
- Run ./stop-dlcbt.sh from this folder.

stop-dlcbt.sh only signals the PID recorded in data/dlcbt.pid, and only
if that process command line still contains this package's config path.
It will not delete data/config, data/downloads, data/torrents, or
data/logs.

File > Restart, if used, is supposed to spawn this same start-dlcbt.sh
after the process stops. That path is implemented in this build but was
not GUI-verified when the archive was packaged. The official updater
plugin is not included on purpose.

How isolation was designed
--------------------------
Stock BiglyBT listens for a second launch on 127.0.0.1:6880
(Constants.DEFAULT_INSTANCE_PORT). If that bind fails, Main.java calls
CoreSingleInstanceClient.sendArgs and then exits, which would hand your
command line to the already-running client. This launcher avoids that by
setting MULTI_INSTANCE=true (no bind / no sendArgs) and a dedicated
instance port 26980, and by refusing to start when 26980 is busy.

Plugins load from the isolated user path (data/config/plugins) and this
package's application plugins directory, not from ~/.biglybt/plugins.
The instance lock file .azlock is also created under the isolated
config path (CoreImpl.getLockFile()).

Starting the GUI still initializes a BitTorrent client. It may open the
isolated listen port 26981 and attempt other client background activity.
Do not add or start torrents until you intend to. This package does not
include a torrent and should not be pointed at public swarms for this
experiment without your own legal test file.

Remaining limitations
---------------------
- Not a signed official installer
- Linux x86_64 / GTK SWT only
- Java is not bundled; use a system JRE
- GUI appearance depends on your desktop GTK libraries
- GUI, live torrent, Restart, and Exit were not exercised during the
  packaging run that produced this archive. Unit tests cover the piece
  planner, persistence/restart of a stored random plan, unavailable-head
  behaviour, hash-failure non-advance, mode-letter mapping, and legacy
  Sequential migration.
- Column-boundary drag-to-resize has a source-level hit-test change
  (shared 5-pixel divider check instead of SWT cursor identity). That
  change is not GUI-verified. Do not treat it as a confirmed fix.
  "Size Column to Fit" was already known to work.
- Enabling a mode mid-download cancels off-target requests; already
  in-flight blocks can still arrive
- This phase does not implement bans, speed policy, retry timers, or
  queue demotion
