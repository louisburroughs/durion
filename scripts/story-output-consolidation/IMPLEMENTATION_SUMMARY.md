# New Script: Update Domain Business Rules (Consolidated)

## Summary

Created a new script that enhances domain business rules documentation by reading consolidated stories and integrating answers to new questions while preserving all existing information.

**Location:** `/home/louisb/Projects/durion/scripts/story-output-consolidation/update-domain-business-rules-consolidated.sh`

**Executable:** ✅ Yes (chmod +x applied)

## Key Features

### 1. **Reads Consolidated Stories**
- Input: `story-output-consolidation/output/domain-*.txt` (consolidated stories)
- Extracts all "Open Questions" sections from stories
- Provides comprehensive domain context to LLM

### 2. **Loads Existing Documentation**
- Reads existing docs from: `durion/domains/{domain}/.business-rules/`
- Supports three document types:
  - `AGENT_GUIDE.md` (normative)
  - `DOMAIN_NOTES.md` (non-normative, optional)
  - `STORY_VALIDATION_CHECKLIST.md` (actionable checklist)
- LLM is instructed to **enhance** existing docs OR **create new** if missing

### 3. **Preserves All Existing Information**
- LLM explicitly told: "Preserve all existing content structure, sections, and information"
- New questions/answers are integrated into existing framework
- No content is lost; only enhanced with new insights

### 4. **Outputs to Separate Location**
- Output: `story-output-consolidation/output/{domain}/.business-rules/`
- Allows review and comparison before promoting updates
- Original docs in `durion/domains/` remain unchanged
- Can compare and selectively promote enhancements

### 5. **Three Independent LLM Calls**
1. **AGENT_GUIDE.md** - Normative guidance (domain boundaries, entities, business rules, integrations, APIs, security, observability, testing)
2. **DOMAIN_NOTES.md** - Non-normative rationale (architectural philosophy, decision log, tradeoffs, institutional memory, known issues)
3. **STORY_VALIDATION_CHECKLIST.md** - Actionable checklist (scope, data model, API contract, events, security, observability, testing, documentation)

Each call includes:
- Reference to existing docs (if any)
- Full consolidated stories
- Extracted open questions
- Specific instructions for that document type

## Configuration Options

```bash
# Process single domain
./update-domain-business-rules-consolidated.sh --domain inventory

# Process all domains
./update-domain-business-rules-consolidated.sh

# Custom model (default: gpt-5.2)
./update-domain-business-rules-consolidated.sh --model gpt-4o

# Increase delays (default: 2s between calls)
./update-domain-business-rules-consolidated.sh --delay 5

# Dry run (no API calls)
./update-domain-business-rules-consolidated.sh --dry-run

# Custom paths
./update-domain-business-rules-consolidated.sh \
  --input-dir /path/to/consolidated/output \
  --output-dir /path/to/output \
  --domains-ref /path/to/durion/domains
```

## Script Differences from Original

| Aspect | Original `update-domain-business-rules.sh` | New `update-domain-business-rules-consolidated.sh` |
|--------|-----------------------------------------------|-----------------------------------------------------|
| **Input stories** | `./scripts/story-work/output/domain-*.txt` | `./story-output-consolidation/output/domain-*.txt` |
| **Output location** | `./domains/{domain}/.business-rules/` | `./story-output-consolidation/output/{domain}/.business-rules/` |
| **Documents** | AGENT_GUIDE.md + STORY_VALIDATION_CHECKLIST.md | AGENT_GUIDE.md + DOMAIN_NOTES.md + STORY_VALIDATION_CHECKLIST.md |
| **Input max chars** | 120000 | 150000 (larger for consolidated input) |
| **Output max tokens** | 16000 | 18000 (larger for 3 docs + DOMAIN_NOTES) |
| **Reference docs** | Uses output location | Uses `durion/domains/` as reference |
| **Preservation** | Updates in place | Creates output set; originals remain unchanged |
| **Review workflow** | N/A | Can review differences before promoting |

## Document Purposes

### AGENT_GUIDE.md (Normative)
- **Who uses it:** Developers, architects, agents implementing domain features
- **Content:** Authoritative guidance for the domain
- **What it covers:**
  - Domain purpose and system-of-record ownership
  - Key entities and data models
  - Business rules and invariants
  - Event patterns and integration points
  - API contracts and expectations
  - Security/authorization requirements
  - Observability (metrics, traces, attributes)
  - Testing strategies
  - Common pitfalls
  - Open questions needing clarification

