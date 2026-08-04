#!/usr/bin/env python3
"""pre-commit 编码校验：防止中文源文件编码损坏（ADR-0019）。

检查暂存区或指定路径下的 .py / .json 文件：
1. 无 UTF-8 BOM（\xef\xbb\xbf）
2. UTF-8 解码合法
3. .py 能 py_compile 通过；.json 能 json.load 通过

用法：
    python scripts/check-encoding.py              # 检查暂存区（pre-commit 用）
    python scripts/check-encoding.py --paths a.py b.json
    python scripts/check-encoding.py --staged     # 显式检查暂存区

退出码 0 = 全部通过，1 = 有文件不通过。
"""

import argparse
import json
import py_compile
import subprocess
import sys
from pathlib import Path

# 排除目录（双保险：即便误暂存也不扫）
EXCLUDE_DIRS = (".venv", "node_modules", ".git", "__pycache__", ".pytest_cache")
BOM = b"\xef\xbb\xbf"


def staged_files() -> list[Path]:
    """取暂存区中已 add 的文件（相对仓库根）。"""
    out = subprocess.run(
        ["git", "diff", "--cached", "--name-only", "--diff-filter=ACM"],
        capture_output=True,
        text=True,
        check=True,
    )
    return [Path(p) for p in out.stdout.splitlines() if p.strip()]


def is_excluded(path: Path) -> bool:
    return any(part in EXCLUDE_DIRS for part in path.parts)


def check_file(path: Path) -> list[str]:
    """返回该文件的问题列表（空 = 通过）。只检查 .py / .json。"""
    if path.suffix not in (".py", ".json"):
        return []
    if not path.exists() or is_excluded(path):
        return []

    problems: list[str] = []
    raw = path.read_bytes()

    # 1. BOM
    if raw.startswith(BOM):
        problems.append("含 UTF-8 BOM（json.load/py_compile 会拒绝或污染）")

    # 2. UTF-8 合法
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError as e:
        problems.append(f"UTF-8 解码失败: {e}")
        return problems  # 后续检查无意义

    # 3. 语法/结构
    if path.suffix == ".py":
        try:
            py_compile.compile(str(path), doraise=True)
        except py_compile.PyCompileError as e:
            problems.append(f"py_compile 失败: {e}")
    elif path.suffix == ".json":
        try:
            json.loads(text)
        except json.JSONDecodeError as e:
            problems.append(f"json 解析失败: {e}")

    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description="编码校验（ADR-0019）")
    parser.add_argument("--staged", action="store_true", help="检查暂存区（默认）")
    parser.add_argument("--paths", nargs="*", help="检查指定路径")
    args = parser.parse_args()

    if args.paths:
        files = [Path(p) for p in args.paths]
    else:
        files = staged_files()

    # 只保留 .py / .json，排除目录
    targets = [f for f in files if f.suffix in (".py", ".json") and not is_excluded(f)]

    if not targets:
        return 0

    failed = 0
    for f in targets:
        problems = check_file(f)
        if problems:
            failed += 1
            print(f"❌ {f}")
            for p in problems:
                print(f"    {p}")

    if failed:
        print(f"\n编码校验未通过：{failed} 个文件有问题。", file=sys.stderr)
        print("修复：确保 .py/.json 无 BOM、UTF-8 合法、语法/结构可解析。", file=sys.stderr)
        print("见 ADR-0019。", file=sys.stderr)
        return 1

    print(f"编码校验通过：{len(targets)} 个文件 OK。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
