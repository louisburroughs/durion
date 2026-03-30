#!/usr/bin/env python3
from __future__ import annotations

import argparse
import importlib.util
import json
import re
from dataclasses import asdict, dataclass
from pathlib import Path


@dataclass(frozen=True)
class PersonaMetadata:
    canonical_persona: str
    persona_family: str
    actor_type: str


def load_extractor(script_path: Path):
    spec = importlib.util.spec_from_file_location("extract_primary_personas", script_path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Unable to load extractor script: {script_path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def build_persona_rules() -> dict[str, PersonaMetadata]:
    rules: dict[str, PersonaMetadata] = {}

    def register(labels: list[str], canonical: str, family: str, actor_type: str = "human") -> None:
        metadata = PersonaMetadata(canonical, family, actor_type)
        for label in labels:
            rules[label] = metadata

    register(["Service Advisor"], "Service Advisor", "Service Advisor")
    register(
        [
            "Customer Service Representative",
            "CSR",
            "Customer Support Associate",
            "Counter Associate",
            "Front Desk",
            "Cashier",
            "POS Cashier",
            "POS Clerk",
        ],
        "Customer Support Associate",
        "Customer Support Associate",
    )
    register(["Account Manager", "Fleet Account Manager"], "Account Manager", "Account Management")
    register(["Marketing Manager"], "Marketing Manager", "Account Management")

    register(
        ["Shop Manager", "Store Manager", "Back Office", "Manager", "Approver"],
        "Location Manager",
        "Location Management",
    )
    register(["Back Office Manager"], "Back Office Manager", "Location Management")
    register(["Director"], "Director", "Location Management")
    register(["Dispatcher", "Scheduler", "Mobile Lead"], "Dispatcher", "Dispatch")

    register(["Technician", "Mechanic"], "Technician", "Technician")
    register(["Parts Manager"], "Parts Manager", "Parts")
    register(["Parts Associate", "Parts Counter Staff"], "Parts Associate", "Parts")

    register(
        ["Inventory Control Manager", "Inventory Manager", "Inventory Controller", "Inventory Admin"],
        "Inventory Control Manager",
        "Inventory Management",
    )
    register(["Inventory Staff"], "Inventory Staff", "Inventory Management")
    register(["Warehouse Manager"], "Warehouse Manager", "Warehouse")
    register(["Warehouse Associate", "Receiver", "Stock Clerk"], "Warehouse Associate", "Warehouse")

    register(
        [
            "Accounting Associate",
            "Accountant",
            "GL Accountant",
            "GL Specialist",
            "Accounting Clerk",
            "Accounts Receivable Clerk",
            "AP Clerk",
            "Billing Specialist",
            "Billing Administrator",
            "Back Office Accountant",
            "Payroll Clerk",
            "Accounting Operations User",
            "Accounting OPS",
            "Accounting OPS User",
            "Accounting Operations Analyst",
            "Accounting OPS Specialist",
            "Accounting Integration Operator",
            "Accounting Support Analyst",
            "Integration Operator",
            "Finance OPS User",
            "Finance User",
        ],
        "Accounting Associate",
        "Accounting Associate",
    )

    register(["Controller", "Financial Controller", "Finance"], "Controller", "Finance / Accounting Management")
    register(["Finance Manager", "FinanceManager"], "Finance Manager", "Finance / Accounting Management")
    register(["Accounting Manager"], "Accounting Manager", "Finance / Accounting Management")

    register(["Auditor"], "Auditor", "Audit")
    register(["Compliance Auditor"], "Compliance Auditor", "Audit")
    register(["System Auditor"], "System Auditor", "Audit")

    register(["Product Admin", "Product Administrator"], "Product Administrator", "Product Administration")
    register(["Pricing Administrator"], "Pricing Administrator", "Pricing")
    register(["Pricing Analyst"], "Pricing Analyst", "Pricing")
    register(["Pricing Manager"], "Pricing Manager", "Pricing")

    register(
        ["System Administrator", "Admin", "Admin User", "Accounting Admin", "Finance Admin"],
        "System Administrator",
        "System Administration",
    )
    register(["Shop Administrator", "HR Administrator", "OPS Admin"], "Operations Management", "Operations Management")

    register(["Backend Engineer"], "Backend Engineer", "Technical / Platform")
    register(["Domain Architect"], "Domain Architect", "Technical / Platform")
    register(["Platform Engineer"], "Platform Engineer", "Technical / Platform")
    register(["Platform Integrator"], "Platform Integrator", "Technical / Platform")
    register(["Integration Support Engineer"], "Integration Support Engineer", "Technical / Platform")
    register(["Support Engineer"], "Support Engineer", "Technical / Platform")
    register(["Moqui Engineer"], "Moqui Engineer", "Technical / Platform")
    register(["CRM Data Steward"], "CRM Data Steward", "Technical / Platform")

    register(["System", "System User"], "System", "System", actor_type="system")

    return rules


def build_story_overrides() -> dict[str, str]:
    # Story-specific dominant-persona choices resolved by reviewing story intent /
    # acceptance-criteria language when the authored primary persona names multiple roles.
    return {
        "67": "Customer Support Associate",
        "70": "Customer Support Associate",
        "90": "Inventory Control Manager",
        "92": "Dispatcher",
        "93": "Parts Manager",
        "104": "Inventory Control Manager",
        "107": "Service Advisor",
        "114": "Service Advisor",
        "117": "Pricing Administrator",
        "118": "Pricing Analyst",
        "123": "Technician",
        "128": "Service Advisor",
        "133": "Location Manager",
        "156": "Integration Support Engineer",
        "158": "Customer Support Associate",
        "163": "Service Advisor",
        "165": "Support Engineer",
        "181": "Accounting Associate",
        "186": "System Administrator",
        "188": "Controller",
        "189": "Controller",
        "190": "Accounting Associate",
        "191": "Accounting Manager",
        "199": "Finance Manager",
        "200": "Accounting Associate",
        "202": "Controller",
        "203": "Controller",
        "204": "Finance Manager",
        "207": "Platform Engineer",
        "208": "Domain Architect",
        "209": "Accounting Associate",
        "212": "Service Advisor",
        "215": "Service Advisor",
        "216": "Service Advisor",
        "219": "Technician",
        "221": "Technician",
        "222": "Technician",
        "224": "Technician",
        "225": "Dispatcher",
        "226": "Location Manager",
        "228": "Service Advisor",
        "239": "Service Advisor",
        "242": "Warehouse Manager",
        "243": "Technician",
        "280": "Moqui Engineer",
    }


def extract_story_id(path: Path) -> str:
    match = re.search(r"\.(\d+)\.", path.name)
    return match.group(1) if match else path.stem


def dedupe_metadata(items: list[PersonaMetadata]) -> list[PersonaMetadata]:
    seen: set[tuple[str, str, str]] = set()
    result: list[PersonaMetadata] = []
    for item in items:
        key = (item.canonical_persona, item.persona_family, item.actor_type)
        if key not in seen:
            seen.add(key)
            result.append(item)
    return result


def render_single_header(primary_persona: str, metadata: PersonaMetadata) -> str:
    return "\n".join(
        [
            "- `primary_persona`: " + f"`{primary_persona}`",
            "- `canonical_persona`: " + f"`{metadata.canonical_persona}`",
            "- `persona_family`: " + f"`{metadata.persona_family}`",
            "- `actor_type`: " + f"`{metadata.actor_type}`",
        ]
    )


def render_multi_header(primary_persona: str, metadata_items: list[PersonaMetadata]) -> str:
    lines = [f"- `primary_persona`: `{primary_persona}`", "- `persona_metadata_candidates`:"]
    for item in metadata_items:
        lines.extend(
            [
                f"  - `canonical_persona`: `{item.canonical_persona}`",
                f"    `persona_family`: `{item.persona_family}`",
                f"    `actor_type`: `{item.actor_type}`",
            ]
        )
    return "\n".join(lines)


def main() -> int:
    repo_root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(
        description="Build story-by-story persona header suggestions from extracted capability stories."
    )
    parser.add_argument(
        "capabilities_root",
        nargs="?",
        default=repo_root / "docs" / "capabilities",
        type=Path,
        help="Path to docs/capabilities.",
    )
    parser.add_argument(
        "--output-md",
        default=repo_root / "persona_headers_by_story.md",
        type=Path,
        help="Markdown output path.",
    )
    parser.add_argument(
        "--output-json",
        default=repo_root / "persona_headers_by_story.json",
        type=Path,
        help="JSON output path.",
    )
    args = parser.parse_args()

    capabilities_root = args.capabilities_root.resolve()
    extractor = load_extractor(repo_root / "scripts" / "extract_primary_personas.py")
    rules = build_persona_rules()
    story_overrides = build_story_overrides()

    story_entries = []
    review_needed = []

    for story_file in extractor.iter_story_files(capabilities_root):
        raw_primary = extractor.extract_primary_persona(story_file.read_text(encoding="utf-8"))
        if not raw_primary:
            continue

        normalized_labels = extractor.split_personas(raw_primary)
        mapped = []
        unmapped = []
        for label in normalized_labels:
            metadata = rules.get(label)
            if metadata is None:
                unmapped.append(label)
            else:
                mapped.append(metadata)

        mapped = dedupe_metadata(mapped)
        story_id = extract_story_id(story_file)
        capability_slug = story_file.parts[-4]
        issue_url = f"https://github.com/louisburroughs/durion-moqui-frontend/issues/{story_id}"

        override = story_overrides.get(story_id)
        if override:
            override_matches = [item for item in mapped if item.canonical_persona == override]
            if override_matches:
                mapped = [override_matches[0]]

        entry = {
            "story_id": story_id,
            "capability": capability_slug,
            "story_path": str(story_file),
            "issue_url": issue_url,
            "primary_persona": raw_primary,
            "normalized_personas": normalized_labels,
            "persona_metadata": [asdict(item) for item in mapped],
            "unmapped_personas": unmapped,
            "review_required": bool(unmapped or len(mapped) > 1),
        }
        story_entries.append(entry)

        if entry["review_required"]:
            review_needed.append(entry)

    story_entries.sort(key=lambda item: int(item["story_id"]) if str(item["story_id"]).isdigit() else item["story_id"])
    review_needed.sort(key=lambda item: int(item["story_id"]) if str(item["story_id"]).isdigit() else item["story_id"])

    md_lines = [
        "# Story Persona Header Candidates",
        "",
        "Generated from `scripts/extract_primary_personas.py` and the reconciliation rules in `personas.md`.",
        "",
        "This is a review artifact. It is safer than writing directly to story files or GitHub issues before the mappings are validated.",
        "",
        "## Summary",
        "",
        f"- Stories with extracted primary persona: {len(story_entries)}",
        f"- Stories requiring review: {len(review_needed)}",
        "",
    ]

    if review_needed:
        md_lines.extend(["## Review Needed", ""])
        for entry in review_needed:
            reasons = []
            if entry["unmapped_personas"]:
                reasons.append("unmapped personas: " + ", ".join(f"`{label}`" for label in entry["unmapped_personas"]))
            if len(entry["persona_metadata"]) > 1:
                reasons.append("multiple persona candidates")
            md_lines.append(
                f"- Story `{entry['story_id']}` in [{Path(entry['story_path']).name}]({entry['story_path']}): "
                + "; ".join(reasons)
            )
        md_lines.append("")

    md_lines.extend(["## By Story", ""])
    for entry in story_entries:
        story_path = Path(entry["story_path"])
        md_lines.extend(
            [
                f"### Story `{entry['story_id']}`",
                "",
                f"- Story file: [{story_path.name}]({story_path})",
                f"- Capability: `{entry['capability']}`",
                f"- Issue URL: <{entry['issue_url']}>",
                "",
                "Suggested header block:",
                "",
                "```md",
            ]
        )
        if len(entry["persona_metadata"]) == 1 and not entry["unmapped_personas"]:
            md_lines.append(
                render_single_header(
                    entry["primary_persona"],
                    PersonaMetadata(**entry["persona_metadata"][0]),
                )
            )
        else:
            metadata_items = [PersonaMetadata(**item) for item in entry["persona_metadata"]]
            md_lines.append(render_multi_header(entry["primary_persona"], metadata_items))
            if entry["unmapped_personas"]:
                md_lines.append(
                    "- `review_note`: "
                    + f"`Unmapped normalized personas: {', '.join(entry['unmapped_personas'])}`"
                )
            if len(entry["persona_metadata"]) > 1:
                md_lines.append(
                    "- `review_note`: "
                    + "`Multiple persona candidates extracted; choose the best header before writing back to the story.`"
                )
        md_lines.extend(["```", ""])

    args.output_md.write_text("\n".join(md_lines), encoding="utf-8")
    args.output_json.write_text(json.dumps(story_entries, indent=2), encoding="utf-8")

    print(f"Wrote markdown: {args.output_md}")
    print(f"Wrote json: {args.output_json}")
    print(f"Stories requiring review: {len(review_needed)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
