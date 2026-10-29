# Releasing an update

Stable update APKs must retain application ID `dev.srimi.depthwall` and the signing identity used by v1.0.1. The public SHA-256 certificate fingerprint is:

```text
1086e392ec4b7b22b514eb747d6b00521fd69c5d2ecbbfc48b623db7fca39edf
```

The signing key is not in Git. A new key, including a newly generated “personal” key, cannot update the published app. Restore the existing keystore and credentials from their secure backup. Do not put signing passwords, keystores or tokens in issues, pull-request comments, source files or release assets.

## GitHub Actions

Configure these repository Actions secrets from the **existing** signing identity:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 encoding of the existing keystore, without whitespace |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Existing signing-key alias |
| `ANDROID_KEY_PASSWORD` | Password of that signing key |

Update `versionCode` and `versionName` in `app/build.gradle.kts`, add `docs/releases/<version>.md`, review the changes, and merge them into `main`. Run **Actions → Signed Android release → Run workflow** on `main`.

The manual workflow builds the optimized release, runs release unit tests and lint, then checks the APK's signing certificate, package, version, non-debuggable flag and ZIP/native-library alignment. It creates a draft GitHub release in this repository, uploads the APK, `verification.json` and `SHA256SUMS`, downloads them again and checks their hashes before publishing. An upload or validation failure leaves the release unpublished. Published releases and unrelated tags are never overwritten. A retry may replace only the three expected assets in an unpublished draft targeting the exact same source commit. A draft for another commit or with unexpected assets requires inspection; do not delete a published version to retry.

The workflow deliberately fails if the original signing configuration is unavailable. It does not replace the key or publish a debug APK as an update. Signing files are temporary and removed in the final step; public build artifacts contain no keystore or passwords.

## Local preparation

Restore the gitignored `keystore.properties` and its referenced keystore. Build with JDK 17 and Android SDK 37.0:

```sh
python3 -m unittest discover -s tools -p 'test_verify_release.py'
./gradlew assembleRelease testReleaseUnitTest lintRelease
python3 tools/verify_release.py app/build/outputs/apk/release/app-release.apk \
  --build-tools "$ANDROID_HOME/build-tools/37.0.0" --output artifacts/release
```

`verify_release.py` requires successful release unit-test and lint reports from the same build. It produces the release-named APK, a public verification record and checksums. Use the same draft/upload/download/verify/publish sequence when publishing locally. The release must target the exact source commit used for the APK; never upload an unsigned or differently signed file under the expected release filename.

These checks establish build integrity and certificate compatibility, not successful execution on every Android device. Record actual emulator/phone installation, same-key upgrade and feature test results separately. Preserve existing app data when testing upgrades; do not uninstall as a workaround for a signing mismatch.

## Separate preview APKs

The `preview` build type provides an installable test build when the production signing key is unavailable. It inherits release optimization, uses package ID `dev.srimi.depthwall.preview`, adds `-preview.1` to the version name, and displays **DepthWall Preview** in the launcher and wallpaper picker. It is non-debuggable but signed with the local development/debug certificate. It cannot update the stable app and does not share its private data.

```sh
./gradlew assemblePreview testPreviewUnitTest lintPreview
```

The output is `app/build/outputs/apk/preview/app-preview.apk`. Preserve the preview signing identity securely if later builds must update this preview; a different machine's debug keystore may have a different certificate. Never publish keystores or signing credentials.

Before publication, check the actual APK's signature, package/version, non-debuggable flag, ZIP alignment and embedded source revision, and record the preview test/lint results. Upload the clearly named preview APK, public verification evidence and checksums to a draft targeting that exact commit. Download the uploaded assets and verify their hashes before publishing with **Pre-release** enabled; keep the stable release as latest.

Preview release notes must explain the separate installation, development certificate, lack of automatic data migration and remaining phone-test limitations. The stable `Signed Android release` workflow and `verify_release.py` retain their production certificate checks; do not weaken them to accept a preview APK.
