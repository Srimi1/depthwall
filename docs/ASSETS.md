# Content and branding

DepthWall includes 48 static generative illustrations, 12 prepared layered landscapes (24 separate layer files), and seven scene thumbnails. The native scenes are procedural OpenGL compositions. None of these files is extracted from the Play Store reference app or a Xiaomi port.

`tools/generate_art.py` creates the catalogue and original artwork at 1440 × 3120. It requires Python, Pillow and NumPy. The resulting WebP assets and catalogue are committed so an ordinary Android build does not require Python.

The DepthWall logo was generated from the brief in `branding/PROMPT.md`. The master is preserved in `branding/depthwall-logo.png`. Android adaptive and monochrome launcher resources are under `app/src/main/res/`.

For downloaded NASA imagery, the application preserves the source title, creator/credit field, source page and NASA usage-guidelines notice. Users can open the source to check third-party credits. Wikimedia results are limited to records exposing a Creative Commons or public-domain licence; artist, licence name and file-description URL are preserved. Source records remain attached to offline downloads.

Original Super Wallpaper packages are downloaded and installed separately from their linked distributors. They keep their authors' identity, signatures, licences and rendering behaviour.
