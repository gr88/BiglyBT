#!/usr/bin/env bash
# DownLoadControlBT isolated launcher. Does not touch ~/.biglybt.
set -euo pipefail

INSTANCE_PORT=26980
LISTEN_PORT=26981

die() {
  echo "ERROR: $*" >&2
  exit 1
}

is_real_dir() {
  local d=$1
  [[ -d "$d" ]] || return 1
  [[ ! -L "$d" ]] || return 1
  return 0
}

resolve_root() {
  local src=$1
  local dir
  dir=$(cd "$(dirname "$src")" && pwd -P) || die "cannot resolve script directory"
  printf '%s\n' "$dir"
}

port_in_use() {
  local port=$1
  if command -v ss >/dev/null 2>&1; then
    ss -ltn 2>/dev/null | awk '{print $4}' | grep -E "[:.]${port}\$" >/dev/null 2>&1
    return $?
  fi
  # Fallback: try binding via bash /dev/tcp is not a listen check.
  if command -v python3 >/dev/null 2>&1; then
    python3 - "$port" <<'PY'
import socket, sys
port=int(sys.argv[1])
s=socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.settimeout(0.3)
try:
    r=s.connect_ex(("127.0.0.1", port))
    sys.exit(0 if r==0 else 1)
except Exception:
    sys.exit(1)
finally:
    s.close()
PY
    return $?
  fi
  return 1
}

ROOT=$(resolve_root "${BASH_SOURCE[0]}")
# The launcher lives in the package root; refuse if that root is a symlink itself.
PACKAGE_ROOT=$ROOT
[[ ! -L "$PACKAGE_ROOT" ]] || die "package root is a symlink; extract the archive to a real directory"

DATA="$PACKAGE_ROOT/data"
CONFIG="$DATA/config"
DOWNLOADS="$DATA/downloads"
TORRENTS="$DATA/torrents"
LOGS="$DATA/logs"
PIDFILE="$DATA/dlcbt.pid"
HELPER_DIR="$PACKAGE_ROOT/tools"

REGULAR_CONFIG="$HOME/.biglybt"

mkdir -p "$CONFIG" "$DOWNLOADS" "$TORRENTS" "$LOGS"
for d in "$DATA" "$CONFIG" "$DOWNLOADS" "$TORRENTS" "$LOGS"; do
  is_real_dir "$d" || die "data path must be a real directory, not a symlink: $d"
done

CONFIG_CANON=$(cd "$CONFIG" && pwd -P)
REGULAR_CANON=""
if [[ -e "$REGULAR_CONFIG" ]]; then
  REGULAR_CANON=$(cd "$REGULAR_CONFIG" && pwd -P) || REGULAR_CANON="$REGULAR_CONFIG"
else
  REGULAR_CANON="$REGULAR_CONFIG"
fi

