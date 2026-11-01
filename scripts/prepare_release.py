#!/usr/bin/env python3
"""Set a strictly increasing yy.MMdd.HHmm Android version."""

from __future__ import annotations

import argparse
import re
import subprocess
from datetime import datetime, timedelta, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
GRADLE_PATH = ROOT / "app" / "build.gradle"
VERSION_NAME_PATTERN = re.compile(r"versionName\s+['\"]([^'\"]+)['\"]")
VERSION_CODE_PATTERN = re.compile(r"versionCode\s+(\d+)")
RELEASE_VERSION_PATTERN = re.compile(r"^(\d{2})\.(\d{2})(\d{2})\.(\d{2})(\d{2})$")


def version_code(version_name: str) -> int:
    match = RELEASE_VERSION_PATTERN.fullmatch(version_name)
    if not match:
        raise ValueError(f"版本号必须为 yy.MMdd.HHmm：{version_name}")
    year, month, day, hour, minute = map(int, match.groups())
    datetime(2000 + year, month, day, hour, minute)
    return year * 10_000_000 + month * 100_000 + day * 1_000 + hour * 100 + minute


def version_from_datetime(value: datetime) -> str:
    return value.strftime("%y.%m%d.%H%M")


def version_datetime(version_name: str) -> datetime:
    match = RELEASE_VERSION_PATTERN.fullmatch(version_name)
    if not match:
        raise ValueError(f"当前版本号格式无效：{version_name}")
    year, month, day, hour, minute = map(int, match.groups())
    return datetime(2000 + year, month, day, hour, minute)


def current_time() -> datetime:
    try:
        from zoneinfo import ZoneInfo

        return datetime.now(ZoneInfo("Asia/Shanghai"))
    except Exception:
        return datetime.now(timezone(timedelta(hours=8)))


def tag_exists(tag: str) -> bool:
    result = subprocess.run(
        ["git", "rev-parse", "--verify", "--quiet", f"refs/tags/{tag}"],
        cwd=ROOT,
        check=False,
        capture_output=True,
    )
    return result.returncode == 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--version", default="", help="yy.MMdd.HHmm；留空则使用当前时间")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    gradle = GRADLE_PATH.read_text(encoding="utf-8")
    name_match = VERSION_NAME_PATTERN.search(gradle)
    code_match = VERSION_CODE_PATTERN.search(gradle)
    if not name_match or not code_match:
        raise SystemExit("app/build.gradle 缺少 versionName 或 versionCode。")
    current_name = name_match.group(1)
    current_code = int(code_match.group(1))

    if args.version:
        next_name = args.version.strip().removeprefix("v")
        next_code = version_code(next_name)
        if next_code <= current_code:
            raise SystemExit("新版本的 versionCode 必须高于当前版本。")
    else:
        candidate = current_time().replace(tzinfo=None, second=0, microsecond=0)
        next_name = version_from_datetime(candidate)
        next_code = version_code(next_name)
        if next_code <= current_code:
            candidate = version_datetime(current_name)
            candidate += timedelta(minutes=1)
            while version_code(version_from_datetime(candidate)) <= current_code:
                candidate += timedelta(minutes=1)
            next_name = version_from_datetime(candidate)
            next_code = version_code(next_name)

    next_tag = f"v{next_name}"
    if tag_exists(next_tag):
        raise SystemExit(f"标签 {next_tag} 已存在，拒绝覆盖已有版本。")

    if args.dry_run:
        print(f"versionName={next_name}")
        print(f"versionCode={next_code}")
        return 0

    gradle = VERSION_CODE_PATTERN.sub(f"versionCode {next_code}", gradle, count=1)
    gradle = VERSION_NAME_PATTERN.sub(f"versionName '{next_name}'", gradle, count=1)
    GRADLE_PATH.write_text(gradle, encoding="utf-8")
    print(f"versionName={next_name}")
    print(f"versionCode={next_code}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
