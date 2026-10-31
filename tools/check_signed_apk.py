"""Prove a signed APK holds exactly the entries of the unsigned APK that CI built.

Usage: python3 tools/check_signed_apk.py unsigned.apk signed.apk

APK Signature Scheme v2/v3 signing (without v1) inserts a signing block before the
ZIP central directory and leaves every local file record untouched. So the signed
file must start with the unsigned file's bytes up to its central directory, and
both must list the same entries.
"""
from __future__ import annotations

import struct
import sys
import zipfile
from pathlib import Path

EOCD = b"PK\x05\x06"


def central_directory_offset(data: bytes) -> int:
    position = data.rfind(EOCD, max(0, len(data) - 65557))
    if position < 0:
        raise ValueError("Not a ZIP archive: end of central directory not found.")
    return struct.unpack("<I", data[position + 16:position + 20])[0]


def entries(path: Path) -> list[tuple[str, int, int, int, int]]:
    with zipfile.ZipFile(path) as archive:
        return [(i.filename, i.CRC, i.compress_type, i.compress_size, i.file_size) for i in archive.infolist()]


def check(unsigned: Path, signed: Path) -> None:
    before, after = unsigned.read_bytes(), signed.read_bytes()
    records = central_directory_offset(before)
    if after[:records] != before[:records]:
        raise ValueError("Signed APK entries differ from the unsigned build.")
    if entries(unsigned) != entries(signed):
        raise ValueError("Signed APK lists different entries from the unsigned build.")
    if any(name.startswith("META-INF/") and name.upper().endswith((".SF", ".RSA", ".EC", ".DSA"))
           for name, *_ in entries(signed)):
        raise ValueError("Unexpected v1 (JAR) signature files in the signed APK.")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    try:
        check(Path(sys.argv[1]), Path(sys.argv[2]))
    except (ValueError, OSError, zipfile.BadZipFile) as error:
        raise SystemExit(f"Signed APK check failed: {error}") from error
    print("Signed APK contains exactly the unsigned build's entries.")
