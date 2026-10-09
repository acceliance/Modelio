#!/bin/bash
# Packages a Modelio macOS product archive (tar.gz produced by build-mac.sh) as a .dmg.
# Runs on macOS only (codesign, hdiutil, ditto, xcrun).
#
#   package-dmg.sh <x86_64|aarch64> <archive.tar.gz> <output.dmg>
#
# Signing mode (environment):
#   default (no SIGN_IDENTITY)  ad-hoc signature: every Mach-O file, then the bundle. Runs anywhere, but macOS shows
#                               "unidentified developer" on a downloaded copy.
#   SIGN_IDENTITY=<name or SHA-1 of a "Developer ID Application" certificate in the keychain>
#                               real signing for distribution: hardened runtime + secure timestamp + entitlements,
#                               also for the native libraries hidden inside jars (sign-jar-natives.py), the bundle
#                               and the .dmg.
#       SIGN_KEYCHAIN=<keychain>   keychain that holds the identity (optional)
#       SIGN_TIMESTAMP=--timestamp (default) or --timestamp=none (throw-away self-signed test identities, offline)
#   NOTARIZE=1 (needs SIGN_IDENTITY)  submit the app and the .dmg to Apple's notary service and staple the tickets.
#       either  NOTARY_KEY_PATH=<AuthKey.p8> NOTARY_KEY_ID=<id> [NOTARY_ISSUER=<issuer uuid>]   (App Store Connect API key)
#       or      NOTARY_APPLE_ID=<apple id> NOTARY_PASSWORD=<app-specific password> NOTARY_TEAM_ID=<team id>
#   Setup of the certificate and secrets: .github/MACOS-SIGNING.md
set -euo pipefail

ARCH="$1"; ARCHIVE="$2"; OUT="$3"
case "$ARCH" in x86_64|aarch64) ;; *) echo "bad arch: $ARCH"; exit 2 ;; esac
HERE="$(cd "$(dirname "$0")" && pwd)"

SIGN_IDENTITY="${SIGN_IDENTITY:--}"
SIGN_KEYCHAIN="${SIGN_KEYCHAIN:-}"
SIGN_TIMESTAMP="${SIGN_TIMESTAMP:---timestamp}"
NOTARIZE="${NOTARIZE:-0}"
ENTITLEMENTS="$HERE/entitlements.plist"

if [ "$SIGN_IDENTITY" = "-" ]; then
    MODE=adhoc
    CS=(--force --sign - --timestamp=none)
else
    MODE=identity
    CS=(--force --sign "$SIGN_IDENTITY" --options runtime "$SIGN_TIMESTAMP")
    [ -n "$SIGN_KEYCHAIN" ] && CS+=(--keychain "$SIGN_KEYCHAIN")
fi
if [ "$NOTARIZE" = 1 ]; then
    [ "$MODE" = identity ] || { echo "NOTARIZE=1 needs SIGN_IDENTITY (a Developer ID Application certificate)"; exit 2; }
    if [ -n "${NOTARY_KEY_PATH:-}" ]; then
        NOTARY_AUTH=(--key "$NOTARY_KEY_PATH" --key-id "${NOTARY_KEY_ID:?NOTARY_KEY_ID missing}")
        [ -n "${NOTARY_ISSUER:-}" ] && NOTARY_AUTH+=(--issuer "$NOTARY_ISSUER")
    elif [ -n "${NOTARY_APPLE_ID:-}" ]; then
        NOTARY_AUTH=(--apple-id "$NOTARY_APPLE_ID" --password "${NOTARY_PASSWORD:?NOTARY_PASSWORD missing}" --team-id "${NOTARY_TEAM_ID:?NOTARY_TEAM_ID missing}")
    else
        echo "NOTARIZE=1 needs NOTARY_KEY_PATH/NOTARY_KEY_ID (API key) or NOTARY_APPLE_ID/NOTARY_PASSWORD/NOTARY_TEAM_ID"; exit 2
    fi
fi
echo "signing mode: $MODE   notarize: $NOTARIZE"

# submit <file> : notarize with Apple and wait; prints the notary log and fails if not accepted
notarize() {
    local file="$1" json id status
    json="$(mktemp)"
    echo "== notarizing $(basename "$file") (this can take several minutes)"
    xcrun notarytool submit "$file" "${NOTARY_AUTH[@]}" --wait --timeout 60m --output-format json > "$json" || true
    cat "$json"; echo
    id="$(/usr/bin/python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("id",""))' "$json")"
    status="$(/usr/bin/python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("status",""))' "$json")"
    if [ "$status" != "Accepted" ]; then
        echo "::error::notarization of $(basename "$file") finished with status '$status'"
        [ -n "$id" ] && xcrun notarytool log "$id" "${NOTARY_AUTH[@]}" || true
        exit 1
    fi
    echo "notarization accepted (submission $id)"
}

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
echo "== extract $ARCHIVE"
tar xzf "$ARCHIVE" -C "$WORK"
APP="$(ls -d "$WORK"/*.app | head -1)"
APPNAME="$(basename "$APP")"
VERSION="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$APP/Contents/Info.plist" 2>/dev/null || echo 5.4.1)"
echo "app=$APPNAME version=$VERSION"

# --- 1. native libraries hidden inside jars (notarization scans inside archives) ---------------------------------------
if [ "$MODE" = identity ]; then
    echo "== signing native libraries inside jars"
    CS_QUOTED="$(printf '%q ' "${CS[@]}")"
    python3 "$HERE/sign-jar-natives.py" "$APP" --codesign-args "$CS_QUOTED"
fi

