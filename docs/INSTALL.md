# Install DepthWall

1. Copy the signed `DepthWall-1.0.0.apk` to the OnePlus.
2. Open it from Files and allow installation from that source when Android asks.
3. Open DepthWall. Browse **Explore**, **Super**, **Depth Studio**, or **Library**.

## A first wallpaper

- **Still:** Explore → choose an image → adjust crop → Home, Lock or Both → Set wallpaper.
- **Live:** Super → choose a DepthWall original → Preview unlock → Preview & apply → finish in Android's wallpaper chooser.
- **Depth:** Depth Studio → choose a scene → select an optional clock → adjust clock position and parallax → Preview & apply.
- **Your photo:** use the import icon → Depth + clock → Edit mask. Erase the background while retaining the subject, then save the depth wallpaper. Transparent PNG/WebP foreground layers can also be imported.

The app's clock is separate from crDroid's native lock-screen clock. Leave the app clock off if you prefer the ROM clock. No-root support does not replace SystemUI's clock or guarantee Xiaomi's Always-On Display transitions.

## Original Xiaomi / HyperOS packs

1. Open **Super → Xiaomi / HyperOS**.
2. Use **Get original pack** to visit the port author's linked release. Earth and Mars also have the exact older linuxct release links.
3. After downloading, return to **Inspect / install downloaded APK**.
4. Review archive checks. If required, enable **Allow from this source**, return and press Install again. Android's installer asks for final confirmation.
5. The installed pack appears automatically with its exposed scene list. Each **Preview & apply** button opens Android's picker for that scene. **Locations** appears only when the installed version provides public settings; the inspected modern OUXyl releases do not.

Original ports are separate applications. A pack might install successfully but still fail to render on a particular crDroid build. DepthWall's original scene remains available for each theme. Signature conflicts are reported; the app does not uninstall or silently replace an existing port.

## Offline content and downloads

All 48 bundled static images, 12 prepared depth scenes and seven native scenes work offline. NASA/Wikimedia searches need internet. Downloads default to unmetered connections; change this in Library → Settings if required. Export writes to `Pictures/DepthWall`. Clearing thumbnail cache preserves saved wallpapers.

## Build from source

Use JDK 17, Android SDK platform 37.0 and build-tools 37.0.0. Set `ANDROID_HOME` or create an untracked `local.properties` pointing to your SDK.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
./gradlew connectedDebugAndroidTest
```

For your own signing identity:

```sh
python3 tools/create_personal_key.py
./gradlew assembleRelease
```

The release appears in `app/build/outputs/apk/release/`. Keep `private/depthwall-release.jks` and `keystore.properties` together in a secure backup. Do not commit them. A new key cannot update an APK signed with the old key.

## Physical-phone validation later

Connect the phone with USB debugging, confirm the model/codename and crDroid build, then install the signed APK. Run `tools/device_report.sh SERIAL OUTPUT_DIRECTORY` for a read-only diagnostic snapshot. See the compatibility report for the remaining device acceptance checks.
