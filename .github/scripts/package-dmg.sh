#!/bin/bash
# Packages a Modelio macOS product archive (tar.gz produced by build-mac.sh) as a .dmg.
# Runs on macOS only (codesign, hdiutil, ditto).
#
#   package-dmg.sh <x86_64|aarch64> <archive.tar.gz> <output.dmg>
#
# What it does:
#   1. extracts the .app
#   2. ad-hoc signs every Mach-O file inside it, then the bundle, and verifies the seal. The launcher and the
#      JRE come from other signers, and the launcher was renamed and given a new Info.plist, so their original
#      signatures are no longer valid for this bundle; a consistent ad-hoc seal avoids the "app is damaged" error
#      on quarantined downloads (it is still "unidentified developer": no Apple Developer ID, no notarization).
#   3. builds a compressed HFS+ disk image with the app, an /Applications link and a README
set -euo pipefail

ARCH="$1"; ARCHIVE="$2"; OUT="$3"
case "$ARCH" in x86_64|aarch64) ;; *) echo "bad arch: $ARCH"; exit 2 ;; esac

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
echo "== extract $ARCHIVE"
tar xzf "$ARCHIVE" -C "$WORK"
APP="$(ls -d "$WORK"/*.app | head -1)"
APPNAME="$(basename "$APP")"
VERSION="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$APP/Contents/Info.plist" 2>/dev/null || echo 5.4.1)"
echo "app=$APPNAME version=$VERSION"

echo "== ad-hoc signing Mach-O files"
COUNT=0
while IFS= read -r -d '' f; do
    if file -b "$f" | grep -q 'Mach-O'; then
        codesign --force --sign - --timestamp=none "$f" >/dev/null 2>&1 || { echo "FAILED to sign $f"; codesign --force --sign - --timestamp=none "$f"; exit 1; }
        COUNT=$((COUNT + 1))
    fi
done < <(find "$APP" -type f \( -perm -u+x -o -name '*.dylib' -o -name '*.so' -o -name '*.jnilib' \) -print0)
echo "signed $COUNT Mach-O files"

echo "== sign and verify the bundle"
codesign --force --sign - --timestamp=none "$APP"
codesign --verify --strict --verbose=2 "$APP"
codesign -dv "$APP" 2>&1 | grep -E "Identifier|Signature|Format|CodeDirectory" || true
echo "(spctl is expected to reject an ad-hoc signed app:)"
spctl --assess --type execute -v "$APP" 2>&1 || true

echo "== build the disk image"
STAGE="$WORK/stage"
mkdir "$STAGE"
ditto "$APP" "$STAGE/$APPNAME"
ln -s /Applications "$STAGE/Applications"
cat > "$STAGE/README.txt" <<EOF
Modelio $VERSION - macOS ($ARCH) - unsigned test build
=====================================================

Install
  1. Drag "$APPNAME" onto the Applications shortcut in this window.
  2. This build has an ad-hoc signature only (no Apple Developer ID, not notarized), so macOS blocks the first
     launch of a downloaded copy. Either
       - Control-click the app > Open > Open, or System Settings > Privacy & Security > "Open Anyway", or
       - in Terminal:  xattr -dr com.apple.quarantine "/Applications/$APPNAME"

Architecture
  $ARCH build. $(if [ "$ARCH" = x86_64 ]; then echo "On Apple Silicon it runs through Rosetta 2 (softwareupdate --install-rosetta)."; else echo "Native Apple Silicon build (no Rosetta needed). Code formatting (astyle) is not available in this build."; fi)

Run from Terminal (shows the Eclipse/OSGi log, useful for development)
  "/Applications/$APPNAME/Contents/MacOS/modelio" -consoleLog

Layout (Eclipse RCP application, everything is under Contents/Eclipse)
  Contents/MacOS/modelio            native launcher
  Contents/Eclipse/modelio.ini      launcher arguments (-vm points to the bundled Java 11)
  Contents/Eclipse/plugins          OSGi bundles
  Contents/Eclipse/jre              bundled Temurin 11 JRE
  Contents/Info.plist, Resources/   bundle metadata and icon
  User data and logs:  ~/.modelio/5.4/
After editing anything inside the bundle, re-sign it:  codesign --force --deep --sign - "/Applications/$APPNAME"
EOF

rm -f "$OUT"
hdiutil create -volname "Modelio $VERSION ($ARCH)" -srcfolder "$STAGE" -fs HFS+ -format UDZO -ov "$OUT"
hdiutil verify "$OUT"
ls -lh "$OUT"
shasum -a 256 "$OUT"
