# AOD support and emulator graphics

Investigated on **28 October 2026**. These notes describe platform restrictions and a diagnostic emulator configuration. The [compatibility report](COMPATIBILITY.md) contains the per-scene results; the OnePlus 7T Pro McLaren has not been physically verified.

## Always-On Display is controlled by the ROM

DepthWall is a normally installed, no-root application. It cannot enable Android's ambient-wallpaper interface just by adding a permission request or an AOD toggle.

Android 17 declares `android.permission.AMBIENT_WALLPAPER` as `signature|preinstalled`. Current crDroid `17.0` retains that restriction. This is not a runtime permission that the user can grant through an app permission dialog. [Android 17 permission declaration](https://android.googlesource.com/platform/frameworks/base/+/android17-release/core/res/AndroidManifest.xml), [crDroid permission declaration](https://github.com/crdroidandroid/android_frameworks_base/blob/17.0/core/res/AndroidManifest.xml#L8062)

The `supportsAmbientMode` wallpaper XML attribute and `WallpaperService.Engine.onAmbientModeChanged` callback are hidden system APIs. They are absent from the public SDK 37 API surface used to build DepthWall. Moreover, crDroid checks the permission when binding an ambient-capable wallpaper: without it, a user-initiated selection throws `SecurityException`; other binding attempts fail. The exception is for watch devices. **Do not add `supportsAmbientMode=true` to the ordinary APK's wallpaper metadata.** [Framework callback](https://android.googlesource.com/platform/frameworks/base/+/android17-release/core/java/android/service/wallpaper/WallpaperService.java), [crDroid binding check](https://github.com/crdroidandroid/android_frameworks_base/blob/17.0/services/core/java/com/android/server/wallpaper/WallpaperManagerService.java#L3538)

crDroid's generic `config_dozeSupportsAodWallpaper` default is also false. A device overlay can change that value, but enabling the ROM's AOD clock does not grant a third-party wallpaper ambient access. The source branch is evidence about the framework, not proof of the user's installed build or device overlay. [crDroid configuration](https://github.com/crdroidandroid/android_frameworks_base/blob/17.0/core/res/res/values/config.xml#L2696)

### Behavior in the no-root app

- Native wallpaper animation and sensors stop when hidden or non-interactive. `PowerManager.isInteractive` is false during doze even when the panel shows ambient system content. A black or frozen wallpaper after screen-off is therefore not, by itself, a renderer failure. [Power-state semantics](https://developer.android.com/reference/android/os/PowerManager#isInteractive())
- AOD status must remain unavailable or unverified unless the actual ROM supplies a supported integration and it has been tested. Checking `AMBIENT_WALLPAPER` with `checkSelfPermission` can establish permission denial; a granted permission alone does not prove working AOD.
- A guarded `Settings.ACTION_DISPLAY_SETTINGS` shortcut can open the ROM's display options. It cannot enable HyperOS-specific AOD transitions. The app's optional clock is separate from SystemUI's AOD and lock-screen clocks.
- Original HyperOS ports remain separate applications. DepthWall opens their public wallpaper services; it cannot activate their private AOD services or grant their privileged permissions.

If a future ROM integration is verified, its ambient renderer should stop sensors and animation, draw a dim static frame on entry or an explicit framework redraw, and suspend completely while hidden. Drawing once on `ACTION_SCREEN_OFF` does not make Android show that frame on AOD. Do not remove the interactive-state guard or add a continuous background loop as an AOD workaround.

Physical acceptance requires the exact crDroid build, device AOD configuration, permission state, wallpaper callbacks and visible output during doze, followed by screen-off/on and power measurements. ROM modification, preinstallation and root modules are outside the current build.

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
