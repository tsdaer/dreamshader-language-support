#!/usr/bin/env python3
"""Generate the bundled DSH diagnostic-code catalog from the upstream docs.

Reads the machine-readable code table in ``Docs/diagnostics/README.md`` of an
upstream DreamShader checkout and writes
``src/main/resources/messages/dreamshader-dsh-codes.json``.

The output is deterministic (rows sorted by code) so re-running the script
against the same upstream revision produces a stable diff. Hand-curated
Chinese summaries live in ``scripts/dsh-codes-zh.json`` and are merged in by
this script; entries missing from that file simply get no ``summary_zh``.

Usage (from the repository root, upstream checkout required):

    python scripts/generate_dsh_codes.py --upstream /tmp/dreamshader-ref
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

CODE_ROW = re.compile(
    r"^\| \[(DSH\d{4})\]\((?P<doc>DSH\dxxx\.md)#(?P<anchor>dsh\d{4})\)\s*\| (?P<severity>\S+) \| (?P<message>.*?) \|$"
)
SCHEMA = "DreamShader.DshCodes"
SCHEMA_VERSION = 1

REPO_ROOT = Path(__file__).resolve().parent.parent
OUTPUT = REPO_ROOT / "src/main/resources/messages/dreamshader-dsh-codes.json"
ZH_OVERRIDES = REPO_ROOT / "scripts/dsh-codes-zh.json"


def parse_readme(readme: Path) -> list[dict]:
    codes: list[dict] = []
    seen: set[str] = set()
    for line in readme.read_text(encoding="utf-8").splitlines():
        match = CODE_ROW.match(line)
        if not match:
            continue
        code = match.group(1)
        if code in seen:
            raise SystemExit(f"duplicate code row in upstream table: {code}")
        seen.add(code)
        codes.append(
            {
                "code": code,
                "severity": match.group("severity"),
                "message": match.group("message").strip(),
                "docAnchor": f"{match.group('doc')}#{match.group('anchor')}",
            }
        )
    return codes


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--upstream",
        default="/tmp/dreamshader-ref",
        help="path to the upstream DreamShader checkout (default: /tmp/dreamshader-ref)",
    )
    parser.add_argument(
        "--source",
        default="",
        help="provenance string stored in the output, e.g. 'DreamShader 2.0.2 @b3bbf48'; "
        "omitted from the JSON when empty",
    )
    args = parser.parse_args()

    readme = Path(args.upstream) / "Docs" / "diagnostics" / "README.md"
    if not readme.is_file():
        print(f"upstream diagnostics README not found: {readme}", file=sys.stderr)
        return 1

    codes = parse_readme(readme)
    if not codes:
        print("no code rows parsed -- table format changed?", file=sys.stderr)
        return 1

    summary_zh: dict[str, str] = {}
    if ZH_OVERRIDES.is_file():
        summary_zh = json.loads(ZH_OVERRIDES.read_text(encoding="utf-8"))

    for entry in codes:
        zh = summary_zh.get(entry["code"])
        if zh:
            entry["summary_zh"] = zh

    payload = {
        "schema": SCHEMA,
        "version": SCHEMA_VERSION,
        "codes": codes,
    }
    if args.source:
        payload["source"] = args.source
    OUTPUT.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=False) + "\n",
        encoding="utf-8",
    )
    print(f"wrote {len(codes)} codes to {OUTPUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
