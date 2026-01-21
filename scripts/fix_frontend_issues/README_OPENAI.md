# fix_issue_openai.py – OpenAI-Based Story Editor

## Overview

`fix_issue_openai.py` is a Python CLI tool that uses the **OpenAI API** to automatically review, refine, and update GitHub issue stories for the Durion platform. It replaces the Copilot CLI integration with direct OpenAI API calls, enabling more reliable story editing with full prompt customization and GitHub issue synchronization.

## Key Features

- **OpenAI API Integration**: Uses `gpt-4o-mini` (or configurable model) via OpenAI's REST API
- **Full Prompt Building**: Constructs comprehensive prompts including:
  - Main prompt instructions (from `.github/prompts/story-frontend-rewrite.prompt.md`)
  - Domain-specific business rules (from `domains/{domain}/.business-rules/`)
  - Complete issue body and metadata
  - Explicit task and output requirements
- **Batch Processing**: Process single issues, comma-separated lists, or numeric ranges
- **GitHub Integration**: Automatically updates issues with new content and labels after successful OpenAI processing
- **YAML Frontmatter Parsing**: Preserves and manages YAML labels in story frontmatter
- **Comprehensive Logging**: Debug logs written to `fix_issue_debug.log` in the issue directory
- **Validation**: Validates inputs, ensures required files exist, and reports clear errors

## Installation

No additional dependencies beyond the base Python 3 environment. Uses only built-in modules:
- `urllib.request` / `urllib.error` for HTTP communication
- `json` for payload handling
- `re`, `logging`, `argparse`, etc.

Requires the existing `publish_stories.py` module for GitHub integration.

## Environment Variables

- **OPENAI_API_KEY** (required): Your OpenAI API key for making API calls
- **GITHUB_TOKEN** (optional): GitHub personal access token for updating issues with `--update-github`

## Usage

### Basic: Update a single after.md file

```bash
python3 fix_issue_openai.py inventory /path/to/after.md
```

The script will:
1. Read the after.md file
2. Extract labels and body
3. Load business rules from `domains/inventory/.business-rules/`
4. Build a comprehensive prompt with all context
5. Call OpenAI API
6. Write the updated story to `fixed.md` in the same directory

### Process by issue ID (single)

```bash
python3 fix_issue_openai.py --issue 67 \
  --issue-dir /home/louisb/Projects/durion/scripts/story-work/frontend \
  --update-github --owner louisburroughs --repo durion-moqui-frontend
```

### Process multiple issues by comma-separated list

```bash
python3 fix_issue_openai.py --issues 65,67,68 \
  --issue-dir /home/louisb/Projects/durion/scripts/story-work/frontend \
  --model gpt-4o \
  --update-github --owner louisburroughs --repo durion-moqui-frontend
```

### Process a range of issues

```bash
python3 fix_issue_openai.py --issue-range 60-70 \
  --issue-dir /home/louisb/Projects/durion/scripts/story-work/frontend \
  --prompt-file .github/prompts/story-frontend-rewrite.prompt.md \
  --update-github --owner louisburroughs --repo durion-moqui-frontend
```

### Dry-run mode (preview without making changes)

```bash
python3 fix_issue_openai.py inventory /path/to/after.md --dry-run
```

## Command-Line Options

| Option | Description |
|--------|-------------|
| `domain` | Domain name (e.g., 'inventory', 'order'). Optional if `domain:*` label exists in after.md |
| `after_md` | Path to the after.md file to process |
| `--issue ID` | Process a single issue by ID |
| `--issues ID1,ID2,ID3` | Process multiple issues by comma-separated IDs |
| `--issue-range START-END` | Process all issues in a numeric range (inclusive) |
| `--issue-dir PATH` | Directory containing issue folders (required with `--issue*`) |
| `--prompt-file PATH` | Custom prompt file (defaults to `.github/prompts/story-frontend-rewrite.prompt.md`) |
| `--model NAME` | OpenAI model to use (default: `gpt-4o-mini`) |
| `--dry-run` | Preview changes without writing files or calling APIs |
| `--update-github` | Update the GitHub issue with new content and labels |
| `--owner NAME` | GitHub repository owner (required with `--update-github`) |
| `--repo NAME` | GitHub repository name (required with `--update-github`) |

## How It Works

### 1. **Input Validation**
   - Verifies after.md exists and contains YAML frontmatter
   - Extracts labels and body content
   - Determines domain from CLI arg, Required section, or labels

### 2. **Prompt Building** (`build_full_prompt()`)
   - Loads main prompt instructions from `.github/prompts/story-frontend-rewrite.prompt.md`
   - Loads all business rules files from `domains/{domain}/.business-rules/`
   - Includes complete issue body and metadata
   - Appends explicit task instructions and output requirements
   - Returns a comprehensive, well-structured prompt string

