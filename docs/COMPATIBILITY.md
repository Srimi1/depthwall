# Compatibility and verification

Assessment date: **28 October 2026**. This is an emulator-tested personal preview, not final acceptance on crDroid.

The requested phone is the OnePlus 7T Pro McLaren 4G (`hotdog`), nominally 1440 × 3120. It was not connected. The user explicitly chose emulator verification now and phone testing later. The exact crDroid build, its 32-bit support, live-wallpaper destinations and AOD interfaces remain unknown.

## Environment

| Item | Verified environment |
|---|---|
| Host | Apple M2, macOS, JDK 17 |
| Android tools | SDK platform `android-37.0`, build-tools 37.0.0, emulator 37.2.12 |
| Runtime | Google APIs ARM64 emulator, Android 17, API 37 |
| Display | 1080 × 2400, 60 Hz; app uses active display dimensions |
| Graphics | SwiftShader software rendering with host CPU acceleration |
| Application | `dev.srimi.depthwall`, minimum API 31, target/compile API 37 |
| Build | Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21 |

AGP warns that its officially tested maximum SDK is 36.1. API 37 builds and emulator execution succeed with the installed 37.0 platform. API 31–36 runtime coverage is not established by this report.

The older AVD creation tool wrote an incorrect API target and an undersized data partition. The task-specific emulator configuration was corrected before testing. These emulator setup failures were not app failures.

## Verified application paths

- Release APK installation and launch on API 37.
- Automated Compose navigation, local search, full-screen preview and settings access.
- 48 static assets, 12 background/foreground pairs and seven native scene entries exist. All 48 static file hashes are distinct. The bundled contact sheet was visually reviewed.
- OpenGL shader compilation, GL error checks, nonblank output and distinct rendered pixels for Earth, Mars, Saturn, Moon, Snow Mountain, Geometry, Sun and layered depth with a digital clock.
- Crop bounds/aspect calculations, hidden rendering policy, battery/thermal policy and display refresh cap.
- Local PNG import, invalid image rejection, opaque foreground rejection, MediaStore export and actual home-wallpaper ID change.
- APK inspector reads package identity, certificate and file digest, and rejects an unrelated package.
- Preview staging preserves separate home/lock configurations. Actual Android picker cancellation was also checked: previewing Mars and cancelling retained the applied Earth engine.
- Android's system picker offered Home, Lock, and Both. Applying Earth to Both created a live wallpaper with `mWhich=3` and the DepthWall service component.
- Hidden engine counter stayed at 29 frames across repeated snapshots while another app covered the wallpaper; visibility was false and renderer error was `none`.
- NASA search and a 0.5 MB Blue Marble download succeeded through the app. Credits, source URL, favourite state and the downloaded Library entry were retained.
- Prepared depth preview with a digital clock was visually inspected. A manual mask stroke was saved as a foreground; the resulting custom layered wallpaper appeared under Library → Imports.
- Dark theme and preview controls were visually reviewed; contrast and selected-chip colours were corrected.

The automated suite comprises five local unit tests and seven device tests. Build, lint and test summaries are delivered with the release evidence. Static analysis and Android's installer validate the signed application package separately from runtime tests.

## Original Super Wallpaper ports: explicit pending results

The [author's collection index](https://telegra.ph/Download-08-27-11) links six OUXyl package families. The [author's announcement](https://t.me/s/appports/19) describes 18 scenes. That count is an author statement, not a result reproduced here.

| Family / release | Source route in app | Binary inspection | API 37 installation / scenes |
|---|---|---|---|
| Earth — OUXyl ALPHA 2.6.561-11211547-ogl-64-fix1 | Author-linked APKMirror release | Not acquired | Unverified; all exposed locations pending |
| Mars — OUXyl ALPHA 2.6.561-11211547-ogl-64-fix1 | Author-linked APKMirror release | Not acquired | Unverified; all exposed locations pending |
| Saturn — OUXyl ALPHA 2.6.561-11211547-ogl-64-fix1 | Author-linked APKMirror release | Not acquired | Unverified; all exposed variants pending |
| Moon — OUXyl ALPHA 2.6.563-08071126-ogl-fix1 | Author-linked APKMirror release | Not acquired | Unverified; all exposed locations pending |
| Snow Mountain — OUXyl ALPHA 2.6.561-11211547-ogl-64-fix1 | Author-linked APKMirror release | Not acquired | Unverified; all exposed scenes pending |
| Geometry — OUXyl ALPHA 2.7.561-11211547-vulkan-fix1 | Author-linked APKMirror release | Not acquired | Unverified; all exposed variants pending |
| Earth — linuxct 2.6.260-08131844-211107 | Exact legacy release link | Not acquired | Unverified |
| Mars — linuxct 2.6.260-08131855-211107 | Exact legacy release link | Not acquired | Unverified |
| Sun | No verified original port identified | Not applicable | DepthWall original only |

The download pages returned HTTP 403 to the local retrieval client. Telegram links exposed a group/application route, not a directly retrievable APK. No original APK hashes, signatures, SDKs, ELF requirements, dependencies, exported components or runtime outcomes are invented. No location list is fabricated. **The plan's original-pack binary-inspection and every-variant acceptance criteria remain incomplete.**

DepthWall provides source links and a local-file inspector so legally obtained packs can be assessed later. It compares SDK/ABI compatibility, installed signer conflicts and version downgrades, then delegates installation to Android. Installed services are discovered through PackageManager; settings are opened only when the activity is exported, enabled and not protected by a permission. Passing these archive checks is not source-authenticity verification.

To record an acquired APK without executing it:

```sh
python3 tools/inspect_apk.py /path/to/pack.apk artifacts/ports/earth
```

Before describing a port as supported, record its SHA-256, signer(s), package/version, min/target SDK, native ABIs and ELF page-size needs, wallpaper service components, exported settings, external dependencies, each exposed scene, and actual installation/rendering results on Android 17 and the phone.

## Physical-phone acceptance still required

1. Record exact model, `hotdog` codename, crDroid build, Android API, supported ABIs, active resolution and refresh rate.
2. Install the release APK. Inspect every static/depth asset and native scene at 1440 × 3120 and the phone's active resolution.
3. Verify separate home/lock choices, unlock transitions, screen off/on, launcher changes, reboot, process recreation, time/timezone/theme changes, and battery saver.
4. Exercise transparent foreground picking, erase/restore/undo, all clock styles, crop extremes, offline use after cache clearing, interrupted downloads, insufficient space, invalid APKs, signer conflicts and unavailable settings activities.
5. Verify approximate WorkManager rotation at 1/6/24 hours, persisted configuration, favourites filtering, battery constraints and pausing when live wallpapers are active. Real elapsed schedule behaviour has not been established here.
6. Compare a static baseline and each quality profile at fixed brightness/radio conditions. Measure frame pacing, PSS, thermal state and energy over repeated runs. Confirm hidden CPU/sensor activity stops. Emulator software rendering is unsuitable for phone battery or frame-rate claims.
7. Complete the original-pack matrix above with every actually exposed location and variant. Keep failures visible and retain the native alternative.

Use `tools/device_report.sh SERIAL OUTPUT_DIRECTORY` to collect a read-only snapshot. No root modules, ROM flashing, guaranteed HyperOS AOD transitions or native SystemUI-clock layering are part of this build. Phone thermal, battery and frame-pacing measurements are **not yet available**.
