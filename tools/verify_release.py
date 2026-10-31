"""Verify an update-compatible release APK and assemble its public verification files."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import subprocess
from pathlib import Path
from xml.etree import ElementTree

# Public certificate fingerprint of the signing identity introduced in v1.2.0 (not a secret).
# The v1.0.1 key was lost; see docs/RELEASING.md.
RELEASE_CERTIFICATE = "bed080b5e4a2d85b95b09d6a0df92b05ef944f7eb4509404ab4fc7811dc32e37"
PACKAGE_NAME = "dev.srimi.depthwall"


def run(*command: str) -> str:
    return subprocess.run(command, check=True, capture_output=True, text=True).stdout


def single_signer_certificate(output: str) -> str:
    # Build-tools 37 names the signing scheme ("V2 Signer:") instead of "Signer #1".
    # Keep ambiguous, multiple and rotation signers fail-closed in either format.
    counts = re.findall(r"^Number of signers: (\d+)\s*$", output, re.MULTILINE)
    lines = [line.strip() for line in output.splitlines() if " certificate SHA-256 digest:" in line]
    if len(lines) != 1 or (counts and counts != ["1"]):
        raise ValueError("Expected exactly one APK signing certificate.")
    legacy = re.fullmatch(r"Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]{64})", lines[0])
    modern = re.fullmatch(r"(?:V1|V2|V3\.0) Signer: certificate SHA-256 digest: ([0-9a-fA-F]{64})", lines[0])
    if legacy:
        return legacy[1].lower()
    if modern and counts == ["1"]:
        return modern[1].lower()
    raise ValueError("Expected exactly one APK signing certificate in a supported apksigner format.")


def certificate_from_output(output: str) -> str:
    certificate = single_signer_certificate(output)
    if certificate != RELEASE_CERTIFICATE:
        raise ValueError("APK signing certificate differs from the release identity; this APK cannot update installed releases.")
    return certificate


def metadata_from_badging(output: str) -> dict[str, str | int]:
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", output, re.MULTILINE)
    if not package or package[1] != PACKAGE_NAME:
        raise ValueError("APK has the wrong application ID or unreadable package metadata.")
    if re.search(r"^application-debuggable\s*$", output, re.MULTILINE):
        raise ValueError("A debuggable APK must not be published as the release.")
    if not re.fullmatch(r"\d+\.\d+\.\d+", package[3]):
        raise ValueError("Expected a stable semantic version such as 1.1.0.")
    if int(package[2]) <= 2 or tuple(map(int, package[3].split("."))) <= (1, 0, 1):
        raise ValueError("Release must be newer than the published v1.0.1 (versionCode 2).")
    return {"applicationId": package[1], "versionCode": int(package[2]), "versionName": package[3]}


def release_evidence(root: Path) -> dict:
    tests = sorted((root / "app/build/test-results/testReleaseUnitTest").glob("TEST-*.xml"))
    if not tests:
        raise ValueError("Release unit-test reports are missing; run testReleaseUnitTest first.")
    totals = {name: 0 for name in ("tests", "failures", "errors", "skipped")}
    for report in tests:
        suite = ElementTree.parse(report).getroot()
        for name in totals:
            totals[name] += int(suite.attrib.get(name, "0"))
    if totals["tests"] == 0 or totals["failures"] or totals["errors"]:
        raise ValueError("Release unit tests did not pass.")
    lint = root / "app/build/reports/lint-results-release.xml"
    if not lint.exists():
        raise ValueError("Release lint report is missing; run lintRelease first.")
    severities: dict[str, int] = {}
    for issue in ElementTree.parse(lint).getroot().findall("issue"):
        severity = issue.attrib["severity"]
        severities[severity] = severities.get(severity, 0) + 1
    if severities.get("Error", 0) or severities.get("Fatal", 0):
        raise ValueError("Release lint has errors.")
    return {"unitTests": totals, "lint": severities, "deviceTestsExecuted": False}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--build-tools", required=True, type=Path, help="Android SDK build-tools directory")
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--output", type=Path, default=Path("artifacts/release"))
    args = parser.parse_args()
    root = args.root.resolve()
    apk = args.apk.resolve()
    tools = args.build_tools.resolve()
    if not apk.is_file():
        raise ValueError("Release APK does not exist.")
    signature = run(str(tools / "apksigner"), "verify", "--verbose", "--print-certs", str(apk))
    certificate = certificate_from_output(signature)
    metadata = metadata_from_badging(run(str(tools / "aapt"), "dump", "badging", str(apk)))
    declared = (root / "app/build.gradle.kts").read_text()
    version = re.search(r'versionName\s*=\s*"([^"]+)"', declared)
    code = re.search(r'versionCode\s*=\s*(\d+)', declared)
    if not version or not code or metadata["versionName"] != version[1] or metadata["versionCode"] != int(code[1]):
        raise ValueError("APK version does not match this checkout's Gradle configuration.")
    # Android 15+ can use 16 KiB memory pages. Check native-library alignment before publishing.
    run(str(tools / "zipalign"), "-c", "-P", "16", "4", str(apk))
    commit = run("git", "-C", str(root), "rev-parse", "HEAD").strip()
    if run("git", "-C", str(root), "status", "--porcelain").strip():
        raise ValueError("Commit the source changes before preparing public release artifacts.")
    evidence = release_evidence(root)
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    destination = output / f"DepthWall-{metadata['versionName']}.apk"
    if apk != destination:
        shutil.copyfile(apk, destination)
    verification = {**metadata, "sourceCommit": commit, "certificateSha256": certificate,
                    "apkSha256": sha256(destination), "apkBytes": destination.stat().st_size,
                    "zipAlignment": "4-byte zip / 16 KiB native library pages", **evidence}
    report = output / "verification.json"
    report.write_text(json.dumps(verification, indent=2) + "\n")
    (output / "SHA256SUMS").write_text("".join(f"{sha256(path)}  {path.name}\n" for path in (destination, report)))
    print(json.dumps({"apk": str(destination), "version": metadata["versionName"], "sha256": verification["apkSha256"]}))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        raise SystemExit(f"Release verification failed: {error}") from error
