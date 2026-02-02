---
name: Frontend Story Fulfillment
description: "Guided prompt for implementing frontend UI and integration for a capability story. Consumes a backend API/event and wires UI, validation, error handling, and tests. References contract guides and architecture documents for patterns and standards."
agent: "agent"
model: Claude Sonnet 4.5 (copilot)
---

# Frontend Story Fulfillment Prompt

## ⚠️ CRITICAL: DO NOT STOP UNTIL COMPLETION

**YOU MUST CONTINUE WORKING UNTIL ONE OF THESE CONDITIONS IS MET:**

1. ✅ **A pull request has been successfully created** with all changes committed and pushed
2. 🚫 **You are genuinely blocked** by missing information, credentials, or infrastructure issues that prevent further progress

**DO NOT STOP FOR:**
- Convenience or "checking in" with the user
- Partial completion of tasks
- After completing individual steps in the checklist
- To "let the user review" intermediate progress

**YOUR RESPONSIBILITY:**
- Work through the ENTIRE implementation checklist
- Resolve issues autonomously when possible
- Only escalate true blockers that require external action
- Create the PR as the final deliverable

**If you find yourself wanting to stop before PR creation, ask yourself:**
- "Is there truly a blocker I cannot resolve?"
- "Have I exhausted all available tools and approaches?"
- "Am I stopping out of habit rather than necessity?"

If the answer to these is "no", **KEEP WORKING**.

---

You are implementing capability {{capability_label}} (e.g., CAP:089).

