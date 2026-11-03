import struct
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_aod_module import (OVERLAY, OVERLAY_PATH, OVERLAY_TARGET, check_overlay_target,  # noqa: E402
                              overlay_target, package, signature_hash, signing_certificate)


def prefixed(data: bytes) -> bytes:
    return struct.pack("<I", len(data)) + data


def signed_apk(certificate: bytes, scheme: int = 0xF05368C0) -> bytes:
    """A minimal ZIP whose v2/v3 signing block carries one signer with [certificate]."""
    signed_data = prefixed(b"") + prefixed(prefixed(certificate))
    value = prefixed(prefixed(prefixed(signed_data)))
    pairs = struct.pack("<QI", len(value) + 4, scheme) + value
    size = len(pairs) + 24
    block = struct.pack("<Q", size) + pairs + struct.pack("<Q", size) + b"APK Sig Block 42"
    entries = b"local file records"
    directory = len(entries) + len(block)
    eocd = b"PK\x05\x06" + b"\0" * 12 + struct.pack("<I", directory) + b"\0\0"
    return entries + block + eocd


class SignatureTest(unittest.TestCase):
    def test_matches_java_arrays_hash_code(self):
        # Arrays.hashCode(new byte[]{1, 2, 3}) == 30817; signed bytes wrap like Java ints.
        self.assertEqual(signature_hash(bytes([1, 2, 3])), format(30817, "x"))
        self.assertEqual(signature_hash(bytes([0xFF])), format(30, "x"))
        self.assertEqual(len(signature_hash(bytes(range(256)) * 8)) <= 8, True)

    def test_reads_first_signer_certificate_from_v3_and_v2(self):
        certificate = b"\x30\x82DER certificate"
        self.assertEqual(signing_certificate(signed_apk(certificate)), certificate)
        self.assertEqual(signing_certificate(signed_apk(certificate, 0x7109871A)), certificate)

    def test_rejects_unsigned_archives(self):
        with self.assertRaises(ValueError):
            signing_certificate(b"PK\x05\x06" + b"\0" * 18)


class PackageTest(unittest.TestCase):
    def test_module_layout_versions_and_permissions(self):
        with tempfile.TemporaryDirectory() as directory:
            apk, overlay, out = (Path(directory, name) for name in ("app.apk", "overlay.apk", "module.zip"))
            apk.write_bytes(b"apk")
            overlay.write_bytes(b"overlay")
            package(apk, out, overlay, {"versionName": "1.5.0", "versionCode": "7", "name": "dev.srimi.depthwall.preview"}, "7861")
            with zipfile.ZipFile(out) as archive:
                names = set(archive.namelist())
                self.assertTrue({"module.prop", "customize.sh", "META-INF/com/google/android/update-binary",
                                 "system/app/DepthWall/DepthWall.apk", OVERLAY_PATH,
                                 "depthwall.sighash"} <= names)
                prop = archive.read("module.prop").decode()
                self.assertIn("version=v1.5.0", prop)
                self.assertIn("versionCode=7", prop)
                self.assertEqual(archive.read("depthwall.package"), b"dev.srimi.depthwall.preview\n")
                self.assertEqual(archive.getinfo("customize.sh").external_attr >> 16, 0o755)
                self.assertEqual(archive.getinfo("service.sh").external_attr >> 16, 0o755)
                self.assertEqual(archive.getinfo("system/app/DepthWall/DepthWall.apk").compress_type, zipfile.ZIP_STORED)

    def test_overlay_is_optional(self):
        with tempfile.TemporaryDirectory() as directory:
            apk, out = Path(directory, "app.apk"), Path(directory, "module.zip")
            apk.write_bytes(b"apk")
            package(apk, out, None, {"versionName": "1.5.0", "versionCode": "7", "name": "dev.srimi.depthwall"}, None)
            with zipfile.ZipFile(out) as archive:
                self.assertNotIn(OVERLAY_PATH, archive.namelist())
                self.assertNotIn("depthwall.sighash", archive.namelist())



class OverlayTargetTest(unittest.TestCase):
    def test_shipped_overlay_targets_systemui_not_the_framework(self):
        # Zygote preloads static overlays for "android" into every app, DenyList'd apps included.
        self.assertEqual(OVERLAY_TARGET, "com.android.systemui")
        self.assertEqual(overlay_target(OVERLAY / "AndroidManifest.xml"), OVERLAY_TARGET)
        check_overlay_target(OVERLAY / "AndroidManifest.xml")

    def test_shipped_overlay_sets_the_systemui_override(self):
        values = (OVERLAY / "res/values/config.xml").read_text()
        self.assertIn('<integer name="config_dozeSupportsAodWallpaperOverride">1</integer>', values)
        self.assertNotIn("config_dozeSupportsAodWallpaper\"", values)

    def test_refuses_a_framework_overlay(self):
        with tempfile.TemporaryDirectory() as directory:
            manifest = Path(directory, "AndroidManifest.xml")
            manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="x">'
                                '<overlay android:targetPackage="android" android:isStatic="true" /></manifest>')
            with self.assertRaises(SystemExit):
                check_overlay_target(manifest)

    def test_rejects_a_manifest_without_an_overlay_target(self):
        with tempfile.TemporaryDirectory() as directory:
            manifest = Path(directory, "AndroidManifest.xml")
            manifest.write_text('<manifest package="x"><application /></manifest>')
            with self.assertRaises(SystemExit):
                overlay_target(manifest)

    def test_old_framework_overlay_name_is_gone(self):
        self.assertNotEqual(OVERLAY_PATH, "system/product/overlay/DepthWallAodOverlay.apk")
        self.assertTrue(OVERLAY_PATH.startswith("system/product/overlay/"))


if __name__ == "__main__":
    unittest.main()
