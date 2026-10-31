# DepthWall architecture

## Native application

Kotlin + Compose, minimum API 31, compile/target API 37. The catalogue uses Room; preferences use DataStore. A small synchronous SceneStore handles Android's preview/apply boundary. No account, telemetry or application server is used.

`WallpaperItem` distinguishes static images, layered depth, native scenes and external services. The shipped catalogue has 64 static illustrations (`catalog.json`, `extra_catalog.json`, `device_catalog.json`), 12 layer pairs and seven native preview entries. Explore's "Best for your phone" row reads its ordered ids from `device_picks.json`, outside Room, because seeding never updates existing rows and the row mixes kinds. Original art is reproducible with the generators in `tools/`; these are generative illustrations, not photographs or copies of Xiaomi artwork.

Images use Android's document picker for import and MediaStore for export. Downloads are bounded, HTTPS-only and limited to NASA/Wikimedia image hosts. Saved files are retained independently of the thumbnail cache. Credits and source links persist with the image record.

## Applying wallpapers

Static images use a shared crop calculation in the editor and `WallpaperManager`. A home, lock or combined destination is explicit. Rotation uses a unique WorkManager periodic job, defaults off, requires adequate battery, uses local static images, and pauses when a live wallpaper is active on the requested destination.

For native scenes, UI changes edit a draft. Opening Android's picker stages the pending configuration and a snapshot of current wallpaper IDs/components without changing active home/lock records. `Engine.onApplyWallpaper(which)` supplies authoritative destinations when the picker supports that callback. Android's legacy LivePicker can return success without calling it, including on API 37. The fallback compares fresh system wallpaper IDs after `RESULT_OK` and confirms this app's component before committing the selected destination. It distinguishes a new lock application from Android moving the previous shared home wallpaper to lock. Cancellation and unconfirmed results preserve active configuration. Pending state is persisted across activity recreation. Android decides which destinations it supports.

The behavior follows [LivePicker's apply/result flow](https://android.googlesource.com/platform/packages/wallpapers/LivePicker/+/refs/heads/main/src/com/android/wallpaper/livepicker/LiveWallpaperPreview.java) and [framework wallpaper ID/migration handling](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/wallpaper/WallpaperManagerService.java). API 31–36 device behavior remains unverified.

## Rendering

Each WallpaperService engine owns a render thread and EGL context. A shared OpenGL painter renders planetary scenes or background → clock → foreground layers. Native planetary scenes are original stylised interpretations: Earth, Mars and Moon transition toward a terrain composition; Saturn has a ring plane; Sun uses procedural surface activity; Snow Mountain and Geometry have their own compositions.

Rendering uses a bounded offscreen framebuffer, then scales to the actual wallpaper surface:

| Profile | Maximum render width | Transition / idle target |
|---|---:|---:|
| Battery | 720 px | 30 fps / on demand |
| Balanced | 960 px | 60 fps / 15 fps |
| High Quality | 1440 px | up to 90 fps / 30 fps |
| Battery saver or thermal pressure | 720 px | 15 fps / on demand |

Frame rates are targets, not measured OnePlus results. The idle rate applies only to time-driven scenes: settled Earth/Mars/Moon terrain and depth layers render on demand, with motion events and minute ticks requesting new frames. In-app previews follow the same quality, battery and thermal policy and pause under the mask editor or credits dialog. Rendering stops when invisible or the screen is off. Motion listeners are removed when hidden, without a surface, in Battery mode, under battery saver or thermal pressure. Power/display state and GL uniform locations are cached instead of queried every frame. The framework-protected `USER_PRESENT` action has a dedicated exported receiver so SystemUI's separate UID can deliver unlock events; other receivers remain unexported. This prevents the cached lock state from leaving a home scene stuck in orbit after a real swipe unlock. Layer textures are decoded to the active render size; gallery requests are bounded and Coil has a 32 MiB memory cache. Full originals remain available for export. Clocks refresh by minute when otherwise idle. There are no wake locks or persistent foreground services.

The native clock belongs to SystemUI; DepthWall's clock is a separate optional wallpaper layer. The application does not request overlay, accessibility, root, device administration or broad storage permissions.

## Always-on display

The no-root app does not declare hidden ambient-wallpaper support. Android 17 and crDroid require the system-only `AMBIENT_WALLPAPER` permission for that flag, and reject ambient wallpaper binding without it (except watches). Screen-off rendering therefore stays stopped. The Settings screen offers crDroid's exported native AOD activity, with display/settings fallbacks and read-only preference observations. Both the wallpaper and preview gate drawing on their own display being `STATE_ON` as well as global interactivity; ambient/off display changes cancel future frames and stop wallpaper sensors. AOD requires a supported ROM integration and physical-device verification, not a timer drawing while the screen is off.

## Original Super Wallpaper ports

PackManager discovers wallpaper services and exported settings activities from installed packages. The inspected modern packages expose all 18 locations as separate public wallpaper services; DepthWall lists those services. Public settings are available only on versions that actually export a configuration activity. Private activities are not launched. Original packs retain their original engine and performance characteristics.

APK imports are statically inspected for identity, SDKs, ABI libraries, signing certificates, signing conflicts, version downgrades and declared components. Android's package installer performs final installation validation with visible user interaction. Passing archive checks is not a claim of source authenticity or runtime compatibility.

## Updating and rebuilding

The source and Gradle wrapper are versioned. The personal release key and passwords are excluded from Git. Preserve both the private keystore and its local configuration to install future APK updates without removing user data. No proprietary original pack APKs are committed or bundled.
