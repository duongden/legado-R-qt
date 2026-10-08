#!/usr/bin/env python3
"""Check 64-bit ELF LOAD/RELRO and uncompressed JNI ZIP alignment in an APK."""

import argparse
import struct
import zipfile
from pathlib import Path

PAGE = 16384
PT_LOAD = 1
PT_GNU_RELRO = 0x6474E552


def elf_errors(data):
    if data[:4] != b"\x7fELF" or data[4] != 2:
        return ["expected a 64-bit ELF"]
    endian = "<" if data[5] == 1 else ">"
    offset = struct.unpack_from(endian + "Q", data, 32)[0]
    size, count = struct.unpack_from(endian + "HH", data, 54)
    errors = []
    loads = []
    relros = []
    for index in range(count):
        kind, _, file_offset, address, _, _, memory_size, alignment = struct.unpack_from(
            endian + "IIQQQQQQ", data, offset + index * size
        )
        if kind == PT_LOAD:
            loads.append((address, address + memory_size))
            if alignment < PAGE or (address - file_offset) % PAGE:
                errors.append(f"LOAD {index} is not 16 KB aligned")
        elif kind == PT_GNU_RELRO:
            relros.append((index, address, address + memory_size))
    for index, start, end in relros:
        # A RELRO suffix may include padding past its LOAD segment. Rounding it
        # up is safe when no writable data follows within that LOAD segment.
        suffix = any(low <= start < high <= end for low, high in loads)
        if end % PAGE and not suffix:
            errors.append(f"RELRO {index} is neither a LOAD suffix nor 16 KB aligned")
    if not loads:
        errors.append("no LOAD segments")
    return errors


def check_apk(path):
    failures = 0
    checked = 0
    with zipfile.ZipFile(path) as archive, path.open("rb") as raw:
        for entry in archive.infolist():
            if not entry.filename.startswith(("lib/arm64-v8a/", "lib/x86_64/")):
                continue
            if not entry.filename.endswith(".so"):
                continue
            checked += 1
            errors = elf_errors(archive.read(entry))
            if entry.compress_type == zipfile.ZIP_STORED:
                raw.seek(entry.header_offset + 26)
                name_size, extra_size = struct.unpack("<HH", raw.read(4))
                data_offset = entry.header_offset + 30 + name_size + extra_size
                if data_offset % PAGE:
                    errors.append("uncompressed ZIP entry is not 16 KB aligned")
            failures += bool(errors)
            print(f"{'FAIL' if errors else 'PASS'} {entry.filename}")
            for error in errors:
                print(f"  {error}")
    print(f"Checked {checked} native libraries; {failures} failed.")
    return 1 if failures else 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    args = parser.parse_args()
    raise SystemExit(check_apk(args.apk))
