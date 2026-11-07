#!/usr/bin/env bash
# Assemble the isolated DownLoadControlBT Linux x86_64 test archive.
# Paths are derived from this script's location; JAVA_HOME is used when set.
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
SRC_ROOT=$(cd "$SCRIPT_DIR/../.." && pwd -P)
PKG_SRC="$SCRIPT_DIR"
STAGE="${DLCBT_STAGE:-$SCRIPT_DIR/stage}"
OUTDIR="${DLCBT_OUTDIR:-$SRC_ROOT/../artifacts}"

if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi
command -v java >/dev/null 2>&1 || {
  echo "ERROR: java not found. Set JAVA_HOME or put java on PATH." >&2
  exit 1
}
command -v javac >/dev/null 2>&1 || {
  echo "ERROR: javac not found. Set JAVA_HOME to a JDK." >&2
  exit 1
}

if [[ -n "$(git -C "$SRC_ROOT" status --porcelain)" ]]; then
  echo "ERROR: checkout is dirty; commit the tested tree before packaging" >&2
  git -C "$SRC_ROOT" status --short >&2
  exit 1
fi

COMMIT=$(git -C "$SRC_ROOT" rev-parse HEAD)
SHORT=$(git -C "$SRC_ROOT" rev-parse --short=9 HEAD)
UPSTREAM_VERSION=4.1.0.1_B07
STAMP="${DLCBT_VERSION_STAMP:-$(TZ=America/Chicago date +%Y%m%d%H%M)}"
NAME="DownLoadControlBT-linux-x86_64-${STAMP}"
DEST="$STAGE/$NAME"

echo "Assembling $NAME from $COMMIT (upstream $UPSTREAM_VERSION)"

rm -rf "$STAGE"
mkdir -p "$DEST/data/config" "$DEST/data/downloads" "$DEST/data/torrents" "$DEST/data/logs" "$DEST/tools" "$OUTDIR"

install -m 644 "$SRC_ROOT/uis/target/BiglyBT.jar" "$DEST/BiglyBT.jar"
install -m 644 "$SRC_ROOT/uis/lib/swt-linux-64.jar" "$DEST/swt.jar"
install -m 644 "$SRC_ROOT/core/lib/commons-cli.jar" "$DEST/commons-cli.jar"
install -m 644 "$SRC_ROOT/LICENSE" "$DEST/LICENSE"
printf '%s\n' "$STAMP" > "$DEST/version.txt"
install -m 644 "$PKG_SRC/README.txt" "$DEST/README.txt"
sed -i "s/SOURCE_COMMIT/${COMMIT}/g; s/SOURCE_SHORT/${SHORT}/g; s/VERSION_STAMP/${STAMP}/g" "$DEST/README.txt"
if [[ -f "$SRC_ROOT/docs/DownLoadControlBT-CHANGELOG.md" ]]; then
  install -m 644 "$SRC_ROOT/docs/DownLoadControlBT-CHANGELOG.md" "$DEST/DownLoadControlBT-CHANGELOG.md"
fi
install -m 755 "$PKG_SRC/start-dlcbt.sh" "$DEST/start-dlcbt.sh"
install -m 755 "$PKG_SRC/stop-dlcbt.sh" "$DEST/stop-dlcbt.sh"

printf 'Isolated download directory for this test package only.\n' > "$DEST/data/downloads/README.txt"
printf 'Isolated torrent-file directory for this test package only.\n' > "$DEST/data/torrents/README.txt"
printf 'File logs appear here as biglybt.log when the client runs.\n' > "$DEST/data/logs/README.txt"
printf 'Isolated BiglyBT config (biglybt.config) is created on first launch.\n' > "$DEST/data/config/README.txt"

cat > "$DEST/SOURCE.txt" << SOURCENOTE
source_commit=$COMMIT
source_commit_short=$SHORT
upstream_version=$UPSTREAM_VERSION
upstream_repo=https://github.com/BiglySoftware/BiglyBT
upstream_base_commit=0e68b708357a86debe1423df05740f6f184fd690
fork=https://github.com/gr88/BiglyBT
branch=DownLoadControlBT
pull_request=https://github.com/gr88/BiglyBT/pull/1
package_version=$STAMP
package_version_zone=America/Chicago
architecture=linux-x86_64
swt=uis/lib/swt-linux-64.jar renamed to swt.jar
main_class=com.biglybt.ui.Main
SOURCENOTE

javac --release 8 -cp "$DEST/BiglyBT.jar:$DEST/commons-cli.jar" \
  -d "$DEST/tools" "$PKG_SRC/InitIsolatedConfig.java"

{
  echo "packaged_files_sha256:"
  hashed=(BiglyBT.jar swt.jar commons-cli.jar start-dlcbt.sh stop-dlcbt.sh README.txt LICENSE version.txt SOURCE.txt tools/InitIsolatedConfig.class)
  if [[ -f "$DEST/DownLoadControlBT-CHANGELOG.md" ]]; then
    hashed+=(DownLoadControlBT-CHANGELOG.md)
  fi
  (cd "$DEST" && sha256sum "${hashed[@]}")
} > "$DEST/PACKAGED-SHA256.txt"

if find "$DEST" \( -name 'hosts.yml' -o -name '*.torrent' -o -name 'box-secrets.json' -o -name '.git' \) -print -quit | grep -q .; then
  echo "ERROR: unexpected sensitive file staged" >&2
  exit 1
fi

if find "$DEST" -type l | grep -q .; then
  echo "ERROR: staged package contains symlinks" >&2
  find "$DEST" -type l >&2
  exit 1
fi

mkdir -p "$OUTDIR"
(cd "$STAGE" && tar -czf "$OUTDIR/$NAME.tar.gz" "$NAME")
(cd "$OUTDIR" && sha256sum "$NAME.tar.gz" > "$NAME.tar.gz.sha256")

echo "Wrote $OUTDIR/$NAME.tar.gz"
ls -lh "$OUTDIR/$NAME.tar.gz" "$OUTDIR/$NAME.tar.gz.sha256"
cat "$OUTDIR/$NAME.tar.gz.sha256"
echo "version=$STAMP"
