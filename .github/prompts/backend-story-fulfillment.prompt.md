---
name: Backend Story Fulfillment
description: "Guided prompt for implementing backend endpoints/services for a capability story. Implements contract-driven behavior, provider contract tests, validation, and OpenAPI annotations. References backend architecture documents and module conventions."
agent: "agent"
model: Claude Sonnet 4.5 (copilot)
---

# Backend Story Fulfillment Prompt

## ⚠️ CRITICAL: DO NOT STOP UNTIL COMPLETION

**YOU MUST CONTINUE WORKING UNTIL ONE OF THESE CONDITIONS IS MET:**

1. ✅ All required story changes are implemented and **committed** on `cap/CAP{{capability_id}}` (push the branch if possible)
2. 🚫 You are genuinely blocked by missing information, credentials, or infrastructure issues that prevent further progress

**DO NOT CREATE A PULL REQUEST IN THIS STORY PROMPT.**
Pull requests are created only during **capability completion** (after all child stories/issues under the capability are complete).

**DO NOT STOP FOR:**
- Convenience or "checking in" with the user
- Partial completion of tasks
- After completing individual steps in the checklist
- To "let the user review" intermediate progress

**YOUR RESPONSIBILITY:**
- Work through the ENTIRE implementation checklist
- Resolve issues autonomously when possible
- Only escalate true blockers that require external action
- Commit changes to the capability branch
- Push the capability branch when possible

**If you find yourself wanting to stop before committing + pushing, ask yourself:**
- "Is there truly a blocker I cannot resolve?"
- "Have I exhausted all available tools and approaches?"
- "Am I stopping out of habit rather than necessity?"

If the answer to these is "no", **KEEP WORKING**.

---

## Team-Mode Execution (Lead Coder)

When invoked under coder team-mode:
- `Lead Coder` coordinates and delegates; it MUST NOT write code directly.
- `Lead Coder` delegates file-changing work to:
  - `Client Coder` for `RestClient`/outbound integration artifacts.
  - `API Surface Coder` for controllers/DTOs/service interfaces/validation/OpenAPI/event annotations.
  - `Domain Data Coder` for service implementations/entities/repositories/domain logic.
- `Coder` is legacy fallback only when team-mode delegation is blocked and is invoked by `Lead Coder` (not directly by Orchestrator).

Context (inputs — agent will be provided values at runtime)
- `OPENAPI_PATH` — path to the authoritative `openapi.yaml` (e.g., `pos-<module>/target/openapi.yaml`) which MUST NOT be overwritten.
- `BACKEND_API_REFERENCE_PATH` — path to generated API reference (e.g., `domains/<domain>/.business-rules/BACKEND_API_REFERENCE.generated.md`).

You are implementing capability {{capability_label}} (e.g., CAP:089).

