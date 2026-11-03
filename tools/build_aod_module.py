"""Package DepthWall as a Magisk/KernelSU module for crDroid Always-On Display.

Usage: python3 tools/build_aod_module.py [signed.apk] [--out artifacts/DepthWall-AOD-module.zip] [--no-overlay]

The module installs the APK at /system/app/DepthWall. AMBIENT_WALLPAPER is a
signature|preinstalled permission, so Android grants it to that system copy and
DepthWall's ambient wallpaper component can bind on AOD. An optional static
SystemUI overlay sets config_dozeSupportsAodWallpaperOverride to 1, which crDroid's
SystemUI reads before the framework's config_dozeSupportsAodWallpaper (default false).

The overlay targets SystemUI, never "android". Zygote preloads static framework
overlays and every app inherits the open file, so a framework overlay served from
a Magisk module shows up in the memory maps of apps on the DenyList (1.5.0 did this,
and root-detecting apps flagged the phone). Only SystemUI loads a SystemUI overlay.

The overlay is compiled with the Android SDK's aapt2/zipalign/apksigner and
signed with the local debug key; a static, preinstalled overlay is trusted by its
partition rather than its certificate.
"""
from __future__ import annotations

import argparse
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODULE = ROOT / "tools" / "magisk" / "module"
OVERLAY = ROOT / "tools" / "magisk" / "overlay"
PACKAGES = {"dev.srimi.depthwall", "dev.srimi.depthwall.preview"}
EOCD = b"PK\x05\x06"
SIGNING_MAGIC = b"APK Sig Block 42"
# APK Signature Scheme v3 and v2 block IDs; v3 is preferred because it carries rotation.
SCHEME_IDS = (0xF05368C0, 0x7109871A)
EXECUTABLE = {"customize.sh", "service.sh", "META-INF/com/google/android/update-binary"}
OVERLAY_TARGET = "com.android.systemui"
OVERLAY_PATH = "system/product/overlay/DepthWallAodSystemUIOverlay.apk"


def signing_certificate(apk: bytes) -> bytes:
    """DER certificate of the first signer in the v3 (or v2) APK signing block."""
    end = apk.rfind(EOCD, max(0, len(apk) - 65557))
    if end < 0:
        raise ValueError("Not a ZIP archive: end of central directory not found.")
    directory = struct.unpack("<I", apk[end + 16:end + 20])[0]
    if apk[directory - 16:directory] != SIGNING_MAGIC:
        raise ValueError("APK has no v2/v3 signing block.")
    size = struct.unpack("<Q", apk[directory - 24:directory - 16])[0]
    # Pairs sit between the leading size field and the trailing size + magic.
    position, blocks = directory - size, {}
    while position < directory - 24:
        length, block_id = struct.unpack("<QI", apk[position:position + 12])
        blocks[block_id] = apk[position + 12:position + 8 + length]
        position += 8 + length

    def prefixed(data: bytes, offset: int) -> tuple[bytes, int]:
        length = struct.unpack("<I", data[offset:offset + 4])[0]
        return data[offset + 4:offset + 4 + length], offset + 4 + length

    for scheme in SCHEME_IDS:
        if scheme in blocks:
            signers, _ = prefixed(blocks[scheme], 0)
            signer, _ = prefixed(signers, 0)
            signed, _ = prefixed(signer, 0)
            _, offset = prefixed(signed, 0)  # digests
            certificates, _ = prefixed(signed, offset)
            certificate, _ = prefixed(certificates, 0)
            return certificate
    raise ValueError("APK has no v2/v3 signer.")


def signature_hash(certificate: bytes) -> str:
    """Android's Signature.hashCode() (Arrays.hashCode of the DER bytes), as dumpsys prints it."""
    value = 1
    for byte in certificate:
        value = (31 * value + (byte - 256 if byte > 127 else byte)) & 0xFFFFFFFF
    return format(value, "x")


