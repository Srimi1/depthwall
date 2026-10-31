import struct
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from check_signed_apk import central_directory_offset, check  # noqa: E402


def build(path, files):
    with zipfile.ZipFile(path, "w") as archive:
        for name, data in files:
            archive.writestr(name, data, compress_type=zipfile.ZIP_DEFLATED)


def insert_signing_block(source, target, block=b"APK Sig Block 42" * 8):
    """Simulate v2 signing: insert a block before the central directory and shift its offset."""
    data = source.read_bytes()
    offset = central_directory_offset(data)
    end = data.rfind(b"PK\x05\x06")
    eocd = bytearray(data[end:])
    eocd[16:20] = struct.pack("<I", offset + len(block))
    target.write_bytes(data[:offset] + block + data[offset:end] + bytes(eocd))


class SignedApkCheckTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.root = Path(self.directory.name)
        self.unsigned = self.root / "unsigned.apk"
        build(self.unsigned, [("AndroidManifest.xml", b"m" * 400), ("classes.dex", b"d" * 900)])

    def tearDown(self):
        self.directory.cleanup()

    def test_accepts_an_inserted_signing_block(self):
        signed = self.root / "signed.apk"
        insert_signing_block(self.unsigned, signed)
        check(self.unsigned, signed)

    def test_rejects_changed_contents(self):
        other = self.root / "other.apk"
        build(other, [("AndroidManifest.xml", b"m" * 400), ("classes.dex", b"x" * 900)])
        signed = self.root / "signed.apk"
        insert_signing_block(other, signed)
        with self.assertRaises(ValueError):
            check(self.unsigned, signed)

    def test_rejects_v1_signature_files(self):
        v1 = self.root / "v1.apk"
        build(v1, [("AndroidManifest.xml", b"m" * 400), ("classes.dex", b"d" * 900), ("META-INF/CERT.RSA", b"sig")])
        with self.assertRaises(ValueError):
            check(self.unsigned, v1)


if __name__ == "__main__":
    unittest.main()
