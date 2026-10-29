import unittest

from verify_preview import PACKAGE_NAME, preview_certificate, preview_metadata
from verify_release import RELEASE_CERTIFICATE


class PreviewVerificationTests(unittest.TestCase):
    def setUp(self):
        self.badging = (f"package: name='{PACKAGE_NAME}' versionCode='3' versionName='1.1.0-preview.1'\n"
                        "application-label:'DepthWall Preview'\n")
        self.manifest = f'android:authorities(0x01010018)="{PACKAGE_NAME}.files"\n'

    def test_accepts_separate_preview(self):
        self.assertEqual(preview_metadata(self.badging, self.manifest)["applicationId"], PACKAGE_NAME)

    def test_rejects_stable_package(self):
        with self.assertRaises(ValueError):
            preview_metadata(self.badging.replace(PACKAGE_NAME, "dev.srimi.depthwall"), self.manifest)

    def test_rejects_stable_version(self):
        with self.assertRaises(ValueError):
            preview_metadata(self.badging.replace("1.1.0-preview.1", "1.1.0"), self.manifest)

    def test_rejects_debuggable_and_test_only(self):
        for flag in ["debuggable", "testOnly"]:
            with self.subTest(flag=flag), self.assertRaises(ValueError):
                preview_metadata(self.badging, self.manifest + f"android:{flag}(0x01010000)=(type 0x12)0xffffffff\n")
        with self.assertRaises(ValueError):
            preview_metadata(self.badging + "application-debuggable\n", self.manifest)

    def test_rejects_shared_provider(self):
        with self.assertRaises(ValueError):
            preview_metadata(self.badging, self.manifest.replace(PACKAGE_NAME, "dev.srimi.depthwall"))

    def test_records_preview_certificate(self):
        certificate = "a" * 64
        self.assertEqual(preview_certificate("Signer #1 certificate SHA-256 digest: " + certificate), certificate)

    def test_rejects_production_and_ambiguous_certificates(self):
        for output in ["", "Signer #1 certificate SHA-256 digest: " + RELEASE_CERTIFICATE,
                       "Signer #1 certificate SHA-256 digest: " + "a" * 64 + "\nSigner #2 certificate SHA-256 digest: " + "b" * 64]:
            with self.subTest(output=output), self.assertRaises(ValueError):
                preview_certificate(output)


if __name__ == "__main__":
    unittest.main()
