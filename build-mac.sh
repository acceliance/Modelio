#!/bin/bash
# =============================================================================
# build-mac.sh
#
# Cross-builds the Modelio macOS (cocoa / x86_64) product with Tycho on Linux
# (intended for WSL Ubuntu, see setup-wsl.sh) and collects the archive.
#
# What it does:
#   1. checks tools (JDK 11, Maven) and memory
#   2. generates dev-platform/rcp-target/rcp.target with absolute paths
#      (the tracked file is restored afterwards)
#   3. runs the AGGREGATOR build with -Pplatform.mac,product.org
#   4. copies the macOS archive to ./dist and sanity-checks its content
#
# Signing, notarization, .dmg creation and launch tests need macOS and are NOT
# done here (see the GitHub Actions macOS runner for that).
#
# Usage (from the repo root, inside WSL, repo on the Linux filesystem):
#   ./build-mac.sh                 # full build
#   ./build-mac.sh --products-only # rebuild only the products module (fast re-run
#                                  # after a previous full build)
#   ./build-mac.sh --offline       # mvn -o
#   ./build-mac.sh --check-only    # only inspect an existing archive in products/target
#   ./build-mac.sh --arm64         # Apple Silicon (macosx/cocoa/aarch64); adds the launcher after the Tycho build
# =============================================================================

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$REPO_ROOT"

PRODUCTS_ONLY=0
OFFLINE=0
CHECK_ONLY=0
ARM64=0
for arg in "$@"; do
    case "$arg" in
        --products-only) PRODUCTS_ONLY=1 ;;
        --offline)       OFFLINE=1 ;;
        --check-only)    CHECK_ONLY=1 ;;
        --arm64)         ARM64=1 ;;
        -h|--help)       sed -n 2,26p "$0"; exit 0 ;;
        *) echo "Unknown option: $arg"; exit 2 ;;
    esac
done

DIST_DIR="$REPO_ROOT/dist"
TARGET_FILE="dev-platform/rcp-target/rcp.target"
MVN_PROFILES="platform.mac,product.org"
ARCH_TAG="x86_64"
if [ "$ARM64" -eq 1 ]; then
    # Apple Silicon: aarch64 environment (launcher is added after the build, see add_arm64_launcher)
    MVN_PROFILES="platform.mac.arm,product.org"
    ARCH_TAG="aarch64"
    export ARM64=1
fi

check_archive() {
    local archive="$1"
    echo
    echo "== Sanity check: $archive =="
    local listing
    listing="$(tar tvzf "$archive")"

    local launcher jre_java
    launcher="$(echo "$listing" | grep -E 'Contents/MacOS/[^/]+$' | grep -vE '\.(ini|dylib)$' | head -3 || true)"
    jre_java="$(echo "$listing" | grep -E 'jre/Contents/Home/bin/java$' || true)"

    echo "-- launcher candidates (Contents/MacOS):"
    echo "${launcher:-  NONE FOUND}"
    echo "-- bundled JRE java:"
    echo "${jre_java:-  NONE FOUND}"

    local problems=0
    [ -z "$launcher" ] && { echo "PROBLEM: no launcher under Contents/MacOS"; problems=1; }
    [ -z "$jre_java" ] && { echo "PROBLEM: bundled macOS JRE not found (jre/Contents/Home/bin/java)"; problems=1; }
    if [ -n "$launcher" ] && ! echo "$launcher" | head -1 | grep -qE '^-..x'; then
        echo "PROBLEM: launcher is not executable in the archive (permissions lost)"; problems=1
    fi
    if [ -n "$jre_java" ] && ! echo "$jre_java" | head -1 | grep -qE '^-..x'; then
        echo "PROBLEM: bundled java is not executable in the archive (permissions lost)"; problems=1
    fi
    local win_leak
    win_leak="$(echo "$listing" | grep -ciE 'swt\.(win32|gtk)' || true)"
    [ "$win_leak" -gt 0 ] && echo "WARNING: $win_leak win32/gtk SWT entries in the macOS archive"
    [ "$problems" -eq 0 ] && echo "OK: basic checks passed (this does NOT prove the app launches)."
    return $problems
}

# Apple Silicon: Tycho cannot assemble the native launcher for macosx/aarch64 from the Eclipse 4.18 target
# (see products/macos-aarch64/README.txt). Add launcher, Info.plist and icon to the finished archive.
add_arm64_launcher() {
    local archive="$1"
    local root="$REPO_ROOT/dev-platform/rcp-target/rcp-eclipse/eclipse-aarch64/binary/org.eclipse.equinox.executable_root.cocoa.macosx.aarch64_3.8.1700.v20220509-0833"
    local plist="$REPO_ROOT/products/macos-aarch64/Info.plist.template"
    local icns="$REPO_ROOT/products/icons/modelio.icns"
    for f in "$root" "$plist" "$icns"; do
        [ -f "$f" ] || { echo "ERROR: missing $f"; return 1; }
    done
    command -v unzip >/dev/null || { echo "ERROR: unzip not found (run setup-wsl.sh)"; return 1; }

    local work app version
    work="$(mktemp -d)"
    tar xzf "$archive" -C "$work" 2>/dev/null
    app="$(ls -d "$work"/*.app | head -1)"
    [ -d "$app" ] || { echo "ERROR: no .app in $archive"; rm -rf "$work"; return 1; }
    version="$(grep -m1 -o '<version>[^<]*' "$REPO_ROOT/pom.xml" | sed 's/<version>//; s/-SNAPSHOT//')"
    [ -n "$version" ] || version="5.4.1"

    mkdir -p "$app/Contents/MacOS" "$app/Contents/Resources"
    unzip -p "$root" Eclipse.app/Contents/MacOS/launcher > "$app/Contents/MacOS/modelio"
    chmod 755 "$app/Contents/MacOS/modelio"
    cp "$icns" "$app/Contents/Resources/modelio.icns"
    sed "s/@VERSION@/$version/g" "$plist" > "$app/Contents/Info.plist"

    # repack with a clean owner and the same top-level folder
    ( cd "$work" && tar czf "$archive.new" --owner=0 --group=0 --numeric-owner "$(basename "$app")" )
    mv -f "$archive.new" "$archive"
    rm -rf "$work"
    echo "Added arm64 launcher, Info.plist and icon to $(basename "$archive")"
}

