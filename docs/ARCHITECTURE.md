# DepthWall architecture

## Native application

Kotlin + Compose, minimum API 31, compile/target API 37. The catalogue uses Room; preferences use DataStore. A small synchronous SceneStore handles Android's preview/apply boundary. No account, telemetry or application server is used.

`WallpaperItem` distinguishes static images, layered depth, native scenes and external services. The shipped catalogue has 48 static illustrations, 12 layer pairs and seven native preview entries. Original art is reproducible with `tools/generate_art.py`; these are generative illustrations, not photographs or copies of Xiaomi artwork.

Images use Android's document picker for import and MediaStore for export. Downloads are bounded, HTTPS-only and limited to NASA/Wikimedia image hosts. Saved files are retained independently of the thumbnail cache. Credits and source links persist with the image record.

## Applying wallpapers

Static images use a shared crop calculation in the editor and `WallpaperManager`. A home, lock or combined destination is explicit. Rotation uses a unique WorkManager periodic job, defaults off, requires adequate battery, uses local static images, and pauses when a live wallpaper is active on the requested destination.

For native scenes, UI changes edit a draft. Opening the Android live-wallpaper picker stages a pending configuration without changing the active home/lock records. On Android 36+, `Engine.onApplyWallpaper(which)` commits to the actual selected destination. API 31–35 use the picker's successful activity result for the home configuration. Cancelling never commits. Android decides which live-wallpaper destinations it supports.

## Rendering

Each WallpaperService engine owns a render thread and EGL context. A shared OpenGL painter renders planetary scenes or background → clock → foreground layers. Native planetary scenes are original stylised interpretations: Earth, Mars and Moon transition toward a terrain composition; Saturn has a ring plane; Sun uses procedural surface activity; Snow Mountain and Geometry have their own compositions.

Rendering uses a bounded offscreen framebuffer, then scales to the actual wallpaper surface:

| Profile | Maximum render width | Transition / idle target |
|---|---:|---:|
| Battery | 720 px | 30 fps / on demand |
| Balanced | 960 px | 60 fps / 15 fps |
| High Quality | 1440 px | up to 90 fps / 30 fps |
| Battery saver or thermal pressure | 720 px | 15 fps / on demand |

Frame rates are targets, not measured OnePlus results. Rendering stops when invisible or the screen is off. Motion listeners are removed when hidden, in Battery mode, under battery saver or thermal pressure. Clocks refresh by minute when otherwise idle. There are no wake locks or persistent foreground services.

The native clock belongs to SystemUI; DepthWall's clock is a separate optional wallpaper layer. The application does not request overlay, accessibility, root, device administration or broad storage permissions.

## Original Super Wallpaper ports

PackManager discovers wallpaper services and exported settings activities from installed packages. Original locations are selected in the port's own settings; DepthWall does not fabricate a list of internal variants or access private activities. Original packs retain their original engine and performance characteristics.

APK imports are statically inspected for identity, SDKs, ABI libraries, signing certificates, signing conflicts, version downgrades and declared components. Android's package installer performs final installation validation with visible user interaction. Passing archive checks is not a claim of source authenticity or runtime compatibility.

## Updating and rebuilding

The source and Gradle wrapper are versioned. The personal release key and passwords are excluded from Git. Preserve both the private keystore and its local configuration to install future APK updates without removing user data. No proprietary original pack APKs are committed or bundled.
