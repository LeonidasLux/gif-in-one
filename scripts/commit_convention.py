#!/usr/bin/env python3
"""Shared Conventional Commits parser for release tooling."""

from __future__ import annotations

import re
from dataclasses import dataclass


COMMIT_TYPES = {
    "feat": "新功能",
    "fix": "问题修复",
    "perf": "性能优化",
    "refactor": "体验优化",
    "style": "界面样式",
    "docs": "文档",
    "test": "测试",
    "build": "构建",
    "ci": "持续集成",
    "chore": "维护",
    "revert": "回退",
}

HEADER_PATTERN = re.compile(
    r"^(feat|fix|perf|refactor|style|docs|test|build|ci|chore|revert)"
    r"(?:\(([^()\r\n]{1,40})\))?(!)?: ([^\r\n]{1,72})$"
)


@dataclass(frozen=True)
class ParsedCommit:
    valid: bool
    error: str = ""
    type: str = ""
    scope: str = ""
    subject: str = ""
    body: str = ""
    breaking: bool = False


def parse_commit_message(message: str) -> ParsedCommit:
    normalized = str(message or "").replace("\r\n", "\n").strip()
    header, _, remainder = normalized.partition("\n")
    match = HEADER_PATTERN.fullmatch(header)
    if not match:
        return ParsedCommit(
            valid=False,
            error="提交标题必须符合“类型(范围): 说明”，例如 feat(update): 增加应用内更新",
        )
    commit_type, scope, breaking_mark, subject = match.groups()
    body = remainder.strip()
    return ParsedCommit(
        valid=True,
        type=commit_type,
        scope=scope or "",
        subject=subject,
        body=body,
        breaking=bool(breaking_mark) or bool(re.search(r"(^|\n)BREAKING CHANGE:\s*\S", body)),
    )


def is_release_commit(commit: ParsedCommit) -> bool:
    return commit.valid and commit.type == "chore" and commit.scope == "release"
