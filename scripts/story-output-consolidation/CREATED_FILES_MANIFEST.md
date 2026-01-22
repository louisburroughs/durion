# Files Created: Domain Business Rules Updater (Consolidated)

## Location
`/home/louisb/Projects/durion/scripts/story-output-consolidation/`

## Files

### 1. Main Script
```
update-domain-business-rules-consolidated.sh
├─ Size: 23KB
├─ Executable: ✅ Yes
├─ Language: Bash
└─ Purpose: Main script to generate/enhance domain business rules documents
```

**Key Features:**
- Reads consolidated stories from `output/domain-*.txt`
- Loads existing docs from reference location (`durion/domains/{domain}/.business-rules/`)
- Generates 3 documents per domain:
  - AGENT_GUIDE.md (normative)
  - DOMAIN_NOTES.md (non-normative)
  - STORY_VALIDATION_CHECKLIST.md (actionable checklist)
- Outputs to `output/{domain}/.business-rules/`
- Preserves all existing information while adding new answers
- Supports dry-run mode for testing

**Quick Start:**
```bash
cd /home/louisb/Projects/durion/scripts/story-output-consolidation
export OPENAI_API_KEY="sk-..."
./update-domain-business-rules-consolidated.sh --domain inventory
```

### 2. Documentation Files

#### UPDATE_DOMAIN_BUSINESS_RULES_CONSOLIDATED_README.md (14KB)
Complete reference documentation including:
- Purpose and overview
- Key differences from original script
- Prerequisites and setup
- Detailed usage instructions with examples
- All configuration options
- Output structure and document purposes
- How it works (step-by-step)
- Example run output
- Token and cost considerations
- Error handling
- Workflow integration
- Related scripts
- Troubleshooting guide
- Review and promotion workflow
- Future enhancements

#### QUICK_START.md (3.6KB)
Quick reference guide including:
- One-liner setup and run
- Step-by-step walkthrough
- What it does
- Common command options
- Output locations
- Compare and promote instructions
- Basic troubleshooting
- Help command reference

#### IMPLEMENTATION_SUMMARY.md (8.6KB)
This document. Includes:
- Summary of what was created
- Key features overview
- Configuration options
- Comparison with original script
- Document purposes (AGENT_GUIDE, DOMAIN_NOTES, STORY_VALIDATION_CHECKLIST)
- Usage workflow (consolidate → update → review → promote)
- API costs and rate limiting
- Error handling
- Next steps
- Files created checklist

## Input/Output Structure

### Input Sources
```
story-output-consolidation/output/
└── domain-*.txt (consolidated stories from consolidate-stories-by-domain.sh)

durion/domains/
└── {domain}/.business-rules/
    ├── AGENT_GUIDE.md (reference, not modified)
    ├── DOMAIN_NOTES.md (reference, not modified - optional)
    └── STORY_VALIDATION_CHECKLIST.md (reference, not modified)
```

### Output Location
```
story-output-consolidation/output/
└── {domain}/.business-rules/
    ├── AGENT_GUIDE.md (generated/enhanced)
    ├── DOMAIN_NOTES.md (generated/enhanced)
    └── STORY_VALIDATION_CHECKLIST.md (generated/enhanced)
```

## Configuration & Environment

