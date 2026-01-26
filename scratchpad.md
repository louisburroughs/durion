### Core policy

* **All contract truth lives in `durion`** under:

  * `domains/{domain}/.business-rules/BACKEND_CONTRACT_GUIDE.md`
* **Backend repo PRs may not be merged** unless the corresponding `durion` contract PR is merged (or explicitly waived).

This makes `durion` the canonical governance layer and keeps code honest.

---

## Capability execution workflow (practical + scalable)

### 0) Capability kickoff (you or coordinator agent)

For capability `cap:<cap-id>`:

* Create (or ensure) project view filtered by capability.
* Validate each parent STORY has:

  * backend child issue + frontend child issue
  * domain set
  * acceptance criteria and Gherkin
* Add/confirm labels:

  * `cap:<cap-id>`
  * `domain:<domain>`
  * `status:ready`

### 1) Backend contract PR (durion) — **first**

For each parent STORY:

* Agent opens a PR to `durion` branch: `cap/<cap-id>`
* Adds/updates the relevant entry in the domain contract guide:

  * story linkage: parent + child IDs
  * endpoint/event contract summary
  * **examples**
  * **behavioral assertions** (always-true invariants)
  * marker: `contract-status: draft`

This PR is small and fast: it “declares intent”.

### 2) Backend code PR(s)

* Agent implements backend repo changes on branch `cap/<cap-id>`
* Adds provider behavioral contract tests
* PR references:

  * Parent STORY link
  * Backend child issue
  * Contract guide anchor link
  * Durion contract PR link

Backend PR may be opened in parallel with the contract PR, but **cannot be merged** until contract PR is merged.

### 3) Contract freeze

When backend PR reaches “ready for review”:

* Agent updates durion guide entry marker to:

  * `contract-status: stable-for-ui`
* If contract semantics changed during implementation, update examples/assertions accordingly.

### 4) Frontend PR(s)

* Frontend agent begins only once guide entry is `stable-for-ui`.
* Frontend PR references:

  * Parent STORY link
  * Frontend child issue
  * Contract guide anchor link

### 5) Integration completion

* Parent STORY checklist updated (PR links + merged status)
* Capability proceeds story-by-story until complete.

---

## Required automation (recommended)

### A) PR templates (must exist)

In **every repo**, PR template requires:

* `Capability:` cap:<cap-id>
* `Parent STORY:` durion#123
* `Child issue:` <repo>#456
* `Contract guide entry:` link to anchor
* `Durion contract PR:` link (required if backend PR touches API/event behavior)

### B) Status checks / branch protection

* Enable required status checks per repo.
* Add a required check named e.g.:

  * `contract-sync` (see below)

### C) Contract-sync workflow (key best practice)

Create a workflow (backend repos) that fails the PR if:

* PR modifies controllers / OpenAPI / DTOs / event schemas, **and**
* PR does not link to a `durion` contract PR in the PR body (regex), **or**
* the referenced durion contract PR is not merged (optional enhancement)

Implementation can be incremental: start by requiring link presence; enforce merge dependency later.

---

## Behavioral contract tests (how to structure)

### Provider behavioral test suite (backend repo)

Create a test module/category like:

* `ContractBehaviorIT` or `ContractScenariosIT`

Each scenario maps to “Examples” in the guide:

* Happy path
* validation errors
* auth errors
* idempotency
* concurrency-safe invariants

These are **not** mock-only tests; they run against the service in test mode.

### Consumer behavioral tests (frontend)

At minimum:

* tests that UI handles error model and shape correctly
* contract-driven sample payload tests (use examples from the guide)

---

## Agent operating instructions (what to tell Copilot)

### “Two-PR” rule (mandatory)

For backend changes:

1. **PR A:** `durion` contract guide update
2. **PR B:** backend repo implementation

Both must reference parent STORY and each other.

### Branch naming (mandatory)

* Always: `cap/<cap-id>`
* Never: `feature/*` or arbitrary names

### Start conditions

* If contract entry missing → open durion PR first.
* If guide says `stable-for-ui` and backend changes it → must revert to `draft` and notify.

---

## One suggestion to simplify cross-repo agent work

Add a `capability workspace manifest` file in `durion`:

`docs/capabilities/<cap-id>/CAPABILITY_MANIFEST.yaml`

* impacted repos list
* stories list (parent + children)
* contract guide anchors
* wireframe paths

