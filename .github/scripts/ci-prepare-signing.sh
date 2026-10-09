#!/bin/bash
# CI only (macOS runner): imports the Developer ID certificate from repository secrets into a temporary keychain and
# exports the SIGN_*/NOTARY_* variables used by package-dmg.sh to the following steps ($GITHUB_ENV).
# Fails early with a clear message when something is missing. See .github/MACOS-SIGNING.md for the secrets.
#
# Secrets (passed as environment variables by the workflow):
#   MACOS_CERT_P12         base64 of the exported "Developer ID Application" certificate (.p12)   [required]
#   MACOS_CERT_PASSWORD    password of that .p12                                                  [required]
#   MACOS_SIGN_IDENTITY    certificate name, e.g. "Developer ID Application: Acme (TEAMID)"        [optional: auto-detected]
#   notarization, either an App Store Connect API key:
#   APPLE_API_KEY_P8       contents of the AuthKey_XXXX.p8 file
#   APPLE_API_KEY_ID       the key ID
#   APPLE_API_ISSUER_ID    the issuer ID (a UUID; not needed for an individual key)
#   or an Apple ID:
#   APPLE_ID, APPLE_APP_PASSWORD (app-specific password), APPLE_TEAM_ID
set -euo pipefail

need() { [ -n "${!1:-}" ] || { echo "::error::signing was requested but the secret $1 is not set (see .github/MACOS-SIGNING.md)"; exit 1; }; }
need MACOS_CERT_P12
need MACOS_CERT_PASSWORD
if [ -z "${APPLE_API_KEY_P8:-}" ] && [ -z "${APPLE_ID:-}" ]; then
    echo "::error::notarization credentials missing: set APPLE_API_KEY_P8 + APPLE_API_KEY_ID (+ APPLE_API_ISSUER_ID), or APPLE_ID + APPLE_APP_PASSWORD + APPLE_TEAM_ID"
    exit 1
fi

KC="$RUNNER_TEMP/modelio-signing.keychain-db"
KCPW="$(uuidgen)"
echo "$MACOS_CERT_P12" | base64 --decode > "$RUNNER_TEMP/cert.p12"
security create-keychain -p "$KCPW" "$KC"
security set-keychain-settings -lut 21600 "$KC"
security unlock-keychain -p "$KCPW" "$KC"
security import "$RUNNER_TEMP/cert.p12" -k "$KC" -P "$MACOS_CERT_PASSWORD" -T /usr/bin/codesign -T /usr/bin/security
rm -f "$RUNNER_TEMP/cert.p12"
security set-key-partition-list -S apple-tool:,apple:,codesign: -s -k "$KCPW" "$KC" > /dev/null
# keep the login keychain searchable and put ours first
# shellcheck disable=SC2046
security list-keychains -d user -s "$KC" $(security list-keychains -d user | sed 's/"//g')
echo "KEYCHAIN_TO_CLEAN=$KC" >> "$GITHUB_ENV"

IDENTITY="${MACOS_SIGN_IDENTITY:-}"
if [ -z "$IDENTITY" ]; then
    IDENTITY="$(security find-identity -v -p codesigning "$KC" | grep 'Developer ID Application' | head -1 | sed -E 's/.*"(.*)".*/\1/' || true)"
fi
[ -n "$IDENTITY" ] || { echo "::error::no valid 'Developer ID Application' identity found in the imported certificate"; security find-identity -p codesigning "$KC" || true; exit 1; }
echo "identity: $IDENTITY"

{
    echo "SIGN_IDENTITY=$IDENTITY"
    echo "SIGN_KEYCHAIN=$KC"
    echo "SIGN_TIMESTAMP=--timestamp"
    echo "NOTARIZE=1"
} >> "$GITHUB_ENV"

if [ -n "${APPLE_API_KEY_P8:-}" ]; then
    need APPLE_API_KEY_ID
    printf '%s\n' "$APPLE_API_KEY_P8" > "$RUNNER_TEMP/AuthKey.p8"
    chmod 600 "$RUNNER_TEMP/AuthKey.p8"
    {
        echo "NOTARY_KEY_PATH=$RUNNER_TEMP/AuthKey.p8"
        echo "NOTARY_KEY_ID=$APPLE_API_KEY_ID"
        echo "NOTARY_ISSUER=${APPLE_API_ISSUER_ID:-}"
    } >> "$GITHUB_ENV"
else
    need APPLE_APP_PASSWORD
    need APPLE_TEAM_ID
    echo "::add-mask::$APPLE_APP_PASSWORD"
    {
        echo "NOTARY_APPLE_ID=$APPLE_ID"
        echo "NOTARY_PASSWORD=$APPLE_APP_PASSWORD"
        echo "NOTARY_TEAM_ID=$APPLE_TEAM_ID"
    } >> "$GITHUB_ENV"
fi
echo "signing and notarization configured"
