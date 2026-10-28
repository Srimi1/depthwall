# Native rendering performance

Measured on **28 October 2026** using the Android 17/API 37 ARM64 emulator on Apple M2. Initial before/after observations are retained below, followed by a check of the **final signed v1.0.1 APK**. They do not certify physical OnePlus performance or original HyperOS packs.

## Observed idle and lifecycle behavior

Both captures were of the actually applied **DepthWall Earth scene, Balanced profile**, on the same emulator and graphics setup. An attempted Cobalt depth-wallpaper application had not taken effect; the engine dump continued to identify `scene=earth`. Consequently, this is an Earth comparison, not a Cobalt or layered-depth benchmark. The old dump also contains a retired depth-preview engine; its counter is excluded.

| Observation | Before optimization | Initial updated candidate |
|---|---|---|
| Settled, visible Earth over approximately 15 seconds | 264 → 716: **452 frame-counter increments** | 99 → 99: **0 increments** |
| Screen off over approximately 10 seconds | Not captured in this comparison | 99 → 99; `visible=false`, `sensors=false` |
| Wake, unlock and return to Home | Not captured in this comparison | Counter reached 208; `visible=true`, `sensors=true`, `error=none` |

The updated visible capture's monotonic timestamps span **15.251 seconds**. The baseline 15-second and hidden 10-second intervals are the test's nominal wait durations; their text dumps do not contain equivalent timing markers. The wake capture establishes that rendering resumed, not a measured transition frame rate or latency.

These are app-level frame-counter increments, not compositor-presented frames. Zero counter growth in this stationary, settled sample demonstrates that the app stopped its idle draw loop. It does not establish zero CPU use or power consumption: visible parallax sensors remained registered and can request frames after movement. Clock updates, edits, offsets, unlock transitions and surface events can also request redraws. Screen-off samples show the sensors stopped, and waking restored them.

The [machine-readable observations](native-performance.json) retain the selected engine lines, counters, timings and evidence-file hashes. Raw captures are in the local `performance-1.0.1/baseline` and `performance-1.0.1/updated` evidence folders.

## Final signed APK recheck

APK SHA-256: `ce7a11d6dbc8e326f09a263734fb06d43babec0faca5583eb395138dd6de0454`. With the emulator lock screen enabled, a real sleep → wake → swipe unlock delivered the protected `USER_PRESENT` event. Earth reached `progress=1.0` and `unlocked=true`. The final visible counter stayed **238 → 238 over 15.308 seconds**. Screen-off counters stayed **238 → 238 over a nominal 10 seconds**, with sensors stopped. A second real swipe unlock resumed rendering to 386 frames and delivered a second unlock event, then settled again without a renderer error.

