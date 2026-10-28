# Changelog

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
