# Install DepthWall

These instructions target **v1.2.0** (Android 12 or newer), with the OnePlus 7T Pro McLaren as the default phone profile. Download release APKs only from this repository's [Releases page](https://github.com/Srimi1/depthwall/releases), and read the selected release's signing and validation notes.

1. Download the APK attached to the release and copy it to your phone.
2. Open it from Files and allow installation from that source when Android asks.
3. Open DepthWall. Use **Home**, **Live**, **Browse**, **Depth** or **Library** at the bottom.

**Coming from 1.0.1 or 1.1.0:** 1.2.0 uses a new signing key because the earlier key was lost. Uninstall the old DepthWall first, then install 1.2.0. Its favourites, downloads and settings do not carry over. Updates after 1.2.0 install normally.

Android accepts an in-place update only when the APK uses the same signing identity as the installed version. A debug APK or a release signed with a new key cannot update an older personal release. If Android reports a signature mismatch, keep the installed app and obtain a build signed with its original key; uninstalling would remove its private library and preferences. The v1.1.0 database migration preserves existing favourites and saved image paths, but installation and upgrade still need validation on a physical phone.

Historical v1.0.1 installation and upgrade from v1.0.0 were verified on the API 37 emulator using the same signing key. Those results do not establish v1.1.0 phone compatibility; see the [compatibility report](COMPATIBILITY.md).

## Try the 1.1.0 preview

