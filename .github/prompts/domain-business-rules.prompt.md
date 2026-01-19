---
name: 'Domain Business Rules Documentation'
agent: 'Story Authoring Agent'
description: 'Generate comprehensive domain business rules documentation for the a given domain, including normative agent guides, non-normative decision notes, and story validation checklists, modeled on the accounting domain examples.'
model: GPT-5.2
---
# DOMAIN_BUSINESS_RULES_PROMPT.md

You are a domain expert in {domain} for the Durion enterprise truck service management system. Use the accounting domain files in `durion/domains/accounting/.business-rules/` as templates and examples when producing deliverables for the {domain} domain. Your task: read the "Open Questions" sections in the {domain} AGENT_GUIDE and STORY_VALIDATION_CHECKLIST (or the provided source files), resolve every open question, reconcile todos, and output three complete Markdown documents using the exact filenames below.

Primary outputs (exact filenames)
- `AGENT_GUIDE.md` — normative agent guide for the {domain} domain (modeled on the accounting AGENT_GUIDE structure and Decision Index). This is an update from the provided AGENT_GUIDE file, incorporating resolved questions and reconciled todos.
- `{DOMAIN}_DOMAIN_NOTES.md` — non-normative, verbose rationale and decision log (modeled on ACCOUNTING_DOMAIN_NOTES.md). This is a new file (if a file exists. replace it).
- `STORY_VALIDATION_CHECKLIST.md` — story validation checklist (modeled on the accounting STORY_VALIDATION_CHECKLIST.md). This is an update from the provided STORY_VALIDATION_CHECKLIST file, incorporating resolved questions and reconciled todos.

Core rules for outputs
- Each file must begin with an H1 heading that is the filename (for example: `# AGENT_GUIDE.md`). After that, use `##` for top-level document sections and `###`/`####` as needed for subsections.
- Include a 2–4 sentence Summary at the top and a "Completed items" checklist in each file.
- Decision IDs must follow this pattern: `DECISION-INVENTORY-###` (zero-padded 3 digits). Each Decision ID listed in `AGENT_GUIDE.md` must have a corresponding detailed section in `{DOMAIN}_DOMAIN_NOTES.md`.
- Wherever the accounting examples contain a useful pattern (Decision Index table, Domain Boundaries, Key Entities, Invariants, Ingestion monitoring, idempotency, permission gating, etc.), replicate the structure and adapt content to {domain} semantics.

CRITICAL, MANDATORY END-OF-DOCUMENT REQUIREMENT (READ CAREFULLY)
- Each of the three output Markdown files MUST end exactly with the following lines (including the single blank line between them), and nothing else after them (no extra text, no additional blank lines, no trailing whitespace beyond a single newline if your system enforces one):
## End

