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

Original pack APKs are not bundled. All eight referenced binaries were inspected; the six modern packages install on the Android 17 emulator and expose 18 scenes. Preview results are mixed (six visible, eleven black/loading, one crash); see the [per-scene report](docs/COMPATIBILITY.md). The two ARMv7-only legacy releases cannot install on the ARM64-only emulator. Physical-phone support is unverified. The seven **DepthWall original** scenes are independently implemented, stylised alternatives. Sun has no verified original port in the linked collection.

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

DepthWall's optional clock is drawn inside its own wallpaper. The native crDroid clock and ROM-specific AOD behaviour remain controlled by the ROM.
