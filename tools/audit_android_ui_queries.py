#!/usr/bin/env python3
"""Inventory explicit DAO calls in Android UI; this is not a thread-safety proof."""
import argparse
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/java/io/legado/app/ui"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    entries = []
    for path in sorted(UI.rglob("*.kt")):
        for number, line in enumerate(path.read_text().splitlines(), 1):
            call = re.search(r"appDb\.(\w+Dao)\.(\w+)", line)
            if call:
                entries.append((str(path.relative_to(ROOT)), number, ".".join(call.groups())))
    lines = ["# Android UI DAO inventory", "",
             "Explicit DAO references only. An entry can already run on IO, return a Flow, or",
             "belong to a worker callback. Review surrounding control flow before changing it.",
             "This inventory does not include calls hidden in entity/helper methods.", "",
             f"Total explicit references: {len(entries)}", "",
             "| File | Line | DAO member |", "| --- | ---: | --- |"]
    lines.extend(f"| `{path}` | {line} | `{call}` |" for path, line, call in entries)
    if args.output:
        args.output.write_text("\n".join(lines) + "\n")
    print(f"Inventoried {len(entries)} explicit Android UI DAO references")


if __name__ == "__main__":
    main()
