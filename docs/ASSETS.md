# Content and branding

DepthWall includes 64 static generative illustrations, 12 prepared layered landscapes (24 separate layer files), and seven scene thumbnails. The native scenes are procedural OpenGL compositions. None of these files is extracted from the Play Store reference app or a Xiaomi port.

Three generators create the artwork. They require Python, Pillow and NumPy. The resulting WebP assets and catalogues are committed so an ordinary Android build does not require Python.

| Generator | Output | Catalogue |
|---|---|---|
| `tools/generate_art.py` | 48 statics, 12 depth pairs and 7 scene previews at 1440 × 3120. Rewrites every file, so do not re-run it for a single change. | `catalog.json` |
| `tools/generate_extra_art.py` | Five car and theme originals at 2160 × 4680 | `extra_catalog.json` |
| `tools/generate_device_art.py` | Twenty-three "Best for your phone" originals at 2160 × 4680, plus the ordered picks list | `device_catalog.json`, `device_picks.json` |

The phone collection follows the OnePlus 7T Pro panel: luma at or below 3 is true black, the status bar band and clock zone stay dark, subjects sit in the 40 to 80% height band, fine detail stays off the curved outer 5% of each side, and woven patterns repeat no finer than 36 px so they do not shimmer under parallax. `python3 -m unittest tools/test_generate_device_art.py` checks those rules and that rendering is deterministic. Carbon weave is authored geometry, not a texture, and no design uses a marque, car silhouette, logo or brand name.

The DepthWall logo was generated from the brief in `branding/PROMPT.md`. The master is preserved in `branding/depthwall-logo.png`. Android adaptive and monochrome launcher resources are under `app/src/main/res/`.

For downloaded NASA imagery, the application preserves the source title, creator/credit field, source page and NASA usage-guidelines notice. Users can open the source to check third-party credits. Wikimedia results are limited to records exposing a Creative Commons or public-domain licence; artist, licence name and file-description URL are preserved. Source records remain attached to offline downloads.

Original Super Wallpaper packages are downloaded and installed separately from their linked distributors. They keep their authors' identity, signatures, licences and rendering behaviour.