End of document.
- This requirement is absolute: files that do not end with those lines exactly are non-compliant. Document generation and concatenation must preserve these final lines inside each individual file content (i.e., each file's content, before being placed into the concatenated response, must itself end with that two-line block).

Required templates to include verbatim in the prompt (fill-in sections for the agent to populate):

=== AGENT_GUIDE.md TEMPLATE ===

## Summary

- [2–4 sentences summarizing scope, what changed, and why]

## Completed items

- [ ] Generated Decision Index
- [ ] Mapped Decision IDs to `{DOMAIN}_DOMAIN_NOTES.md`
- [ ] Reconciled todos from original AGENT_GUIDE

## Decision Index

| Decision ID | Title |
| --- | --- |
| DECISION-INVENTORY-001 | [Short title] |
| DECISION-INVENTORY-002 | [Short title] |

## Domain Boundaries

- What Inventory owns (system of record)
- What Inventory does *not* own

## Key Entities / Concepts

| Entity | Description |
| --- | --- |
| InventoryItem | [description] |
| Reservation | [description] |

## Invariants / Business Rules

- [Enumerate core invariants; follow accounting example style: rounding, state transitions, forbidden states]

## Mapping: Decisions → Notes

| Decision ID | One-line summary | Link to notes |
| --- | --- | --- |
| DECISION-INVENTORY-001 | [summary] | [{DOMAIN}_DOMAIN_NOTES.md](#decision-{domain}-001---short-title) |

## Open Questions (from source)

For each open question discovered in the source files, include a subsection using this format:

### Q: <original question text>

- Answer: <1–3 sentence answer>
- Assumptions:
	- <bullet list>
- Rationale:
	- <bullet list or short paragraph>
- Impact:
	- <systems, APIs, tests, migration>
- Decision ID: DECISION-INVENTORY-###

## Todos Reconciled

- Original todo: "<text>" → Resolution: Resolved (brief plan) | Defer (reason, owner) | Replace with task: `TASK-INV-###`

=== END AGENT_GUIDE.md TEMPLATE ===

=== {DOMAIN}_DOMAIN_NOTES.md TEMPLATE ===

## Summary

- [2–4 sentences explaining purpose and scope of notes for auditors and architects]

## Completed items

- [ ] Linked each Decision ID to a detailed rationale

## Decision details

Each decision must have a section in this format. Use the decision ID anchor exactly as shown so `AGENT_GUIDE.md` can link to it.

### DECISION-INVENTORY-### — [Short Title]

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: <concise statement of the decision>
- Alternatives considered:
	- Option A: pros/cons
	- Option B: pros/cons
- Reasoning and evidence:
	- <detailed narrative, evidence, and decisions>
- Architectural implications:
	- Components affected (APIs, DB schema, read models)
	- ASCII or mermaid diagram (optional)
- Auditor-facing explanation:
	- What to inspect, what logs/read models to query, example queries or audit artifacts
- Migration & backward-compatibility notes:
	- Steps, data migration commands, safe-to-deploy order
- Governance & owner recommendations:
	- Suggested owner, review cadence, gating criteria for changes

Repeat for each decision.

=== END {DOMAIN}_DOMAIN_NOTES.md TEMPLATE ===

=== STORY_VALIDATION_CHECKLIST.md TEMPLATE ===

## Summary

- [2–4 sentence summary of checklist scope and changes]

## Completed items

- [ ] Updated acceptance criteria for each resolved open question

## Scope / Ownership

- [ ] Confirm story labeled `domain:{domain}`
- [ ] Verify primary actor(s) and permissions

## Data Model & Validation

- [ ] Validate required inputs and types (itemId, locationId, quantity, uom)
- [ ] Verify date/time and timezone semantics for {domain} events
- [ ] Quantity and rounding rules: [explicit rules]

## API Contract

- [ ] Verify endpoints, pagination, error handling, per-row errors

## Events & Idempotency

- [ ] Reservation and adjustment events include idempotency keys
- [ ] UI and services handle retry semantics (reuse idempotency key on retry)

## Security

- [ ] Permission gating for sensitive payloads and raw payload redaction

## Observability

- [ ] Ensure trace identifiers and audit fields surface in UI and logs

## Acceptance Criteria (per resolved question)

For each resolved open question, provide a testable acceptance criteria block:

### Q: <original question text>

- Acceptance: <clear pass/fail criteria>
- Test Fixtures: <example inputs>
- Example API request/response (code block)

=== END STORY_VALIDATION_CHECKLIST.md TEMPLATE ===

Additional rules
- Prefer accounting document structure: Decision Index table, explicit Decision IDs, and separation of normative vs non-normative content.
- If a question lacks context, pick a safe default, document assumptions, and mark the decision with `safe_to_defer: true` if it should be re-evaluated.
- File output behavior: The agent must write the three Markdown files to `/home/louisb/Projects/durion/domains/{domain}/.business-rules/`. If files with the same names already exist in that directory, rename/replace them. After writing the files, the agent should send a concise chat confirmation listing the file paths it created/updated (one line per file) and any brief notes (maximum two sentences). The agent must not output the full file contents to the chat console.


Output format for the agent run

- Primary behavior: The agent must create or update the three required Markdown files in `/home/louisb/Projects/durion/domains/{domain}/.business-rules/`. If files with the same names already exist, rename and replace them. Do not return the full file contents in the chat. After successfully writing the files, the agent should output only a brief confirmation message in chat listing the file paths written and a short success summary (max two sentences).

- Fallback behavior: If the execution environment prevents writing files to disk, the agent must clearly explain the limitation and only then return the concatenated file contents in the chat as a last resort. When returning content as a fallback, the agent must prefix the concatenation exactly with these markers:

=== FILE: AGENT_GUIDE.md ===
[full AGENT_GUIDE.md content]
=== FILE: {DOMAIN}_DOMAIN_NOTES.md ===
[full {DOMAIN}_DOMAIN_NOTES.md content]
=== FILE: STORY_VALIDATION_CHECKLIST.md ===
[full STORY_VALIDATION_CHECKLIST.md content]

Validation checklist
- Before finishing, ensure the generated files satisfy the completeness checklist (Decision mapping, detailed notes, reconciled todos, acceptance criteria for each resolved question).

---
