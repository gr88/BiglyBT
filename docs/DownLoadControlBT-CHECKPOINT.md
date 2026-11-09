# DownLoadControlBT checkpoint (2026-11-09, America/Chicago)

Ban enforcement, queue-switch removal, Library DLC column, Play Now
hiding, Open Torrent Options sizing/autofit, upload unlimited and the
SWT 4.968 default are in source with unit tests (see the changelog
2026-11-09 entry). Nothing here is GUI- or live-peer-verified.

## Known limits of the ban implementation

- Bans live in memory; restarting the client clears them. No unban UI;
  `dlcbt.ban.enabled=false` turns the policy off (existing bans then
  stop being enforced because every check is policy-gated).
- Speed rule is not measured while any torrent or global download cap
  is set, so a user cap cannot ban every peer.
- The post-handshake same-IP close in `PEPeerTransportProtocol` is not
  counted as a duplicate (only the `PeerManager.route` incoming reject
  is), so one socket is never double-counted.
- Above-max bans are rare in practice because the per-peer `peer_down`
  limiter already caps peers at the same value.

## Unresolved UI (best effort only)

- Column-width drag unreliable; reorder works.
- Files tab first right-click view jump.
- Choose-torrent native `FileDialog` (`OpenTorrentWindow.java` ~376)
  top offscreen: no portable SWT placement for GTK native dialogs.
- Doubled small/large window with HDMI extended desktop. SWT 4.968 is
  now the default (4.922 via `--legacy-swt`) as a mitigation to try.
- GTK checkbox with no checkmark: not reproducible on the build box
  (both SWT jars render checkmarks under Xvfb). The box GTK printed
  "Could not load a pixbuf from .../Adwaita/assets/check-symbolic.svg",
  so a missing SVG pixbuf loader (e.g. librsvg2-common /
  gdk-pixbuf loaders cache) on the desktop is a plausible cause to check.

## Future idea (not implemented)

When a piece is full of received or requested data, that is a good
moment to hit Rerequest Blocks; possibly make it automatic.

## Intentional non-goals already decided

No /24 or /64 widening of DLCBT bans. No peer-ID cross-family bans. No
public network tests. Restart transport remains as user-confirmed.
Queue engine stays one-file-at-a-time; Off still disables gates.

stage/ under packaging/dlcbt is a prior build artifact; do not commit it.
