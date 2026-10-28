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

The automated suite comprises six local unit tests and eight device tests (all passed, none skipped). The final signed release was installed and launched separately. Lint completed with 0 errors and 31 warnings, principally dependency-update and Kotlin convenience suggestions. Build and test summaries are delivered with the release evidence. Static analysis and Android's installer validate the signed application package separately from runtime tests.

## Original Super Wallpaper ports: inspected and emulator tested

The [author's collection index](https://telegra.ph/Download-08-27-11) links six OUXyl families. All six modern APKs and both exact linuxct legacy APKs were acquired and inspected. Their signatures verify. The modern manifests and installed PackageManager services independently reproduce **18 exposed scenes**. Full package IDs, version/SDK requirements, ABIs, SHA-256 digests, certificates, exported components and ELF dependencies are in [the binary inspection report](ORIGINAL_PORTS.md) and [machine-readable inspection](port-inspection.json).

All six modern packages install on the API 37 emulator. Both named linuxct packages fail installation with `INSTALL_FAILED_NO_MATCHING_ABIS`: they contain ARMv7 libraries only, while this emulator supports ARM64 only. This does not establish the phone's ABI support. The authors use different certificates for the same Earth/Mars package IDs, so changing between their releases requires resolving the signing conflict. DepthWall reports conflicts and does not silently replace installed packs.

These exact modern releases expose variants through public wallpaper services, with **no exported configuration activity**. DepthWall lists those services and opens Android's preview; private Unity/MainActivity components are not invoked. The legacy releases have exported MainActivity settings but could not be runtime tested here.

### Every modern variant

| Family | Exposed scene | Emulator preview | Applied component recorded |
|---|---|---|---|
| Earth | Croatia Cape Kamenjak | Imagery visible | Yes |
| Earth | Guangxi, China Longji Terraced Fields | Black/loading after 30 seconds | Yes |
| Earth | Greenland Ilulissat | Black/loading after 30 seconds | Yes |
| Earth | Greece Navagio Beach | Black/loading after 30 seconds | Yes |
| Earth | Italy Dolomites | Imagery visible | Yes |
| Mars | Nilosyrtis Mensae Region | Imagery visible | Yes |
| Mars | Gale Crater | Black/loading after 30 seconds | Yes |
| Mars | Crater in Mawrth Vallis | Black/loading after 30 seconds | Yes |
| Mars | Pahrump Hills | Black/loading after 30 seconds | Yes |
| Mars | Bahram Vallis | Crash dialog | Not confirmed |
| Saturn | Faraway rings | Imagery visible | Yes |
| Moon | The Apennines | Black/loading after 30 seconds | Not confirmed |
| Moon | Moon Palace | Black/loading after 30 seconds | Not confirmed |
| Moon | The far side of the Moon | Black/loading after 30 seconds | Not confirmed |
| Moon | Chang'e 5 | Black/loading after 30 seconds | Yes |
| Moon | Tranquility Base | Black/loading after 30 seconds | Yes |
| Snowmountain | Snowy peaks | Imagery visible | Not confirmed |
| Geometry | Geometry | Imagery visible | Not confirmed |

Six previews showed actual scene imagery, eleven remained black/loading after 30 seconds, and Bahram Vallis showed a crash dialog. A registered wallpaper component alone does not count as successful rendering. Snow Mountain and Geometry rendered in preview, but the attempted home-selection flow could not be confirmed. These are bounded observations, not full lifecycle support claims. Unity native crashes and graphics configuration errors occurred during port testing; the software GPU environment differs substantially from the phone's Adreno GPU. No family is certified for crDroid by these emulator observations.

[Per-scene runtime records](port-runtime.json) retain the component identities and observed outcomes. Original APKs, extracted artwork and third-party screenshots are kept out of the repository/release. Sun has no verified original in this collection; its DepthWall scene is an original implementation.

The app's separate seven native alternatives remain available. Archive inspection compares SDK/ABI requirements, installed signer conflicts and downgrades before Android's installer is opened. Public settings must be exported, enabled and unprotected. Archive signature verification proves internal signing integrity, not endorsement or authorship.

To repeat static inspection:

```sh
python3 tools/inspect_apk.py /path/to/pack.apk artifacts/ports/earth
```

To run the optional integration test with these exact six modern packages already installed:

```sh
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.ports=true
```

## Physical-phone acceptance still required

1. Record exact model, `hotdog` codename, crDroid build, Android API, supported ABIs, active resolution and refresh rate.
2. Install the release APK. Inspect every static/depth asset and native scene at 1440 × 3120 and the phone's active resolution.
3. Verify separate home/lock choices, unlock transitions, screen off/on, launcher changes, reboot, process recreation, time/timezone/theme changes, and battery saver.
4. Exercise transparent foreground picking, erase/restore/undo, all clock styles, crop extremes, offline use after cache clearing, interrupted downloads, insufficient space, invalid APKs, signer conflicts and unavailable settings activities.
5. Verify approximate WorkManager rotation at 1/6/24 hours, persisted configuration, favourites filtering, battery constraints and pausing when live wallpapers are active. Real elapsed schedule behaviour has not been established here.
6. Compare a static baseline and each quality profile at fixed brightness/radio conditions. Measure frame pacing, PSS, thermal state and energy over repeated runs. Confirm hidden CPU/sensor activity stops. Emulator software rendering is unsuitable for phone battery or frame-rate claims.
7. Repeat the original-pack matrix above on the physical phone with every exposed location and variant. Keep failures visible and retain the native alternative.

Use `tools/device_report.sh SERIAL OUTPUT_DIRECTORY` to collect a read-only snapshot. No root modules, ROM flashing, guaranteed HyperOS AOD transitions or native SystemUI-clock layering are part of this build. Phone thermal, battery and frame-pacing measurements are **not yet available**.
