# Compatibility and verification

Assessment date: **28 October 2026**, **v1.0.1**. This is an emulator-tested personal preview, not final acceptance on crDroid.

The requested phone is the OnePlus 7T Pro McLaren 4G (`hotdog`), nominally 1440 × 3120. It was not connected. The user explicitly chose emulator verification now and phone testing later. The exact crDroid build, its 32-bit support, live-wallpaper destinations and AOD interfaces remain unknown.

## Environment

| Item | Verified environment |
|---|---|
| Host | Apple M2, macOS, JDK 17 |
| Android tools | SDK platform `android-37.0`, build-tools 37.0.0, emulator 37.2.12 |
| Runtime | Google APIs ARM64 emulator, Android 17, API 37 |
| Display | 1080 × 2400, 60 Hz; app uses active display dimensions |
| Current port-test graphics | Apple M2 host Vulkan/MoltenVK through guest ANGLE; nonconformant `exposeES32ForTesting` override |
| Prior v1.0.0 graphics | SwiftShader software rendering with host CPU acceleration |
| Application | `dev.srimi.depthwall`, minimum API 31, target/compile API 37 |
| Build | Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21 |

AGP warns that its officially tested maximum SDK is 36.1. API 37 builds and emulator execution succeed with the installed 37.0 platform. API 31–36 runtime coverage is not established by this report.

The older AVD creation tool wrote an incorrect API target and an undersized data partition. The task-specific emulator configuration was corrected before testing. These emulator setup failures were not app failures.

## v1.0.1 verification status

| Check | Current result |
|---|---|
| Local unit tests | 13 passed, 0 failures/errors |
| Original ports, cold fixture | All 18 home scenes visually present at 5 and 15 seconds with exact service binding; 17 previews visible by 40 seconds |
| Remaining cold-preview failure | Bahram Vallis black at 20 and 40 seconds |
| Known native crashes in cold logs | 4 SIGSEGV events across Croatia (1), Pahrump Hills (1), Bahram Vallis (2); visible home captures followed recovery |
| Five-scene warm-switch check | All five preview/home captures showed imagery; Longji and Bahram each produced a SIGSEGV followed by a SIGTRAP during recovery |
| Connected app tests | 14 passed; 1 opt-in destructive port-reset fixture skipped; 0 failures/errors |
| v1.0.1 signed APK installation and launch | Passed, including an in-place upgrade from the same-key v1.0.0 APK |
| Native rendering | Stationary Earth stops its idle frame loop; see [measured results](PERFORMANCE.md). No phone power/heat measurements |

The updated automated app checks cover gallery/navigation, image import/export/static application, all seven native worlds plus depth, orbit/blend/terrain rendering, texture reloads, exact original-pack hashes/signatures and all 18 exported services. New picker regression tests cover successful results without the newer engine callback, activity/store recreation, separate destinations, cancellation, shared-home migration to lock, and callback ordering. Build and lint passed (0 lint errors, 35 warnings).

A real signed-APK picker check applied Cobalt Valley to Both, Earth to Home while preserving Cobalt on Lock, then Mars to Lock while preserving Earth on Home. Cancelling a Saturn preview retained both selected scenes and wallpaper IDs. A separate lock-screen check visibly showed Mars while Earth remained assigned to Home. Final real swipe-unlock checks reached the native landscape, stopped idle draws, stopped sensors on screen-off, and resumed correctly; see the measured report. This fixes the v1.0.0 path that could show the correct depth preview but leave the active scene at its default: the stock API 37 LivePicker does not call `Engine.onApplyWallpaper`. The new fallback validates fresh wallpaper IDs and components after a successful picker result.

Visible imagery and successful component binding are separate from process stability. Empty crash-buffer captures did not prove an absence of crashes: the full logcat records contained native faults. The [crash analysis](port-errors.json) records exact log lines, timestamps, process IDs and restarts. The original APKs were not modified or re-signed.

The [diagnostic launcher](../tools/start_port_test_emulator.sh) successfully booted the emulator with `debug.angle.feature_overrides_enabled=exposeES32ForTesting` verified, without an extra manual `setprop`. The override bypasses normal GLES capability checks and does not establish GLES conformance. Although the launch requests ASTC emulation disabled, effective logs still report GPU ASTC decompression. See [AOD and emulator reproduction](AOD_AND_EMULATOR.md) for the exact configuration and its limits.