This check caught and fixed an intermediate receiver bug: `RECEIVER_NOT_EXPORTED` blocked the unlock event sent by SystemUI's separate UID, leaving the home scene in orbit. A dedicated exported receiver accepts only Android's protected unlock action; no per-frame lock-state polling was added. The final values above supersede the intermediate failed unlock sample. [Android receiver guidance](https://developer.android.com/develop/background-work/background-tasks/broadcasts)

## What changed

- Coil's image memory cache is capped at **32 MiB**. Grid thumbnails request **480 × 720** decoding instead of loading full-size originals for each card. This bounds that cache, not the app's total heap or GPU allocations.
- Image decoding reads bounds first, rejects sources above 100 megapixels, and uses power-of-two sampling toward a default maximum edge of 4096 pixels. Layer textures use the render dimensions and the device's maximum texture size, with a 4096-pixel upper bound.
- Native previews use the quality/power policy and stop when hidden, inactive or non-interactive. Settled layers and terrain redraw on relevant events rather than maintaining an idle animation timer. Preview transitions remain explicit.
- The wallpaper service uses applied-scene change listeners, cached device state and display-change callbacks rather than reading those values every frame. It removes duplicate scheduled frame callbacks. Hidden, screen-off and surface-destroyed paths stop rendering and sensors; graphics resources are released when their surface or engine is destroyed.

These behaviors are implemented in [the image loader](../app/src/main/java/dev/srimi/depthwall/DepthWallApp.kt), [thumbnail requests](../app/src/main/java/dev/srimi/depthwall/ui/DepthApp.kt), [image decoder](../app/src/main/java/dev/srimi/depthwall/data/Repository.kt), [preview](../app/src/main/java/dev/srimi/depthwall/wallpaper/SceneView.kt), and [wallpaper service](../app/src/main/java/dev/srimi/depthwall/wallpaper/DepthWallpaperService.kt). They affect DepthWall originals, not the separately installed third-party ports.

The native [render policy](../app/src/main/java/dev/srimi/depthwall/data/Models.kt) requests these budgets; they are policy targets, not measured delivered frame rates:

| Profile/state | Transition target | Continuous idle target | Internal render-width cap |
|---|---|---|---|
| Balanced | 60 fps | 15 fps when the scene has continuous motion | 960 px |
| Battery | 30 fps | No idle animation | 720 px |
| High Quality | Display refresh rate, bounded to 30–90 fps | 30 fps when the scene has continuous motion | 1440 px |
| Battery saver or moderate-or-higher thermal pressure | 15 fps | No idle animation | 720 px |

Layered depth is event-driven. Earth, Mars and Moon become event-driven after their surface transition settles; their orbit phases can animate. An enabled wallpaper clock can request a minute update. Parallax motion redraws are throttled. The other native scenes retain their budgeted motion when permitted by the profile.

## Reproduce the counter check

1. Install the candidate being assessed and record its version, device fingerprint and graphics setup. On the emulator, use the [documented graphics profile](AOD_AND_EMULATOR.md); its testing override is nonconformant.
2. Manually choose **Super → DepthWall originals → Earth**, select **Balanced**, leave the clock off and use the default scene configuration. Finish **Apply** in Android's picker. Return to Home and allow the unlock transition to settle.
3. Confirm the active component and the actual service dump before naming the scene under test. Do not assume that the chosen card became the applied wallpaper. If several engines are present, compare the same visible, non-preview Earth engine and process across both samples.

```sh
mkdir -p /tmp/depthwall-perf-check
adb -s emulator-5554 shell dumpsys wallpaper > /tmp/depthwall-perf-check/wallpaper.txt
adb -s emulator-5554 shell dumpsys activity service dev.srimi.depthwall/.wallpaper.DepthWallpaperService > /tmp/depthwall-perf-check/visible-0.txt
sleep 15
adb -s emulator-5554 shell dumpsys activity service dev.srimi.depthwall/.wallpaper.DepthWallpaperService > /tmp/depthwall-perf-check/visible-1.txt
```

Keep Home stationary during the interval. Check `scene=earth`, `quality=Balanced`, `surface=true`, `visible=true` and the process ID in both files. Record elapsed time separately and calculate the counter difference only if the engine/process has not restarted. Then manually turn the screen off, collect two dumps ten seconds apart, and verify `visible=false`, `sensors=false` and an unchanged counter. Wake and unlock, return to Home, and verify resumed rendering with no renderer error.

## Limits and remaining checks

Memory dumps were collected, but the baseline process retained earlier preview history while the updated process had restarted. They are **not comparable memory trials**. No PSS reduction or memory-saving percentage is claimed. Repeat both versions from equivalent clean process states with the same preview history before comparing memory.

The emulator's host GPU and test override do not model the phone's Adreno GPU or panel power. No battery-life, heat, CPU-usage, frame-pacing or phone-performance improvement has been measured. A final candidate recheck must verify successful application of the requested scene, idle behavior, motion/clock updates, screen off/on, preview cancellation, and lifecycle recovery before promoting these preliminary measurements to final-release evidence.
