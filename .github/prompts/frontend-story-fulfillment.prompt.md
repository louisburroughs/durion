---
name: Frontend Story Fulfillment
description: "Guided prompt for implementing frontend UI and integration for a capability story. Consumes a backend API/event and wires UI, validation, error handling, and tests. References contract guides and architecture documents for patterns and standards."
agent: "agent"
model: GPT-5.2
---

# Frontend Story Fulfillment Prompt

You are implementing capability {capability_label} i.e.:CAP:275
Parent STORY: [durion#{parent_story_number}](https://github.com/louisburroughs/durion/issues/{parent_story_number})
Frontend child issues:
- [durion-moqui-frontend#{child_story_number}](https://github.com/louisburroughs/durion-moqui-frontend/issues/{child_story_number})
- [durion-moqui-frontend#{child_story_number}](https://github.com/louisburroughs/durion-moqui-frontend/issues/{child_story_number})

Contract guide entry (stable-for-ui):
  durion repo, domains/{domain}/.business-rules/BACKEND_CONTRACT_GUIDE.md

Backend API endpoint/event is now implemented and available:
  Backend PR: [link to backend PR](https://github.com/louisburroughs/durion-positivity-backend/pull/{backend_pull_request})

I need you to:
1. Consume the backend API/event in the frontend
2. Implement UI screens/pages matching the wireframes
3. Wire form submissions, validation, and error handling
4. Add UI tests (Jest) for key user flows
5. Ensure error handling matches backend error model (shape, codes, messages)

Architecture & References:
- See `durion-moqui-frontend/AGENTS.md` for the official frontend tooling and local run/build commands.
- Consult `docs/architecture/` (workspace root) for frontend architecture guidance and integration patterns.
- Reference the contract guide at `domains/{domain}/.business-rules/BACKEND_CONTRACT_GUIDE.md` for DTO shapes, examples, and invariants.
- Use the capability manifest at `docs/capabilities/{capability_id}/CAPABILITY_MANIFEST.yaml` for repo lists, wireframes, and coordination details.
- API bridge reference: `runtime/component/durion-positivity/` inside the Moqui runtime component — prefer this existing bridge for backend calls.

Implementation Patterns & Links:
- Services: Implement API calls in a services layer (composables or service classes) following the frontend architecture doc and `durion-moqui-frontend/AGENTS.md` guidance.
- Components: Keep presentation logic inside Vue components; put business logic and state orchestration in services/composables as documented in `docs/architecture/`.
- Types & Contract Safety: Obtain TypeScript DTOs from the contract guide or OpenAPI spec (if present) in the backend repo. Ensure types mirror the contract examples and validation rules in `domains/{domain}/.business-rules/BACKEND_CONTRACT_GUIDE.md`.
- API Bridge Usage: Use `runtime/component/durion-positivity/` for network integration; do not add ad-hoc HTTP clients that duplicate bridge behaviour.
- Testing: Follow Jest patterns and contract-driven test examples found in `docs/architecture/` and `durion-moqui-frontend/AGENTS.md`; add tests that assert handling of happy path, validation errors, and auth/errors per the contract.

Substitution & Validation

**Required Input Keys (JSON)**
All of the following keys MUST be present in the input JSON, or substitution will fail:
- `capability_label` (string) — e.g., "CAP:275"
- `parent_story_number` (integer) — e.g., 276
- `child_story_number` (integer) — e.g., 280
- `domain` (string) — e.g., "security", "accounting", "inventory"
- `backend_pull_request` (integer) — e.g., 418
- `capability_id` (string) — e.g., "cap:CAP275"

**Example JSON Input**
```json
{
  "capability_label": "CAP:275",
  "parent_story_number": 276,
  "child_story_number": 280,
  "domain": "security",
  "backend_pull_request": 418,
  "capability_id": "cap:CAP275"
}
```

**Substitution Algorithm (Python)**
```python
import re

def substitute_prompt(prompt_template, input_json):
    """
    Substitute placeholders in prompt template with values from input JSON.
    
    Args:
        prompt_template (str): The raw prompt text with {placeholder} markers
        input_json (dict): Dictionary with keys matching placeholder names
    
    Returns:
        str: Prompt with all placeholders replaced
    
    Raises:
        ValueError: If any required key is missing from input_json
    """
    required_keys = {
        "capability_label",
        "parent_story_number",
        "child_story_number",
        "domain",
        "backend_pull_request",
        "capability_id"
    }
    
    # Validate all required keys are present
    missing_keys = required_keys - set(input_json.keys())
    if missing_keys:
        raise ValueError(f"Missing required keys: {missing_keys}")
    
    # Perform substitution: replace {key} with value from input_json
    result = prompt_template
    for key, value in input_json.items():
        placeholder = "{" + key + "}"
        result = result.replace(placeholder, str(value))
    
    return result
```

**Substitution Algorithm (JavaScript)**
```javascript
function substitutePrompt(promptTemplate, inputJson) {
  const requiredKeys = [
    "capability_label",
    "parent_story_number",
    "child_story_number",
    "domain",
    "backend_pull_request",
    "capability_id"
  ];
  
  // Validate all required keys are present
  const missingKeys = requiredKeys.filter(key => !(key in inputJson));
  if (missingKeys.length > 0) {
    throw new Error(`Missing required keys: ${missingKeys.join(", ")}`);
  }
  
  // Perform substitution: replace {key} with value from inputJson
  let result = promptTemplate;
  for (const [key, value] of Object.entries(inputJson)) {
    const placeholder = `{${key}}`;
    result = result.replaceAll(placeholder, String(value));
  }
  
  return result;
}
```

**Validation Steps**
1. Parse input JSON and verify it is valid JSON syntax.
2. Check that all required keys (listed above) are present in the JSON.
3. Verify that `parent_story_number`, `child_story_number`, and `backend_pull_request` are integers.
4. Verify that `capability_label`, `domain`, and `capability_id` are non-empty strings.
5. Perform substitution using the algorithm above.
6. Verify the output contains no remaining `{placeholder}` patterns (use regex: `\{[a-z_]+\}`).
7. If any unsubstituted placeholders remain, raise an error and list them.

Output Expectations
- Produce a short implementation checklist (3–8 bullets) including files changed, services added, and tests created.
- Produce a list of exact files to modify or create (workspace-relative paths).
- Provide any TypeScript interfaces/types you added or updated (inline or file references).
- Keep the response concise; prefer a JSON summary for machine consumption and a short human-readable checklist.

Notes
- Do not hardcode secrets or API keys. Use environment variables or the existing runtime configuration.
- If the contract guide is missing examples for an edge case, add a note to the capability manifest and create minimal contract-driven tests that document expected behaviour.

---
Assume placeholders will be injected from the provided JSON input and validate that all required keys are present before proceeding.
