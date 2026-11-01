#!/usr/bin/env python3
"""Create the update.json asset consumed by the Android updater."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


def describe_asset(path: Path, name: str) -> dict[str, object]:
    data = path.read_bytes()
    return {
        "name": name,
        "size": len(data),
        "sha256": hashlib.sha256(data).hexdigest(),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--version-code", required=True, type=int)
    parser.add_argument("--arm64", required=True, type=Path)
    parser.add_argument("--x86-64", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    manifest = {
        "versionName": args.version_name,
        "versionCode": args.version_code,
        "assets": {
            "arm64-v8a": describe_asset(args.arm64, "gif-in-one-arm64-v8a.apk"),
            "x86_64": describe_asset(args.x86_64, "gif-in-one-x86_64.apk"),
        },
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
