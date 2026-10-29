"""Verify a separate test APK without weakening production update checks."""
from __future__ import annotations

import argparse
import json
import re
import shutil
import subprocess
import zipfile
from pathlib import Path
from xml.etree import ElementTree

from verify_release import RELEASE_CERTIFICATE, run, sha256, single_signer_certificate

PACKAGE_NAME = "dev.srimi.depthwall.preview"


def preview_metadata(badging: str, manifest: str) -> dict:
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.MULTILINE)
    if not package or package[1] != PACKAGE_NAME:
        raise ValueError("Preview must have its own application ID; never replace the stable app.")
    if not re.fullmatch(r"\d+\.\d+\.\d+-preview\.\d+", package[3]) or int(package[2]) < 1:
        raise ValueError("Expected a preview version such as 1.1.0-preview.1.")
    if "application-label:'DepthWall Preview'" not in badging:
        raise ValueError("Preview launcher label is missing.")
    if "application-debuggable" in badging:
        raise ValueError("Preview APK must be non-debuggable.")
    for flag in ("debuggable", "testOnly"):
        value = re.search(rf"android:{flag}\([^)]*\)=\(type 0x12\)(0x[0-9a-fA-F]+)", manifest)
        if value and int(value[1], 16):
            raise ValueError(f"Preview APK must not set {flag}.")
    authorities = re.findall(r'android:authorities\([^)]*\)="([^"]+)"', manifest)
    if PACKAGE_NAME + ".files" not in authorities or any(not a.startswith(PACKAGE_NAME + ".") for a in authorities):
        raise ValueError("Preview provider authorities must be isolated from the stable app.")
    return {"applicationId": package[1], "versionCode": int(package[2]), "versionName": package[3],
            "debuggable": False, "testOnly": False, "providerAuthorities": authorities}


def preview_certificate(output: str) -> str:
    certificate = single_signer_certificate(output)
    if certificate == RELEASE_CERTIFICATE:
        raise ValueError("Preview should use its separate development signing identity.")
    return certificate


def preview_evidence(root: Path) -> dict:
    reports = sorted((root / "app/build/test-results/testPreviewUnitTest").glob("TEST-*.xml"))
    totals = dict.fromkeys(("tests", "failures", "errors", "skipped"), 0)
    for report in reports:
        suite = ElementTree.parse(report).getroot()
        for name in totals:
            totals[name] += int(suite.attrib.get(name, "0"))
    if not totals["tests"] or totals["failures"] or totals["errors"] or totals["skipped"]:
        raise ValueError("Preview unit tests must all pass.")
    lint = root / "app/build/reports/lint-results-preview.xml"
    severities: dict[str, int] = {}
    for issue in ElementTree.parse(lint).getroot().findall("issue"):
        severity = issue.attrib["severity"]
        severities[severity] = severities.get(severity, 0) + 1
    if severities.get("Error", 0) or severities.get("Fatal", 0):
        raise ValueError("Preview lint has errors.")
    return {"unitTests": totals, "lint": severities, "deviceTestsExecuted": False, "physicalPhoneAodVerified": False}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--build-tools", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=Path("artifacts/preview"))
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    apk, tools = args.apk.resolve(), args.build_tools.resolve()
    if run("git", "-C", str(root), "status", "--porcelain").strip():
        raise ValueError("Commit the source before preparing public preview artifacts.")
    commit = run("git", "-C", str(root), "rev-parse", "HEAD").strip()
    signature = run(str(tools / "apksigner"), "verify", "--verbose", "--print-certs", str(apk))
    certificate = preview_certificate(signature)
    metadata = preview_metadata(run(str(tools / "aapt"), "dump", "badging", str(apk)),
                                run(str(tools / "aapt"), "dump", "xmltree", str(apk), "AndroidManifest.xml"))
    declared = (root / "app/build.gradle.kts").read_text()
    version = re.search(r'versionName\s*=\s*"([^"]+)"', declared)
    suffix = re.search(r'versionNameSuffix\s*=\s*"(-preview\.\d+)"', declared)
    code = re.search(r'versionCode\s*=\s*(\d+)', declared)
    if not version or not suffix or not code or metadata["versionName"] != version[1] + suffix[1] or metadata["versionCode"] != int(code[1]):
        raise ValueError("Preview APK version differs from this checkout.")
    with zipfile.ZipFile(apk) as archive:
        revision = archive.read("META-INF/version-control-info.textproto").decode()
    if f'revision: "{commit}"' not in revision:
        raise ValueError("Rebuild the APK at this source commit before publishing.")
    run(str(tools / "zipalign"), "-c", "-P", "16", "4", str(apk))
    evidence = preview_evidence(root)
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    destination = output / f"DepthWall-{metadata['versionName']}.apk"
    if apk != destination:
        shutil.copyfile(apk, destination)
    record = {**metadata, "buildType": "preview", "sourceCommit": commit, "embeddedSourceCommit": commit,
              "signingIdentity": "Development certificate; separate test app, not a production update",
              "certificateSha256": certificate, "apkSha256": sha256(destination), "apkBytes": destination.stat().st_size,
              "zipAlignment": "4-byte zip / 16 KiB native library pages", "productionUpdateCompatible": False,
              "automaticStableDataMigration": False, **evidence}
    report = output / "verification.json"
    report.write_text(json.dumps(record, indent=2) + "\n")
    (output / "SHA256SUMS").write_text("".join(f"{sha256(path)}  {path.name}\n" for path in (destination, report)))
    print(json.dumps({"apk": str(destination), "sourceCommit": commit, "sha256": record["apkSha256"]}))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError, subprocess.CalledProcessError) as error:
        raise SystemExit(f"Preview verification failed: {error}") from error
