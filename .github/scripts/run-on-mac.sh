#!/bin/bash
# Installs and launches Modelio on a physical Mac from the .dmg (or an extracted .app) built by CI.
#
#   bash run-on-mac.sh [--log] [--no-launch] [path/to/Modelio-*.dmg | path/to/Modelio*.app]
#
# With no path it looks next to this script, in this order:
#   1. a Modelio*.app (this is the case when the script is run from inside the mounted .dmg)
#   2. the Modelio-*-macosx-<arch>.dmg that matches this Mac's CPU (arm64 -> aarch64, Intel -> x86_64)
#
# It then: checks the .dmg against SHA256SUMS (if present), clears the download quarantine flag, mounts the image,
# copies the app to /Applications (or ~/Applications if that is not writable), unmounts, verifies the signature
# seal, clears the quarantine flag on the installed copy and launches it.
#
#   --log        run the launcher in this Terminal with -consoleLog (Eclipse/OSGi log on screen) instead of `open`
#   --no-launch  install only
# Environment: INSTALL_DIR overrides the install folder.
set -euo pipefail

LOG=0; LAUNCH=1; SRC=""
for a in "$@"; do
    case "$a" in
        --log) LOG=1 ;;
        --no-launch) LAUNCH=0 ;;
        -h|--help) sed -n '2,19p' "$0"; exit 0 ;;
        *) SRC="$a" ;;
    esac
done

[ "$(uname -s)" = Darwin ] || { echo "This script is for macOS."; exit 1; }
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MACH="$(uname -m)"                         # arm64 or x86_64
if [ "$MACH" = arm64 ]; then WANT=aarch64; else WANT=x86_64; fi

MNT=""
detach() { if [ -n "$MNT" ]; then hdiutil detach "$MNT" -quiet 2>/dev/null || hdiutil detach "$MNT" -force -quiet 2>/dev/null || true; MNT=""; fi; }
trap detach EXIT

# --- locate the source -------------------------------------------------------------------------------------------
if [ -z "$SRC" ]; then
    SRC="$(ls -d "$HERE"/*.app 2>/dev/null | head -1 || true)"
    [ -n "$SRC" ] || SRC="$(ls "$HERE"/Modelio-*-macosx-"$WANT".dmg 2>/dev/null | head -1 || true)"
    [ -n "$SRC" ] || { echo "No Modelio*.app or Modelio-*-macosx-$WANT.dmg next to the script. Pass a path as argument."; exit 1; }
fi
[ -e "$SRC" ] || { echo "Not found: $SRC"; exit 1; }
echo "source: $SRC   (this Mac: $MACH)"

# --- .dmg: checksum, quarantine, mount ---------------------------------------------------------------------------
case "$SRC" in
    *.dmg)
        SUMS="$(dirname "$SRC")/SHA256SUMS.txt"
        if [ -f "$SUMS" ] && grep -q "$(basename "$SRC")" "$SUMS"; then
            (cd "$(dirname "$SRC")" && grep "$(basename "$SRC")" "$SUMS" | shasum -a 256 -c -) || { echo "Checksum mismatch: the image is corrupted or modified."; exit 1; }
        fi
        xattr -d com.apple.quarantine "$SRC" 2>/dev/null || true
        MNT="$(mktemp -d /tmp/modelio-dmg.XXXXXX)"
        hdiutil attach "$SRC" -nobrowse -readonly -mountpoint "$MNT" -quiet
        APP_SRC="$(ls -d "$MNT"/*.app | head -1)"
        ;;
    *.app) APP_SRC="$SRC" ;;
    *) echo "Expected a .dmg or a .app: $SRC"; exit 1 ;;
esac
[ -d "$APP_SRC" ] || { echo "No .app inside $SRC"; exit 1; }

# --- architecture check ------------------------------------------------------------------------------------------
BIN_INFO="$(file -b "$APP_SRC/Contents/MacOS/modelio")"
case "$BIN_INFO" in
    *arm64*)  APP_ARCH=arm64 ;;
    *x86_64*) APP_ARCH=x86_64 ;;
    *) APP_ARCH=unknown ;;
esac
echo "app architecture: $APP_ARCH"
if [ "$APP_ARCH" = arm64 ] && [ "$MACH" != arm64 ]; then
    echo "This is the Apple Silicon build but this Mac is Intel. Use the x86_64 image."; exit 1
fi
if [ "$APP_ARCH" = x86_64 ] && [ "$MACH" = arm64 ] && ! /usr/bin/pgrep -q oahd 2>/dev/null; then
    echo "Note: this is the Intel build on Apple Silicon; it needs Rosetta 2:"
    echo "      softwareupdate --install-rosetta --agree-to-license"
fi

# --- install -----------------------------------------------------------------------------------------------------
INSTALL_DIR="${INSTALL_DIR:-/Applications}"
mkdir -p "$INSTALL_DIR" 2>/dev/null || true
[ -w "$INSTALL_DIR" ] || { INSTALL_DIR="$HOME/Applications"; mkdir -p "$INSTALL_DIR"; echo "(/Applications is not writable for this user; using $INSTALL_DIR)"; }
DEST="$INSTALL_DIR/$(basename "$APP_SRC")"
if [ -e "$DEST" ]; then echo "replacing existing $DEST"; rm -rf "$DEST"; fi
ditto "$APP_SRC" "$DEST"
detach
xattr -dr com.apple.quarantine "$DEST" 2>/dev/null || true
echo "installed: $DEST"
if codesign --verify --strict "$DEST" 2>/dev/null; then echo "signature seal: valid (ad-hoc)"; else echo "WARNING: signature seal is NOT valid (was the bundle modified?). Re-sign: codesign --force --deep --sign - \"$DEST\""; fi

# --- launch ------------------------------------------------------------------------------------------------------
if [ "$LAUNCH" = 1 ]; then
    if [ "$LOG" = 1 ]; then
        echo "starting with -consoleLog (Ctrl-C to quit) ..."
        exec "$DEST/Contents/MacOS/modelio" -consoleLog
    else
        open "$DEST"
        echo "launched. Logs: ~/.modelio/5.4/   Run again with --log to see the console log in this Terminal."
    fi
fi
