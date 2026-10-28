# AOD support and emulator graphics

Investigated on **28 October 2026**. These notes describe platform restrictions and a diagnostic emulator configuration. The [compatibility report](COMPATIBILITY.md) contains the per-scene results; the OnePlus 7T Pro McLaren has not been physically verified.

## Always-On Display is controlled by the ROM

DepthWall is a normally installed, no-root application. It cannot enable Android's ambient-wallpaper interface just by adding a permission request or an AOD toggle.

Android 17 declares `android.permission.AMBIENT_WALLPAPER` as `signature|preinstalled`. Current crDroid `17.0` retains that restriction. This is not a runtime permission that the user can grant through an app permission dialog. [Android 17 permission declaration](https://android.googlesource.com/platform/frameworks/base/+/android17-release/core/res/AndroidManifest.xml), [crDroid permission declaration](https://github.com/crdroidandroid/android_frameworks_base/blob/d858d5b90926053eb39cdaa327a7c98efa90a181/core/res/AndroidManifest.xml#L9159)

The `supportsAmbientMode` wallpaper XML attribute and `WallpaperService.Engine.onAmbientModeChanged` callback are hidden system APIs. They are absent from the public SDK 37 API surface used to build DepthWall. Moreover, crDroid checks the permission when binding an ambient-capable wallpaper: without it, a user-initiated selection throws `SecurityException`; other binding attempts fail. The exception is for watch devices. **Do not add `supportsAmbientMode=true` to the ordinary APK's wallpaper metadata.** [Framework callback](https://android.googlesource.com/platform/frameworks/base/+/android17-release/core/java/android/service/wallpaper/WallpaperService.java), [crDroid binding check](https://github.com/crdroidandroid/android_frameworks_base/blob/d858d5b90926053eb39cdaa327a7c98efa90a181/services/core/java/com/android/server/wallpaper/WallpaperManagerService.java#L3802)

crDroid's generic `config_dozeSupportsAodWallpaper` default is also false. A device overlay can change that value, but enabling the ROM's AOD clock does not grant a third-party wallpaper ambient access. The source branch is evidence about the framework, not proof of the user's installed build or device overlay. [crDroid configuration](https://github.com/crdroidandroid/android_frameworks_base/blob/d858d5b90926053eb39cdaa327a7c98efa90a181/core/res/res/values/config.xml#L3063)

### Behavior in the no-root app

- Native wallpaper animation and sensors stop when hidden or non-interactive. `PowerManager.isInteractive` is false during doze even when the panel shows ambient system content. A black or frozen wallpaper after screen-off is therefore not, by itself, a renderer failure. [Power-state semantics](https://developer.android.com/reference/android/os/PowerManager#isInteractive())
- AOD status must remain unavailable or unverified unless the actual ROM supplies a supported integration and it has been tested. Checking `AMBIENT_WALLPAPER` with `checkSelfPermission` can establish permission denial; a granted permission alone does not prove working AOD.
- A guarded `Settings.ACTION_DISPLAY_SETTINGS` shortcut can open the ROM's display options. It cannot enable HyperOS-specific AOD transitions. The app's optional clock is separate from SystemUI's AOD and lock-screen clocks.
- Original HyperOS ports remain separate applications. DepthWall opens their public wallpaper services; it cannot activate their private AOD services or grant their privileged permissions.

If a future ROM integration is verified, its ambient renderer should stop sensors and animation, draw a dim static frame on entry or an explicit framework redraw, and suspend completely while hidden. Drawing once on `ACTION_SCREEN_OFF` does not make Android show that frame on AOD. Do not remove the interactive-state guard or add a continuous background loop as an AOD workaround.

Physical acceptance requires the exact crDroid build, device AOD configuration, permission state, wallpaper callbacks and visible output during doze, followed by screen-off/on and power measurements. ROM modification, preinstallation and root modules are outside the current build.

## OnePlus 7T Pro McLaren 4G: current source findings

The requested device is the **4G/LTE `hotdog` family**, with a 1440 × 3120 AMOLED panel. The [Lineage device record](https://github.com/LineageOS/lineage_wiki/blob/7ddd0afd7c4d0a599cda6a4ddb693aa394c78145/_data/devices/hotdog.yml#L25) lists HD1910/HD1911/HD1913/HD1917 and networks through 4G LTE. Do not substitute the separate HD1925/`OnePlus7TProNR` [hotdogg product](https://github.com/crdroidandroid/android_device_oneplus_hotdogg/blob/5657d6f40eca20535d4fc4ea4fcc21879faddb75/lineage_hotdogg.mk#L18); that source is from crDroid's older 14.0 branch and is cited only to distinguish the device family. McLaren trim is not established by `Build.MODEL` alone.

The following are **source observations from 28 October 2026**, not results from the user's installed ROM:

| Observation | Immutable source |
| --- | --- |
| The crDroid 17.0 `hotdog` product inherits the SM8150 common device configuration. | [hotdog device.mk, line 63](https://github.com/crdroidandroid/android_device_oneplus_hotdog/blob/f9a6e7e9b1072570f324e36e6bea3b686ddf2f78/device.mk#L63) |
| The common framework overlay enables native always-on display availability. | [`config_dozeAlwaysOnDisplayAvailable=true`, line 11](https://github.com/crdroidandroid/android_device_oneplus_sm8150-common/blob/33cb3764334296b7ccc8bc52eb7e8842ec4c0a9c/overlay/FrameworksResTarget/res/values/config.xml#L11) |
| The same product includes `OplusDoze` and its device overlay. | [common.mk, line 143](https://github.com/crdroidandroid/android_device_oneplus_sm8150-common/blob/33cb3764334296b7ccc8bc52eb7e8842ec4c0a9c/common.mk#L143) |
| The common overlay leaves always-on display disabled by default, and enables ROM burn-in protection. | [default, line 49](https://github.com/crdroidandroid/android_device_oneplus_sm8150-common/blob/33cb3764334296b7ccc8bc52eb7e8842ec4c0a9c/overlay/OPlusFrameworksResCommon/res/values/config.xml#L49), [burn-in protection, line 111](https://github.com/crdroidandroid/android_device_oneplus_sm8150-common/blob/33cb3764334296b7ccc8bc52eb7e8842ec4c0a9c/overlay/OPlusFrameworksResCommon/res/values/config.xml#L111) |
| crDroid Settings exports `com.android.settings.display.AmbientDisplayAlwaysOnActivity`. This is a system settings page, not an app AOD implementation. | [Settings manifest, line 608](https://github.com/crdroidandroid/android_packages_apps_Settings/blob/abc2eeaaa08e58625334200479563893649b8218/AndroidManifest.xml#L608), [screen implementation](https://github.com/crdroidandroid/android_packages_apps_Settings/blob/abc2eeaaa08e58625334200479563893649b8218/src/com/android/settings/display/AmbientDisplayAlwaysOnPreferenceScreen.kt) |

These sources support directing the user to **crDroid's native clock/notification AOD**. They do not grant DepthWall ambient-wallpaper permission or establish that its image will be shown while the display is dozing. `AMBIENT_WALLPAPER`, the binding restriction and the generic wallpaper-AOD default described above remain in the same current framework revision. Both the [ambient callback](https://github.com/crdroidandroid/android_frameworks_base/blob/d858d5b90926053eb39cdaa327a7c98efa90a181/core/java/android/service/wallpaper/WallpaperService.java#L869) and [wallpaper XML ambient attribute](https://github.com/crdroidandroid/android_frameworks_base/blob/d858d5b90926053eb39cdaa327a7c98efa90a181/core/res/res/values/attrs.xml#L9319) remain hidden/system APIs. A supported ROM integration and phone validation are required for DepthWall imagery during AOD.

A guarded shortcut can try the exported system AOD activity, then fall back to display settings and general settings if the installed build differs. It must not call private activities, write secure settings, or keep the panel awake to imitate AOD. SDK 37 has no public `Settings.ACTION_AMBIENT_DISPLAY_SETTINGS` constant; `ACTION_DREAM_SETTINGS` is the screensaver page, not AOD.

### Interpreting a read-only phone report

The current framework marks `doze_always_on`, `suppress_doze`, `doze_always_on_auto_mode`, `doze_always_on_auto_time` and display inversion as readable settings. The [settings definitions](https://github.com/crdroidandroid/android_frameworks_base/blob/d858d5b90926053eb39cdaa327a7c98efa90a181/core/java/android/provider/Settings.java#L11714) still hide the doze constants from the public SDK. Missing or denied values must be reported as unknown. In particular, `doze_always_on_wallpaper_enabled` and `doze_on_charge` are not marked readable in that revision.

A stored `doze_always_on=1` means the preference is set, not that the panel is currently displaying AOD. The [framework availability check](https://github.com/crdroidandroid/android_frameworks_base/blob/d858d5b90926053eb39cdaa327a7c98efa90a181/core/java/android/hardware/display/AmbientDisplayConfiguration.java#L306) also considers the device's ambient implementation and color inversion; ROM schedules, bedtime suppression, power-saving policy and proximity/pocket behavior can affect the visible result. Likewise, a wallpaper permission check is only one prerequisite, not proof of a working ambient renderer.

### Acceptance on the actual 4G phone

1. Record the reported model/device, Android API, full crDroid build/fingerprint, and whether native AOD already works. Confirm this is `hotdog`, not the separate `hotdogg` model. Do not infer the installed overlay configuration from a source branch alone.
2. In crDroid's AOD settings, enable its native always-on clock if desired. Confirm it works with a simple system wallpaper first, including the chosen schedule. Keep the ROM's burn-in protection and brightness control enabled.
3. Apply a DepthWall original or depth wallpaper through Android's picker. Lock the phone and observe native AOD, then wake/unlock it and confirm the correct wallpaper resumes. Repeat screen-off/on and home/lock selections. The wallpaper's own clock must not be presented as SystemUI's AOD clock.
4. Check expected transitions with Battery Saver, bedtime/scheduled AOD, and covered/uncovered proximity sensor. A policy-driven pause should not be labeled an app rendering failure.
5. Record actual doze/display state and verify that DepthWall animation and motion sensors stop during screen-off/doze, then resume only when the relevant display is interactive. Do not force a continuous render loop, hold a wake lock, or use a fullscreen activity as a substitute for native AOD.
6. Record visible output, stability, wake recovery and battery/thermal observations on that exact build. Until these steps are run, native AOD coexistence is **unverified on the phone**, and DepthWall wallpaper AOD is **not enabled by this no-root APK**.

## Diagnostic graphics profile for Apple Silicon

The investigated setup uses Apple M2, Android Emulator **37.2.12**, and the Android 17/API 37 Google APIs ARM64 image. The initial SwiftShader path did not expose the texture capability required by the inspected Moon port: Filament aborted with `Texture format 94 not supported on this platform`. The enum in that exact APK identifies format 94 as `SRGB8_ALPHA8_ASTC_8x8`.

Enabling guest ANGLE over SwiftShader with ASTC emulation did not resolve that failure. The successful Moon and Earth Longji probes instead used guest ANGLE over the host Apple M2 Vulkan/MoltenVK path and an explicit ANGLE testing override. The original APKs were unchanged. Successful probes are not a substitute for the complete scene and lifecycle matrix in the compatibility report.

The launch requested `-VulkanAstcLdrEmulation`, and the log acknowledged the feature as disabled. However, the same run reported `ASTC LDR emulation mode: Gpu`, `ASTC emulation:on CPU decoding:off`, and GPU ASTC decompression pipelines. Therefore, **native ASTC passthrough has not been established**. Record this as a working host-backed graphics configuration with reported GPU decompression, not proof that the emulator bypasses ASTC emulation.

### Launch and verify

Use the repository's [diagnostic launcher](../tools/start_port_test_emulator.sh) from an Apple Silicon Mac with the existing `DepthWall_API37` AVD. Close that AVD before relaunching it; do not run two instances against the same data directory.

```sh
mkdir -p artifacts/emulator-graphics
sh tools/start_port_test_emulator.sh DepthWall_API37 -port 5554 -no-window > artifacts/emulator-graphics/emulator.log 2>&1
```

The command stays attached to the emulator. Use another terminal for the tests. The script defaults `ANDROID_HOME` to the standard macOS SDK location and requests:

```text
ANGLE_FEATURE_OVERRIDES_ENABLED=exposeES32ForTesting
ANGLE_FEATURE_OVERRIDES_DISABLED=supportsBlend*
-gpu host
-feature GuestAngle,Vulkan,-VulkanAstcLdrEmulation
-memory 4096
-no-snapshot -no-audio -no-boot-anim
```

Guest ANGLE requires Vulkan. The emulator forwards the ANGLE environment overrides as guest boot properties on supported images. Do not assume that an environment variable was consumed: inspect the actual guest and application logs. [Emulator boot-property implementation](https://android.googlesource.com/platform/external/qemu/+/emu-master-dev/android/android-emu/android/userspace-boot-properties.cpp)

```sh
adb -s emulator-5554 wait-for-device
adb -s emulator-5554 shell getprop sys.boot_completed
adb -s emulator-5554 shell getprop ro.hardware.egl
adb -s emulator-5554 shell getprop ro.boot.hardware.angle_feature_overrides_enabled
adb -s emulator-5554 shell getprop debug.angle.feature_overrides_enabled
```

Wait for `sys.boot_completed` to report `1`. Confirm `ro.hardware.egl` is `angle` and the emulator log selects **Apple M2 / MoltenVK**. Record both the requested ASTC flag and the effective emulation/decompression messages; they differed in the observed run. A missing debug property alone is not a failure if the boot override is effective. The confirmed diagnostic probe explicitly set the debug override before starting a fresh port process:

```sh
adb -s emulator-5554 shell setprop debug.angle.feature_overrides_enabled exposeES32ForTesting
```

Already running applications may retain their previous graphics configuration. Start each port from a fresh process when comparing profiles, record that condition, and separately test switching between scenes in a shared process. Keep screenshots, logcat and the selected wallpaper component together. A registered component, successful preview launch or absence of a crash dialog alone does not establish rendered imagery.

### Limits of this workaround

`exposeES32ForTesting` bypasses ANGLE's normal maximum GLES-version checks. ANGLE explicitly uses it for test coverage on configurations missing required features. It is a **nonconformant diagnostic override**, not evidence of full GLES 3.2 support or a production driver fix. A GLES version string after this override is insufficient to establish compatibility. [ANGLE implementation at the inspected revision](https://chromium.googlesource.com/angle/angle/+/8d8796935e4faeaf19e67ac0a5949e71a5252e7f/src/libANGLE/renderer/vulkan/vk_renderer.cpp#5054)

This profile is specific to the measured host, emulator and image. It does not patch the ports, cure every shared-process Unity lifecycle issue, change AOD permissions, or validate the OnePlus Adreno driver. It must not be used for phone frame-rate, thermal or battery claims. Recheck effective capabilities after emulator/image updates. Native DepthWall scene tests and all 18 original-port scene tests remain separate verification paths.