### DOMAIN_NOTES.md (Non-Normative)
- **Who uses it:** Architects, senior engineers, governance reviewers
- **Content:** Exploratory and rationale-focused documentation
- **What it covers:**
  - Architectural philosophy and goals
  - Decision log (why choices were made)
  - Tradeoffs and rejected alternatives
  - Institutional memory
  - Known issues and risks
  - Future considerations
- **Note:** Complements AGENT_GUIDE.md without duplicating it

### STORY_VALIDATION_CHECKLIST.md (Actionable)
- **Who uses it:** Story reviewers, implementation validators
- **Content:** Working checklist for validating implementations
- **What it covers:**
  - Scope and ownership verification
  - Data model validation
  - API contract compliance
  - Event handling and idempotency
  - Security checks
  - Observability requirements
  - Performance and failure modes
  - Testing coverage
  - Documentation completeness
  - Open questions affecting validation

## Usage Workflow

### 1. Consolidate Stories
```bash
cd /home/louisb/Projects/durion/scripts
./consolidate-stories-by-domain.sh
# Creates: story-output-consolidation/output/domain-*.txt
```

### 2. Update Business Rules
```bash
cd story-output-consolidation
export OPENAI_API_KEY="sk-..."
./update-domain-business-rules-consolidated.sh

# Creates: output/{domain}/.business-rules/{AGENT_GUIDE.md, DOMAIN_NOTES.md, STORY_VALIDATION_CHECKLIST.md}
```

### 3. Review Differences
```bash
# Compare output with reference
diff -u ../../domains/inventory/.business-rules/AGENT_GUIDE.md \
        output/inventory/.business-rules/AGENT_GUIDE.md
```

### 4. Promote When Ready
```bash
# Copy enhanced docs to canonical location
cp -r output/inventory/.business-rules/* ../../domains/inventory/.business-rules/
git add ../../domains/inventory/.business-rules/
git commit -m "Update inventory domain docs with consolidated story insights"
```

## Documentation Files Created

1. **Script:** `/home/louisb/Projects/durion/scripts/story-output-consolidation/update-domain-business-rules-consolidated.sh` (23KB)
   - Main executable script
   - Reads existing docs, generates enhanced versions
   - Supports single domain or all domains

2. **Full README:** `/home/louisb/Projects/durion/scripts/story-output-consolidation/UPDATE_DOMAIN_BUSINESS_RULES_CONSOLIDATED_README.md`
   - Comprehensive documentation
   - Usage examples
   - Troubleshooting guide
   - Workflow integration details

3. **Quick Start:** `/home/louisb/Projects/durion/scripts/story-output-consolidation/QUICK_START.md`
   - One-liner commands
   - Common tasks
   - Basic troubleshooting

## API Costs

- **Model:** gpt-5.2
- **Calls per domain:** 3 (AGENT_GUIDE, DOMAIN_NOTES, STORY_VALIDATION_CHECKLIST)
- **Input tokens per call:** ~60K (includes existing docs + consolidated stories)
- **Output tokens per call:** ~10K
- **Total domains (typical):** ~13
- **Total calls:** ~39
- **Estimated cost:** $5-10 per complete run

## Rate Limiting

- Default delay: 2 seconds between API calls
- Can be increased with `--delay` option for free tier accounts
- Script reports delays in output

## Error Handling

The script:
- ✅ Validates dependencies (curl, jq)
- ✅ Checks for OPENAI_API_KEY
- ✅ Verifies input/output directories
- ✅ Handles HTTP errors from OpenAI
- ✅ Skips empty domain files
- ✅ Reports failures with exit code 1

## Next Steps

1. **Test with single domain:**
   ```bash
   cd /home/louisb/Projects/durion/scripts/story-output-consolidation
   export OPENAI_API_KEY="sk-..."
   ./update-domain-business-rules-consolidated.sh --domain inventory --delay 3
   ```

2. **Review generated docs:**
   ```bash
   cat output/inventory/.business-rules/AGENT_GUIDE.md
   ```

3. **Compare with originals:**
   ```bash
   diff -u ../../domains/inventory/.business-rules/AGENT_GUIDE.md \
           output/inventory/.business-rules/AGENT_GUIDE.md
   ```

4. **Promote when satisfied:**
   ```bash
   cp output/inventory/.business-rules/*.md ../../domains/inventory/.business-rules/
   ```

## Files Created

- ✅ `update-domain-business-rules-consolidated.sh` (main script, 23KB)
- ✅ `UPDATE_DOMAIN_BUSINESS_RULES_CONSOLIDATED_README.md` (full docs)
- ✅ `QUICK_START.md` (quick reference)

All scripts are executable and ready to use.
