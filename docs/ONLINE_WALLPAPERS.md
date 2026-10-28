# Sources, downloads and phone settings

DepthWall 1.1 adds paginated online collections without an app-imposed download count, account or payment. Every saved image retains its source page and available credits. Network services can still rate-limit requests or remove images. Storage and per-file resource limits apply: 64 MiB for online images, 100 megapixels for decoded source dimensions, and 2 MiB for imported HTML pages.

## Collections

| Collection | Integration | Download |
| --- | --- | --- |
| NASA | Public NASA Image and Video Library API | Largest supported image within resource limits; older thumbnail-only results resolve through NASA's asset API |
| ESA, ISRO, JAXA, CSA | Clearly labeled agency-related searches on Wikimedia Commons | Original JPEG, PNG or WebP, with CC/public-domain license metadata |
| Wikimedia | Public Commons search API | Original image, separate lightweight preview and source attribution |
| Wallhaven | Public API, SFW results only, no key required | Original image, creator terms on the source page |
| Other websites | User-pasted HTTPS page or image URL | Detected original/download links, JSON-LD, OpenGraph or image metadata |

NASA usage guidelines and third-party credits still apply. Commons license restrictions remain attached. Wallhaven and other websites being accessible does not grant a license; inspect the source terms. Wallpaper Abyss (Alpha Coders), ESA, ESO, Hubble and Webb are also available as browser shortcuts. There is no promise of an official API integration for those shortcuts or of universal access to every website. Login pages, anti-bot pages, paid downloads, unsupported formats and scripts that hide image links can require browser download followed by **Import photo**.

The **Load more** button follows provider pagination. A 4K filter can leave one page empty while further pages remain available. Switching provider, search or quality cancels the visible request and discards stale results. Failed additional pages preserve the existing grid and can be retried. The 4K source badge requires both known dimensions: at least 3840 on the long edge and 2160 on the short edge. A landscape 4K image may have much less detail after a tall phone crop.

Website requests require public HTTPS destinations and validate DNS and every redirect. Local/private addresses and non-image download responses are rejected. Image previews use the same guarded networking client. The app does not send photos to a server for depth processing.

## OnePlus 7T Pro McLaren

The default phone profile is **1440 × 3120** (19.5:9), including when Android currently renders at a lower resolution. Choose **Current screen** for another display.

- **Download original** saves the source bytes in the private offline library.
- **Original** export copies static source bytes to Pictures/DepthWall, preserving their resolution and format. It does not apply the preview crop. Layered scenes export a flattened image.
- **Phone optimized** export applies the preview crop at the selected display size.
- **4K portrait** export uses up to **2160 × 4680** for the 7T Pro aspect ratio. Other screen ratios fit inside those bounds. Small source crops are never enlarged. It is an output target, not generated detail.

The five bundled car/theme originals have 2160 × 4680 dimensions. They are original illustrations with papaya/black racing, carbon geometry and space-blue designs, without automotive logos. Reproduce them with `python3 tools/generate_extra_art.py` (Pillow and NumPy).

Papaya, Space blue and AMOLED monochrome change DepthWall's interface palette. Android icons, system theme colours, AOD and the ROM's own lock-screen clock remain controlled by Android/crDroid.

## Depth from Settings

Open the Settings icon from any tab, or **Settings** in Android's DepthWall live wallpaper picker. Set the default depth mode, parallax strength, clock style and clock position. Newly opened previews use these defaults. **Save & update active depth** also updates confirmed active DepthWall depth scenes without replacing another wallpaper or a native planet scene.

A real foreground-over-clock effect needs a prepared layered scene or a cutout created with **Edit mask** / **Import layer**. The app's clock is rendered inside the wallpaper. Settings cannot place a subject in front of the ROM's native lock-screen clock or grant restricted AOD permissions.

## Upgrade and validation

Room schema 2 adds source width/height through an explicit migration that retains existing downloads and favourites. Source/parser, paging, size, redirect and crop logic have automated tests; settings persistence, database migration and export behavior also have Android instrumentation tests. Run:

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest
```

Compilation uses JDK 17 and Android SDK **37.0** via the minor-API compileSdk DSL. Physical OnePlus testing remains necessary for actual visual crop, motion, lock-screen behavior and battery use. A debug APK is signed with a development key and cannot update a release signed with a different personal key.
