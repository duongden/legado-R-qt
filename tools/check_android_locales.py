#!/usr/bin/env python3
"""Validate Android Vietnamese coverage and format arguments without modifying resources."""
from collections import Counter
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1] / "app/src/main/res"
FORMAT = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]")


def strings(directory):
    result = {}
    for path in sorted(directory.glob("*.xml")):
        for node in ET.parse(path).getroot():
            if node.tag != "string":
                continue
            key = node.attrib["name"]
            if key in result:
                raise ValueError(f"Duplicate string: {directory.name}/{key}")
            result[key] = node
    return result


def main():
    default, vietnamese = strings(ROOT / "values"), strings(ROOT / "values-vi")
    errors = []
    for key, original in default.items():
        if original.get("translatable") == "false":
            continue
        raw = "".join(original.itertext())
        translated = vietnamese.get(key)
        if translated is None:
            if any("\u3400" <= char <= "\u9fff" for char in raw):
                errors.append(f"Missing Vietnamese: {key}")
            continue
        if original.get("formatted") == "false":
            continue
        if Counter(FORMAT.findall(raw)) != Counter(FORMAT.findall("".join(translated.itertext()))):
            errors.append(f"Format arguments differ: {key}")
    print(f"Checked {len(default)} default and {len(vietnamese)} Vietnamese strings")
    for error in errors:
        print(error)
    return bool(errors)


if __name__ == "__main__":
    sys.exit(main())
