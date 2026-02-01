---
name: Backend Story Fulfillment
description: "Guided prompt for implementing backend endpoints/services for a capability story. Implements contract-driven behavior, provider contract tests, validation, and OpenAPI annotations. References backend architecture documents and module conventions."
agent: "agent"
model: GPT-5.2
---

# Backend Story Fulfillment Prompt

You are implementing capability {capability_id}.
Parent STORY: durion#{parent_story_number}
Backend child issue: {module}#{child_issue_number}

Contract guide entry (stable reference):
  durion repo, domains/{domain}/.business-rules/BACKEND_CONTRACT_GUIDE.md
  Anchor: [describe or link the specific section]

I need you to:
1. Implement the endpoint/service to match the contract
2. Add provider behavioral contract tests (`ContractBehaviorIT`)
3. Include examples from the contract guide in the tests
4. Add validation & error handling per the assertions
5. Include concurrency-safe patterns if needed (idempotency, optimistic locking)
6. Add or update OpenAPI annotations (`@Operation`, `@ApiResponse`, etc.) if the module exposes REST

Architecture & References (REPLACE "Module structure" with authoritative docs):
- See `durion-positivity-backend/AGENTS.md` for backend repo quick start, build, and run commands.
- Consult `docs/architecture/` (workspace root) for service and module-level architecture guidance.
- Review `pos-archunit` and existing ArchUnit tests for package and layering conventions.
- Module conventions & packaging: refer to `durion-positivity-backend/AGENTS.md` and `pos-*-module README` files for package layout and `com.positivity.{module}` rules.
- Event + telemetry rules: consult `pos-events` and `observability/` docs for `@EmitEvent` usage and event type registration patterns.

Implementation Patterns & Links (REPLACE "Follow existing pos-* module patterns"):
- Controller: Keep controllers thin. Validate input, map DTOs, delegate to service layer. Follow examples in other `pos-*` modules.
- Service: Implement orchestration, business rules, and transactional boundaries in the service layer; annotate transactional methods as required.
- Repository: Use Spring Data JPA repositories under `internal.repository` and prefer domain objects under `internal.entity`.
- DTOs/Entities: Keep DTOs in `internal.dto` and entities in `internal.entity`. Use `@NonNull` on non-null method params per workspace null-safety standards.
- Events: Emit domain events using `@EmitEvent` where state changes occur and register event types on startup using the EventTypeInitializer pattern.
- ArchUnit: Ensure new code follows internal package encapsulation and layering rules; add ArchUnit tests if needed.

Module File Layout (examples)
- `pos-<module>/src/main/java/com/positivity/<module>/Pos<Module>Application.java` (root)
- `pos-<module>/src/main/java/com/positivity/<module>/service/` (service layer)
- `pos-<module>/src/main/java/com/positivity/<module>/internal/controller/`
- `pos-<module>/src/main/java/com/positivity/<module>/internal/service/`
- `pos-<module>/src/main/java/com/positivity/<module>/internal/repository/`
- `pos-<module>/src/main/java/com/positivity/<module>/internal/entity/`
- `pos-<module>/src/main/java/com/positivity/<module>/internal/dto/`
- `pos-<module>/src/test/java/com/positivity/<module>/contract/` (ContractBehaviorIT)

Testing Requirements
- Add `ContractBehaviorIT` tests using examples from the contract guide covering: happy path, validation errors, auth failures, idempotency, and concurrency invariants.
- Add ArchUnit tests if the change introduces new packages or layering concerns.

Substitution & Validation

**Required Input Keys (JSON)**
- `capability_id` (string) — e.g., "cap:CAP275"
- `parent_story_number` (integer)
- `child_issue_number` (integer)
- `domain` (string)
- `module` (string) — pos module folder name, e.g., "pos-order"
- `package_base` (string) — Java package base, e.g., "com.positivity.order"

**Example JSON Input**
```json
{
  "capability_id": "cap:CAP275",
  "parent_story_number": 276,
  "child_issue_number": 456,
  "domain": "security",
  "module": "pos-order",
  "package_base": "com.positivity.order"
}
```

**Substitution Algorithm (Python)**
```python
def substitute_prompt(prompt_template, input_json):
    required_keys = {"capability_id","parent_story_number","child_issue_number","domain","module","package_base"}
    missing = required_keys - set(input_json.keys())
    if missing:
        raise ValueError(f"Missing keys: {missing}")
    result = prompt_template
    for k,v in input_json.items():
        result = result.replace("{"+k+"}", str(v))
    return result
```

**Validation Steps**
1. Parse JSON input; ensure valid syntax.
2. Ensure all required keys are present.
3. Validate numeric fields are integers.
4. Validate strings are non-empty.
5. Perform substitution and verify no `{placeholder}` remains (regex `\{[a-z_]+\}`).

Output Expectations
- Provide a concise implementation checklist (3–10 bullets) listing controllers, services, repositories, entities, DTOs, and tests to add/modify.
- List exact workspace-relative file paths to change or create.
- Provide code snippets for critical pieces: controller signature, service method, repository query, and a sample ContractBehaviorIT test using contract examples.
- Specify required configuration changes (if any), e.g., event type registration, properties, or feature flags.

Notes
- Do NOT hardcode secrets or credentials; use existing config and environment variables.
- Follow null-safety (`@NonNull`) and event-logging (`@EmitEvent`) conventions documented in backend AGENTS.md.
- If contract examples are missing for an edge case, add tests that capture desired behaviour and update the contract guide in `durion`.

---
Assume placeholders will be injected from the provided JSON input and validate that all required keys are present before proceeding.