if [[ "$CONFIG_CANON" == "$REGULAR_CANON" || "$CONFIG_CANON" == "$REGULAR_CANON"/* ]]; then
  die "refusing to use regular BiglyBT config at $REGULAR_CANON"
fi

case "$(uname -s)" in
  Linux) ;;
  *) die "this package is labeled Linux x86_64 only (found $(uname -s))" ;;
esac

arch=$(uname -m)
case "$arch" in
  x86_64|amd64) ;;
  *) die "this package includes Linux x86_64 GTK SWT natives and will not run on $arch" ;;
esac

# Honor JAVA_HOME when it points at a real java binary; otherwise use PATH.
if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
  JAVA_CMD="${JAVA_HOME}/bin/java"
elif command -v java >/dev/null 2>&1; then
  JAVA_CMD=$(command -v java)
else
  die "java not found. Set JAVA_HOME to a Java 17+ JRE/JDK, or put java on PATH. This package was built with Temurin 17."
fi

# Default compact SWT scaling. Preserve any existing JAVA_TOOL_OPTIONS.
# --no-compact skips the default; an already-present -Dswt.autoScale=* is left alone.
DLC_COMPACT=1
LAUNCH_ARGS=()
for arg in "$@"; do
  case "$arg" in
    --compact) DLC_COMPACT=1 ;;
    --no-compact) DLC_COMPACT=0 ;;
    *) LAUNCH_ARGS+=("$arg") ;;
  esac
done
set -- "${LAUNCH_ARGS[@]+"${LAUNCH_ARGS[@]}"}"

if [[ "$DLC_COMPACT" == "1" ]]; then
  case " ${JAVA_TOOL_OPTIONS:-} " in
    *" -Dswt.autoScale="*) ;;
    *) export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+${JAVA_TOOL_OPTIONS} }-Dswt.autoScale=100" ;;
  esac
fi

java_ver=$("$JAVA_CMD" -version 2>&1 | head -n 1)
echo "Using $JAVA_CMD ($java_ver)"

for j in "$PACKAGE_ROOT/BiglyBT.jar" "$PACKAGE_ROOT/swt.jar" "$PACKAGE_ROOT/commons-cli.jar"; do
  [[ -f "$j" ]] || die "missing runtime jar: $j"
done

# Isolation: never talk to the stock instance port 6880.
# MULTI_INSTANCE=true skips StartServer/sendArgs entirely (Main.java).
# azureus.instance.port is still set so any code that reads Constants.INSTANCE_PORT
# does not fall back to 6880 if MULTI_INSTANCE is later removed.
COMMON_JAVA_OPTS=(
  -DMULTI_INSTANCE=true
  -Dazureus.instance.port="${INSTANCE_PORT}"
  -Dazureus.config.path="${CONFIG_CANON}"
  -Dazureus.install.path="${PACKAGE_ROOT}"
  -Dazureus.doc.path="${DATA}"
  -Dazureus.platform.manager.disable=true
  -Dazureus.internal.browser.disable=true
  -Dazureus.overridelog=1
  -Dazureus.overridelogdir="${LOGS}"
  -Duser.dir="${PACKAGE_ROOT}"
  -Dazureus.restart.cmd="${PACKAGE_ROOT}/start-dlcbt.sh"
  -Daz.factory.ClientRestarter.impl=com.biglybt.core.downloadcontrolbt.DownloadControlBTRestarter
)

CP="$PACKAGE_ROOT/BiglyBT.jar:$PACKAGE_ROOT/swt.jar:$PACKAGE_ROOT/commons-cli.jar"

run_init() {
  "$JAVA_CMD" \
    "${COMMON_JAVA_OPTS[@]}" \
    -cp "$CP:$HELPER_DIR" \
    InitIsolatedConfig \
    "$DOWNLOADS" \
    "$TORRENTS"
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  # Help is printed before CoreFactory.create() and, with MULTI_INSTANCE=true,
  # without binding or contacting the single-instance port.
  run_init >/dev/null
  "$JAVA_CMD" \
    "${COMMON_JAVA_OPTS[@]}" \
    -cp "$CP" \
    com.biglybt.ui.Main -h
  exit 0
fi

if [[ "${1:-}" == "--init-only" ]]; then
  run_init
  exit 0
fi

if [[ $# -gt 0 ]]; then
  die "this launcher does not accept extra arguments (refusing to pass torrents or UI commands that BiglyBT might auto-open). Use --help or --init-only."
fi

if [[ -f "$PIDFILE" ]]; then
  oldpid=$(cat "$PIDFILE" || true)
  if [[ -n "${oldpid:-}" ]] && kill -0 "$oldpid" 2>/dev/null; then
    die "DownLoadControlBT test instance already running as PID $oldpid (pid file $PIDFILE). Not starting another copy, and not contacting any other BiglyBT process."
  fi
  rm -f "$PIDFILE"
fi

# If our dedicated control port is already taken, refuse rather than let
# Main.java fall through to CoreSingleInstanceClient.sendArgs (that path
# only runs when MULTI_INSTANCE is not true, but we still treat a busy
# dedicated port as an isolation conflict).
if port_in_use "$INSTANCE_PORT"; then
  die "127.0.0.1:${INSTANCE_PORT} is already in use. Refusing to start so this launcher cannot attach to another process. This dedicated port is not the stock BiglyBT port 6880."
fi

echo "Initializing isolated configuration under $CONFIG_CANON"
run_init

echo "Starting DownLoadControlBT test client"
echo "  config:     $CONFIG_CANON"
echo "  downloads:  $DOWNLOADS"
echo "  torrents:   $TORRENTS"
echo "  logs:       $LOGS/biglybt.log"
echo "  control:    127.0.0.1:${INSTANCE_PORT} (not 6880)"
echo "  listen:     ${LISTEN_PORT} (configured in isolated biglybt.config)"
echo "  regular ~/.biglybt is not used"
echo "Stop with File > Exit, or ./stop-dlcbt.sh. Data directories are kept."

cd "$PACKAGE_ROOT"
"$JAVA_CMD" \
  "${COMMON_JAVA_OPTS[@]}" \
  -cp "$CP" \
  com.biglybt.ui.Main &
pid=$!
echo "$pid" > "$PIDFILE"
cleanup() {
  if kill -0 "$pid" 2>/dev/null; then
    kill "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
  fi
  rm -f "$PIDFILE"
}
trap cleanup INT TERM
set +e
wait "$pid"
status=$?
set -e
rm -f "$PIDFILE"
exit "$status"
