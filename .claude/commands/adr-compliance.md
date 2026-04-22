---
name: ADR Compliance Crawl Prompt
description: Audit durion-positivity-backend for ADR non-compliance and generate a repair-planning report in markdown or YAML.
---


# ADR Compliance Crawl

Run a compliance audit against `durion-positivity-backend` using ACCEPTED ADRs as the authoritative policy.

## Inputs

- `TARGET_REPO` (default: `durion-positivity-backend`)
- `ADR_ROOT` (default: `durion/docs/adr`)
- `OUTPUT_FORMAT` (`md` or `yaml`, default: `md`)
- `OUTPUT_PATH` (optional; if omitted, write under `durion-positivity-backend/docs/compliance/`)
- `MODULE_SCOPE` (optional; comma-separated list like `pos-inventory,pos-order`)
- `SEVERITY_THRESHOLD` (optional; `low|medium|high`, default: `low`)

## Objective

Produce a machine-usable non-compliance report that identifies ADR violations at class/config level and provides a prioritized repair plan.

## Procedure

1. Read ADR index and statuses:
- `durion/docs/adr/README.md`

2. Load ADR decision set:
- include only `ACCEPTED` ADRs
- exclude superseded/deprecated decisions where explicitly indicated

3. Build audit rules from ADRs:
- emit a rule list with:
  - `adr_id`
  - `rule_id`
  - `description`
  - `check_type` (`machine-checkable|manual-review-required`)

4. Crawl code:
- target Java source and relevant config/resources in `TARGET_REPO`
- if `MODULE_SCOPE` provided, limit scan accordingly

5. Emit findings:
- include all required finding fields from the agent contract
- suppress duplicates; keep strongest evidence

6. Build repair queue:
- order by severity then estimated effort
- group by module
- include dependency notes (what should be fixed first)

7. Write output:
- if `OUTPUT_PATH` not provided:
  - markdown: `durion-positivity-backend/docs/compliance/adr-non-compliance-report.md`
  - yaml: `durion-positivity-backend/docs/compliance/adr-non-compliance-report.yaml`

## Markdown Output Template

```md
# ADR Non-Compliance Report

## Scan Metadata
- timestamp:
- target_repo:
- adr_root:
- output_format:
- module_scope:

## ADR Rules Audited
| ADR | Rule ID | Check Type | Description |
|-----|---------|------------|-------------|

## Findings
### High
- ID:
  - ADR:
  - Rule:
  - Module:
  - Class:
  - File:
  - Line:
  - Evidence:
  - Non-compliance:
  - Repair recommendation:
  - Effort:
  - Confidence:

### Medium

### Low

## Repair Queue
1. NC-XXXX - <short action>

## Open Questions
- <ambiguities or ADR gaps>
```

## YAML Output Template

```yaml
scan_metadata:
  timestamp: ""
  target_repo: "durion-positivity-backend"
  adr_root: "durion/docs/adr"
  module_scope: []
  severity_threshold: "low"

adr_rules:
  - adr_id: "ADR-0026"
    rule_id: "service_package_interfaces_only"
    check_type: "machine-checkable"
    description: "Classes in ..service.. must be interfaces"

findings:
  - id: "NC-0001"
    severity: "high"
    confidence: "high"
    adr_id: "ADR-0026"
    rule_id: "service_package_interfaces_only"
    module: "pos-inventory"
    class_name: "com.positivity.inventory.service.SomeServiceImpl"
    file: "pos-inventory/src/main/java/.../SomeServiceImpl.java"
    line: 42
    evidence: "Concrete class found in public service package"
    non_compliance: "Public service package contains implementation class"
    repair_recommendation: "Move implementation to internal.service and keep interface in service package"
    repair_effort: "S"
    repair_owner: "backend-module-owner"

repair_queue:
  - rank: 1
    finding_id: "NC-0001"
    action: "Refactor service boundary"
    dependency: null

open_questions: []
```

## Validation Rules

- The report must be deterministic and reproducible from current repo state.
- Every finding must reference an ADR ID.
- Every finding must include file evidence.
- If no findings exist, output empty findings with a completion summary.