**Parent Capability:** [durion#{{parent_capability_number}}]({{parent_capability_url}}) — {{parent_capability_title}}

**Parent Stories (under this capability):**
{{parent_stories_list}}

**Frontend Child Issues for this parent story:**
{{frontend_child_issues}}

**Frontend Impacted Repositories:**
{{frontend_impacted_repos}}

Contract guide entry (stable-for-ui):
  durion repo, domains/{{domain}}/.business-rules/BACKEND_CONTRACT_GUIDE.md

Backend API endpoint/event is now implemented and available:
  Backend PR: [link to backend PR]({{backend_pull_request_address}})

**Implementation Checklist**
  1. Read and understand the parent story and capability requirements.
  2. Read and understand the frontend child stories and their specific requirements. **READ COMMENTS FOR CLARIFICATION OF ISSUES IN THE STORIES**
  3. **Create and switch to a feature branch in ALL impacted component repositories:**
     
     **CRITICAL:** The capability manifest specifies `impacted_component_repos` which lists the actual repositories where code changes will be made. These are typically component repositories like `durion-crm`, NOT the coordination repository `durion-moqui-frontend` (which only tracks issues).
     
     For each repository in `impacted_component_repos`:
     ```bash
     # Example for durion-crm component
     cd /home/louisb/Projects/durion-crm
     git fetch origin
     git checkout main
     git pull origin main
     git checkout -b cap/CAP{{capability_id}}
     ```
     
     **Component Repository Paths:** Frontend components are located at `/home/louisb/Projects/{repo-name}` where `{repo-name}` is extracted from the impacted_component_repos slug. For example:
     - `louisburroughs/durion-crm` → `/home/louisb/Projects/durion-moqui-frontend/runtime/component/durion-crm`
     - `louisburroughs/durion-hr` → `/home/louisb/Projects/durion-moqui-frontend/runtime/component/durion-hr`
     
     **IMPORTANT:** All subsequent code changes MUST be made in these component repositories while on the feature branch. Verify you are on the correct branch before making any file changes:
     ```bash
     git branch --show-current  # Should output: cap/CAP{{capability_id}}
     ```
  4. Validate, Update or Implement the following in the feature branch of each impacted component repository:  **Check for existing implementations to update first before adding new code**
    (A). Consume the backend API/event in the frontend
    (B). Implement UI screens/pages matching the wireframes
    (C). Wire form submissions, validation, and error handling
    (D). Add UI tests (Jest) for key user flows
    (E). Ensure error handling matches backend error model (shape, codes, messages)
  5. **Commit changes to the feature branch in each component repository:**
     ```bash
     # Repeat for each impacted component repository
     cd /home/louisb/Projects/durion-crm  # or durion-hr, etc.
     git add .
     git commit -m "feat({{domain}}): implement CAP{{capability_id}} frontend UI"
     ```
  6. **Push the branch and create a pull request for each component repository:**
     ```bash
     # Repeat for each impacted component repository
     cd /home/louisb/Projects/durion-crm  # or durion-hr, etc.
     git push -u origin cap/CAP{{capability_id}}
     
     # Create PR using GitHub CLI
     gh pr create --base main --head cap/CAP{{capability_id}} \
       --title "feat({{domain}}): CAP{{capability_id}} frontend implementation" \
       --body "Implements frontend UI for capability {{capability_label}} (Issue #{{frontend_issue_number}})"
     ```
  

Architecture & References:
- See `durion-moqui-frontend/AGENTS.md` for the official frontend tooling and local run/build commands.
- Consult `docs/architecture/` (workspace root) for frontend architecture guidance and integration patterns.
- Reference the contract guide at `domains/{{domain}}/.business-rules/BACKEND_CONTRACT_GUIDE.md` for DTO shapes, examples, and invariants.
- Use the capability manifest at `docs/capabilities/{{capability_id}}/CAPABILITY_MANIFEST.yaml` for repo lists, wireframes, and coordination details.
- **Component repositories** (e.g., durion-crm, durion-hr) contain the actual screen implementations and business logic
- **Integration bridge:** The `runtime/component/durion-positivity/` component inside durion-moqui-frontend provides the API bridge to backend services — prefer this existing bridge for backend calls.

Implementation Patterns & Links:
- **Repository Structure:** Frontend components live in separate git repositories (e.g., `/home/louisb/Projects/durion-crm`) but are mounted into the Moqui runtime at `runtime/component/{component-name}/` during development and deployment.
- **Screen Location:** Create Moqui XML screens in `{component-repo}/screen/` directory following the path pattern `screen/durion/{domain}/{ScreenName}.xml`
- Services: Implement API calls in a services layer (composables or service classes) following the frontend architecture doc and `durion-moqui-frontend/AGENTS.md` guidance.
- Components: Keep presentation logic inside Vue components; put business logic and state orchestration in services/composables as documented in `docs/architecture/`.
- Types & Contract Safety: Obtain TypeScript DTOs from the contract guide or OpenAPI spec (if present) in the backend repo. Ensure types mirror the contract examples and validation rules in `domains/{{domain}}/.business-rules/BACKEND_CONTRACT_GUIDE.md`.
- API Bridge Usage: Use `runtime/component/durion-positivity/` for network integration; do not add ad-hoc HTTP clients that duplicate bridge behaviour.
- Testing: Follow Jest patterns and contract-driven test examples found in `docs/architecture/` and `durion-moqui-frontend/AGENTS.md`; add tests that assert handling of happy path, validation errors, and auth/errors per the contract.

Substitution & Validation

**Required Input Structure (CAPABILITY_MANIFEST.yaml)**
The following fields MUST be present in CAPABILITY_MANIFEST.yaml, or substitution will fail:
- `meta.capability_id` (string) — e.g., "CAP:275"
- `meta.owner_repo` (string) — e.g., "louisburroughs/durion"
- `parent_capability.issue` (integer) — capability issue number
- `parent_capability.domain` (string) — e.g., "security", "crm"
- `stories[].parent_story.issue` (integer) — parent story issue number(s)
- `stories[].children.frontend` (object) — frontend child issue. Must have `issue` field and non-empty `impacted_component_repos` list.
- `stories[].children.frontend.impacted_component_repos` (list) — MUST be complete; may not be empty. Lists all component repos affected by this frontend work.
- `stories[0].contract_guide.path` (string) — path to contract guide
- `repositories[].type` and `repositories[].slug` — must include "backend" and "frontend-coordination" type entries

Optional fields:
- `stories[].pr_links.backend_pr` (string or empty) — optional; may be omitted or empty if backend changes are still in progress

**Example CAPABILITY_MANIFEST.yaml Input**
```yaml
meta:
  capability_id: CAP:089
  capability_name: '[CAP] Party Management (Commercial Accounts & Individuals)'
  owner_repo: louisburroughs/durion
parent_capability:
  repo: louisburroughs/durion
  issue: 89
  title: '[CAP] Party Management (Commercial Accounts & Individuals)'
  domain: crm
  labels:
  - type:capability
  - domain:crm
coordination:
  github_project_url: https://github.com/users/louisburroughs/projects/1
  status_field_name: status:{Backlog,Ready,In Progress,In Review,Done}
  preferred_branch_prefix: cap/
contract_registry:
  root_path: domains
  guide_path_suffix: .business-rules/BACKEND_CONTRACT_GUIDE.md
repositories:
- name: durion-positivity-backend
  slug: louisburroughs/durion-positivity-backend
  type: backend
- name: durion-moqui-frontend
  slug: louisburroughs/durion-moqui-frontend
  type: frontend-coordination
stories:
- parent_story:
    repo: louisburroughs/durion
    issue: 95
    title: '[STORY] Party: Create Commercial Account'
    labels:
    - type:story
    - domain:crm
  children:
    frontend:
      repo: louisburroughs/durion-moqui-frontend
      issue: 176
      impacted_component_repos:
      - louisburroughs/durion-crm
  contract_guide:
    repo: louisburroughs/durion
    path: domains/crm/.business-rules/BACKEND_CONTRACT_GUIDE.md
    status: draft
    - repo: louisburroughs/durion-moqui-frontend
      issue: 281
      impacted_component_repos:
      - louisburroughs/durion-hr
      business_rules:
      - repo: louisburroughs/durion
        path: domains/security/.business-rules/AGENT_GUIDE.md
  contract_guide:
    repo: louisburroughs/durion
    path: domains/security/.business-rules/BACKEND_CONTRACT_GUIDE.md
    status: draft
    openapi:
      producer_repo: louisburroughs/durion-positivity-backend
      spec_path: pos-security-service/target/openapi.json
```

**Substitution Algorithm (Python)**
```python
import re
import yaml
from typing import Dict, Any, Set

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
        frontend_repo = next(
            r for r in manifest_data['repositories']
            if r['type'] == 'frontend-coordination'
        )['slug']
        
        children = manifest_data['stories'][0]['children']
        
        # Handle frontend as either single object or list of objects
        frontend_data = children['frontend']
        if isinstance(frontend_data, dict):
            # Single frontend issue
            frontend_issues = [frontend_data]
        elif isinstance(frontend_data, list):
            # Multiple frontend issues
            frontend_issues = frontend_data
        else:
            raise ValueError("stories[0].children.frontend must be dict or list")
        
        # Extract issue numbers and validate impacted_component_repos
        frontend_child_issues = []
        all_impacted_repos = []
        for idx, frontend_issue in enumerate(frontend_issues):
            issue_number = frontend_issue['issue']
            issue_repo = frontend_issue.get('repo', frontend_repo)
            impacted_repos = frontend_issue.get('impacted_component_repos', [])
            
            if not impacted_repos:
                raise ValueError(f"stories[0].children.frontend[{idx}].impacted_component_repos must not be empty")
            
            all_impacted_repos.extend(impacted_repos)
            frontend_child_issues.append({
                'issue_number': issue_number,
                'issue_repo': issue_repo,
                'issue_url': f"https://github.com/{issue_repo}/issues/{issue_number}"
            })
        
        # Format frontend child issues as markdown list
        frontend_issues_markdown = '\n'.join([
            f"- [{item['issue_repo']}#{item['issue_number']}]({item['issue_url']})"
            for item in frontend_child_issues
        ])
        
        contract_guide = manifest_data['stories'][0]['contract_guide']
        
        return {
            'capability_label': capability_id,
            'capability_name': capability_name,
            'owner_repo': owner_repo,
            'parent_story_number': parent_story_number,
            'domain': domain,
            'parent_story_address': f"https://github.com/{owner_repo}/issues/{parent_story_number}",
            'frontend_child_issues': frontend_issues_markdown,
            'contract_path': contract_guide['path'],
            'contract_status': contract_guide['status'],
            'backend_repo': backend_repo,
            'frontend_repo': frontend_repo,
            'capability_id': capability_id.lower().replace(':', '_'),
            'impacted_component_repos': list(set(all_impacted_repos))  # deduplicate
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
2. Check that all required keys (listed above) are present in the YAML.
3. Verify that `parent_story_number`, `child_story_number`, and `backend_pull_request` are urls.
4. Verify that `capability_label`, `domain`, and `capability_id` are non-empty strings.
5. Perform substitution using the algorithm above.
6. Verify the output contains no remaining `{{placeholder}}` patterns (use regex: `\{{[a-z_]+\}}`).
7. If any unsubstituted placeholders remain, raise an error and list them.

Output Expectations
- Produce a short implementation checklist (3–8 bullets) including files changed, services added, and tests created.
- Produce a list of exact files to modify or create (workspace-relative paths).
- Provide any TypeScript interfaces/types you added or updated (inline or file references).
- Keep responses concise; prefer JSON summaries for machine consumption and short human-readable checklists.
- **IMPORTANT** Put ALL implementation details INCLUDING COMPLETION DETAILS in a markdown document with proper headings and code blocks. Put this document in /durion/docs/capabilities/CAP-{capability_id}/CAP-{capability_id}-frontend-implementation.md.

Notes
- Do not hardcode secrets or API keys. Use environment variables or the existing runtime configuration.
- If the contract guide is missing examples for an edge case, add a note to the capability manifest and create minimal contract-driven tests that document expected behaviour.

---
Assume placeholders will be injected from the provided JSON input and validate that all required keys are present before proceeding.
