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
  Random interiors (there is no separate Auto file queue switch).
  Off in that dialog disables all experimental gates. Regular ~/.biglybt
  is never written. I2P/Tor network checkboxes default on here; this
  package does not install I2P or Tor plugins and does not provide
  privacy. Private-torrent DHT/PEX restrictions remain as in BiglyBT.

Requirements
------------
- A Linux x86_64 system with GTK 3 libraries for the SWT GUI
  (typically already present on Ubuntu Desktop)
- Java 17 JRE or JDK. The launcher uses $JAVA_HOME/bin/java when that
  file is executable; otherwise it uses java from PATH. Java 8+ may
  start; this package was built with Temurin 17.0.21.
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
left unchanged. Use --no-compact to skip adding the default.

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
  waits for the old JVM pid (not a fixed two-second sleep), then
  re-executes this start-dlcbt.sh with stdin/stdout/stderr detached
  from the dying parent. Child banners go to
  data/logs/restart-child.log so a future failed relaunch has an error
  log. The launcher also retries a lingering pid-file or port 26980
  before refusing. It does not launch a non-isolated Main and does not
  apply updater payloads. The spawn transport is covered by a dummy
  subprocess unit test; File > Restart itself was not GUI-clicked in
  the packaging run.

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

Exact first test steps (Random)
-------------------------------
Use a legal torrent you already have the right to download. Pick a small
file so the plan is easy to read in the log. Experimental order stays
disabled until you turn it on.

1. Start the isolated client:

       ./start-dlcbt.sh

2. Add that legal torrent. Keep it STOPPED. Do not start downloading yet.
   In BiglyBT: use the open/add torrent action and choose to add the
   torrent in a stopped/queued state.

3. Open that torrent's Files view. Select exactly one file. Do not
   multi-select other files. The DLC column should be blank on every
   file until you enable a mode.

4. Right-click that one file and choose:

       Experimental: DownLoadControlBT -> Random (R)

   The DLC column should show R on that file only. Other files stay blank.

5. Only after the column shows R, start the torrent/download.

Expected piece order for the selected file: its first containing piece,
then its last distinct containing piece, then a shuffle of the interior
pieces. The same plan is reused if you stop and later restart the client
without changing mode or file. The client waits if the current piece is
unavailable; it does not skip ahead. One piece stays active until it is
hash-verified.

Watch data/logs/biglybt.log for lines starting with "DownLoadControlBT:".
A Random enable should log mode information, the seed, and the full
order= list. Later lines use keys such as active-R-<piece>, req-<piece>,
wait-<piece>, and complete. Sequential enablement logs a first-last-then-
ascending order instead, and active keys use active-S-<piece>.

To compare modes on the same file, switch the submenu to Sequential (S)
or Off. Switching mode writes a new plan as appropriate. Off clears the
DLC letter.

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
