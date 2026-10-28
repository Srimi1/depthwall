# Original port binary inspection

Inspected on 28 October 2026 using Android build-tools 37.0.0 (`aapt2`, `apksigner`), `apkanalyzer`, and LLVM ELF inspection. APKs were obtained from the exact source URLs in `PackManager`. All eight archives pass APK signature verification. The APKs and their artwork are not distributed with DepthWall.

## Package matrix

| Release | Package | Min / target API | Native ABIs | Public scenes | Exported configuration activities |
|---|---|---|---|---:|---|
| ALPHA-2.6.561-11211547-ogl-64-fix1 | `com.miui.miwallpaper.earth` | 26 / 26 | arm64-v8a, armeabi-v7a | 5 | None |
| linuxct-2.6.260-08131844-211107 | `com.miui.miwallpaper.earth` | 26 / 29 | armeabi-v7a | 1 | com.miui.miwallpaper.earth.MainActivity |
| ALPHA-2.6.561-11211547-ogl-64-fix1 | `com.miui.miwallpaper.mars` | 26 / 26 | arm64-v8a, armeabi-v7a | 5 | None |
| linuxct-2.6.260-08131855-211107 | `com.miui.miwallpaper.mars` | 26 / 29 | armeabi-v7a | 1 | com.miui.miwallpaper.mars.MainActivity |
| ALPHA-2.6.561-11211547-ogl-64-fix1 | `com.miui.miwallpaper.saturn` | 26 / 26 | arm64-v8a, armeabi-v7a | 1 | None |
| ALPHA-2.6.563-08071126-ogl-fix1 | `com.miui.miwallpaper.moon` | 28 / 28 | arm64-v8a, armeabi-v7a | 5 | None |
| ALPHA-2.6.561-11211547-ogl-64-fix1 | `com.miui.miwallpaper.snowmountain` | 26 / 29 | arm64-v8a, armeabi-v7a | 1 | None |
| ALPHA-2.7.561-11211547-vulkan-fix1 | `com.miui.miwallpaper.geometry` | 26 / 26 | arm64-v8a, armeabi-v7a | 1 | None |

Modern OUXyl releases use certificate SHA-256 `4f1ddf3c18f970903983f20126180032bd1021f643a773d8708685631733610c`. The named linuxct releases use `04d4b1ef6598760fc745ace65c7084e38cd9d74447bfea3b14af53ddd75adacc`. Earth and Mars retain the same package IDs across these authors, so their different signing identities prevent an in-place update. DepthWall reports this conflict instead of replacing a package.

Modern packs expose locations as separate public wallpaper services. They do not expose public configuration activities in these exact APKs. Their private MainActivity/UnityPlayerActivity components are not launched by DepthWall. The older linuxct packages expose their MainActivity configuration screen, but their 32-bit-only native libraries prevent installation on the tested ARM64-only emulator.

## Native requirements

The modern packages include ARM64 and ARMv7 libraries. Earth, Mars, Saturn, Snow Mountain and Geometry bundle Unity/IL2CPP; Moon bundles Filament. ELF dependencies include bundled helper libraries and Android system libraries such as EGL, GLES, libc, libandroid and liblog. No extra manifest `uses-library` dependency was found. Requested privileged permissions are listed in the machine-readable report; inspection does not imply Android grants them.

Several ARM64 libraries have 4 KB ELF load-segment alignment; others use 64 KB. The tested emulator has a 4 KB page size. Compatibility with a 16 KB kernel is not established. Record the phone kernel page size and supported ABIs before making a device-specific claim.

[Full hashes, signers, services, permissions and ELF dependencies](port-inspection.json) are recorded for reproducible comparison. Runtime results are in [the compatibility report](COMPATIBILITY.md).

## Exposed modern scenes

### Earth

- Croatia Cape Kamenjak — `com.miui.miwallpaper.earth.superwallpaper.EarthSuperWallpaper`
- Guangxi, China Longji Terraced Fields — `com.miui.miwallpaper.earth.superwallpaper.EarthSuperWallpaper1`
- Greenland Ilulissat — `com.miui.miwallpaper.earth.superwallpaper.EarthSuperWallpaper2`
- Greece Navagio Beach — `com.miui.miwallpaper.earth.superwallpaper.EarthSuperWallpaper3`
- Italy Dolomites — `com.miui.miwallpaper.earth.superwallpaper.EarthSuperWallpaper4`

### Mars

- Nilosyrtis Mensae Region — `com.miui.miwallpaper.mars.superwallpaper.MarsSuperWallpaper`
- Gale Crater — `com.miui.miwallpaper.mars.superwallpaper.MarsSuperWallpaper1`
- Crater in Mawrth Vallis — `com.miui.miwallpaper.mars.superwallpaper.MarsSuperWallpaper2`
- Pahrump Hills — `com.miui.miwallpaper.mars.superwallpaper.MarsSuperWallpaper3`
- Bahram Vallis — `com.miui.miwallpaper.mars.superwallpaper.MarsSuperWallpaper4`

### Saturn

- Faraway rings — `com.miui.miwallpaper.saturn.superwallpaper.SaturnSuperWallpaper`

### Moon

- The Apennines — `com.miui.miwallpaper.moon.superwallpaper.MoonSuperWallpaper`
- Moon Palace — `com.miui.miwallpaper.moon.superwallpaper.MoonSuperWallpaper1`
- The far side of the Moon — `com.miui.miwallpaper.moon.superwallpaper.MoonSuperWallpaper2`
- Chang'e 5 — `com.miui.miwallpaper.moon.superwallpaper.MoonSuperWallpaper3`
- Tranquility Base — `com.miui.miwallpaper.moon.superwallpaper.MoonSuperWallpaper4`

### Snowmountain

- Snowy peaks — `com.miui.miwallpaper.snowmountain.superwallpaper.SnowmountainSuperWallpaper`

### Geometry

- Geometry — `com.miui.miwallpaper.geometry.superwallpaper.GeometrySuperWallpaper`

