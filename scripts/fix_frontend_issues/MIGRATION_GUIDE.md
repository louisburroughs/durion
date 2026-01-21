# Migration: Copilot CLI → OpenAI API

## Summary of Changes

You now have **two implementations** for story editing:

1. **fix_issue.py** (Original): Uses GitHub Copilot CLI (`copilot` executable)
2. **fix_issue_openai.py** (New): Uses OpenAI API directly via HTTP/REST

## Why Switch to OpenAI API?

### Problems with Copilot CLI Integration
- ❌ Copilot CLI asks clarification questions instead of executing tasks
- ❌ Attempts to use git commands for file discovery
- ❌ Outputs tool calls as JSON instead of executing them
- ❌ No output file created despite successful exit code
- ❌ Requires Copilot CLI binary installed and authenticated
- ❌ Complex prompt structure misinterpreted as meta-instructions

### Advantages of OpenAI API
- ✅ Direct HTTP requests (no subprocess/CLI complexity)
- ✅ Predictable behavior (no meta-question loops)
- ✅ Full control over prompt structure
- ✅ Reliable file output (direct Python writes)
- ✅ Automatic GitHub integration post-processing
- ✅ Better error handling and diagnostics
- ✅ Works with any OpenAI model (gpt-4o, gpt-4-turbo, gpt-4o-mini, etc.)
- ✅ No additional binary dependencies

## Architecture Comparison

### fix_issue.py (Copilot CLI)
```
┌─ after.md ─────────────────────┐
│                                 │
│ 1. Parse labels & body         │
│ 2. Extract domain              │
│ 3. Build minimal prompt        │
│ 4. Pass to Copilot CLI         │
│    └─ Subprocess execution     │
│       └─ Stdin delivery        │
│       └─ Waiting for fixed.md  │
│ 5. (fixed.md not created?)     │
└─────────────────────────────────┘
```

### fix_issue_openai.py (OpenAI API)
```
┌─ after.md ──────────────────────────────────────┐
│                                                  │
│ 1. Parse labels & body                         │
│ 2. Extract domain                              │
│ 3. Load business rules                         │
│ 4. Load prompt instructions                    │
│ 5. Build FULL comprehensive prompt             │
│ 6. Call OpenAI API (HTTP POST)                 │
│    ├─ Construct JSON payload                   │
│    ├─ Send to api.openai.com                   │
│    └─ Parse JSON response                      │
│ 7. Write fixed.md (direct file write)         │
│ 8. (Optional) Update GitHub issue              │
│    ├─ Extract labels from fixed.md             │
│    ├─ Update issue body                        │
│    └─ Apply labels                             │
│ 9. Return status                               │
└──────────────────────────────────────────────────┘
```

## Prompt Structure

### Original (fix_issue.py) - Minimal
```
TASK: Review and update the story below...

BUSINESS RULES:
[first 500 chars]

STORY:
[issue_body]

OUTPUT INSTRUCTION:
Use create_file tool to write...
```

### New (fix_issue_openai.py) - Comprehensive
```
=== STORY EDITING INSTRUCTIONS ===
[Full prompt file content from .github/prompts/story-frontend-rewrite.prompt.md]

=== BUSINESS RULES FOR DOMAIN: {domain} ===
[Full content of AGENT_GUIDE.md]
[Full content of DOMAIN_NOTES.md]
[Full content of STORY_VALIDATION_CHECKLIST.md]
[Any other .md files in domains/{domain}/.business-rules/]

=== ISSUE TO BE UPDATED ===
[Complete issue body]

=== TASK ===
Review and update the included issue...

=== OUTPUT REQUIREMENTS ===
Output ONLY the complete, updated story body:
- Include complete YAML frontmatter with labels
- Preserve all section structure
- Resolve open questions using business rules
- Include decision rationale
- Use proper labels and markdown formatting
- No meta-commentary; only the final story content
```

## Usage Comparison

### Original (Copilot CLI)
```bash
python3 fix_issue.py inventory /path/to/after.md \
  --rebuild-with-copilot \
  --prompt-file .github/prompts/story-frontend-rewrite.prompt.md \
  --model gpt-5-mini \
  --update-github --owner louisburroughs --repo durion-moqui-frontend
```

### New (OpenAI API)
```bash
python3 fix_issue_openai.py inventory /path/to/after.md \
  --prompt-file .github/prompts/story-frontend-rewrite.prompt.md \
  --model gpt-4o-mini \
  --update-github --owner louisburroughs --repo durion-moqui-frontend
```

### By Issue ID
```bash
# Original
python3 fix_issue.py --issue 67 --issue-dir ... --rebuild-with-copilot ...

# New
python3 fix_issue_openai.py --issue 67 --issue-dir ...
```

## API Integration Details