The [1.1.0-preview.1 prerelease](https://github.com/Srimi1/depthwall/releases/tag/v1.1.0-preview.1) provides **DepthWall-1.1.0-preview.1.apk**. Install it and open **DepthWall Preview**. It uses package ID `dev.srimi.depthwall.preview` and installs alongside the existing app, with a separate library and preferences. Keep stable DepthWall installed; its data is not automatically migrated to the preview.

This optimized, non-debuggable APK uses a development signing certificate. It is not a production update, and future preview updates need that same preview certificate. Physical OnePlus/crDroid testing remains pending. Read the [preview release notes](releases/1.1.0-preview.1.md) before installing.

## A first wallpaper

- **Still:** Home or Browse → choose an image → adjust crop → Home, Lock or Both → Set wallpaper.
- **Live:** Live → choose a scene → Preview unlock → Preview & apply → finish in Android's wallpaper chooser.
- **Depth:** Depth Studio → choose a scene → select an optional clock → adjust clock position and parallax → Preview & apply.
- **Your photo:** use the import icon → Depth + clock → Edit mask. Erase the background while retaining the subject, then save the depth wallpaper. Transparent PNG/WebP foreground layers can also be imported.

The app's clock is separate from crDroid's native lock-screen clock. Leave the app clock off if you prefer the ROM clock. Wallpaper AOD is unavailable to this normally installed APK: Android's ambient-wallpaper permission is restricted, and current crDroid retains that restriction. Open **Settings → Set up & check AOD → Open system AOD settings** for crDroid's clock and notification controls. The app targets the OnePlus 7T Pro McLaren **4G (`hotdog`)**, shows read-only observations, and refreshes them after returning from system settings. Missing preferences stay unknown; a stored On value does not verify physical AOD. Use **Copy compatibility report** to record the reported Android build and relevant settings. See [AOD details](AOD_AND_EMULATOR.md).

## Phone themes and depth settings

Open **Settings** from any tab, or the **Settings** button in Android's DepthWall live wallpaper picker.

- Choose **Papaya**, **Space blue**, or **AMOLED monochrome** for the app interface.
- Keep **OnePlus 7T Pro** for the 1440 × 3120 screen profile, or choose **Current screen** for another device.
- Select **Original**, **Phone optimized**, or **4K portrait** for exports to Pictures. Original preserves a static image's source bytes. The other options use the preview crop; 4K portrait targets up to 2160 × 4680 without enlarging smaller source crops.
- Set the default depth mode, parallax strength, clock style and clock position. **Save & update active depth** also changes the motion and app clock on an active DepthWall depth scene.

Foreground-over-clock depth requires a prepared layered scene or a foreground created with **Edit mask** / **Import layer**. These settings do not alter the ROM's native clock, system icons or AOD.

## Original Xiaomi / HyperOS packs

1. Open **Live → Original Xiaomi / HyperOS packs** and expand it.
2. Use **Get original pack** to visit the port author's linked release. Earth and Mars also have the exact older linuxct release links.
3. After downloading, return to **Inspect / install downloaded APK**.
4. Review archive checks. If required, enable **Allow from this source**, return and press Install again. Android's installer asks for final confirmation.
5. The installed pack appears automatically with its exposed scene list. Each **Preview & apply** button opens Android's picker for that scene. **Locations** appears only when the installed version provides public settings; the inspected modern OUXyl releases do not.

Original ports are separate applications. A pack might install successfully but still fail to render or crash on a particular crDroid build. If an original is heavy, black or unreliable, choose **Use DepthWall original** on its pack card, then preview and apply that native scene. Battery, Balanced and High Quality settings control DepthWall originals, not the rendering inside another app. Signature conflicts are reported; the app does not uninstall or silently replace an existing port.

The v1.0.1 emulator retest displayed all 18 original scenes on the home screen under a cold-process diagnostic fixture, but Croatia, Pahrump Hills and Bahram Vallis still produced native crashes. Bahram's preview remained black at 20 and 40 seconds. A five-scene warm-switch follow-up displayed all five scenes, but Longji and Bahram each produced two native failures during switching and recovery. Do not treat the diagnostic emulator graphics override or process resets as a phone-side fix. [Detailed outcomes](COMPATIBILITY.md)

### Black or frozen after a restart

Before you enter your PIN after a restart, Android shows its default wallpaper for every live wallpaper that is not direct-boot aware. That includes DepthWall and the original ports. Your choice comes back after unlocking.

If the wallpaper stays black or frozen after unlocking:

- **DepthWall originals:** open the scene and apply it again. Since 1.2.0 the engine rebuilds its graphics surface when the same wallpaper is applied again, and it retries automatically if the surface could not be created during start-up.
- **Original ports:** applying the same port again does nothing, because Android only sends a command to the engine that is already running. On the pack card, tap **Black or frozen? Restart it**. DepthWall switches the affected screens to plain black so Android unbinds the stuck engine. It then offers the port's App info page so you can force stop it, and opens the picker so you can apply the scene fresh. Choose **Home and lock**.
- Do not put two scenes from the same pack on Home and Lock separately. Both run in one process, and starting them together after a restart is the crash pattern recorded in [the compatibility report](COMPATIBILITY.md#after-a-restart).
- If a port keeps failing, use **Use DepthWall original** on its card.

If Android replaced the wallpaper with its default image after unlocking, the port crashed twice within ten seconds and Android removed it. Apply it again. `tools/device_report.sh` captures the log lines that show which case happened.

## Offline content and downloads

All **76 bundled static images**, 12 prepared depth scenes and seven native scenes work offline. Sixteen are 2160 × 4680 originals: two car illustrations, three papaya, carbon and space-blue themes, and the eleven designs in Explore's **Best for your phone** row (also under the **Papaya Edition** and **Space** filters).

Online browsing includes NASA originals, SFW Wallhaven, Wikimedia, and clearly labeled ESA / ISRO / JAXA / CSA collections via Wikimedia Commons. Use **Load more wallpapers** to continue a search. **4K sources only** requires known source dimensions of at least 3840 × 2160 in either orientation; a tall crop can contain less detail than its landscape source.

**Import from a website** accepts public HTTPS wallpaper pages or direct images. It detects image links and metadata, including original download links when available. Browser shortcuts include ESA, ESO, Hubble, Webb and Wallpaper Abyss. Sites that require browser access or do not expose usable images need a browser download followed by **Import photo**. This is not universal access to every wallpaper site.

DepthWall has no download-count quota, account or subscription. Source rate limits, image rights, storage and per-file resource limits still apply. Downloads default to unmetered connections; change this in Settings if required. **Download original** keeps the source file in the offline library. Export writes to `Pictures/DepthWall` using the selected export profile. Clearing thumbnail cache preserves saved wallpapers. See [sources, quality and website import](ONLINE_WALLPAPERS.md) for details.

## Build from source

Use JDK 17 and Android SDK platform **37.0**. Install the Android Build Tools requested by Gradle (the verified local build used 35.0.0), and accept the SDK licenses. Set `ANDROID_HOME` or create an untracked `local.properties` pointing to your SDK.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest
```

The v1.1.0 debug and release builds each passed **46 unit tests** and Android lint with **0 errors** (41 warnings). Release optimization and resource shrinking passed, and the unsigned release output passed alignment checks. The debug APK and Android test APK also compiled successfully. Android instrumentation tests, physical-phone behavior and the v1.1.0 upgrade path were not executed in that validation. The historical v1.0.1 suite passed 13 unit tests and 14 device tests, with the destructive port-reset fixture skipped outside explicit port runs; those earlier results are retained separately. For original-port diagnostics on the measured Apple Silicon setup, use [the emulator guide](AOD_AND_EMULATOR.md). Its launcher has booted with the requested `exposeES32ForTesting` debug property without a manual `setprop`; the override is explicitly nonconformant and unsuitable for phone performance claims.

For a first personal release only, create a signing identity. To update an existing installation, restore its existing keystore and signing configuration instead:

```sh
python3 tools/create_personal_key.py
./gradlew assembleRelease
```

For repeatable publication with the original key, follow the [release workflow instructions](RELEASING.md).

The debug APK appears in `app/build/outputs/apk/debug/app-debug.apk`. Release output appears in `app/build/outputs/apk/release/`; verify its signature before distribution. Keep `private/depthwall-release.jks` and `keystore.properties` together in a secure backup. Do not commit them. A new key cannot update an APK signed with the old key.

## Physical-phone validation later

Connect the phone with USB debugging, confirm the model/codename and crDroid build, then install the signed APK. Run `tools/device_report.sh SERIAL OUTPUT_DIRECTORY` for a read-only diagnostic snapshot. See the compatibility report for the remaining device acceptance checks.
