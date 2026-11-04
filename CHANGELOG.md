# Changelog

## 1.5.1 (2026-11-03)

Installs as a normal update over 1.5.0. If you use the AOD module, rebuild it from the 1.5.1 APK and flash it over the old one.

### Fixed

- **Apps that detect root flagged the phone after the AOD module was flashed.** The 1.5.0 module set crDroid's "wallpapers on AOD" flag with an overlay on the Android framework. Android loads framework overlays into every app before Magisk's DenyList can hide anything, so an app such as DigiYatra could see a file from the Magisk module and reported the phone as rooted. crDroid already sets that flag itself, so the module now ships no overlay at all. For a ROM that leaves the flag off, `tools/build_aod_module.py --systemui-overlay` adds an overlay that only SystemUI loads. The builder refuses an overlay that targets the framework.
- **Updating the module switched wallpaper-on-AOD back on.** Magisk and KernelSU replace the module folder on update, which dropped the "turned on once" marker. The installer now carries it over.

### Added

- **StandBy starts by itself.** On the charger and on its side, StandBy now starts when the screen times out. Upright, the phone sleeps as usual, and standing it upright closes StandBy. The new **Start by itself on its side** setting is on by default. With the AOD module (or a one-time `adb shell pm grant` of `WRITE_SECURE_SETTINGS`), DepthWall makes StandBy Android's screen saver while charging; otherwise StandBy settings explains the one-time setup.
- **`tools/integrity_report.sh`**, a read-only phone report: boot and build state, Developer options, VPN, Magisk modules and DenyList, app versions and installers, and, for every process of DigiYatra, Claude, DigiLocker, Google Play services, the Play Store, zygote and any package you name, each system path that is really served from `/data` (a magic-mounted module file, whatever its name). It stops if adb cannot reach the phone and says so when root is not available.

### Changed

- The module builder ships no overlay unless you pass `--systemui-overlay`. `--no-overlay` is still accepted.
- The AOD checklist reads the value SystemUI actually uses: its override first, then the framework flag.

### Not changed by this release

- Apps that rely on Google Play Integrity's device verdict, such as the Claude app since late October 2026, still refuse a phone with an unlocked bootloader, with or without the module.

See [release notes](docs/releases/1.5.1.md).

## 1.5.0 (2026-11-02)

**New signing key.** The key behind 1.2.0–1.4.0 is not available. Uninstall DepthWall before installing 1.5.0; its library and settings are not carried over. Later versions update normally.

### Added

- **Super wallpapers on Always-On Display (rooted phones).** The new DepthWall AOD module for Magisk or KernelSU installs DepthWall as a system app, so crDroid grants it the ambient wallpaper permission, and turns on crDroid's wallpaper-on-AOD setting. On AOD, Earth, Mars, Moon, Saturn, Sun, Snow Mountain, Geometry and depth scenes show a small, softened frame on true black beneath the AOD clock; waking the phone zooms it back to the lock-screen orbit. One still frame per AOD entry, shifted a few pixels each time, with no motion, sensors or clock. **Settings → Always-on display** shows a readiness checklist and applies the AOD wallpaper. Phones without the module behave as before.
- **StandBy.** A full-screen clock on the charger. Choose DepthWall StandBy as the screen saver and set it to start while charging. In landscape, swipe sideways between a clock, your DepthWall planet and widgets (calendar, battery and charging power, next alarm, world clock); swipe up or down for another face. Portrait shows a nightstand clock. Auto night mode turns everything red and dims the screen in a dark room. A Quick Settings tile and **Save & try** open it on demand.
- **Eleven clock faces**, shared by StandBy and the wallpaper clock: Digital, Minimal, Analogue, Bold, Float, Solar, World, Flip, Word, Dot matrix and Gauge (a McLaren-style tachometer).

See [release notes](docs/releases/1.5.0.md).

## 1.4.0 (2026-10-31)

Installs as a normal update over 1.3.0.

### Fixed

- **Xiaomi/HyperOS Super Wallpaper packs that crash.** Earth, Mars, Saturn, Snow Mountain and Geometry are built on Unity 2019, which crashes on 64-bit Android 11 and later ("Using memoryadresses from more than 16GB of memory"). Moon uses a different engine and works. **Live → Original Xiaomi / HyperOS packs → Install pack as 32-bit** rebuilds a downloaded pack without its 64-bit engine, signs it on the phone with a key kept in the Android Keystore, and opens Android's installer. The original pack must be uninstalled first, and the phone must support 32-bit apps.

### Changed

- **Super-wallpaper-style live scenes.** Earth, Mars and Moon start as a whole planet in orbit and zoom to a curved horizon on unlock. Earth adds oceans, clouds, an atmosphere rim and city lights. Saturn gets lit rings and shadows, Snow gets moonlit peaks with fog and falling snow, and Geometry gets glowing 3D rings.

### Added

- **12 new wallpapers** in Best for your phone: Aurora Peaks, Papaya Dunes, Earthrise, Papaya Lattice, Nebula Ribbon, Star Spiral, Prism Light, Contour Lines, Papaya Ring, Alpine Lake, Comet and Abyssal Bloom.

See [release notes](docs/releases/1.4.0.md).

## 1.3.0 (2026-10-31)

Installs as a normal update over 1.2.0, so there's no uninstall this time.

### Changed

- **New design.**
  - Five tabs: Home, Live, Browse, Depth and Library.
  - A larger header that shows the app version.
  - Poster-style cards with the title on the artwork.
  - A refreshed AMOLED palette and typography.
