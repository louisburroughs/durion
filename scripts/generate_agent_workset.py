#!/usr/bin/env python3
"""
Generate AGENT_WORKSET.yaml for a capability folder.

The workset is derived from:
- docs/capabilities/<CAP-XXX>/CAPABILITY_MANIFEST.yaml
- docs/capabilities/<CAP-XXX>/stories/frontend/*.frontend.md
- durion-positivity-backend/<module>/openapi.yaml

Usage:
  python3 scripts/generate_agent_workset.py --capability CAP-002
  python3 scripts/generate_agent_workset.py --capability CAP-002 --dry-run
"""

from __future__ import annotations

import argparse
import re
from pathlib import Path
from typing import Any

import yaml


HTTP_PATH_PATTERN = re.compile(r"\b(GET|POST|PUT|PATCH|DELETE)\s+(/[^\s)`]+)", re.IGNORECASE)
OPERATION_ID_PATTERN = re.compile(r"\b([a-z]+[A-Za-z0-9]+)\b")

SDK_PACKAGE_BY_DOMAIN = {
    "workexec": "packages/sdk-workorder",
    "order": "packages/sdk-order",
    "inventory": "packages/sdk-inventory",
    "security": "packages/sdk-security",
    "accounting": "packages/sdk-accounting",
}

ENDPOINT_OPERATION_RULES: list[tuple[str, str, str]] = [
    ("POST", "/api/estimates", "createEstimate"),
    ("GET", "/api/estimates/{estimateid}", "getEstimateById"),
    ("POST", "/items/parts", "addEstimateItem"),
    ("POST", "add-part", "addEstimateItem"),
    ("POST", "items:add-part", "addEstimateItem"),
    ("PATCH", "/items/", "updateEstimateItem"),
    ("PUT", "/items/", "updateEstimateItem"),
    ("POST", "calculate", "calculateEstimateTotals"),
    ("POST", "pricing/v1/calculate-totals", "calculateEstimateTotals"),
    ("POST", "summary-snapshots", "createEstimateSnapshot"),
    ("POST", "/snapshots", "createEstimateSnapshot"),
    ("GET", "summary", "getEstimateSummary"),
    ("GET", "summary-snapshots", "getEstimateSummary"),
    ("POST", "revise", "reopenEstimate"),
    ("POST", "revisions", "reopenEstimate"),
]

TITLE_OPERATION_RULES: list[tuple[str, list[str]]] = [
    ("create draft", ["createEstimate", "getEstimateById"]),
    ("add parts", ["addEstimateItem", "updateEstimateItem", "calculateEstimateTotals", "getEstimateById"]),
    ("add labor", ["addEstimateItem", "updateEstimateItem", "calculateEstimateTotals", "getEstimateById"]),
    ("calculate taxes and totals", ["calculateEstimateTotals", "getEstimateById"]),
    ("revise estimate", ["reopenEstimate", "patchEstimateStatus", "getEstimateById"]),
    ("present estimate summary", ["createEstimateSnapshot", "getEstimateSummary", "getEstimateById"]),
]


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    parser.add_argument("--capability", required=True, help="Capability folder name, e.g., CAP-002")
    parser.add_argument("--workspace-root", default=".")
    parser.add_argument("--backend-root", default="../durion-positivity-backend")
    parser.add_argument("--dry-run", action="store_true")
    return parser


def read_yaml(path: Path) -> dict[str, Any]:
    return yaml.safe_load(path.read_text(encoding="utf-8")) or {}


def normalize_openapi_spec_path(spec_path: str) -> str:
    if spec_path.endswith("/target/openapi.json"):
        return spec_path.replace("/target/openapi.json", "/openapi.yaml")
    if spec_path.endswith("openapi.json"):
        return spec_path.replace("openapi.json", "openapi.yaml")
    return spec_path


def normalize_story_path(raw_story_path: str, capability_dir: Path) -> Path:
    if raw_story_path.startswith("./"):
        return capability_dir / raw_story_path[2:]
    return capability_dir / raw_story_path


def collect_openapi_operation_ids(openapi_path: Path) -> set[str]:
    openapi = read_yaml(openapi_path)
    operation_ids: set[str] = set()
    paths = openapi.get("paths") or {}
    for methods in paths.values():
        if not isinstance(methods, dict):
            continue
        for operation in methods.values():
            if not isinstance(operation, dict):
                continue
            operation_id = operation.get("operationId")
            if operation_id:
                operation_ids.add(operation_id)
    return operation_ids


def extract_endpoint_hints(story_text: str) -> list[tuple[str, str]]:
    return [(method.upper(), path.split("?")[0]) for method, path in HTTP_PATH_PATTERN.findall(story_text)]


def add_known_operation(inferred: set[str], known_operation_ids: set[str], operation_id: str) -> None:
    if operation_id in known_operation_ids:
        inferred.add(operation_id)


def infer_from_endpoint_hints(
    hints: list[tuple[str, str]], inferred: set[str], known_operation_ids: set[str]
) -> None:
    for method, path in hints:
        normalized = path.lower()
        for rule_method, contains_text, operation_id in ENDPOINT_OPERATION_RULES:
            if method == rule_method and contains_text in normalized:
                add_known_operation(inferred, known_operation_ids, operation_id)


