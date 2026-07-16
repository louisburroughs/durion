#!/usr/bin/env python3
"""
Validate curated backend contract guides.

Checks:
1. Required section headings exist.
2. Forbidden duplicated sections are absent.
3. operationId references in guide tables exist in module OpenAPI.
4. ADR and issue references match expected formats.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import yaml


REQUIRED_SECTION_HEADINGS = [
    "purpose & scope",
    "how to use this guide",
    "domain invariants",
    "capability index",
    "capability sections",
    "frontend api lookup",
    "events & cross-domain dependencies",
    "verification metadata",
    "references",
]

FORBIDDEN_SECTION_HEADINGS = [
    "json field naming conventions",
    "data types & formats",
    "enum value conventions",
    "identifier naming",
    "timestamp conventions",
    "collection & pagination",
    "error response format",
    "correlation id & request tracking",
    "api endpoints",
    "entity-specific contracts",
    "examples",
    "request schema",
    "response schema",
]

ISSUE_URL_RE = re.compile(r"https://github\.com/[^/\s]+/[^/\s]+/issues/\d+\b")
ISSUE_SHORT_RE = re.compile(r"\b[a-zA-Z0-9_.-]+#\d+\b")
ISSUE_NUMBER_RE = re.compile(r"(?<!\w)#\d+\b")
ISSUE_URL_ANY_RE = re.compile(r"https://github\.com/[^/\s]+/[^/\s]+/issues/([^\s)\]>]+)")

ADR_TOKEN_RE = re.compile(r"\bAD-[A-Za-z0-9-]+\b")
ADR_VALID_RE = re.compile(r"^AD-\d{3}$")
ADR_ALT_VALID_RE = re.compile(r"^ADR-\d+$")

OPENAPI_METHODS = {"get", "post", "put", "delete", "patch", "head", "options"}

DOMAIN_TO_MODULE = {
    "accounting": "pos-accounting",
    "crm": "pos-customer",
    "inventory": "pos-inventory",
    "location": "pos-location",
    "order": "pos-order",
    "people": "pos-people",
    "pricing": "pos-price",
    "security": "pos-security-service",
    "shopmgmt": "pos-shop-manager",
    "workexec": "pos-workorder",
    "billing": "pos-invoice",
    "product": "pos-catalog",
    "audit": "pos-event-receiver",
}


@dataclass
class ValidationResult:
    guide_path: Path
    violations: list[str]

    @property
    def is_valid(self) -> bool:
        return not self.violations


def parse_frontmatter(content: str) -> tuple[dict[str, Any], str]:
    if not content.startswith("---\n"):
        return {}, content
    end = content.find("\n---\n", 4)
    if end == -1:
        return {}, content
    frontmatter = content[4:end]
    body = content[end + 5 :]
    try:
        data = yaml.safe_load(frontmatter) or {}
        if not isinstance(data, dict):
            return {}, body
        return data, body
    except Exception:
        return {}, body


def normalize_heading(text: str) -> str:
    text = text.strip().lower()
    text = re.sub(r"\s+", " ", text)
    return text


def extract_headings(markdown_body: str) -> list[str]:
    headings: list[str] = []
    for line in markdown_body.splitlines():
        match = re.match(r"^#{2,6}\s+(.+?)\s*$", line)
        if match:
            headings.append(normalize_heading(match.group(1)))
    return headings


def extract_operation_ids_from_tables(markdown_body: str) -> set[str]:
    lines = markdown_body.splitlines()
    operation_ids: set[str] = set()
    i = 0
    while i < len(lines):
        header = lines[i]
        if "|" not in header or "operationid" not in header.lower():
            i += 1
            continue
        if i + 1 >= len(lines) or "|" not in lines[i + 1]:
            i += 1
            continue
        header_cells = [cell.strip().lower() for cell in header.strip().strip("|").split("|")]
        try:
            op_col_idx = header_cells.index("operationid")
        except ValueError:
            i += 1
            continue
        j = i + 2
        while j < len(lines) and lines[j].lstrip().startswith("|"):
            row_cells = [cell.strip() for cell in lines[j].strip().strip("|").split("|")]
            if op_col_idx < len(row_cells):
                cell = row_cells[op_col_idx]
                for token in re.findall(r"`([A-Za-z][A-Za-z0-9_]*)`", cell):
                    operation_ids.add(token)
            j += 1
        i = j
    return operation_ids


def load_openapi_spec(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as f:
        if path.suffix.lower() in {".yaml", ".yml"}:
            return yaml.safe_load(f) or {}
        return json.load(f)


def extract_openapi_operation_ids(spec: dict[str, Any]) -> set[str]:
    operation_ids: set[str] = set()
    paths = spec.get("paths") or {}
    if not isinstance(paths, dict):
        return operation_ids
    for _, operations in paths.items():
        if not isinstance(operations, dict):
            continue
        for method, details in operations.items():
            if str(method).lower() not in OPENAPI_METHODS:
                continue
            if not isinstance(details, dict):
                continue
            op_id = details.get("operationId")
            if isinstance(op_id, str) and op_id.strip():
                operation_ids.add(op_id.strip())
    return operation_ids


def resolve_openapi_path(
    repo_root: Path, backend_root: Path, guide_path: Path, frontmatter: dict[str, Any]
) -> Path | None:
    source = frontmatter.get("openapi_source")
    candidates: list[Path] = []

    if isinstance(source, str) and source.strip():
        source = source.strip()
        if source.startswith("durion-positivity-backend/"):
            candidates.append(backend_root / source.removeprefix("durion-positivity-backend/"))
        elif source.startswith("pos-"):
            candidates.append(backend_root / source)
        else:
            candidates.append(repo_root / source)

    domain = guide_path.parts[-3]
    module = DOMAIN_TO_MODULE.get(domain)
    if module:
        candidates.extend(
            [
                backend_root / module / "openapi.yaml",
                backend_root / module / "openapi.yml",
                backend_root / module / "openapi.json",
                backend_root / module / "target" / "openapi.json",
            ]
        )

    for candidate in candidates:
        if candidate.exists():
            return candidate
    return None


def validate_issue_reference_format(body: str) -> list[str]:
    violations: list[str] = []
    for match in ISSUE_URL_ANY_RE.finditer(body):
        suffix = match.group(1)
        if not suffix.isdigit():
            violations.append(f"Malformed issue URL: {match.group(0)}")
    return violations


def validate_adr_reference_format(body: str) -> list[str]:
    violations: list[str] = []
    for token in ADR_TOKEN_RE.findall(body):
        if ADR_VALID_RE.match(token) or ADR_ALT_VALID_RE.match(token):
            continue
        violations.append(f"Malformed ADR token: {token}")
    return violations


def validate_guide(repo_root: Path, backend_root: Path, guide_path: Path) -> ValidationResult:
    content = guide_path.read_text(encoding="utf-8")
    frontmatter, body = parse_frontmatter(content)
    violations: list[str] = []

    headings = extract_headings(body)
    heading_set = set(headings)

    for required in REQUIRED_SECTION_HEADINGS:
        if required not in heading_set:
            violations.append(f"Missing required section heading: '{required}'")

    for forbidden in FORBIDDEN_SECTION_HEADINGS:
        if forbidden in heading_set:
            violations.append(f"Forbidden duplicated section heading present: '{forbidden}'")

    openapi_path = resolve_openapi_path(repo_root, backend_root, guide_path, frontmatter)
    if not openapi_path:
        violations.append("Unable to resolve OpenAPI path for guide")
    else:
        try:
            spec = load_openapi_spec(openapi_path)
            openapi_operation_ids = extract_openapi_operation_ids(spec)
            guide_operation_ids = extract_operation_ids_from_tables(body)
            if not guide_operation_ids:
                violations.append("No operationId values found in guide tables")
            else:
                missing = sorted(guide_operation_ids - openapi_operation_ids)
                if missing:
                    violations.append(
                        f"operationId values not found in OpenAPI ({openapi_path}): {', '.join(missing)}"
                    )
        except Exception as exc:
            violations.append(f"Failed to parse OpenAPI '{openapi_path}': {exc}")

    violations.extend(validate_issue_reference_format(body))
    violations.extend(validate_adr_reference_format(body))

    return ValidationResult(guide_path=guide_path, violations=violations)


def find_guides(repo_root: Path, domains: list[str], files: list[Path]) -> list[Path]:
    if files:
        return [p.resolve() for p in files]

    candidates = sorted(repo_root.glob("domains/*/.business-rules/BACKEND_CONTRACT_GUIDE.md"))
    if not domains:
        return candidates

    domain_set = {d.strip().lower() for d in domains if d.strip()}
    return [p for p in candidates if p.parts[-3].lower() in domain_set]


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate curated backend contract guides.")
    parser.add_argument(
        "--repo-root",
        type=Path,
        default=Path(__file__).resolve().parents[1],
        help="Path to durion repository root",
    )
    parser.add_argument(
        "--backend-root",
        type=Path,
        default=Path.home() / "Projects" / "durion-positivity-backend",
        help="Path to durion-positivity-backend repository root",
    )
    parser.add_argument(
        "--domain",
        action="append",
        default=[],
        help="Domain(s) to validate (repeatable), e.g. --domain accounting",
    )
    parser.add_argument(
        "--file",
        action="append",
        type=Path,
        default=[],
        help="Specific guide file(s) to validate (repeatable)",
    )
    args = parser.parse_args()

    repo_root = args.repo_root.resolve()
    backend_root = args.backend_root.resolve()

    guides = find_guides(repo_root, args.domain, args.file)
    if not guides:
        print("No guides found for validation.")
        return 1

    results = [validate_guide(repo_root, backend_root, path) for path in guides]

    failures = 0
    for result in results:
        rel = result.guide_path.relative_to(repo_root)
        if result.is_valid:
            print(f"PASS {rel}")
            continue
        failures += 1
        print(f"FAIL {rel}")
        for violation in result.violations:
            print(f"  - {violation}")

    print(f"\nValidated {len(results)} guide(s); failures: {failures}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
