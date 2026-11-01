#!/usr/bin/env python3
"""Generate categorized release notes from Conventional Commits."""

from __future__ import annotations

import argparse
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

from commit_convention import COMMIT_TYPES, ParsedCommit, is_release_commit, parse_commit_message


ROOT = Path(__file__).resolve().parent.parent
CATEGORY_ORDER = ["feat", "fix", "perf", "refactor", "style", "docs", "test", "build", "ci", "chore", "revert"]


def git(*args: str, allow_failure: bool = False) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=ROOT,
        check=False,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    if result.returncode != 0:
        if allow_failure:
            return ""
        print(result.stderr.strip() or "无法读取 Git 记录。", file=sys.stderr)
        raise SystemExit(1)
    return result.stdout.strip()


def collect_commits(from_ref: str, to_ref: str, bootstrap: bool) -> list[tuple[str, str, ParsedCommit]]:
    range_args = [f"{from_ref}..{to_ref}"] if from_ref else ["-n", "50", to_ref]
    log = git("log", "--reverse", "--format=%H%x1f%s%x1f%b%x1e", *range_args)
    commits: list[tuple[str, str, ParsedCommit]] = []
    for entry in log.split("\x1e"):
        entry = entry.strip()
        if not entry:
            continue
        parts = entry.split("\x1f")
        commit_hash = parts[0] if parts else ""
        subject = parts[1] if len(parts) > 1 else ""
        body = "\x1f".join(parts[2:]) if len(parts) > 2 else ""
        if subject.startswith("Merge "):
            continue
        parsed = parse_commit_message(f"{subject}\n{body}")
        if bootstrap and not parsed.valid:
            continue
        commits.append((commit_hash, subject, parsed))
    return commits


def build_notes(commits: list[tuple[str, str, ParsedCommit]]) -> str:
    sections: dict[str, list[str]] = defaultdict(list)
    breaking: list[str] = []
    for commit_hash, _, commit in commits:
        if not commit.valid or is_release_commit(commit):
            continue
        scope = f"（{commit.scope}）" if commit.scope else ""
        entry = f"{commit.subject}{scope} · {commit_hash[:7]}"
        sections[commit.type].append(entry)
        if commit.breaking:
            breaking.append(entry)

    lines = ["### 本次更新", ""]
    if breaking:
        lines.extend(["#### 不兼容变更", "", *[f"- {item}" for item in breaking], ""])
    for commit_type in CATEGORY_ORDER:
        entries = sections[commit_type]
        if not entries:
            continue
        lines.extend([f"#### {COMMIT_TYPES[commit_type]}", "", *[f"- {item}" for item in entries], ""])
    if not any(sections.values()):
        lines.extend(["#### 维护", "", "- 常规维护与稳定性更新", ""])
    return "\n".join(lines).strip() + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--from", dest="from_ref", default="")
    parser.add_argument("--to", dest="to_ref", default="HEAD")
    parser.add_argument("--output", default="")
    parser.add_argument("--check-only", action="store_true")
    args = parser.parse_args()

    to_ref = args.to_ref
    from_ref = args.from_ref or git(
        "describe", "--tags", "--abbrev=0", "--match", "v[0-9]*", to_ref, allow_failure=True
    )
    commits = collect_commits(from_ref, to_ref, bootstrap=not bool(from_ref))
    invalid = [(commit_hash, subject, commit) for commit_hash, subject, commit in commits if not commit.valid]
    if invalid:
        print("以下提交不符合 Conventional Commits：", file=sys.stderr)
        for commit_hash, subject, commit in invalid:
            print(f"- {commit_hash[:7]} {subject}", file=sys.stderr)
            print(f"  {commit.error}", file=sys.stderr)
        return 1

    if args.check_only:
        print(f"已校验 {len(commits)} 条提交，格式全部有效。")
        return 0

    notes = build_notes(commits)
    if args.output:
        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(notes, encoding="utf-8")
    else:
        sys.stdout.write(notes)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
