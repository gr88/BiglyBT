# DownLoadControlBT checkpoint — 2026-11-20 (America/Chicago)

Increment starts at 948084665 / release 202611181639. See the 2026-11-20
changelog section for details.

## Implemented

- Isolated setIfUnset / one-shot defaults: slots 80/80, connections
  200/200/500, bias off, UPnP/NAT-PMP off, DHT/PEX/trackers on, global
  speeds 0, re-check and hide-padding off, require encryption on.
- OTO Files one-shot wider Name/Size/Priority/Path.
- Library completed table one-shot widen-to-fit and Done w/DND visible.
- Automatic Size Column to Fit every 360 s (0 = off) on the new
  DownLoadControlBT Options page.
- View > All Peers opened once on first start.
- Idle-peer ban after ~5 s of no data in or out once Fully Established
  (54–64 min, reason idle-peer), including while seeding; suppressed by
  a recent rarest-only reject.
- Automatic NAT/firewall test after sidebar start; popup only if both
  TCP and UDP explicitly fail; saved port written to both listen ports.
- Sticky listen ports (`dlcbt.listen.ports.v1`); launcher prints the
  real tcp.listen / udp.listen values.
- Restart waiter 150 s with waiting/gave up logs, setsid, flock on
  data/dlcbt.lock for the java lifetime, pid file removed only if ours.
- GDK_SCALE=1 in the compact launcher plus computeSize / swt_build NPE
  guards. --legacy-swt kept.
- Rarest-only upload (default on): per file, availability includes us,
  first/last exempt, hide/reject non-rarest, re-check queued requests,
  super-seed limited, Have tick when min rises, disconnect after 8
  non-rarest-only requests with no ban.
- Sidebar percent: MeasureItem uses full client width; one-shot redraw.

## Verification and limits

- Full Maven core + UI reactor: 180 tests, zero failures/errors/skips
  (106 DLCBT tests, 8 new in DownloadControlBTNov20Test).
- No GUI or live-swarm verification of the Options page, NAT popup,
  idle ban, rarest-only announce/reject, column autofit, All Peers,
  sidebar percent, HDMI unplug, or File > Restart.
- Upstream v4.2.0.0 was not merged.

## Unresolved, non-blocking (carried)

Doubled window after resolution/display changes may still occur; the
GDK_SCALE pin and NPE guards are source-level only. Old column-width
drag, first Files right-click jump, native file chooser placement,
checkbox rendering — unproven on the laptop.

No upstream changes. PR #1 never merged. No new cloud agents. Never
commit packaging/dlcbt/stage/, logs, fixture payloads or credentials.
