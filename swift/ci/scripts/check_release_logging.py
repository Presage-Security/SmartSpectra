#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# ///
"""Fail if Swift SDK runtime sources add unconditional print calls."""

from __future__ import annotations

import re
import sys
from pathlib import Path


RUNTIME_SOURCE_ROOT = Path(__file__).resolve().parents[2] / "sdk" / "Sources" / "SmartSpectra"
IGNORED_DIRS = {
    "Documentation",
    "Generated",
}
ALLOWED_PRINT_FILES = {
    Path("Extensions/Benchmarking.swift"),
}
PRINT_CALL = re.compile(r"\bprint\s*\(")


def main() -> int:
    violations: list[tuple[Path, int, str]] = []

    for source_file in sorted(RUNTIME_SOURCE_ROOT.rglob("*.swift")):
        relative_path = source_file.relative_to(RUNTIME_SOURCE_ROOT)
        if relative_path.parts[0] in IGNORED_DIRS:
            continue
        if relative_path in ALLOWED_PRINT_FILES:
            continue

        for line_number, line in enumerate(source_file.read_text(encoding="utf-8").splitlines(), start=1):
            if PRINT_CALL.search(line):
                violations.append((relative_path, line_number, line.strip()))

    if violations:
        print("Swift SDK runtime sources must use Logger.log(...) for release-suppressed diagnostics.")
        for relative_path, line_number, line in violations:
            print(f"{relative_path}:{line_number}: {line}")
        return 1

    print("Swift SDK release logging guard passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
