import json
import sys
import tempfile
import unittest
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import generate_device_art as art  # noqa: E402

ASSETS = Path(__file__).resolve().parents[1] / "app/src/main/assets"


class DeviceArtTest(unittest.TestCase):
    def test_render_is_deterministic_and_respects_panel_rules(self):
        for create in (art.rise, art.edge_glow, art.papaya_ring):
            first = np.asarray(art.render(create))
            second = np.asarray(art.render(create))
            self.assertTrue(np.array_equal(first, second))
            self.assertEqual(first.shape, (4680, 2160, 3))
            self.assertEqual(int(first[:art.TOP].max()), 0)
            luma = first @ np.array([.299, .587, .114])
            self.assertLess(luma[:int(4680 * .32)].mean(), 1.0)
            # Nothing between true black and the first visible step survives the AMOLED pass.
            self.assertFalse(((luma > 0) & (luma <= 3)).any())

    def test_written_catalog_matches_entries_and_picks_resolve(self):
        with tempfile.TemporaryDirectory() as directory:
            written = art.main(out=Path(directory) / "wallpapers", only={"device_rise"})
            self.assertEqual([entry["id"] for entry in written], ["device_rise"])
            thumb = Image.open(Path(directory) / "wallpapers/device_rise_thumb.webp")
            self.assertEqual(thumb.size, (360, 780))
        ids = [entry[0] for entry in art.ENTRIES]
        self.assertEqual(len(ids), len(set(ids)))
        known = set()
        for name in ("catalog.json", "extra_catalog.json", "device_catalog.json"):
            known |= {entry["id"] for entry in json.loads((ASSETS / name).read_text())}
        self.assertTrue(set(art.PICKS) <= known)
        self.assertEqual(json.loads((ASSETS / "device_picks.json").read_text()), art.PICKS)


if __name__ == "__main__":
    unittest.main()
