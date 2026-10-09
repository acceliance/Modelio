#!/bin/bash
# CI only: removes the temporary keychains and key files created by ci-prepare-signing.sh / ci-selfsigned-identity.sh.
for kc in "${KEYCHAIN_TO_CLEAN:-}" "$RUNNER_TEMP/selftest.keychain-db" "$RUNNER_TEMP/modelio-signing.keychain-db"; do
    [ -n "$kc" ] && [ -e "$kc" ] && security delete-keychain "$kc" 2>/dev/null && echo "deleted keychain $kc"
done
rm -f "$RUNNER_TEMP/AuthKey.p8" "$RUNNER_TEMP/cert.p12"
exit 0
