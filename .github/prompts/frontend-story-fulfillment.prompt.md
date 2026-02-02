---
name: Frontend Story Fulfillment
description: "Guided prompt for implementing frontend UI and integration for a capability story. Consumes a backend API/event and wires UI, validation, error handling, and tests. References contract guides and architecture documents for patterns and standards."
agent: "agent"
model: Claude Sonnet 4.5 (copilot)
---

# Frontend Story Fulfillment Prompt

You are implementing capability {{capability_label}} i.e.:CAP:275
Parent CAPABILITY: [durion#{{parent_capability_number}}]({{parent_capability_address}})
Parent STORY: [durion#{{parent_story_number}}]({{parent_story_address}})
Frontend child issues:
{{frontend_child_issues}}
Frontend repository: {{frontend_repo}}

Contract guide entry (stable-for-ui):
  durion repo, domains/{{domain}}/.business-rules/BACKEND_CONTRACT_GUIDE.md

Backend API endpoint/event is now implemented and available:
  Backend PR: [link to backend PR]({{backend_pull_request_address}})

**Implementation Checklist**
  1. Read and understand the parent story and capability requirements.
  2. Read and understand the frontend child stories and their specific requirements. **READ COMMENTS FOR CLARIFICATION OF ISSUES IN THE STORIES**
  3. Create a branch from `main` or 'master' named `cap/CAP{{capability_id}}` in the impacted_component_repos.
  4. Validate,Update or Implement the following in the new branch:  **Check for existing implementations to update first before adding new code**
    (A). Consume the backend API/event in the frontend
    (B). Implement UI screens/pages matching the wireframes
    (C). Wire form submissions, validation, and error handling
    (D). Add UI tests (Jest) for key user flows
    (E). Ensure error handling matches backend error model (shape, codes, messages)
  

Architecture & References:
- See `durion-moqui-frontend/AGENTS.md` for the official frontend tooling and local run/build commands.
- Consult `docs/architecture/` (workspace root) for frontend architecture guidance and integration patterns.
- Reference the contract guide at `domains/{{domain}}/.business-rules/BACKEND_CONTRACT_GUIDE.md` for DTO shapes, examples, and invariants.
- Use the capability manifest at `docs/capabilities/{{capability_id}}/CAPABILITY_MANIFEST.yaml` for repo lists, wireframes, and coordination details.
- API bridge reference: `runtime/component/durion-positivity/` inside the Moqui runtime component — prefer this existing bridge for backend calls.

Implementation Patterns & Links:
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
- `stories[0].parent.issue` (integer) — parent story number
- `stories[0].parent.domain` (string) — e.g., "security"
- `stories[0].children.frontend` (object or list) — frontend child story/stories. Can be a single object with `issue` field, or a list of objects each with `issue` field
- `stories[0].children.frontend[].impacted_component_repos` (list) — MUST be complete; may not be empty. Lists all component repos affected by this frontend work.
- `stories[0].contract_guide.path` (string) — path to contract guide
- `repositories[].type` and `repositories[].slug` — must include "backend" and "frontend-coordination" type entries

Optional fields:
- `stories[0].pr_links.backend_pr` (string or empty) — optional; may be omitted or empty if backend changes are still in progress

**Example CAPABILITY_MANIFEST.yaml Input**
```yaml
meta:
  capability_id: CAP:275
  capability_name: '[CAP] Login & Token Handling (ADR-0011)'
  owner_repo: louisburroughs/durion
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
- parent:
    repo: louisburroughs/durion
    issue: 275
    title: '[CAP] Login & Token Handling (ADR-0011)'
    domain: security
    labels:
    - type:capability
    - domain:security
    - status:ready-for-dev
  children:
    backend:
      repo: louisburroughs/durion-positivity-backend
      issue: 417
    frontend:
    - repo: louisburroughs/durion-moqui-frontend
      issue: 280
      impacted_component_repos:
      - louisburroughs/durion-positivity
      business_rules:
      - repo: louisburroughs/durion
        path: domains/security/.business-rules/AGENT_GUIDE.md
      - repo: louisburroughs/durion
        path: domains/security/.business-rules/BACKEND_CONTRACT_GUIDE.md
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
- Put all implementation details in a markdown document with proper headings and code blocks. Put this document in /durion/docs/capabilities/CAP-{capability_id}/CAP-{capability_id}-frontend-implementation.md.

Notes
- Do not hardcode secrets or API keys. Use environment variables or the existing runtime configuration.
- If the contract guide is missing examples for an edge case, add a note to the capability manifest and create minimal contract-driven tests that document expected behaviour.

---
Assume placeholders will be injected from the provided JSON input and validate that all required keys are present before proceeding.