### OpenAI API Call
```python
def call_openai(prompt: str, model: str = "gpt-4o-mini") -> str:
    url = "https://api.openai.com/v1/chat/completions"
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }
    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": "You are a story editor..."},
            {"role": "user", "content": prompt}
        ],
        "temperature": 0.3,
        "max_tokens": 8000,
    }
    response = _http_post_json(url, headers, payload, timeout_s=120)
    return response['choices'][0]['message']['content']
```

### GitHub Integration
```python
def update_github_issue(owner: str, repo: str, issue_number: int,
                       new_body: str, labels: list = None) -> bool:
    client = GitHubClient(token)
    body_only = extract_body_without_labels(new_body)
    client.update_issue_body(owner, repo, issue_number, body_only)
    apply_labels(client, owner, repo, issue_number, required, recommended, blocking)
```

## Output Flow

### Original (Copilot CLI)
```
OpenAI Response
    ↓
Copilot CLI Process
    ↓
(attempts to write fixed.md?)
    ↓
Python script checks for fixed.md
    ↓
(file not found)
```

### New (OpenAI API)
```
OpenAI API Response
    ↓
Python parses JSON
    ↓
Direct file write: fixed.md
    ↓
Parse YAML frontmatter
    ↓
(Optional) GitHub API call
    ↓
Update issue body + labels
    ↓
Success!
```

## Environment Requirements

### Original (Copilot CLI)
- `copilot` CLI binary installed and authenticated
- OPENAI_API_KEY (passed to Copilot CLI)
- GITHUB_TOKEN (for --update-github)

### New (OpenAI API)
- OPENAI_API_KEY (for direct API calls)
- GITHUB_TOKEN (for --update-github)
- Python 3.8+ (built-in modules only)

## Error Handling

### Original
- Copilot CLI exit code 0 but no output
- Unclear if file write failed or Copilot didn't run
- Thinking log contains meta-questions
- Limited error context in logs

### New
- Clear HTTP error responses from OpenAI
- JSON parsing validates response structure
- File write is direct (fails clearly if permissions wrong)
- GitHub API errors are captured and logged
- Full request/response in debug log

## Switching Recommendations

### Use `fix_issue_openai.py` if:
- ✅ You want reliable, predictable story editing
- ✅ You're already using OpenAI API elsewhere
- ✅ You want full business rules included in context
- ✅ You want automatic GitHub synchronization
- ✅ You're experiencing issues with Copilot CLI integration
- ✅ You prefer HTTP/REST over subprocess

### Keep `fix_issue.py` if:
- ✅ You specifically need Copilot CLI behavior
- ✅ You want to test Copilot's agent mode
- ✅ You're troubleshooting Copilot-specific workflows

## Migration Steps

1. **Verify Dependencies**
   ```bash
   echo $OPENAI_API_KEY  # Should be set
   echo $GITHUB_TOKEN     # Should be set for --update-github
   ```

2. **Test with Single Issue (Dry-run)**
   ```bash
   python3 fix_issue_openai.py inventory /path/to/after.md --dry-run
   ```

3. **Process Sample Issue**
   ```bash
   python3 fix_issue_openai.py inventory /path/to/after.md
   cat /path/to/fixed.md  # Verify output
   ```

4. **Update GitHub (if satisfied)**
   ```bash
   python3 fix_issue_openai.py inventory /path/to/after.md \
     --update-github --owner <owner> --repo <repo>
   ```

5. **Batch Process**
   ```bash
   python3 fix_issue_openai.py \
     --issue-range 60-70 \
     --issue-dir scripts/story-work/frontend \
     --update-github --owner <owner> --repo <repo>
   ```

## Troubleshooting

| Issue | Original | New |
|-------|----------|-----|
| Copilot asks questions | See copilot_thinking.log | N/A (direct API) |
| No output file | Check exit code | Check HTTP status + file permissions |
| GitHub not updated | Manual step needed | Check GITHUB_TOKEN + API response |
| Rate limited | Copilot handles | Retry with backoff |
| API authentication | Copilot handles | Check OPENAI_API_KEY value |

## Performance Notes

- **API Latency**: ~2-5 seconds per request (depends on model and prompt size)
- **Token Usage**: Full business rules included (~3000-5000 tokens per request)
- **Cost**: gpt-4o-mini (~$0.15 per 1M input tokens, $0.60 per 1M output tokens)
- **Batch Processing**: Sequential (consider threading for large batches)

## Next Steps

1. Replace references to `fix_issue.py` with `fix_issue_openai.py` in scripts
2. Update documentation to recommend OpenAI API approach
3. Monitor costs and model performance
4. Consider caching business rules for repeat domains
5. Add telemetry/metrics on story quality improvements

---

**New File**: `/home/louisb/Projects/durion/scripts/fix_frontend_issues/fix_issue_openai.py`  
**Documentation**: `/home/louisb/Projects/durion/scripts/fix_frontend_issues/README_OPENAI.md`  
**Examples**: `/home/louisb/Projects/durion/scripts/fix_frontend_issues/examples.sh`
