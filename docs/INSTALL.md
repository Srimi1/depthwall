# Install DepthWall

These instructions target **v1.0.1**. Installation and upgrade from v1.0.0 were verified on the API 37 emulator using the same signing key. The phone remains untested; see the [compatibility report](COMPATIBILITY.md).

1. Copy the signed `DepthWall-1.0.1.apk` to the OnePlus.
2. Open it from Files and allow installation from that source when Android asks.
3. Open DepthWall. Browse **Explore**, **Super**, **Depth Studio**, or **Library**.

## A first wallpaper

- **Still:** Explore → choose an image → adjust crop → Home, Lock or Both → Set wallpaper.
- **Live:** Super → choose a DepthWall original → Preview unlock → Preview & apply → finish in Android's wallpaper chooser.
- **Depth:** Depth Studio → choose a scene → select an optional clock → adjust clock position and parallax → Preview & apply.
- **Your photo:** use the import icon → Depth + clock → Edit mask. Erase the background while retaining the subject, then save the depth wallpaper. Transparent PNG/WebP foreground layers can also be imported.

The app's clock is separate from crDroid's native lock-screen clock. Leave the app clock off if you prefer the ROM clock. Wallpaper AOD is unavailable to this normally installed APK: Android's ambient-wallpaper permission is restricted, and current crDroid retains that restriction. Library → Settings provides access to your ROM's display settings for its own AOD and clock options. See [AOD details](AOD_AND_EMULATOR.md).

## Original Xiaomi / HyperOS packs

1. Open **Super → Xiaomi / HyperOS**.
2. Use **Get original pack** to visit the port author's linked release. Earth and Mars also have the exact older linuxct release links.
3. After downloading, return to **Inspect / install downloaded APK**.
4. Review archive checks. If required, enable **Allow from this source**, return and press Install again. Android's installer asks for final confirmation.
5. The installed pack appears automatically with its exposed scene list. Each **Preview & apply** button opens Android's picker for that scene. **Locations** appears only when the installed version provides public settings; the inspected modern OUXyl releases do not.

Original ports are separate applications. A pack might install successfully but still fail to render or crash on a particular crDroid build. If an original is heavy, black or unreliable, choose **Use DepthWall original** on its pack card, then preview and apply that native scene. Battery, Balanced and High Quality settings control DepthWall originals, not the rendering inside another app. Signature conflicts are reported; the app does not uninstall or silently replace an existing port.

The v1.0.1 emulator retest displayed all 18 original scenes on the home screen under a cold-process diagnostic fixture, but Croatia, Pahrump Hills and Bahram Vallis still produced native crashes. Bahram's preview remained black at 20 and 40 seconds. A five-scene warm-switch follow-up displayed all five scenes, but Longji and Bahram each produced two native failures during switching and recovery. Do not treat the diagnostic emulator graphics override or process resets as a phone-side fix. [Detailed outcomes](COMPATIBILITY.md)

## Offline content and downloads

All 48 bundled static images, 12 prepared depth scenes and seven native scenes work offline. NASA/Wikimedia searches need internet. Downloads default to unmetered connections; change this in Library → Settings if required. Export writes to `Pictures/DepthWall`. Clearing thumbnail cache preserves saved wallpapers.

## Build from source

Use JDK 17, Android SDK platform 37.0 and build-tools 37.0.0. Set `ANDROID_HOME` or create an untracked `local.properties` pointing to your SDK.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
./gradlew connectedDebugAndroidTest
```

The v1.0.1 suite passes 13 local unit tests and 14 device tests; the destructive port-reset fixture is skipped outside explicit port runs. Signed installation and same-key upgrade were checked; v1.0.0 results are retained separately as a baseline. For original-port diagnostics on the measured Apple Silicon setup, use [the emulator guide](AOD_AND_EMULATOR.md). Its launcher has booted with the requested `exposeES32ForTesting` debug property without a manual `setprop`; the override is explicitly nonconformant and unsuitable for phone performance claims.

For your own signing identity:

```sh
python3 tools/create_personal_key.py
./gradlew assembleRelease
```

The release appears in `app/build/outputs/apk/release/`. Keep `private/depthwall-release.jks` and `keystore.properties` together in a secure backup. Do not commit them. A new key cannot update an APK signed with the old key.

## Physical-phone validation later

Connect the phone with USB debugging, confirm the model/codename and crDroid build, then install the signed APK. Run `tools/device_report.sh SERIAL OUTPUT_DIRECTORY` for a read-only diagnostic snapshot. See the compatibility report for the remaining device acceptance checks.
