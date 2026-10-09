# Signing and notarizing the macOS images

By default CI produces `.dmg` images with an **ad-hoc** signature: they run, but macOS shows "unidentified developer"
on a downloaded copy. With an Apple Developer ID certificate the same pipeline signs everything properly, notarizes it
with Apple and staples the tickets, so macOS opens it without a warning.

Nothing here is active until you add the secrets and start a manual run with `sign: true`.

## What you need

| | |
|---|---|
| Apple Developer Program membership | about 99 USD per year. An individual account is quick; an organization needs a D-U-N-S number |
| A **Developer ID Application** certificate | one certificate signs both the Intel and the Apple Silicon build |
| Notarization credentials | an App Store Connect **API key**, or an Apple ID + app-specific password |

(The *Developer ID Installer* certificate is only for `.pkg` installers; the *Apple Development* certificate of a free
account cannot be used for distribution.)

## 1. Create the certificate (on a Mac)

1. Keychain Access > Certificate Assistant > *Request a Certificate From a Certificate Authority*: enter your email,
   choose *Saved to disk*. This creates a `.certSigningRequest` and a private key in your login keychain.
2. https://developer.apple.com/account/resources/certificates > **+** > *Developer ID Application* > upload the request >
   download the `.cer` and double-click it (it pairs with the private key).
3. Keychain Access > *My Certificates* > right-click "Developer ID Application: ..." > *Export* > `.p12`, set a password.
4. Encode it for GitHub:  `base64 -i DeveloperID.p12 | pbcopy`

## 2. Create the notarization key

App Store Connect > Users and Access > Integrations > *App Store Connect API* > generate a key (role *Developer*).
Download `AuthKey_XXXXXXXXXX.p8` (you can download it only once), note the **Key ID** and the **Issuer ID**.

Alternative without an API key: your Apple ID, an **app-specific password** (appleid.apple.com > Sign-In and Security)
and your **Team ID** (developer.apple.com > Membership).

## 3. Add the repository secrets

GitHub > repository > Settings > Secrets and variables > Actions > *New repository secret*. Never put these in the repo.

| Secret | Value | Required |
|---|---|---|
| `MACOS_CERT_P12` | the base64 text from step 1 | yes |
| `MACOS_CERT_PASSWORD` | the `.p12` password | yes |
| `MACOS_SIGN_IDENTITY` | certificate name, e.g. `Developer ID Application: Acme SAS (ABCDE12345)` | no (auto-detected) |
| `APPLE_API_KEY_P8` | the whole content of the `.p8` file | API key route |
| `APPLE_API_KEY_ID` | the Key ID | API key route |
| `APPLE_API_ISSUER_ID` | the Issuer ID (a UUID; leave out for an individual key) | API key route |
| `APPLE_ID`, `APPLE_APP_PASSWORD`, `APPLE_TEAM_ID` | Apple ID route | alternative |

## 4. Run it

Actions > *macOS build* > *Run workflow* > tick **sign** > Run. The `package` job then, per architecture:
signs the native libraries inside the jars, every binary, the app (hardened runtime + entitlements) > notarizes the
app and staples the ticket > builds the `.dmg` > signs, notarizes and staples it. The images are the
`modelio-dmg-<arch>` artifacts. Runs started by a pull request never sign (secrets are not exposed to forks and the
notary service would be called on every push).

The scripts: `.github/scripts/package-dmg.sh` (local use too, see its header), `sign-jar-natives.py`,
`entitlements.plist`, `ci-prepare-signing.sh`.

## What is tested before you have the account

The `signing-selftest` job signs an Apple Silicon build with a throw-away self-signed certificate through the same
code path (hardened runtime, entitlements, natives inside jars, bundle, `.dmg`) and starts the signed app. It cannot
test Apple's notary service; the first notarization is the first real test of that part.

## If notarization fails

The job prints Apple's log (`notarytool log`). The usual causes and fixes:

| Message | Fix |
|---|---|
| "The binary is not signed with a valid Developer ID certificate" / "not signed" for a file inside a jar | a native library is missing from `sign-jar-natives.py` (add the extension or path) |
| "The signature does not include a secure timestamp" | `SIGN_TIMESTAMP` must be `--timestamp` (default) |
| "The executable does not have the hardened runtime enabled" | the file was signed without `--options runtime` |
| the app crashes at start only when signed | an entitlement is missing in `entitlements.plist` (JIT, unsigned executable memory, library validation) |

## Notes

- Modified jars lose their publisher's Java signature (the content changed); OSGi does not verify it by default, and the
  tool removes the stale signature files so nothing fails on a digest mismatch.
- After editing the app on your own Mac you can re-sign it ad-hoc (`codesign --force --deep --sign - "/Applications/Modelio 5.4.1.app"`);
  that drops the Developer ID signature and the notarization, which is fine for local development.
- Certificates expire (5 years for Developer ID): replace the `MACOS_CERT_*` secrets then.
