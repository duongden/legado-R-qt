#!/usr/bin/env python3
"""Measure an explicitly selected debug package; never clear data or collect app logs."""
import argparse
import json
from pathlib import Path
import re
import statistics
import subprocess


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial")
    parser.add_argument("--runs", type=int, default=3)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.runs < 1:
        parser.error("--runs must be positive")
    package = "io.legado.app.rqt.debug"
    component = package + "/io.legado.app.ui.main.MainActivity"
    command = [args.adb] + (["-s", args.serial] if args.serial else [])

    def shell(*parts):
        return subprocess.run(command + ["shell", *parts], check=True, capture_output=True, text=True).stdout

    launches = []
    for _ in range(args.runs):
        shell("am", "force-stop", package)
        result = shell("am", "start", "-W", "-n", component)
        if "Status: ok" not in result or "Error:" in result:
            raise RuntimeError("Debug launcher failed; no measurement recorded")
        launches.append({key: int(value) for key, value in
                         re.findall(r"(TotalTime|WaitTime|ThisTime): (\d+)", result)})
    memory = shell("dumpsys", "meminfo", package)
    pss = re.search(r"TOTAL PSS:\s*(\d+)", memory)
    times = [item["TotalTime"] for item in launches if "TotalTime" in item]
    measurement = {"sdk": int(shell("getprop", "ro.build.version.sdk").strip()),
                   "cold_launches_ms": launches,
                   "median_total_ms": statistics.median(times) if times else None,
                   "pss_kb": int(pss[1]) if pss else None,
                   "scope": "launcher only; no claim about reader or TTS performance"}
    args.output.write_text(json.dumps(measurement, indent=2) + "\n")
    print(json.dumps(measurement))


if __name__ == "__main__":
    main()
