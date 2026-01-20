# fix_issue.py — Resolve Open Questions in Issue Descriptions

## Overview

`fix_issue.py` reads issue descriptions from `after.md` files, matches open questions against canonical business rules from `durion/domains/{domain}/.business-rules/`, and automatically resolves them. The script writes a corrected `fixed.md` file with updated labels and body, and optionally publishes changes to GitHub.

## Key Features

- **Domain-aware resolution**: Reads business rules from domain-specific `.business-rules/` directories and matches questions to authoritative decisions.
- **Batch processing**: Process single files, individual issues, lists of issues, or ranges of issues.
- **Optional GitHub integration**: Update issue bodies and labels directly in durion-moqui-frontend (requires `GITHUB_TOKEN`).
- **Copilot integration**: Generate Copilot prompts or invoke the Copilot CLI to rebuild issues.
- **Dry-run mode**: Preview changes without writing files or calling external services.
- **Flexible domain specification**: Provide domain via CLI argument or extract from `domain:*` labels in `after.md`.

## Usage

### Basic: Fix a single file

```bash
python3 fix_issue.py accounting path/to/after.md
```

### Process issues by ID

With auto-detected `.story-work` directory:

```bash
# Single issue
python3 fix_issue.py --issue 123

# List of issues
python3 fix_issue.py --issues 101,103,109

# Range of issues
python3 fix_issue.py --issue-range 200-210
```

### Process with explicit issue directory

```bash
python3 fix_issue.py --issues 65,66 --issue-dir /path/to/story-work
```

### Update GitHub (requires domain label in after.md)

```bash
export GITHUB_TOKEN="ghp_..."
python3 fix_issue.py --issues 65,66 --update-github --owner louisburroughs --repo durion-moqui-frontend
```

### Dry-run mode

Preview all changes without writing or updating GitHub:

```bash
python3 fix_issue.py --issue 123 --dry-run
python3 fix_issue.py --issues 65,66 --update-github --owner louisburroughs --repo durion-moqui-frontend --dry-run
```

### Generate Copilot prompt

Print JSON payload suitable for Copilot integration:

```bash
python3 fix_issue.py accounting path/to/after.md --print-copilot
```

### Invoke Copilot CLI to rebuild

With the Copilot CLI installed and configured:

```bash
python3 fix_issue.py --issue 123 --rebuild-with-copilot
```

When `--rebuild-with-copilot` is used, the script now calls the Copilot CLI with:

- `-p @<prompt-file>`: prompt text read from file (recommended to avoid escaping). Falls back to inline prompt when no file is provided.
- `--agent <path>`: the workspace agent file at `.github/agents/story-authoring.agent.md`
- `--allow-all-tools`: permits the agent to use all available tools
- `--add-dir <workspace-root>`: grants the agent access to the repository root so it can read project context

## Command-Line Options

| Option | Description |
|--------|-------------|
| `domain` | (Optional) Domain name. If omitted, extracted from `domain:*` label in `after.md` |
| `after_md` | (Optional) Path to `after.md` file |
| `--issue ID` | Single issue ID to process |
| `--issues ID,ID,...` | Comma-separated list of issue IDs |
| `--issue-range START-END` | Inclusive range of issue IDs (e.g., `100-110`) |
| `--issue-dir PATH` | Directory containing issue folders (e.g., `.story-work/`); auto-detected if omitted |
| `--dry-run` | Preview changes without writing files or updating GitHub |
| `--print-copilot` | Print Copilot JSON prompt to stdout |
| `--rebuild-with-copilot` | Run the Copilot CLI to rebuild the issue |
| `--prompt-file PATH` | Path to prompt file for Copilot CLI (used with `--rebuild-with-copilot`; defaults to built-in prompt if omitted) |
| `--agent-ref AGENT` | Agent reference for Copilot (default: `agent://copilot`) |
| `--update-github` | Update issue body and labels in GitHub |
| `--owner USERNAME` | GitHub repo owner (required with `--update-github`) |
| `--repo REPO` | GitHub repo name (required with `--update-github`) |

## Environment Variables

| Variable | Purpose | Required |
|----------|---------|----------|
| `GITHUB_TOKEN` | GitHub API authentication token | Only if `--update-github` is used |

## How It Works

1. **Read `after.md`**: Parse YAML frontmatter (labels) and Markdown body
2. **Extract domain**: Use CLI argument, or extract from `domain:*` label
3. **Load business rules**: Read canonical decisions from `durion/domains/{domain}/.business-rules/`
4. **Find open questions**: Detect question patterns (Q:, TODO:, FIXME:, etc.)
5. **Match & resolve**: Use keyword matching to link questions to business rules; replace with resolved answers
6. **Update labels**: Add/remove status labels based on unresolved questions
7. **Write `fixed.md`**: Output corrected file with updated labels and body
8. **(Optional) Update GitHub**: If `--update-github`, publish to GitHub issue

