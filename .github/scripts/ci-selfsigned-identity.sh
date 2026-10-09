#!/bin/bash
# CI only (macOS runner): creates a THROW-AWAY self-signed code-signing identity in a temporary keychain, trusts it
# for code signing on this (ephemeral) runner and exports SIGN_IDENTITY/SIGN_KEYCHAIN/SIGN_TIMESTAMP to the next
# steps. It lets CI exercise everything about Developer ID signing that does not need Apple: the hardened runtime,
# the entitlements, signing the natives inside jars, signing the bundle and the .dmg, and running the signed app.
# It cannot test notarization (needs a real certificate and Apple's service).
set -euo pipefail

D="$(mktemp -d)"
KC="$RUNNER_TEMP/selftest.keychain-db"
PW="$(uuidgen)"
NAME="Modelio Self-Signed Test"

cat > "$D/openssl.cnf" <<EOF
[req]
distinguished_name = dn
x509_extensions = ext
prompt = no
[dn]
CN = $NAME
[ext]
basicConstraints = critical,CA:false
keyUsage = critical,digitalSignature
extendedKeyUsage = critical,codeSigning
EOF
/usr/bin/openssl req -x509 -newkey rsa:2048 -nodes -days 2 -keyout "$D/key.pem" -out "$D/cert.pem" -config "$D/openssl.cnf" 2>/dev/null
/usr/bin/openssl pkcs12 -export -inkey "$D/key.pem" -in "$D/cert.pem" -out "$D/id.p12" -passout pass:"$PW" -name "$NAME"

security create-keychain -p "$PW" "$KC"
security set-keychain-settings -lut 3600 "$KC"
security unlock-keychain -p "$PW" "$KC"
security import "$D/id.p12" -k "$KC" -P "$PW" -T /usr/bin/codesign -T /usr/bin/security
security set-key-partition-list -S apple-tool:,apple:,codesign: -s -k "$PW" "$KC" > /dev/null
# shellcheck disable=SC2046
security list-keychains -d user -s "$KC" $(security list-keychains -d user | sed 's/"//g')
# trust it for code signing (ephemeral CI runner only)
sudo security add-trusted-cert -d -r trustRoot -p codeSign -k /Library/Keychains/System.keychain "$D/cert.pem"

echo "identities in the test keychain:"
security find-identity -p codesigning "$KC"
HASH="$(security find-identity -v -p codesigning "$KC" | grep "$NAME" | head -1 | awk '{print $2}')"
[ -n "$HASH" ] || { echo "::error::the self-signed test identity is not valid for code signing"; security find-identity -p codesigning "$KC"; exit 1; }
{
    echo "SIGN_IDENTITY=$HASH"
    echo "SIGN_KEYCHAIN=$KC"
    echo "SIGN_TIMESTAMP=--timestamp=none"
} >> "$GITHUB_ENV"
echo "test identity ready: $HASH"