def sdk() -> Path:
    for candidate in (os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT"), Path.home() / "Library/Android/sdk"):
        if candidate and Path(candidate, "build-tools").is_dir():
            return Path(candidate)
    raise SystemExit("Android SDK not found. Set ANDROID_HOME.")


def latest(directory: Path) -> Path:
    def version(path: Path) -> tuple:
        return tuple(int(part) for part in re.findall(r"\d+", path.name))
    return max((p for p in directory.iterdir() if p.is_dir()), key=version)


def run(*command: str | Path) -> str:
    result = subprocess.run([str(c) for c in command], capture_output=True, text=True)
    if result.returncode != 0:
        raise SystemExit(f"{Path(str(command[0])).name} failed:\n{result.stderr or result.stdout}")
    return result.stdout


def badging(tools: Path, apk: Path) -> dict[str, str]:
    line = run(tools / "aapt2", "dump", "badging", apk).splitlines()[0]
    return dict(re.findall(r"(\w+)='([^']*)'", line))


def overlay_target(manifest: Path) -> str:
    """targetPackage of the overlay manifest's <overlay> element."""
    match = re.search(r'<overlay\b[^>]*\bandroid:targetPackage="([^"]+)"', manifest.read_text())
    if not match:
        raise SystemExit(f"{manifest} has no <overlay android:targetPackage>.")
    return match.group(1)


def check_overlay_target(manifest: Path) -> None:
    """Refuse a framework overlay: zygote would load it into every app, hidden apps included."""
    target = overlay_target(manifest)
    if target != OVERLAY_TARGET:
        raise SystemExit(f"{manifest} targets {target}. The AOD overlay must target {OVERLAY_TARGET}: "
                         "a static overlay for 'android' is preloaded by zygote and leaks the module into every app.")


def build_overlay(tools: Path, platform: Path, work: Path) -> Path:
    check_overlay_target(OVERLAY / "AndroidManifest.xml")
    compiled, unsigned, aligned, signed = (work / name for name in ("res.zip", "overlay-unsigned.apk", "overlay-aligned.apk", "overlay.apk"))
    run(tools / "aapt2", "compile", "--dir", OVERLAY / "res", "-o", compiled)
    run(tools / "aapt2", "link", "--manifest", OVERLAY / "AndroidManifest.xml", "-I", platform / "android.jar",
        "--min-sdk-version", "31", "--target-sdk-version", "37", "-o", unsigned, compiled)
    run(tools / "zipalign", "-p", "-f", "4", unsigned, aligned)
    keystore = Path.home() / ".android" / "debug.keystore"
    if not keystore.exists():
        keystore = work / "overlay.keystore"
        run("keytool", "-genkeypair", "-keystore", keystore, "-storepass", "android", "-keypass", "android",
            "-alias", "androiddebugkey", "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
            "-dname", "CN=DepthWall AOD overlay")
    run(tools / "apksigner", "sign", "--ks", keystore, "--ks-pass", "pass:android", "--key-pass", "pass:android",
        "--ks-key-alias", "androiddebugkey", "--out", signed, aligned)
    return signed


def package(apk: Path, out: Path, overlay: Path | None, info: dict[str, str], sighash: str | None) -> None:
    out.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(MODULE.rglob("*")):
            if not path.is_file():
                continue
            name = path.relative_to(MODULE).as_posix()
            data = path.read_bytes()
            if name == "module.prop":
                data = data.replace(b"@VERSION@", f"v{info['versionName']}".encode())
                data = data.replace(b"@VERSION_CODE@", info["versionCode"].encode())
            entry = zipfile.ZipInfo(name, (2026, 11, 2, 0, 0, 0))
            entry.external_attr = (0o755 if name in EXECUTABLE else 0o644) << 16
            archive.writestr(entry, data, zipfile.ZIP_DEFLATED)
        # APKs are stored uncompressed so Android can map them directly from the module.
        archive.write(apk, "system/app/DepthWall/DepthWall.apk", zipfile.ZIP_STORED)
        if overlay:
            archive.write(overlay, OVERLAY_PATH, zipfile.ZIP_STORED)
        if sighash:
            archive.writestr("depthwall.sighash", sighash + "\n")
        archive.writestr("depthwall.package", info["name"] + "\n")


def main(argv: list[str]) -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("apk", nargs="?", default=ROOT / "app/build/outputs/apk/release/app-release.apk", type=Path)
    parser.add_argument("--out", default=ROOT / "artifacts/DepthWall-AOD-module.zip", type=Path)
    parser.add_argument("--no-overlay", action="store_true", help="Leave SystemUI's AOD wallpaper override unchanged.")
    args = parser.parse_args(argv)
    if not args.apk.is_file():
        raise SystemExit(f"Signed APK not found: {args.apk}. Build it with ./gradlew :app:assembleRelease.")
    android = sdk()
    tools = latest(android / "build-tools")
    info = badging(tools, args.apk)
    if info.get("name") not in PACKAGES:
        raise SystemExit(f"{args.apk} is {info.get('name')}, not DepthWall.")
    run(tools / "apksigner", "verify", args.apk)
    try:
        sighash = signature_hash(signing_certificate(args.apk.read_bytes()))
    except ValueError as error:
        print(f"Warning: signing key not recorded ({error}); the installer will not compare keys.", file=sys.stderr)
        sighash = None
    with tempfile.TemporaryDirectory() as directory:
        overlay = None if args.no_overlay else build_overlay(tools, latest(android / "platforms"), Path(directory))
        package(args.apk, args.out, overlay, info, sighash)
    print(f"{args.out} · DepthWall {info['versionName']} ({info['name']})" + ("" if overlay else " · no overlay"))


if __name__ == "__main__":
    main(sys.argv[1:])
