#!/usr/bin/env bash
# Stop only the DownLoadControlBT test process started by start-dlcbt.sh.
# Does not kill other Java or BiglyBT processes. Does not delete experiment files.
set -euo pipefail

die() {
  echo "ERROR: $*" >&2
  exit 1
}

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
PIDFILE="$ROOT/data/dlcbt.pid"
CONFIG_CANON=$(cd "$ROOT/data/config" 2>/dev/null && pwd -P || true)

if [[ ! -f "$PIDFILE" ]]; then
  echo "No pid file at $PIDFILE — nothing for this package to stop."
  echo "Experiment files under $ROOT/data are left in place."
  exit 0
fi

pid=$(cat "$PIDFILE" || true)
if [[ -z "${pid:-}" ]]; then
  rm -f "$PIDFILE"
  echo "Empty pid file removed. Experiment files were not deleted."
  exit 0
fi

if ! kill -0 "$pid" 2>/dev/null; then
  rm -f "$PIDFILE"
  echo "PID $pid is not running. Pid file removed. Experiment files were not deleted."
  exit 0
fi

cmdline=""
if [[ -r "/proc/$pid/cmdline" ]]; then
  cmdline=$(tr '\0' ' ' < "/proc/$pid/cmdline")
fi

if [[ -n "$CONFIG_CANON" && "$cmdline" != *"$CONFIG_CANON"* ]]; then
  die "PID $pid does not appear to be this package's isolated instance (config path not in command line). Not killing it."
fi

if [[ "$cmdline" != *"azureus.config.path="* && "$cmdline" != *"$CONFIG_CANON"* ]]; then
  die "PID $pid command line does not include azureus.config.path. Not killing it."
fi

echo "Sending TERM to isolated DownLoadControlBT PID $pid"
kill "$pid"
for i in 1 2 3 4 5 6 7 8 9 10; do
  if ! kill -0 "$pid" 2>/dev/null; then
    rm -f "$PIDFILE"
    echo "Stopped. Experiment files under $ROOT/data were preserved."
    exit 0
  fi
  sleep 1
done

echo "Process is still running after TERM; not sending KILL automatically."
echo "You can use File > Exit in the window, or kill $pid yourself if it is stuck."
exit 1
