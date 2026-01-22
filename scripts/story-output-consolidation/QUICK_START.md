# Quick Start: Update Domain Business Rules (Consolidated)

## One-Liner Setup & Run

```bash
cd /home/louisb/Projects/durion/scripts/story-output-consolidation && \
export OPENAI_API_KEY="sk-your-key-here" && \
./update-domain-business-rules-consolidated.sh --domain inventory --delay 3
```

## Step-by-Step

### 1. Prerequisites
- OpenAI API key: `export OPENAI_API_KEY="sk-..."`
- Consolidated domain files: `./output/domain-*.txt` (created by `consolidate-stories-by-domain.sh`)
- Existing domain docs (optional): `../../domains/{domain}/.business-rules/`

### 2. Run for Single Domain
```bash
cd /home/louisb/Projects/durion/scripts/story-output-consolidation
export OPENAI_API_KEY="sk-..."

# Update inventory domain
./update-domain-business-rules-consolidated.sh --domain inventory
```

### 3. Run for All Domains
```bash
# Process all domain-*.txt files
./update-domain-business-rules-consolidated.sh
```

### 4. Check Output
```bash
# View generated documents
ls -la output/inventory/.business-rules/
cat output/inventory/.business-rules/AGENT_GUIDE.md
```

## What It Does

1. Reads consolidated stories from `output/domain-*.txt`
2. Loads existing docs from `../../domains/{domain}/.business-rules/` (if present)
3. Extracts open questions from stories
4. Calls OpenAI 3 times to generate/enhance:
   - **AGENT_GUIDE.md** - Normative domain guidance
   - **DOMAIN_NOTES.md** - Non-normative rationale and exploration
   - **STORY_VALIDATION_CHECKLIST.md** - Actionable validation checklist
5. Outputs to `output/{domain}/.business-rules/`

## Common Options

| Task | Command |
|------|---------|
| Single domain | `./update-domain-business-rules-consolidated.sh --domain inventory` |
| All domains | `./update-domain-business-rules-consolidated.sh` |
| Dry run | `./update-domain-business-rules-consolidated.sh --dry-run` |
| Custom model | `./update-domain-business-rules-consolidated.sh --model gpt-4o` |
| Increase delays | `./update-domain-business-rules-consolidated.sh --delay 5` |
| Custom paths | `./update-domain-business-rules-consolidated.sh --input-dir /path/to/input --output-dir /path/to/output` |

## Output Locations

Generated documents are placed in:
```
story-output-consolidation/output/{domain}/.business-rules/
├── AGENT_GUIDE.md
├── DOMAIN_NOTES.md
└── STORY_VALIDATION_CHECKLIST.md
```

Reference/original documents remain at:
```
durion/domains/{domain}/.business-rules/
├── AGENT_GUIDE.md
├── DOMAIN_NOTES.md (if exists)
└── STORY_VALIDATION_CHECKLIST.md
```

## Compare & Promote

### View Differences
```bash
diff -u ../../domains/inventory/.business-rules/AGENT_GUIDE.md \
        output/inventory/.business-rules/AGENT_GUIDE.md
```

### Copy to Canonical Location (When Ready)
```bash
cp output/inventory/.business-rules/*.md ../../domains/inventory/.business-rules/
```

## Troubleshooting

### No domain files found
```bash
# Ensure consolidated files exist
ls output/domain-*.txt

# If missing, run consolidation first
cd ../
./consolidate-stories-by-domain.sh
```

### API key errors
```bash
export OPENAI_API_KEY="sk-..."
echo $OPENAI_API_KEY  # Verify it's set
```

### Rate limit errors
```bash
# Increase delay between API calls
./update-domain-business-rules-consolidated.sh --delay 10
```

### Token limit exceeded
```bash
# Reduce input size or increase output tokens
./update-domain-business-rules-consolidated.sh --max-chars 100000 --max-tokens 20000
```

## Full Help
```bash
./update-domain-business-rules-consolidated.sh --help
```

See [UPDATE_DOMAIN_BUSINESS_RULES_CONSOLIDATED_README.md](UPDATE_DOMAIN_BUSINESS_RULES_CONSOLIDATED_README.md) for full documentation.