**Parent Capability:** [durion#{{parent_capability_number}}]({{parent_capability_url}}) — {{parent_capability_title}}

**Parent Stories (under this capability):**
{{parent_stories_list}}

**Backend Child Issues for this parent story:**
{{backend_child_issues}}

**Backend Repository (static):**
louisburroughs/durion-positivity-backend

Contract guide entry (draft):
  durion repo, domains/{{domain}}/.business-rules/BACKEND_CONTRACT_GUIDE.md

  **Implementation Checklist**
  1. Read and understand the parent story and capability requirements.
  2. Read and understand the backend child stories and their specific requirements. **READ COMMENTS FOR CLARIFICATION OF ISSUES IN THE STORIES**
  3. **Create or checkout the capability feature branch in the backend repository:**
     ```bash
     cd $WORKSPACE/durion-positivity-backend
     git fetch origin
     git switch main
     git pull --ff-only origin main

     # Try existing local capability branch first
     git switch cap/CAP{{capability_id}}
     # If previous command fails because local branch does not exist, run:
     git switch -c cap/CAP{{capability_id}}

     # If remote capability branch exists, run these:
     git branch --set-upstream-to=origin/cap/CAP{{capability_id}} cap/CAP{{capability_id}}
     git pull --ff-only origin cap/CAP{{capability_id}}
     ```
     **CRITICAL:** This command is idempotent and works whether this is the first story or subsequent story under this capability.
     
     **Auto-approve compatibility rules:**
     - Run one command per line.
     - Do not use command chaining (`&&`, `||`, `;`) or shell `if` blocks.
     - Use fallback as a separate follow-up command only after a failure.
     
     **VERIFY BRANCH:** All subsequent code changes MUST be made while on this branch. Verify you are on the correct branch before making any file changes:
     ```bash
     git branch --show-current  # MUST output: cap/CAP{{capability_id}}
     ```
     
     **IF BRANCH CHECKOUT FAILS:** If you cannot checkout or create the branch:
     - Report the git error immediately
     - Check for local uncommitted changes: `git status`
     - DO NOT proceed with implementation until branch is correct
  4. Validate, Update or Implement the following in the new branch:  **Check for existing implementations to update first before adding new code**
    (A). Implement the endpoint/service to match the contract
    (B). Add provider behavioral contract tests (`ContractBehaviorIT`)
    (C). Use behavior assertions from `BACKEND_CONTRACT_GUIDE.md`; use payload/status examples from `BACKEND_API_REFERENCE.generated.md` and `OPENAPI_PATH` in tests
    (D). Add validation & error handling per the assertions
    (E). Include concurrency-safe patterns if needed (idempotency, optimistic locking)
    (F). Add or update OpenAPI annotations (`@Operation`, `@ApiResponse`, etc.) if the module exposes REST
    (G). **OpenAPI source of truth:** Treat the file at `OPENAPI_PATH` (provided in Runtime Context) as the **read-only** authoritative source.
       - **DO NOT** regenerate this file. Avoid running build commands that might overwrite it (like `clean package`) unless you restore it afterwards.
       - **DO NOT** edit this file manually.
       - Use it only to validate that your implementation matches the contract.
       - If the implementation differs from `openapi.yaml`, **fix the implementation**.
  5. **Commit changes to the feature branch:**
     ```bash
     cd $WORKSPACE/durion-positivity-backend
     git add .
     git commit -m "feat({{domain}}): implement CAP{{capability_id}} backend services"
     ```
  6. **Push the branch (DO NOT open a pull request):**
     ```bash
     cd $WORKSPACE/durion-positivity-backend
     git push -u origin cap/CAP{{capability_id}}
     ```
     
     **VERIFY PUSH SUCCESS:** After pushing, verify the branch exists remotely:
     ```bash
     git ls-remote --heads origin cap/CAP{{capability_id}}
     ```
     
     **IF PUSH FAILS:** Report the error with:
     - Git error message
     - Output of `git status`
     - Output of `git log --oneline -5`

  7. **Confirm completion and stop (DO NOT create pull request):**
     
     ✅ **STORY IMPLEMENTATION COMPLETE** when:
     - All code changes committed to `cap/CAP{{capability_id}}` branch
     - Branch pushed to remote successfully
     - Tests passing (run `./mvnw -pl pos-<module> test` to verify)
     
     🚫 **STOP HERE.** Do NOT create a pull request. 
     
     **Pull request creation happens ONLY when:**
     - ALL child stories under this capability are complete
     - A separate "Capability Completion" prompt is invoked
     - User explicitly requests PR creation
     
     Report completion with:
     - Branch name: `cap/CAP{{capability_id}}`
     - Commit hash: `git rev-parse HEAD`
     - Files changed: `git diff --name-only main...cap/CAP{{capability_id}}`
     - Test results: Pass/fail count

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
- Add `ContractBehaviorIT` tests using behavior assertions from the contract guide and request/response examples from OpenAPI/generated API reference covering: happy path, validation errors, auth failures, idempotency, and concurrency invariants.
- Add ArchUnit tests if the change introduces new packages or layering concerns.

Substitution & Validation

**Required Input Structure (CAPABILITY_MANIFEST.yaml)**
The following fields MUST be present in CAPABILITY_MANIFEST.yaml, or substitution will fail:
- `meta.capability_id` (string) — e.g., "CAP:094"
- `meta.owner_repo` (string) — e.g., "louisburroughs/durion"
- `parent_capability.issue` (integer) — capability issue number
- `parent_capability.domain` (string) — e.g., "crm", "security", "inventory"
- `stories[].parent_story.issue` (integer) — parent story issue number(s)
- `stories[].children.backend` (object) — backend child issue. Must have `issue` field.
- `stories[].contract_guide.path` (string) — path to contract guide
- `repositories[].type` and `repositories[].slug` — must include "backend" type entry (always durion-positivity-backend)

**Example CAPABILITY_MANIFEST.yaml Input**
```yaml
meta:
  capability_id: CAP:094
  capability_name: '[CAP] Workorder Execution Integration (Bidirectional)'
  owner_repo: louisburroughs/durion
coordination:
  github_project_url: https://github.com/users/louisburroughs/projects/1
  preferred_branch_prefix: cap/
contract_registry:
  root_path: domains
  guide_path_suffix: .business-rules/BACKEND_CONTRACT_GUIDE.md
repositories:
- name: durion-positivity-backend
  slug: louisburroughs/durion-positivity-backend
  type: backend
stories:
- parent_story:
    repo: louisburroughs/durion
    issue: 94
    title: '[CAP] Workorder Execution Integration (Bidirectional)'
    domain: crm
    labels:
    - type:capability
    - domain:crm
  children:
    backend:
      repo: louisburroughs/durion-positivity-backend
      issue: 92
  contract_guide:
    repo: louisburroughs/durion
    path: domains/crm/.business-rules/BACKEND_CONTRACT_GUIDE.md
    status: draft
```

**Substitution Algorithm (Python)**
```python
import re
import yaml
from typing import Dict, Any, List

def extract_manifest_values(manifest_data: Dict[str, Any]) -> Dict[str, Any]:
    """
    Extract required substitution values from CAPABILITY_MANIFEST.yaml structure.
    
    Args:
        manifest_data (dict): Parsed YAML from CAPABILITY_MANIFEST.yaml
    
    Returns:
        dict: Dictionary with extracted values for template substitution
    
    Raises:
        ValueError: If required fields are missing from manifest
    """
    try:
        capability_id = manifest_data['meta']['capability_id']
        capability_name = manifest_data['meta']['capability_name']
        owner_repo = manifest_data['meta']['owner_repo']
        
        parent_story = manifest_data['stories'][0]['parent']
        parent_story_number = parent_story['issue']
        domain = parent_story['domain']
        
        backend_repo = next(
            r for r in manifest_data['repositories']
            if r['type'] == 'backend'
        )['slug']
        
        children = manifest_data['stories'][0]['children']
        
        # Handle backend as either single object or list of objects
        backend_data = children['backend']
        if isinstance(backend_data, dict):
            # Single backend issue
            backend_issues = [backend_data]
        elif isinstance(backend_data, list):
            # Multiple backend issues
            backend_issues = backend_data
        else:
            raise ValueError("stories[0].children.backend must be dict or list")
        
        # Extract issue numbers
        backend_child_issues = []
        for backend_issue in backend_issues:
            issue_number = backend_issue['issue']
            issue_repo = backend_issue.get('repo', backend_repo)
            backend_child_issues.append({
                'issue_number': issue_number,
                'issue_repo': issue_repo,
                'issue_url': f"https://github.com/{issue_repo}/issues/{issue_number}"
            })
        
        # Format backend child issues as markdown list
        backend_issues_markdown = '\n'.join([
            f"- [{item['issue_repo']}#{item['issue_number']}]({item['issue_url']})"
            for item in backend_child_issues
        ])
        
        contract_guide = manifest_data['stories'][0]['contract_guide']
        
        return {
            'capability_label': capability_id,
            'capability_name': capability_name,
            'owner_repo': owner_repo,
            'parent_story_number': parent_story_number,
            'domain': domain,
            'parent_story_address': f"https://github.com/{owner_repo}/issues/{parent_story_number}",
            'backend_child_issues': backend_issues_markdown,
            'contract_path': contract_guide['path'],
            'contract_status': contract_guide.get('status', 'draft'),
            'backend_repo': backend_repo,
            'capability_id': capability_id.lower().replace(':', '_')
        }
    except (KeyError, IndexError) as e:
        raise ValueError(f"Required field missing in CAPABILITY_MANIFEST.yaml: {e}")


def substitute_prompt(prompt_template: str, manifest_data: Dict[str, Any]) -> str:
    """
    Substitute placeholders in prompt template with values extracted from CAPABILITY_MANIFEST.yaml.
    
    Args:
        prompt_template (str): The raw prompt text with {{placeholder}} markers
        manifest_data (dict): Parsed CAPABILITY_MANIFEST.yaml data
    
    Returns:
        str: Prompt with all placeholders replaced
    
    Raises:
        ValueError: If required fields are missing or substitution incomplete
    """
    # Extract values from manifest
    values = extract_manifest_values(manifest_data)
    
    # Perform substitution: replace {{key}} with value from extracted values
    result = prompt_template
    for key, value in values.items():
        placeholder = "{{" + key + "}}"
        result = result.replace(placeholder, str(value))
    
    # Validate no unsubstituted placeholders remain
    remaining_placeholders = re.findall(r'\{\{([a-z_]+)\}\}', result, re.IGNORECASE)
    if remaining_placeholders:
        raise ValueError(f"Unsubstituted placeholders remain: {remaining_placeholders}")
    
    return result


def load_and_substitute(manifest_path: str, prompt_template: str) -> str:
    """
    Load CAPABILITY_MANIFEST.yaml and apply substitution to prompt template.
    
    Args:
        manifest_path (str): Path to CAPABILITY_MANIFEST.yaml file
        prompt_template (str): The raw prompt template text
    
    Returns:
        str: Substituted prompt
    
    Raises:
        FileNotFoundError: If manifest file not found
        yaml.YAMLError: If manifest YAML parsing fails
        ValueError: If manifest validation or substitution fails
    """
    with open(manifest_path, 'r') as f:
        manifest_data = yaml.safe_load(f)
    
    return substitute_prompt(prompt_template, manifest_data)
```

**Validation Steps**
1. Parse input YAML and verify it is valid YAML syntax.
2. Check that all required fields (listed above) are present in the YAML.
3. Verify that `parent_story_number` and backend issue numbers are integers.
4. Verify that `capability_label`, `domain`, and `capability_id` are non-empty strings.
5. Perform substitution using the algorithm above.
7. If any unsubstituted placeholders remain, raise an error and list them.

Output Expectations
- Provide a concise implementation checklist (3–10 bullets) listing controllers, services, repositories, entities, DTOs, and tests to add/modify.
- List exact workspace-relative file paths to change or create.
- Provide code snippets for critical pieces: controller signature, service method, repository query, and a sample ContractBehaviorIT test using contract examples.
- Specify required configuration changes (if any), e.g., event type registration, properties, or feature flags.
- **IMPORTANT** Put ALL implementation details INCLUDING COMPLETION DETAILS in a markdown document with proper headings and code blocks. Put this document in /durion/docs/capabilities/CAP-{capability_id}/CAP-{capability_id}-backend-implementation.md.

Notes
- Do NOT hardcode secrets or credentials; use existing config and environment variables.
- Follow null-safety (`@NonNull`) and event-logging (`@EmitEvent`) conventions documented in backend AGENTS.md.
- If contract examples are missing for an edge case, add tests that capture desired behaviour and update the contract guide in `durion`.

---
Assume placeholders will be injected from the CAPABILITY_MANIFEST.yaml and validate that all required fields are present before proceeding.