## Prior v1.0.0 application verification baseline

The following checks were completed for **v1.0.0**. They are retained as previous-version evidence, not claimed as a fresh v1.0.1 acceptance run.

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
- Wikimedia search and a public-domain artwork download succeeded in the signed v1.0.0 release. Artist, public-domain status and the original Commons page remained visible in Image credits.
- Prepared depth preview with a digital clock was visually inspected. A manual mask stroke was saved as a foreground; the resulting custom layered wallpaper appeared under Library → Imports.
- Dark theme and preview controls were visually reviewed; contrast and selected-chip colours were corrected.

The v1.0.0 automated suite comprised six local unit tests and eight device tests (all passed, none skipped). Its signed release was installed and launched separately. Its lint run completed with 0 errors and 31 warnings, principally dependency-update and Kotlin convenience suggestions. Fresh v1.0.1 results appear above.

## Original Super Wallpaper ports: inspected and emulator tested

The [author's collection index](https://telegra.ph/Download-08-27-11) links six OUXyl families. All six modern APKs and both exact linuxct legacy APKs were acquired and inspected. Their signatures verify. The modern manifests and installed PackageManager services independently reproduce **18 exposed scenes**. Full package IDs, version/SDK requirements, ABIs, SHA-256 digests, certificates, exported components and ELF dependencies are in [the binary inspection report](ORIGINAL_PORTS.md) and [machine-readable inspection](port-inspection.json).

All six modern packages install on the API 37 emulator. Both named linuxct packages fail installation with `INSTALL_FAILED_NO_MATCHING_ABIS`: they contain ARMv7 libraries only, while this emulator supports ARM64 only. This does not establish the phone's ABI support. The authors use different certificates for the same Earth/Mars package IDs, so changing between their releases requires resolving the signing conflict. DepthWall reports conflicts and does not silently replace installed packs.

These exact modern releases expose variants through public wallpaper services, with **no exported configuration activity**. DepthWall lists those services and opens Android's preview; private Unity/MainActivity components are not invoked. The legacy releases have exported MainActivity settings but could not be runtime tested here.

### Cold-process retest: every modern variant

Before **each** scene, the harness applied a neutral static wallpaper to both destinations and force-stopped all original packs and the picker. It then opened the requested public service, sampled preview at 20/40 seconds, applied to Home, confirmed the exact active home component, and sampled Home at 5/15 seconds. Every home capture was visually checked for actual imagery. These process resets are a diagnostic fixture, not an app-side repair or ordinary phone usage.

| Family | Exposed scene | Cold preview by 40 s | Home at 5/15 s; exact binding | Known cold native faults |
|---|---|---|---|---|
| Earth | Croatia Cape Kamenjak | Imagery visible | Imagery visible; yes | 1 SIGSEGV; recovered |
| Earth | Guangxi, China Longji Terraced Fields | Imagery visible | Imagery visible; yes | None captured in this window |
| Earth | Greenland Ilulissat | Imagery visible | Imagery visible; yes | None captured in this window |
| Earth | Greece Navagio Beach | Imagery visible | Imagery visible; yes | None captured in this window |
| Earth | Italy Dolomites | Imagery visible | Imagery visible; yes | None captured in this window |
| Mars | Nilosyrtis Mensae Region | Imagery visible | Imagery visible; yes | None captured in this window |
| Mars | Gale Crater | Imagery visible | Imagery visible; yes | None captured in this window |
| Mars | Crater in Mawrth Vallis | Imagery visible | Imagery visible; yes | None captured in this window |
| Mars | Pahrump Hills | Imagery visible | Imagery visible; yes | 1 SIGSEGV; recovered |
| Mars | Bahram Vallis | Black at 20/40 s | Imagery visible; yes | 2 SIGSEGV; recovered |
| Saturn | Faraway rings | Imagery visible | Imagery visible; yes | None captured in this window |
| Moon | The Apennines | Imagery visible | Imagery visible; yes | None captured in this window |
| Moon | Moon Palace | Imagery visible | Imagery visible; yes | None captured in this window |
| Moon | The far side of the Moon | Imagery visible | Imagery visible; yes | None captured in this window |
| Moon | Chang'e 5 | Imagery visible | Imagery visible; yes | None captured in this window |
| Moon | Tranquility Base | Imagery visible | Imagery visible; yes | None captured in this window |
| Snowmountain | Snowy peaks | Imagery visible | Imagery visible; yes | None captured in this window |
| Geometry | Geometry | Imagery visible | Imagery visible; yes | None captured in this window |

The graphics change removed the earlier broad black-output pattern, including Moon's unsupported-texture failure in the tested configuration. It did **not** establish stability for all ports: three scenes produced four native faults despite later visible home captures, and Bahram still had a black preview. The other 15 cold windows contained no captured fatal crashes; that is a bounded observation, not a long-term stability guarantee. These tests used a nonconformant emulator profile and do not establish crDroid support.

The cold failures followed Unity allocator errors and faulted at address `0x179`. The message about addresses spanning more than 16 GB concerns virtual-address regions; it does not prove that a port used 16 GB of physical RAM. The separately installed engine's allocator remains unresolved. DepthWall's own performance controls cannot repair it.

### Warm-switch follow-up

The follow-up reset to the neutral wallpaper only before its first scene, then preserved port processes and wallpaper state between switches. It tested these five scenes, in order:

| Scene | Preview and Home | Stability observation |
|---|---|---|
| Croatia Cape Kamenjak | Imagery visible | No target-package fatal crash captured in this window |
| Guangxi, China Longji Terraced Fields | Imagery visible | Null-pointer SIGSEGV, then restart SIGTRAP on duplicate Unity class registration |
| Nilosyrtis Mensae Region | Imagery visible | No target-package fatal crash captured in this window |
| Bahram Vallis | Imagery visible | Null-pointer SIGSEGV, then restart SIGTRAP on duplicate Unity class registration |
| The Apennines | Imagery visible | No target-package fatal crash captured in this window |

This five-scene check reproduced visible output after a fresh boot using the launcher alone. It does not prove stable switching across all 18 scenes. Both same-family switches, Croatia → Longji and Nilosyrtis → Bahram, produced native failures before later visible captures; those remain stability failures. Additional self-terminations and process restarts are recorded separately in the crash analysis; duplicate AndroidRuntime reports are not counted as extra native crashes.

[Current per-scene runtime records](port-runtime.json) retain exact components, capture hashes and reviewed outcomes. The [original SwiftShader observations](port-runtime-baseline.json) are preserved separately: six visible previews, eleven black/loading previews, and one crash-dialog observation. Original APKs, extracted artwork and third-party screenshots are kept out of the repository/release. Sun has no verified original in this collection; its DepthWall scene is an original implementation.

For a heavy, black or unreliable original, select **Use DepthWall original** on its pack card. Each theme has an independently implemented native alternative; DepthWall's quality/battery settings affect those scenes only. Archive inspection compares SDK/ABI requirements, installed signer conflicts and downgrades before Android's installer is opened. Public settings must be exported, enabled and unprotected. Archive signature verification proves internal signing integrity, not endorsement or authorship.

To repeat static inspection:

```sh
python3 tools/inspect_apk.py /path/to/pack.apk artifacts/ports/earth
```

To run the optional integration test with these exact six modern packages already installed:

```sh
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.ports=true
```

To collect the scene matrix with the matching debug and androidTest APKs installed:

```sh
python3 tools/test_original_ports.py --serial emulator-5554 --output /tmp/depthwall-ports-cold --mode cold
```

This emulator-only harness replaces both current wallpapers with a neutral fixture. Use a fresh output directory, visually review captures, and correlate full logcat with process restarts before assigning a stability result. `--mode warm` preserves state after the initial neutral fixture.

## After a restart

This section describes framework behaviour read from crDroid 17.0 `WallpaperManagerService` (commit `d858d5b90926053eb39cdaa327a7c98efa90a181`, identical to the 17.0 branch head when checked). None of it has been reproduced on the phone yet.

| Situation | What Android does | Log line |
|---|---|---|
| Live wallpaper is not direct-boot aware (DepthWall, all inspected ports) | Shows the default image until the first unlock, then binds the saved wallpaper again | `Wallpaper isn't direct boot aware; using fallback until unlocked` |
| Service dies twice with the second death handled within 10 s of the first (any reason except low memory) | Permanently replaces it with the default wallpaper | `Reverting to built-in wallpaper!` |
| Rebind does not connect within 10 s | Same permanent reset | `Wallpaper reconnect timed out for …` |
| Low-memory kills exhaust three 5 s retries | Same permanent reset | `Reverting to built-in wallpaper due to lmk!` |
| The same wallpaper is applied again | No new engine; the running engine receives `android.wallpaper.reapply` | none |

Consequences for DepthWall:

- A wallpaper that **stays selected but is black or frozen** is a running engine that is not drawing, not a reset. Before 1.2.0, DepthWall's engine never retried a failed renderer start and retried a lost surface forever on the same dead EGL objects. 1.2.0 retries creation with backoff, rebuilds EGL after a lost surface, draws a black placeholder for an unreadable depth layer, and rebuilds on `android.wallpaper.reapply`. `adb shell dumpsys wallpaper` prints `frames`, `msSinceLastFrame`, `rendererAttempts`, `stalled` and `error`.
- Re-applying a port cannot restart it. The **Restart** helper replaces the port with plain black on its screens, which unbinds it, offers a force stop for a cached process, and opens the picker for a fresh bind.
- The recorded port failures make this plausible: cold-start Unity allocator faults, and a SIGSEGV followed by a SIGTRAP when two scenes of one package start in the same process ([crash analysis](port-errors.json)). The emulator's cold-fault deaths were about 19 s apart, so they produced restart loops there rather than resets. Phone timing is unmeasured.
- A normal app cannot apply a live wallpaper without the user's confirmation (`SET_WALLPAPER_COMPONENT` is `signature|privileged`), so every restore needs one tap on **Set wallpaper**.
- Lock-only rotation used to treat a shared live lock screen as static and could replace it after boot. Rotation now uses the home wallpaper when the lock shares it.

To check on the phone: reboot, unlock, wait a minute, then run `tools/device_report.sh SERIAL OUTPUT_DIRECTORY`. `wallpaper-log.txt` and `wallpaper-events.txt` show resets and service deaths, `wallpaper-processes.txt` shows port process IDs (they should change after a Restart with force stop), and `wallpaper-engine.txt` holds DepthWall's engine state.

## Physical-phone acceptance still required

1. Record exact model, `hotdog` codename, crDroid build, Android API, supported ABIs, active resolution and refresh rate.
2. Install the release APK. Inspect every static/depth asset and native scene at 1440 × 3120 and the phone's active resolution.
3. Verify separate home/lock choices, unlock transitions, screen off/on, launcher changes, reboot, process recreation, time/timezone/theme changes, and battery saver.
4. Exercise transparent foreground picking, erase/restore/undo, all clock styles, crop extremes, offline use after cache clearing, interrupted downloads, insufficient space, invalid APKs, signer conflicts and unavailable settings activities.
5. Verify approximate WorkManager rotation at 1/6/24 hours, persisted configuration, favourites filtering, battery constraints and pausing when live wallpapers are active. Real elapsed schedule behaviour has not been established here.
6. Compare a static baseline and each quality profile at fixed brightness/radio conditions. Measure frame pacing, PSS, thermal state and energy over repeated runs. Confirm hidden CPU/sensor activity stops. This emulator's graphics configuration is unsuitable for phone battery or frame-rate claims.
7. Repeat the original-pack matrix above on the physical phone with every exposed location and variant. Keep failures visible and retain the native alternative.

Use `tools/device_report.sh SERIAL OUTPUT_DIRECTORY` to collect a read-only snapshot. Wallpaper AOD requires the restricted `signature|preinstalled` Android permission retained by current crDroid; a regular no-root installation cannot enable it, and adding ambient metadata without the permission can prevent wallpaper binding. See [AOD restrictions](AOD_AND_EMULATOR.md). No root modules, ROM flashing or native SystemUI-clock layering are part of this build. Phone thermal, battery and frame-pacing measurements are **not yet available**.