## Outputs

### Local Output

- **`fixed.md`**: Written next to `after.md`
  - Contains YAML frontmatter with updated labels
  - Resolved questions replaced with links to business rules
  - Unresolved questions marked with `**ACTION REQUIRED**`
  - Label change log appended

### GitHub Output (with `--update-github`)

- Issue body updated with resolved content
- Labels applied:
  - **Required**: Always applied
  - **Blocking/Risk**: Always applied
  - **Recommended**: Applied with `--update-github` (assumes included from `after.md`)

## Exit Codes

| Code | Meaning |
|------|---------|
| 0 | Success: all questions resolved |
| 10 | Validation error (missing files, invalid domain, etc.) |
| 20 | Partial success: some questions remain unresolved (but `fixed.md` written) |

## Label Semantics

The script automatically manages labels based on resolution status:

| Label | When Applied | When Removed |
|-------|--------------|--------------|
| `blocked:clarification` | Open questions remain unresolved | All questions resolved |
| `needs:domain-owner` | Added with `blocked:clarification` | Removed when clarification blocks cleared |
| `status:ready-for-review` | No unresolved questions | Added when questions resolved |

## Business Rules Discovery

The script expects business rules in a canonical structure:

```
durion/domains/{domain}/.business-rules/
├── AGENT_GUIDE.md          # Normative guide with decisions
├── DOMAIN_NOTES.md         # Rationale and design alternatives
└── ...other docs...
```

The script indexes:

- `DECISION-INVENTORY-###` identifiers
- Markdown headings (H2, H3)

## Examples

### Example 1: Fix a single issue from file

```bash
python3 fix_issue.py accounting /path/to/123/after.md
# Output: /path/to/123/fixed.md
```

### Example 2: Batch fix multiple issues and publish to GitHub

```bash
export GITHUB_TOKEN="ghp_xxxx"
python3 fix_issue.py --issues 65,66,67 --update-github --owner louisburroughs --repo durion-moqui-frontend
# Processes: .story-work/65/after.md, .story-work/66/after.md, .story-work/67/after.md
# Updates each corresponding GitHub issue
```

### Example 3: Dry-run to preview changes

```bash
python3 fix_issue.py --issue 100 --issue-range 105-110 --dry-run
# Prints: Processing: .story-work/100/after.md
#         DRY RUN: would have written .story-work/100/fixed.md
#         (similar for each issue in range)
```

## Prompt Customization

By default, when using `--rebuild-with-copilot`, the script sends the following prompt to the Copilot CLI:

```
Resolve open questions in the issue using canonical business rules. For each resolution, include the decision ID or source and one-line rationale.
```

To use a custom prompt, provide a text file path via `--prompt-file`:

```bash
python3 fix_issue.py --issue 123 --rebuild-with-copilot --prompt-file /path/to/custom-prompt.txt
```

**Behavior:**

- If `--prompt-file` is provided and the file exists, its contents are sent to Copilot
- If `--prompt-file` is omitted, the default prompt is used
- If the file cannot be read, a warning is printed and the default prompt is used

**Example custom prompt file (`custom-prompt.txt`):**

```
You are an expert at resolving ambiguities in product specifications.
Analyze the open questions and provide answers backed by business rules.
For each resolution:
1. State the decision clearly
2. Reference the authoritative source (e.g., DECISION-INVENTORY-001)
3. Briefly explain why this is the correct approach
```

Then invoke with:

```bash
python3 fix_issue.py --issue 123 --rebuild-with-copilot --prompt-file custom-prompt.txt
```

## Dependencies

- **Python 3.7+**
- **requests** (optional, for GitHub integration): `pip install requests`
- **publish_stories.py** (optional, for GitHub integration): Must be in parent directory
- **GITHUB_TOKEN** (optional, for GitHub updates): GitHub personal access token

## Troubleshooting

### `Business rules missing or empty for domain`

Verify that `durion/domains/{domain}/.business-rules/` exists and contains `.md` files with decisions.

### `Could not auto-detect issue directory`

Provide `--issue-dir` explicitly, or ensure `.story-work` exists in current or parent directories.

### `GITHUB_TOKEN env var required`

Set your GitHub token: `export GITHUB_TOKEN="ghp_..."`

### GitHub update fails with 404

Ensure `--owner` and `--repo` match an actual repository you have access to.
