# DownLoadControlBT checkpoint — 2026-11-10 (America/Chicago)

Next increment starts at 36e9e663d / release 202611090124. All requested
source changes except the non-blocking doubled-window issue are in this
increment; see the 2026-11-10 changelog for the precise status table.

## Implemented

- Stored file S/R stays visible when unchecked; explicit priorities check
  selected files in both file tables.
- No torrent DLC column in any Library view; Play Now stays hidden.
- Separate per-HTTP-peer limiter follows schedule peer_down, never a
  torrent/global cap; HTTP peers stay out of speed-ban sampling.
- Fully-covered active-piece automatic Rerequest Blocks, immediately
  then at most every 2 seconds; stop once data arrives; no gate bypass.
- Exact Peers-view Waiting for Handshake state (>3s) uses the existing
  54–64 minute temporary-ban registry; TCP Connecting is excluded.
- Higher-priority files preempt only after current piece hash-verifies.
- Files/Experimental forward-from-piece action with persisted per-file
  sequential suffix. S/R clears the suffix; earlier pieces remain missing
  until that explicit change or Off. Then queues next file by priority.
- First/Last piece columns in Open Torrent Options, Peers Ctrl+Space
  toggle + Ctrl-arrow focus, Sources Seeds descending default once.
- Unset-only defaults: .incomplete enabled, auto-rename data off, don't
  delete torrent on remove. Explicit existing/later preferences survive.

## Verification and limits

- Full Maven core + UI reactor package: 164 tests pass, zero failures,
  errors or skips (90 DLCBT tests). UI shade build succeeds. SWT 4.968
  resolves all 1762 SWT member references.
- Init helper defaults verified, including explicit contrary values
  surviving a subsequent init.
- Real-facade regression tests exercise piece-boundary preemption,
  unchecked mode letters and the forward suffix moving to the next file.
- Local synthetic disk fixture verifies partial/complete plain and
  .incomplete files in moved folders (4 cases), then repeats after core
  restart using saved isolated state. No live torrent/network resumption
  claimed, no GUI test, no remote HTTP throughput measurement.
- Handshake trigger is confirmed against Peers StateItem and
  PEPeerTransportProtocol.init() in code; tests cover exact timeout/state,
  temporary duration, address identity and all-controller notification.
- No broad filesystem searches, user torrent/media/log input, regular
  BiglyBT profile or public torrents used. Fixture data/logs stay in temp.

## Unresolved, non-blocking

Doubled window after resolution changes / moving between displays, even
without HDMI: no reproduction on the build box. Keep SWT 4.968 and the
4.922 fallback; no speculative scale environment rewrites. Launching on
the intended monitor was reported to help, not guaranteed.
Old column-width drag, first Files right-click jump, native file chooser
placement and checkbox-rendering issues remain unproven on the laptop.

No upstream changes. PR #1 never merged. No new cloud agents. Never
commit packaging/dlcbt/stage/, logs, fixture payloads or credentials.