def infer_from_title(story_title: str, inferred: set[str], known_operation_ids: set[str]) -> None:
    title_lower = story_title.lower()
    for contains_text, operation_ids in TITLE_OPERATION_RULES:
        if contains_text in title_lower:
            for operation_id in operation_ids:
                add_known_operation(inferred, known_operation_ids, operation_id)


def infer_from_explicit_tokens(story_text: str, inferred: set[str], known_operation_ids: set[str]) -> None:
    for token in OPERATION_ID_PATTERN.findall(story_text):
        if token in known_operation_ids:
            inferred.add(token)


def infer_operation_ids_from_hints(story_title: str, story_text: str, known_operation_ids: set[str]) -> list[str]:
    hints = extract_endpoint_hints(story_text)
    inferred: set[str] = set()

    infer_from_endpoint_hints(hints, inferred, known_operation_ids)
    infer_from_title(story_title, inferred, known_operation_ids)
    infer_from_explicit_tokens(story_text, inferred, known_operation_ids)

    return sorted(inferred)


def select_wireframe(frontend_block: dict[str, Any]) -> str:
    wireframes = frontend_block.get("wireframes") or []
    for wireframe in wireframes:
        if not isinstance(wireframe, dict):
            continue
        if wireframe.get("type") == "wireframe" and wireframe.get("path"):
            return str(wireframe["path"])
    return ""


def resolve_openapi_spec(openapi: dict[str, Any], backend_root: Path) -> tuple[str, Path | None]:
    openapi_spec_rel = normalize_openapi_spec_path(str(openapi.get("spec_path") or ""))
    openapi_spec_full = backend_root / openapi_spec_rel
    if openapi_spec_full.exists():
        return openapi_spec_rel, openapi_spec_full

    fallback = backend_root / "pos-workorder/openapi.yaml"
    if fallback.exists():
        return str(fallback.relative_to(backend_root)), fallback

    return openapi_spec_rel, None


def build_story_record(
    story: dict[str, Any],
    capability_dir: Path,
    backend_root: Path,
    sdk_package: str,
) -> dict[str, Any] | None:
    parent_story = story.get("parent_story") or {}
    children = story.get("children") or {}
    frontend = children.get("frontend") or {}
    backend = children.get("backend") or {}
    contract = story.get("contract_guide") or {}
    openapi = contract.get("openapi") or {}

    frontend_story_path_raw = frontend.get("story_markdown_path")
    if not isinstance(frontend_story_path_raw, str) or not frontend_story_path_raw:
        return None

    frontend_story_path = normalize_story_path(frontend_story_path_raw, capability_dir)
    if not frontend_story_path.exists():
        return None

    backend_issue = backend.get("issue")
    if backend_issue is None:
        return None

    openapi_spec_rel, openapi_spec_full = resolve_openapi_spec(openapi, backend_root)
    known_operation_ids = collect_openapi_operation_ids(openapi_spec_full) if openapi_spec_full else set()
    story_text = frontend_story_path.read_text(encoding="utf-8", errors="ignore")
    operation_ids = infer_operation_ids_from_hints(str(parent_story.get("title") or ""), story_text, known_operation_ids)

    return {
        "parent_issue": parent_story.get("issue"),
        "frontend_issue": frontend.get("issue"),
        "frontend_story_md": str(frontend_story_path.relative_to(capability_dir.parent.parent.parent)),
        "backend_issue": backend_issue,
        "wireframe": select_wireframe(frontend),
        "contract_guide": str(contract.get("path") or ""),
        "sdk_package": sdk_package,
        "openapi_spec": f"durion-positivity-backend/{openapi_spec_rel}" if openapi_spec_rel else "",
        "operation_ids": operation_ids,
    }


def build_workset(capability_dir: Path, backend_root: Path) -> dict[str, Any]:
    manifest_path = capability_dir / "CAPABILITY_MANIFEST.yaml"
    manifest = read_yaml(manifest_path)

    capability_id = ((manifest.get("meta") or {}).get("capability_id") or "")
    domain = ((manifest.get("parent_capability") or {}).get("domain") or "")
    sdk_package = SDK_PACKAGE_BY_DOMAIN.get(domain, "")

    stories_out: list[dict[str, Any]] = []
    for story in manifest.get("stories") or []:
        if not isinstance(story, dict):
            continue
        story_record = build_story_record(story, capability_dir, backend_root, sdk_package)
        if story_record is not None:
            stories_out.append(story_record)

    return {
        "capability_id": capability_id,
        "domain": domain,
        "manifest": str((capability_dir / "CAPABILITY_MANIFEST.yaml").relative_to(capability_dir.parent.parent.parent)),
        "stories": stories_out,
    }


def main() -> int:
    args = build_parser().parse_args()

    workspace_root = Path(args.workspace_root).resolve()
    capability_dir = workspace_root / "docs" / "capabilities" / args.capability
    backend_root = (workspace_root / args.backend_root).resolve()

    if not capability_dir.exists():
        print(f"Capability directory not found: {capability_dir}")
        return 1

    workset = build_workset(capability_dir, backend_root)
    output_path = capability_dir / "AGENT_WORKSET.yaml"
    rendered = yaml.dump(workset, sort_keys=False, default_flow_style=False)

    if args.dry_run:
        print(rendered)
        return 0

    output_path.write_text(rendered, encoding="utf-8")
    print(f"Wrote {output_path}")
    print(f"stories={len(workset.get('stories') or [])}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())