# --- 2. every loose Mach-O file ----------------------------------------------------------------------------------------
echo "== signing Mach-O files ($MODE)"
COUNT=0
while IFS= read -r -d '' f; do
    kind="$(file -b "$f")"
    case "$kind" in *Mach-O*) ;; *) continue ;; esac
    if [ "$MODE" = identity ] && [[ "$kind" == *executable* ]]; then
        codesign "${CS[@]}" --entitlements "$ENTITLEMENTS" "$f" >/dev/null 2>&1 || { echo "FAILED to sign $f"; codesign "${CS[@]}" --entitlements "$ENTITLEMENTS" "$f"; exit 1; }
    else
        codesign "${CS[@]}" "$f" >/dev/null 2>&1 || { echo "FAILED to sign $f"; codesign "${CS[@]}" "$f"; exit 1; }
    fi
    COUNT=$((COUNT + 1))
done < <(find "$APP" -type f \( -perm -u+x -o -name '*.dylib' -o -name '*.so' -o -name '*.jnilib' \) -print0)
echo "signed $COUNT Mach-O files"

# --- 3. the bundle ----------------------------------------------------------------------------------------------------
echo "== sign and verify the bundle"
if [ "$MODE" = identity ]; then
    codesign "${CS[@]}" --entitlements "$ENTITLEMENTS" "$APP"
else
    codesign "${CS[@]}" "$APP"
fi
if [ "$MODE" = identity ]; then
    codesign --verify --deep --strict --verbose=2 "$APP"
else
    codesign --verify --strict --verbose=2 "$APP"      # the check proven in CI for the ad-hoc path
fi
codesign -dv --verbose=2 "$APP" 2>&1 | grep -E "Identifier|Authority|Signature|Format|flags|TeamIdentifier|Timestamp" || true
if [ "$MODE" = identity ]; then
    echo "entitlements on the main executable:"; codesign -d --entitlements - "$APP" 2>&1 | grep -E "allow-jit|unsigned-executable|library-validation" || true
fi
echo "(spctl before notarization is expected to reject:)"
spctl --assess --type execute -v "$APP" 2>&1 || true

# --- 4. notarize the app and staple the ticket to it (works offline afterwards) ----------------------------------------
if [ "$NOTARIZE" = 1 ]; then
    ditto -c -k --keepParent "$APP" "$WORK/app-for-notary.zip"
    notarize "$WORK/app-for-notary.zip"
    xcrun stapler staple "$APP"
    xcrun stapler validate "$APP"
    spctl --assess --type execute -vv "$APP" 2>&1 || { echo "::error::Gatekeeper still rejects the notarized app"; exit 1; }
fi

# --- 5. the disk image ------------------------------------------------------------------------------------------------
echo "== build the disk image"
STAGE="$WORK/stage"
mkdir "$STAGE"
ditto "$APP" "$STAGE/$APPNAME"
ln -s /Applications "$STAGE/Applications"
cp "$HERE/run-on-mac.sh" "$STAGE/run-on-mac.sh"
chmod +x "$STAGE/run-on-mac.sh"

if [ "$NOTARIZE" = 1 ]; then
    TRUST_NOTE="This build is signed with a Developer ID certificate and notarized by Apple: macOS should open it without a warning."
elif [ "$MODE" = identity ]; then
    TRUST_NOTE="This build is signed with a Developer ID certificate but NOT notarized, so macOS may still warn on the first launch of a downloaded copy."
else
    TRUST_NOTE="This build has an ad-hoc signature only (no Apple Developer ID, not notarized), so macOS blocks the first launch of a downloaded copy."
fi

cat > "$STAGE/README.txt" <<EOF
Modelio $VERSION - macOS ($ARCH) - $MODE signed$(if [ "$NOTARIZE" = 1 ]; then echo ", notarized"; fi)
=====================================================

Install and run - one command (Terminal)
  bash "/Volumes/Modelio $VERSION ($ARCH)/run-on-mac.sh"           # installs to /Applications, clears the quarantine flag, launches
  bash "/Volumes/Modelio $VERSION ($ARCH)/run-on-mac.sh" --log     # same, with the Eclipse/OSGi log in the Terminal
  (add --no-launch to only install; INSTALL_DIR=~/Applications changes the install folder)

Install by hand
  1. Drag "$APPNAME" onto the Applications shortcut in this window.
  2. $TRUST_NOTE
     If it is blocked either
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
(that replaces a Developer ID signature with an ad-hoc one; the app then runs locally but is no longer notarized)
EOF

rm -f "$OUT"
hdiutil create -volname "Modelio $VERSION ($ARCH)" -srcfolder "$STAGE" -fs HFS+ -format UDZO -ov "$OUT"

# --- 6. sign, notarize and staple the disk image ------------------------------------------------------------------------
if [ "$MODE" = identity ]; then
    echo "== signing the disk image"
    DMG_CS=(--force --sign "$SIGN_IDENTITY" "$SIGN_TIMESTAMP")
    [ -n "$SIGN_KEYCHAIN" ] && DMG_CS+=(--keychain "$SIGN_KEYCHAIN")
    codesign "${DMG_CS[@]}" "$OUT"
    codesign --verify --verbose=2 "$OUT"
fi
if [ "$NOTARIZE" = 1 ]; then
    notarize "$OUT"
    xcrun stapler staple "$OUT"
    xcrun stapler validate "$OUT"
    spctl --assess --type open --context context:primary-signature -vv "$OUT" 2>&1 || { echo "::error::Gatekeeper rejects the notarized disk image"; exit 1; }
fi
hdiutil verify "$OUT"
ls -lh "$OUT"
shasum -a 256 "$OUT"