find_mac_archive() {
    find "$REPO_ROOT/products/target" -type f -name "*macosx*cocoa*${ARCH_TAG}*.tar.gz" 2>/dev/null | head -1
}

if [ "$CHECK_ONLY" -eq 1 ]; then
    ARCHIVE="$(find_mac_archive)"
    [ -z "$ARCHIVE" ] && { echo "No macOS archive found under products/target"; exit 1; }
    check_archive "$ARCHIVE"
    exit $?
fi

echo "== 1/4 Environment checks =="
case "$REPO_ROOT" in
    /mnt/*) echo "ERROR: repo is on a Windows drive ($REPO_ROOT)."
            echo "       Clone it into the Linux filesystem (e.g. ~/modelio); see setup-wsl.sh."
            exit 1 ;;
esac
command -v mvn >/dev/null || { echo "ERROR: mvn not found (run setup-wsl.sh)"; exit 1; }
command -v java >/dev/null || { echo "ERROR: java not found (run setup-wsl.sh)"; exit 1; }
JAVA_MAJOR="$(java -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+)(\.[0-9]+)?.*/\1/')"
if [ "$JAVA_MAJOR" != "11" ]; then
    echo "WARNING: java major version is $JAVA_MAJOR; the legacy build expects 11."
fi
MEM_GB="$(free -g | awk '/^Mem:/{print $2}')"
if [ "${MEM_GB:-0}" -lt 10 ]; then
    echo "WARNING: only ${MEM_GB} GB RAM visible to WSL; consider memory=12GB in .wslconfig."
fi
export MAVEN_OPTS="${MAVEN_OPTS:--Xmx4g -Xss4m}"
echo "java $JAVA_MAJOR, MAVEN_OPTS=$MAVEN_OPTS"

echo "== 2/4 Generate rcp.target =="
cp "$TARGET_FILE" "$TARGET_FILE.orig"
restore_target() {
    [ -f "$TARGET_FILE.orig" ] && mv -f "$TARGET_FILE.orig" "$TARGET_FILE"
}
trap restore_target EXIT
export ECLIPSE_WS="$REPO_ROOT"
bash ./generate-target.sh

echo "== 3/4 Tycho build (-P$MVN_PROFILES) =="
MVN_ARGS=(clean install -Dmaven.test.skip=true "-P$MVN_PROFILES")
[ "$OFFLINE" -eq 1 ] && MVN_ARGS+=(-o)
START=$(date +%s)
if [ "$PRODUCTS_ONLY" -eq 1 ]; then
    # the target definition (org.modelio:rcp) is installed into ~/.m2 by dev-platform/rcp-target;
    # refresh it so the freshly generated rcp.target is the one products/ resolves against
    (cd dev-platform/rcp-target && mvn "${MVN_ARGS[@]}")
    (cd products && mvn "${MVN_ARGS[@]}")
else
    (cd AGGREGATOR && mvn "${MVN_ARGS[@]}")
fi
echo "Build time: $(( $(date +%s) - START ))s"

echo "== 4/4 Collect macOS archive =="
ARCHIVE="$(find_mac_archive)"
if [ -z "$ARCHIVE" ]; then
    echo "ERROR: build finished but no macOS .tar.gz found under products/target."
    echo "       Archives present:"
    find "$REPO_ROOT/products/target" -maxdepth 3 \( -name '*.tar.gz' -o -name '*.zip' \) | sed 's/^/         /'
    exit 1
fi
mkdir -p "$DIST_DIR"
OUT="$DIST_DIR/modelio-5.4.1-macosx-${ARCH_TAG}.tar.gz"
cp -f "$ARCHIVE" "$OUT"
if [ "$ARM64" -eq 1 ]; then
    add_arm64_launcher "$OUT" || exit 1
fi
ls -lh "$OUT"
check_archive "$OUT" || true

cat <<EOF

Next (needs a Mac or the GitHub macOS runner):
  tar xzf $(basename "$OUT")
  xattr -dr com.apple.quarantine <extracted .app>   # unsigned build: bypass Gatekeeper
  open <extracted .app>

Copy to Windows if needed:  cp "$OUT" /mnt/c/Users/\$USER/Downloads/
EOF