### 3. **OpenAI API Call** (`call_openai()`)
   - Constructs HTTP POST request to `https://api.openai.com/v1/chat/completions`
   - Uses system prompt to set story editor context
   - Sends full prompt as user message
   - Parameters: `temperature=0.3` (low variance), `max_tokens=8000`
   - Returns model's response text

### 4. **Output Processing**
   - Writes raw OpenAI response to `fixed.md` in the issue directory
   - Parses YAML frontmatter from response if present
   - Optionally updates GitHub issue body and labels

### 5. **GitHub Integration** (if `--update-github`)
   - Uses GitHubClient from publish_stories.py
   - Extracts body content (removes YAML frontmatter)
   - Updates issue body with new content
   - Applies labels using domain categorization logic
   - Returns success/failure status

## Output

### File System
- **fixed.md**: Updated story written to `{issue_dir}/fixed.md`
- **fix_issue_debug.log**: Detailed debug log in `{issue_dir}/`

### Console Output
```
✓ Wrote /path/to/fixed.md
Updated GitHub issue louisburroughs/durion-moqui-frontend#67
Updated labels for louisburroughs/durion-moqui-frontend#67: ['domain:frontend', 'status:ready-for-review']
```

### Exit Codes
- `0`: Success
- `1`: Argument validation error
- `10`: File not found, OpenAI API error, or GitHub update error
- `20`: Validation warnings (future extension point)

## Comparison with Original fix_issue.py

| Aspect | Original (Copilot CLI) | New (OpenAI API) |
|--------|------------------------|------------------|
| **Integration** | subprocess.run() with Copilot CLI | urllib.request REST API |
| **Prompt Delivery** | stdin via `--prompt -` | JSON request body |
| **Tool Restrictions** | Limited to `create_file` | Direct response parsing |
| **Prompt Building** | Minimal (3 sections) | Full/comprehensive |
| **File Output** | Via Copilot's create_file tool | Direct Python write |
| **GitHub Updates** | Manual or separate step | Integrated post-processing |
| **Dependency** | GitHub Copilot CLI required | Python + OPENAI_API_KEY |
| **Reliability** | Affected by Copilot's behavior | Direct, predictable |

## Example Workflow

```bash
# Set up environment
export OPENAI_API_KEY="sk-..."
export GITHUB_TOKEN="ghp_..."

# Process issues 65-70 for the inventory domain
cd /home/louisb/Projects/durion
python3 scripts/fix_frontend_issues/fix_issue_openai.py \
  --issue-range 65-70 \
  --issue-dir scripts/story-work/frontend \
  --prompt-file .github/prompts/story-frontend-rewrite.prompt.md \
  --model gpt-4o-mini \
  --update-github \
  --owner louisburroughs \
  --repo durion-moqui-frontend

# Check results
ls -la scripts/story-work/frontend/*/fixed.md
tail -50 scripts/story-work/frontend/67/fix_issue_debug.log
```

## Troubleshooting

### "OPENAI_API_KEY environment variable not set"
```bash
export OPENAI_API_KEY="your-key-here"
```

### "OpenAI API error (401): Unauthorized"
- Verify OPENAI_API_KEY is correct
- Check API key has not expired

### "OpenAI API error (429): Rate limit exceeded"
- Wait a moment and retry
- Use a later timestamp for batch processing

### "Domain not specified and no domain label found"
- Either provide domain as CLI arg: `python3 script.py inventory ...`
- Or add a domain label to the `### Required` section of after.md

### "GitHub client not available"
- Ensure `publish_stories.py` is in the parent directory
- Set GITHUB_TOKEN if planning to use `--update-github`

## Advanced: Customizing the Prompt

Edit or create a custom prompt file and pass it via `--prompt-file`:

```bash
python3 fix_issue_openai.py inventory /path/to/after.md \
  --prompt-file /path/to/custom-prompt.md
```

The prompt file should contain your story editing instructions. They will be combined with business rules and the issue body automatically.

## Integration with durion Workflow

This script fits into the Durion story editing pipeline:

1. **Story Creation**: Use `story_update.py` to generate initial stories
2. **Manual Review**: Authors review in GitHub; add comments/decisions
3. **Automated Refinement**: Use `fix_issue_openai.py` to resolve issues
4. **Final Sync**: GitHub issue is automatically updated with refined content

## Security Notes

- **API Keys**: Store OPENAI_API_KEY and GITHUB_TOKEN in environment, never in code
- **Input Validation**: All file paths are validated before use
- **Output Escaping**: JSON encoding is used for API payloads (urllib handles escaping)
- **No Secret Logging**: Debug logs do not include API keys or sensitive GitHub data

## Future Enhancements

- Support for other OpenAI models (GPT-4 Turbo, etc.)
- Streaming response support for long outputs
- Configurable temperature/max_tokens per domain
- Webhook support for automated processing on issue updates
- Metrics/analytics on story refinement quality