Agents can read this one file rather than crawling the whole project board.

---

If you want, I can draft:
1. a **contract-sync GitHub workflow** (link + changed-files check).


# docs/capabilities/<cap-id>/CAPABILITY_MANIFEST.yaml
# Purpose: Single, agent-friendly index for implementing one capability across multiple repos.
# Scope: Reference-only; canonical requirements remain in GitHub issues and domain contract guides.

meta:
  capability_id: "cap:<cap-id>"              # e.g., cap:pos-order-capture
  capability_name: "<Capability Name>"       # human-friendly name
  owner_repo: "louisburroughs/durion"        # coordination repo
  created_utc: "<YYYY-MM-DDTHH:MM:SSZ>"
  last_updated_utc: "<YYYY-MM-DDTHH:MM:SSZ>"

coordination:
  github_project_url: "<project url>"
  status_field_name: "Status"               # change if your project uses a different field name
  preferred_branch_prefix: "cap/"           # branch naming convention

contract_registry:
  # Canonical contract docs live in durion only (do not mirror).
  root_path: "domains"
  guide_path_suffix: ".business-rules/BACKEND_CONTRACT_GUIDE.md"
  contract_status_markers:
    - "draft"
    - "stable-for-ui"

repositories:
  # List every repo that will receive a cap/<cap-id> branch and PR(s).
  # For Moqui component repos, list each component repo explicitly.
  - name: "durion-positivity-backend"
    slug: "louisburroughs/durion-positivity-backend"
    type: "backend"
    notes: ""
  - name: "durion-moqui-frontend"
    slug: "louisburroughs/durion-moqui-frontend"
    type: "frontend-coordination"
    notes: "Frontend child issues live here; code changes may land in component repos."
  - name: "<moqui-component-repo-1>"
    slug: "louisburroughs/<moqui-component-repo-1>"
    type: "frontend-component"
    notes: ""
  - name: "<moqui-component-repo-2>"
    slug: "louisburroughs/<moqui-component-repo-2>"
    type: "frontend-component"
    notes: ""

stories:
  # One entry per parent STORY in durion.
  # Each parent has exactly one backend child and one frontend child.
  - parent:
      repo: "louisburroughs/durion"
      issue: 123
      title: "<Parent STORY title>"
      domain: "<domain>"                    # e.g., accounting, inventory, workexec
      labels:
        - "cap:<cap-id>"
        - "domain:<domain>"
      acceptance_criteria_ref: "<optional anchor or note>"
    children:
      backend:
        repo: "louisburroughs/durion-positivity-backend"
        issue: 456
      frontend:
        repo: "louisburroughs/durion-moqui-frontend"
        issue: 789
        impacted_component_repos:
          - "louisburroughs/<moqui-component-repo-1>"
          - "louisburroughs/<moqui-component-repo-2>"
        wireframes:
          # Prefer codebase-relative paths where the wireframes live.
          - repo: "louisburroughs/<moqui-component-repo-1>"
            path: "<relative path to wireframe file(s)>"
        business_rules:
          - repo: "louisburroughs/durion"
            path: "domains/<domain>/.business-rules/<file>.md"
    contract_guide:
      repo: "louisburroughs/durion"
      path: "domains/<domain>/.business-rules/BACKEND_CONTRACT_GUIDE.md"
      anchor: "<markdown anchor or heading text for this story>"
      status: "draft"                       # draft | stable-for-ui
      openapi:
        # Early iteration: do not semver-bump; later you can add version fields.
        producer_repo: "louisburroughs/durion-positivity-backend"
        spec_path: "<path if committed, else leave blank>"
        generated: true
    pr_links:
      # Optional: fill as work proceeds.
      durion_contract_pr: ""
      backend_pr: ""
      frontend_prs: []
    merge_order:
      - "durion_contract_pr"
      - "backend_pr"
      - "frontend_prs"

execution:
  agent_rules:
    - "Backend changes that affect API/event behavior REQUIRE a durion contract PR."
    - "Frontend work starts only when contract status is stable-for-ui."
    - "One branch per repo per capability: cap/<cap-id>."
  definition_of_done:
    - "All PRs merged and required checks green"
    - "Contract guide entry updated with examples + behavioral assertions"
    - "Provider behavioral contract tests added/updated"
    - "Frontend wired to stable contract"

