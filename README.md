<p align="center"><img src="branding/depthwall-logo.png" width="160" alt="DepthWall orbital D logo"></p>
<h1 align="center">DepthWall</h1>
<p align="center">A little space. A lot of depth.</p>

A personal, no-root Android wallpaper app with AMOLED blacks and papaya-orange accents. Designed for the OnePlus 7T Pro McLaren (`hotdog`) and crDroid on Android 17. **Physical-phone validation is pending; emulator results are recorded separately.**

## Inside

- **Explore:** 48 original static illustrations, search, category/colour filters, favourites, crop positioning, home/lock application and image export.
- **Super:** seven native OpenGL worlds — Earth, Mars, Saturn, Moon, Snow Mountain, Geometry and Sun — with adjustable performance.
- **Depth Studio:** 12 prepared layered scenes, transparent foreground import, a manual erase/restore mask editor, parallax and optional digital, minimal or analogue clocks.
- **Library:** offline downloads, local imports, saved favourites, settings and optional hourly, six-hourly or daily static rotation.
- Optional NASA and Wikimedia searches retain image credits and source links. No ads, accounts, subscriptions, analytics or hosted backend.

### Original Super Wallpaper ports

The six Xiaomi/HyperOS families are **separate third-party applications**. DepthWall links their sources, inspects locally selected APKs, detects installed wallpaper services, and opens public configuration activities and Android's wallpaper picker. The exact linuxct Earth and Mars releases are linked as legacy options.

Original pack APKs are not bundled. All eight referenced binaries were inspected; the six modern packages install on the Android 17 emulator and expose 18 scenes. During the v1.0.1 cold-process retest, all 18 showed actual home-screen imagery at 5 and 15 seconds with the exact service bound; 17 showed imagery in preview by 40 seconds, while Bahram Vallis remained black in both preview samples. **This is not a stable 18/18 pass:** full logs contained four native crashes across Croatia, Pahrump Hills and Bahram Vallis, with home imagery visible after recovery. Empty crash-buffer captures missed those events.

The retest used a neutral wallpaper and stopped port processes before each scene, plus host-backed guest ANGLE with a nonconformant GLES testing override. A five-scene warm-switch follow-up also showed imagery, but Longji and Bahram each produced two native failures during switching and recovery. Neither run establishes stable switching across all scenes or phone support. The v1.0.1 build passes 13 unit and 14 device tests (one destructive fixture skipped), and the signed APK installs as an update. Native idle rendering and lifecycle measurements are in the [performance report](docs/PERFORMANCE.md). See the [per-scene report](docs/COMPATIBILITY.md), [crash analysis](docs/port-errors.json), and [graphics reproduction notes](docs/AOD_AND_EMULATOR.md).

The two ARMv7-only legacy releases cannot install on the ARM64-only emulator. The seven **DepthWall original** scenes are independently implemented, stylised alternatives. Use **Use DepthWall original** on a pack card if its original port is slow, black or unreliable; quality and battery controls apply to these native scenes. Sun has no verified original port in the linked collection.

## Install and build

Download the signed APK from [Releases](https://github.com/Srimi1/depthwall/releases). See [installation and first use](docs/INSTALL.md), [compatibility and remaining checks](docs/COMPATIBILITY.md), [architecture](docs/ARCHITECTURE.md), and [privacy](docs/PRIVACY.md).

Build with JDK 17 and Android SDK platform **37.0**:

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
./gradlew connectedDebugAndroidTest
```

For a personally signed release, run `python3 tools/create_personal_key.py`, then `./gradlew assembleRelease`. The key and credentials stay outside Git. Preserve them to sign future updates. Dependency versions, Gradle wrapper, source assets and artwork-generation code are checked in.

## Artwork and identity

![Bundled artwork contact sheet](branding/catalog-contact-sheet.jpg)

The [logo master](branding/depthwall-logo.png), Android adaptive/monochrome launcher icons, and [artwork generator](tools/generate_art.py) are included. Wallpaper artwork is original generative illustration. The logo's generation brief is recorded in [branding/PROMPT.md](branding/PROMPT.md). No Xiaomi, OnePlus or McLaren branding is used in the app identity.

DepthWall's optional clock is drawn inside its own wallpaper. The native crDroid clock remains controlled by the ROM. Wallpaper AOD requires a restricted Android permission that current crDroid retains; this normally installed no-root APK cannot enable it. See [AOD support](docs/AOD_AND_EMULATOR.md).