- **Home** opens with a full-screen carousel of the new 7T Pro originals, followed by shelves for Best for your phone, Living worlds, Papaya Edition, space and depth.
- **Live** puts DepthWall's seven built-in live wallpapers first. Optional Xiaomi/HyperOS packs show only once installed, and the rest sit in a collapsed section instead of six "Not installed" cards.

See [release notes](docs/releases/1.3.0.md).

## 1.2.0 (2026-10-31)

**New signing key.** The key behind earlier releases was lost. Uninstall DepthWall before installing 1.2.0; its library and settings are not carried over. Later versions update normally.

### Fixed

- Black or frozen Super wallpapers after a restart. DepthWall's engine now retries a renderer that fails to start, rebuilds EGL after a lost surface instead of retrying the dead one, draws black for an unreadable depth layer instead of failing every frame, and releases EGL before Android releases the surface. Failures are bounded and visible in `dumpsys wallpaper` (`rendererAttempts`, `stalled`, `msSinceLastFrame`).
- Applying the same DepthWall scene again now restarts a stuck engine. Android only sends the running engine a reapply command in that case, and DepthWall rebuilds on it.
- Lock-only rotation could replace a live lock screen that shares the home wallpaper after a reboot.
- A settings file damaged by a power loss is replaced with defaults instead of crashing the process that hosts the wallpaper.
- The unlock transition re-reads the keyguard after boot, so Earth, Mars and Moon no longer stay in orbit view on the home screen.

### Added

- **Black or frozen? Restart it** on installed Xiaomi/HyperOS port cards. Re-applying a port cannot restart it, so DepthWall switches its screens to black (which unbinds it), offers a force stop for a stuck process, and opens the picker for a fresh start.
- A warning when Home and Lock use two scenes of the same port pack, which share one process and crash together at startup.
- **Best for your phone** in Explore: eleven new 2160 × 4680 papaya, carbon and space originals tuned for the OnePlus 7T Pro panel, alongside the best existing stills and the live Sun scene. New **Papaya Edition** filter.
- `tools/device_report.sh` captures WallpaperManagerService resets, service deaths, port process IDs and exit info. `tools/inspect_apk.py` records directBootAware, process and receivers.

### Validation

CI (`.github/workflows/android.yml`) compiles debug, preview and androidTest builds and runs unit tests and lint. Android instrumentation tests were compiled, not run. The restart fixes follow crDroid 17.0 `WallpaperManagerService` source; phone validation after a real reboot is still pending. See [After a restart](docs/COMPATIBILITY.md#after-a-restart) and [release notes](docs/releases/1.2.0.md).

## 1.1.0-preview.1 — 2026-10-29

- Added an optimized, non-debuggable **DepthWall Preview** build using a separate application ID and development signing certificate, so the new 1.1.0 features can be tested alongside stable DepthWall.
- Preview library and preferences are separate; the production signing requirements remain unchanged. Physical OnePlus/crDroid validation is still pending.

See [preview release notes](docs/releases/1.1.0-preview.1.md).

## 1.1.0 — 2026-10-28

### Added

- Paginated NASA and SFW Wallhaven searches, plus Wikimedia collections for ESA, ISRO, JAXA and CSA imagery, with source credits and links.
- A 4K source filter, space/car/theme/Alps searches, and import from accessible HTTPS wallpaper pages or direct image URLs.
- Five original 2160 × 4680 car and theme wallpapers, bringing the offline static collection to 53.
- Papaya, Space blue and AMOLED monochrome interface palettes.
- OnePlus 7T Pro McLaren and current-screen export profiles; original, phone-sized and 4K portrait output options.
- Persistent depth and app-clock defaults, active depth-scene updates, and direct access from Android's wallpaper Settings button.
- Native crDroid AOD setup and read-only compatibility observations for the OnePlus 7T Pro McLaren 4G (`hotdog`), with a copyable report.

### Fixed

- Online browsing can continue past the first page without an app download-count quota.
- NASA originals are preferred even when their size metadata is missing; thumbnails no longer determine download quality.
- Static Original exports retain source bytes. Optimized exports respect the preview crop without upscaling smaller images.
- Settings wait for persisted values before creating an editable draft.
- Wallpaper and preview rendering pause when their own display is off or ambient, including while another display remains interactive; wallpaper sensors follow the same policy.

### Maintenance and validation

- Added a non-destructive database migration for source dimensions and tests for source parsing, pagination, HTTPS/redirect handling, sizing, preferences, migration and exports.
- Selected Android SDK 37.0 explicitly in the build configuration; application version code is 3.
- Validation: optimized release assembly and shrinking passed; all 46 unit tests passed in both debug and release variants; each lint run had 0 errors / 41 warnings. Debug and Android test APKs also compiled. Signing, physical-device and upgrade validation remain pending.

Source availability, rate limits and image usage rights still apply. Website import depends on accessible image metadata. Depth effects use the app's own clock; Android/crDroid retains control of its native lock-screen clock and AOD. In-place upgrades require the existing signing key.

See [release notes](docs/releases/1.1.0.md) and [installation](docs/INSTALL.md).

## 1.0.1

Reduced native wallpaper rendering load and improved wallpaper apply/unlock handling. Historical installation, original-port findings and emulator limitations are recorded in [compatibility](docs/COMPATIBILITY.md) and [performance](docs/PERFORMANCE.md).
