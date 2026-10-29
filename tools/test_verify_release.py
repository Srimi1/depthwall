"""Reject APKs that would break the published update path before release upload."""
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from verify_release import RELEASE_CERTIFICATE, certificate_from_output, metadata_from_badging, main


class ReleaseVerificationTest(unittest.TestCase):
    def test_expected_certificate_is_required(self):
        output = f"Signer #1 certificate SHA-256 digest: {RELEASE_CERTIFICATE}\n"
        self.assertEqual(RELEASE_CERTIFICATE, certificate_from_output(output))
        with self.assertRaisesRegex(ValueError, "differs"):
            certificate_from_output("Signer #1 certificate SHA-256 digest: " + "0" * 64)
        with self.assertRaisesRegex(ValueError, "exactly one"):
            certificate_from_output(output + output.replace("#1", "#2"))
        with self.assertRaisesRegex(ValueError, "exactly one"):
            certificate_from_output("DOES NOT VERIFY\n")

    def test_debug_and_wrong_package_are_rejected(self):
        badging = "package: name='dev.srimi.depthwall' versionCode='3' versionName='1.1.0'\n"
        self.assertEqual(3, metadata_from_badging(badging)["versionCode"])
        with self.assertRaisesRegex(ValueError, "debuggable"):
            metadata_from_badging(badging + "application-debuggable\n")
        with self.assertRaisesRegex(ValueError, "wrong application"):
            metadata_from_badging(badging.replace("dev.srimi.depthwall", "dev.srimi.depthwall.debug"))
        with self.assertRaisesRegex(ValueError, "newer"):
            metadata_from_badging(badging.replace("versionCode='3'", "versionCode='2'"))
        with self.assertRaisesRegex(ValueError, "newer"):
            metadata_from_badging(badging.replace("versionName='1.1.0'", "versionName='1.0.1'"))

    def test_android_37_scheme_label_retains_certificate_pin(self):
        output = f"Number of signers: 1\nV2 Signer: certificate SHA-256 digest: {RELEASE_CERTIFICATE}\n"
        self.assertEqual(RELEASE_CERTIFICATE, certificate_from_output(output))
        with self.assertRaisesRegex(ValueError, "differs"):
            certificate_from_output(output.replace(RELEASE_CERTIFICATE, "a" * 64))

    def test_android_37_rejects_ambiguous_or_multiple_signers(self):
        output = f"Number of signers: 1\nV2 Signer: certificate SHA-256 digest: {RELEASE_CERTIFICATE}\n"
        invalid = [output.replace("signers: 1", "signers: 2"),
                   output.replace("Number of signers: 1\n", ""),
                   output + output.splitlines()[1] + "\n",
                   output.replace("V2 Signer:", "V2 Signer #1:"),
                   output.replace("V2 Signer:", "V3.1 Signer:")]
        for value in invalid:
            with self.subTest(value=value), self.assertRaisesRegex(ValueError, "exactly one"):
                certificate_from_output(value)

    def test_unsigned_apk_failure_creates_no_release_assets(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / "unsigned.apk"
            apk.write_bytes(b"unsigned test fixture")
            output = root / "release"
            argv = ["verify_release.py", str(apk), "--build-tools", str(root), "--output", str(output)]
            with patch("sys.argv", argv), patch("verify_release.run", side_effect=subprocess.CalledProcessError(1, "apksigner")):
                with self.assertRaises(subprocess.CalledProcessError):
                    main()
            self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