### Environment Variables
- `OPENAI_API_KEY` (required) - Your OpenAI API key
- `OPENAI_MODEL` (optional, default: gpt-5.2)
- `OPENAI_API_BASE` (optional, default: https://api.openai.com/v1)
- `OPENAI_ORG` (optional) - OpenAI organization ID

### Command Line Options
```
--input-dir DIR            Directory containing domain-*.txt (default: ./output)
--output-dir DIR           Output directory (default: ./output)
--domains-ref DIR          Reference durion/domains (default: ../../../domains)
--model MODEL              OpenAI model (default: gpt-5.2)
--api-base URL             OpenAI API base URL
--domain NAME              Process single domain only
--max-chars N              Max chars from stories (default: 150000)
--max-tokens N             Max tokens for LLM response (default: 18000)
--delay SECONDS            Delay between API calls (default: 2)
--dry-run                  Dry run mode (no API calls)
-h, --help                 Show help
```

## Typical Workflow

### Phase 1: Consolidate Stories
```bash
cd /home/louisb/Projects/durion/scripts
./consolidate-stories-by-domain.sh
# Output: story-output-consolidation/output/domain-*.txt
```

### Phase 2: Update Business Rules
```bash
cd story-output-consolidation
export OPENAI_API_KEY="sk-..."

# For all domains
./update-domain-business-rules-consolidated.sh

# Or for single domain
./update-domain-business-rules-consolidated.sh --domain inventory
```

### Phase 3: Review Changes
```bash
# View generated docs
cat output/inventory/.business-rules/AGENT_GUIDE.md

# Compare with originals
diff -u ../../domains/inventory/.business-rules/AGENT_GUIDE.md \
        output/inventory/.business-rules/AGENT_GUIDE.md
```

### Phase 4: Promote Updates (Optional)
```bash
# Copy enhanced docs to canonical location
cp output/inventory/.business-rules/*.md ../../domains/inventory/.business-rules/

# Commit changes
git add ../../domains/inventory/.business-rules/
git commit -m "Update inventory domain docs with consolidated story insights"
```

## Key Improvements Over Original Script

1. **Three Documents Instead of Two**
   - Added DOMAIN_NOTES.md for non-normative rationale
   - Better separation of concerns (normative vs exploratory)

2. **Consolidates Open Questions**
   - Extracts all "Open Questions" from stories
   - Integrates answers into documentation
   - Preserves existing information while enhancing

3. **Separate Input/Output**
   - Reads from consolidated stories location
   - Outputs to separate location for review
   - Reference docs in `durion/domains/` never modified
   - Enables review before promotion

4. **Better Preservation**
   - LLM explicitly instructed to preserve existing content
   - Enhancements integrated into existing structure
   - No information loss

5. **Larger Context Windows**
   - Increased max_chars from 120K to 150K
   - Increased max_tokens from 16K to 18K
   - Better for handling three documents

## Dependencies

- `bash` 4.0+
- `curl` - HTTP client
- `jq` - JSON processor
- OpenAI API key with gpt-5.2 (or other) model access

## Supported Models

- `gpt-5.2` (default) - Latest/recommended
- `gpt-4o` - Works well, larger context
- `gpt-4-turbo` - Works but older
- Any OpenAI chat completion model

## Cost Estimate

**Per complete run (all domains):**
- Domains: ~13
- Calls per domain: 3
- Total API calls: ~39
- Input tokens per call: ~60K
- Output tokens per call: ~10K
- Estimated cost: $5-10 USD

**Using gpt-5.2 pricing** (as of January 2026)

## Rate Limits

- Default: 2 second delay between API calls
- For free tier: Use `--delay 10` or higher
- For rate limit errors: Increase delay

## What Gets Generated

### AGENT_GUIDE.md
- Domain purpose and boundaries
- Key entities and concepts
- Business rules and invariants
- Event patterns and integrations
- API expectations
- Security/authorization
- Observability guidance
- Testing strategies
- Common pitfalls
- Open Questions section
- **All existing content preserved**

### DOMAIN_NOTES.md
- Architectural philosophy
- Decision log with tradeoffs
- Rejected alternatives
- Institutional memory
- Known issues and risks
- Future considerations
- Living document for exploration
- **Complements but doesn't duplicate AGENT_GUIDE.md**

### STORY_VALIDATION_CHECKLIST.md
- Scope/Ownership verification
- Data model validation
- API contract compliance
- Event handling and idempotency
- Security checks
- Observability requirements
- Performance and failure modes
- Testing coverage
- Documentation completeness
- Open Questions to Resolve
- **Working checklist for reviewers**

## Success Criteria

✅ Script created and executable
✅ Reads consolidated stories from new location
✅ Loads existing docs from reference location
✅ Generates three documents per domain
✅ Preserves all existing information
✅ Integrates new questions and answers
✅ Outputs to separate location for review
✅ Comprehensive documentation provided
✅ Quick start guide included
✅ Full README with examples and troubleshooting

## Next Actions

1. **Test single domain run:**
   ```bash
   cd /home/louisb/Projects/durion/scripts/story-output-consolidation
   export OPENAI_API_KEY="sk-..."
   ./update-domain-business-rules-consolidated.sh --domain inventory --delay 3
   ```

2. **Review output:**
   ```bash
   cat output/inventory/.business-rules/AGENT_GUIDE.md
   cat output/inventory/.business-rules/DOMAIN_NOTES.md
   cat output/inventory/.business-rules/STORY_VALIDATION_CHECKLIST.md
   ```

3. **Compare with originals:**
   ```bash
   diff -u ../../domains/inventory/.business-rules/AGENT_GUIDE.md \
           output/inventory/.business-rules/AGENT_GUIDE.md
   ```

4. **Promote when ready:**
   ```bash
   cp output/inventory/.business-rules/*.md ../../domains/inventory/.business-rules/
   ```

## Reference Documentation

- **Full README:** [UPDATE_DOMAIN_BUSINESS_RULES_CONSOLIDATED_README.md](UPDATE_DOMAIN_BUSINESS_RULES_CONSOLIDATED_README.md)
- **Quick Start:** [QUICK_START.md](QUICK_START.md)
- **This Document:** [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md)
- **Original Script:** [update-domain-business-rules.sh](../update-domain-business-rules.sh)
- **Consolidation Script:** [consolidate-stories-by-domain.sh](../consolidate-stories-by-domain.sh)

## Support

For issues or questions:
1. Check [UPDATE_DOMAIN_BUSINESS_RULES_CONSOLIDATED_README.md](UPDATE_DOMAIN_BUSINESS_RULES_CONSOLIDATED_README.md) Troubleshooting section
2. Run with `--dry-run` to see what would happen
3. Check OpenAI API status and rate limits
4. Verify OPENAI_API_KEY is set correctly
