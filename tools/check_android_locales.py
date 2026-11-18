#!/usr/bin/env python3
"""Validate Vietnamese resource coverage and format arguments without modifying resources."""
from collections import Counter
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1] / "app/src/main/res"
FORMAT = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]")
# These arrays contain stored IDs, filenames or protocol names, not translated choices.
TECHNICAL_ARRAYS = {
    "icons", "text_suffix", "server_type", "cloud_storage_types",
}


def resources(directory, tag):
    result = {}
    for path in sorted(directory.glob("*.xml")):
        for node in ET.parse(path).getroot():
            if node.tag != tag:
                continue
            key = node.attrib["name"]
            if key in result:
                raise ValueError(f"Duplicate {tag}: {directory.name}/{key}")
            result[key] = node
    return result


def strings(directory):
    return resources(directory, "string")


def validate(root):
    default, vietnamese = strings(root / "values"), strings(root / "values-vi")
    errors = []
    for key, original in default.items():
        if original.get("translatable") == "false":
            continue
        raw = "".join(original.itertext())
        translated = vietnamese.get(key)
        if translated is None:
            errors.append(f"Missing Vietnamese: {key}")
            continue
        if original.get("formatted") == "false":
            continue
        if Counter(FORMAT.findall(raw)) != Counter(FORMAT.findall("".join(translated.itertext()))):
            errors.append(f"Format arguments differ: {key}")
    arrays = resources(root / "values", "string-array")
    vi_arrays = resources(root / "values-vi", "string-array")
    for key, original in arrays.items():
        if (original.get("translatable") == "false" or key in TECHNICAL_ARRAYS
                or key.endswith(("_value", "_values", "_v"))):
            continue
        # References inherit localized strings without duplicating the array itself.
        has_literal_label = any(
            not (item.text or "").startswith("@")
            and re.search(r"[A-Za-z\u3400-\u9fff]", item.text or "")
            for item in original
        )
        translated = vi_arrays.get(key)
        if has_literal_label and translated is None:
            errors.append(f"Missing Vietnamese array: {key}")
        if translated is not None and len(original) != len(translated):
            errors.append(f"Array lengths differ: {key}")
    return errors, len(default), len(vietnamese)


def main():
    errors, default_count, vi_count = validate(ROOT)
    print(f"Checked {default_count} default and {vi_count} Vietnamese strings")
    for error in errors:
        print(error)
    return bool(errors)


if __name__ == "__main__":
    sys.exit(main())
