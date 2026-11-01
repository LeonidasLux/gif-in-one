#!/usr/bin/env python3
"""Validate a Git commit message file against the repository convention."""

from __future__ import annotations

import sys
from pathlib import Path

from commit_convention import COMMIT_TYPES, parse_commit_message


def main() -> int:
    if len(sys.argv) != 2:
        print("用法：python scripts/check_commit_message.py <提交信息文件>", file=sys.stderr)
        return 2
    lines = Path(sys.argv[1]).read_text(encoding="utf-8").splitlines()
    message = "\n".join(line for line in lines if not line.startswith("#")).strip()
    commit = parse_commit_message(message)
    if commit.valid:
        return 0
    print(commit.error, file=sys.stderr)
    print(f"允许的类型：{', '.join(COMMIT_TYPES)}", file=sys.stderr)
    print("破坏性变更请在类型后加 !，并在正文写明 BREAKING CHANGE: 具体影响。", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
