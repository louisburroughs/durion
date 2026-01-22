# Domain Business Rules Updater (Consolidated) - Index

## 📋 Overview

New script system for generating and enhancing domain business rules documentation by reading consolidated stories and existing documentation, then integrating answers to new questions while preserving all existing information.

**Location:** `/home/louisb/Projects/durion/scripts/story-output-consolidation/`

## 🚀 Quick Start

```bash
# 1. Navigate to script directory
cd /home/louisb/Projects/durion/scripts/story-output-consolidation

# 2. Set your OpenAI API key
export OPENAI_API_KEY="sk-your-key-here"

# 3. Run for single domain
./update-domain-business-rules-consolidated.sh --domain inventory --delay 3

# 4. Check output
cat output/inventory/.business-rules/AGENT_GUIDE.md
```

## 📁 Files Created

### Main Script
| File | Size | Purpose |
|------|------|---------|
| `update-domain-business-rules-consolidated.sh` | 23KB | Main executable script |

### Documentation
| File | Size | Purpose |
|------|------|---------|
| `UPDATE_DOMAIN_BUSINESS_RULES_CONSOLIDATED_README.md` | 14KB | **👈 START HERE - Full documentation with examples and troubleshooting** |
| `QUICK_START.md` | 3.6KB | Quick reference with common commands |
| `IMPLEMENTATION_SUMMARY.md` | 8.6KB | Overview of features and improvements |
| `CREATED_FILES_MANIFEST.md` | ~6KB | Manifest of all created files |

## 🎯 What It Does

1. **Reads** consolidated domain stories from `output/domain-*.txt`
2. **Loads** existing business rules from `durion/domains/{domain}/.business-rules/`
3. **Extracts** open questions from consolidated stories
4. **Generates/Enhances** three documents per domain:
   - `AGENT_GUIDE.md` - Normative domain guidance
   - `DOMAIN_NOTES.md` - Non-normative rationale & exploration
   - `STORY_VALIDATION_CHECKLIST.md` - Actionable validation checklist
5. **Outputs** to `output/{domain}/.business-rules/`
6. **Preserves** all existing information while adding new answers

## 📚 Which Document Should I Read First?

**👉 [UPDATE_DOMAIN_BUSINESS_RULES_CONSOLIDATED_README.md](UPDATE_DOMAIN_BUSINESS_RULES_CONSOLIDATED_README.md)**

This is the comprehensive guide with:
- Complete feature description
- Detailed usage examples
- All configuration options
- Output structure and document purposes
- Step-by-step workflow
- Troubleshooting guide

## 🔍 Read by Use Case

| Goal | Read This |
|------|-----------|
| **Quick start** | [QUICK_START.md](QUICK_START.md) |
| **Full documentation** | [UPDATE_DOMAIN_BUSINESS_RULES_CONSOLIDATED_README.md](UPDATE_DOMAIN_BUSINESS_RULES_CONSOLIDATED_README.md) |
| **What was created** | [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md) |
| **File manifest** | [CREATED_FILES_MANIFEST.md](CREATED_FILES_MANIFEST.md) |
| **Run the script** | `./update-domain-business-rules-consolidated.sh --help` |

## 🛠️ Common Commands

```bash
# Show help
./update-domain-business-rules-consolidated.sh --help

# Dry run (no API calls)
./update-domain-business-rules-consolidated.sh --dry-run

# Single domain
./update-domain-business-rules-consolidated.sh --domain inventory

# All domains
./update-domain-business-rules-consolidated.sh

# With custom delays (for rate limits)
./update-domain-business-rules-consolidated.sh --delay 5

# With custom model
./update-domain-business-rules-consolidated.sh --model gpt-4o
```

## 📂 Input/Output Structure

```
Input:   story-output-consolidation/output/domain-*.txt
         ↓
Reference: durion/domains/{domain}/.business-rules/ (NEVER modified)
         ↓
Output:  story-output-consolidation/output/{domain}/.business-rules/
         ├── AGENT_GUIDE.md (enhanced)
         ├── DOMAIN_NOTES.md (enhanced)
         └── STORY_VALIDATION_CHECKLIST.md (enhanced)
```

## 🔄 Typical Workflow

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
# Creates: output/{domain}/.business-rules/{AGENT_GUIDE,DOMAIN_NOTES,STORY_VALIDATION_CHECKLIST}.md
```

### 3. Review Changes
```bash
# View generated docs
cat output/inventory/.business-rules/AGENT_GUIDE.md

# Compare with originals
diff -u ../../domains/inventory/.business-rules/AGENT_GUIDE.md \
        output/inventory/.business-rules/AGENT_GUIDE.md
```

### 4. Promote When Ready
```bash
# Copy enhanced docs to canonical location
cp output/inventory/.business-rules/*.md ../../domains/inventory/.business-rules/

# Commit changes
git add ../../domains/inventory/.business-rules/
git commit -m "Update inventory domain docs with consolidated story insights"
```

## 💡 Key Features

✅ **Three Documents Generated**
- AGENT_GUIDE.md (normative guidance)
- DOMAIN_NOTES.md (non-normative rationale)
- STORY_VALIDATION_CHECKLIST.md (actionable checklist)

✅ **Preserves Existing Information**
- LLM instructed to maintain all existing content
- New answers integrated into existing structure
- No information is lost

✅ **Separate Input/Output**
- Reads from consolidated stories
- Outputs to separate location for review
- Reference docs never modified
- Enables comparison before promotion

✅ **Consolidates Open Questions**
- Extracts all "Open Questions" from stories
- Integrates answers into documentation
- Updates "Open Questions" sections with new questions

✅ **Flexible Configuration**
- Single domain or all domains
- Custom OpenAI model (default: gpt-5.2)
- Configurable API delays, token limits, char limits
- Dry-run mode for testing

✅ **Production Ready**
- Error handling and validation
- Rate limiting support
- Comprehensive logging
- Exit codes for CI/CD integration

## 🤖 Generated Documents Explained

### AGENT_GUIDE.md (Normative)
**For:** Developers, architects, agents implementing features
**Contains:**
- Domain boundaries and system-of-record ownership
- Key entities and data models
- Business rules and invariants
- Event patterns and integrations
- API contracts and expectations
- Security/authorization requirements
- Observability guidance (metrics, traces)
- Testing strategies
- Common pitfalls
- **Open Questions from Frontend Stories**

### DOMAIN_NOTES.md (Non-Normative)
**For:** Architects, senior engineers, governance reviewers
**Contains:**
- Architectural philosophy and goals
- Decision log (why choices were made)
- Tradeoffs and rejected alternatives
- Institutional memory
- Known issues and risks
- Future considerations
- Living document for exploration

### STORY_VALIDATION_CHECKLIST.md (Actionable)
**For:** Story reviewers, implementation validators
**Contains:**
- Scope and ownership verification
- Data model validation
- API contract compliance
- Event handling and idempotency
- Security checks
- Observability requirements
- Performance and failure modes
- Testing coverage
- Documentation completeness
- **Open Questions to Resolve**

## 📊 Costs & Performance

- **Model:** gpt-5.2 (customizable)
- **Domains:** ~13 typical
- **Calls per domain:** 3 (AGENT_GUIDE, DOMAIN_NOTES, STORY_VALIDATION_CHECKLIST)
- **Input tokens:** ~60K per call
- **Output tokens:** ~10K per call
- **Estimated cost:** $5-10 per complete run
- **Rate limit delay:** 2 seconds default (configurable)

## ✅ Verification

All files created successfully:
- ✅ Main script: `update-domain-business-rules-consolidated.sh` (executable)
- ✅ Full README: 14KB comprehensive guide
- ✅ Quick start: 3.6KB reference
- ✅ Implementation summary: 8.6KB overview
- ✅ Files manifest: Complete listing

## 🚀 Next Steps

1. **Read the full documentation:**
   → [UPDATE_DOMAIN_BUSINESS_RULES_CONSOLIDATED_README.md](UPDATE_DOMAIN_BUSINESS_RULES_CONSOLIDATED_README.md)

2. **Try a quick test:**
   ```bash
   cd /home/louisb/Projects/durion/scripts/story-output-consolidation
   export OPENAI_API_KEY="sk-..."
   ./update-domain-business-rules-consolidated.sh --dry-run
   ```

3. **Run for a domain:**
   ```bash
   ./update-domain-business-rules-consolidated.sh --domain inventory --delay 3
   ```

4. **Review output and decide about promotion:**
   ```bash
   diff -u ../../domains/inventory/.business-rules/AGENT_GUIDE.md \
           output/inventory/.business-rules/AGENT_GUIDE.md
   ```

## 📖 Documentation Map

```
INDEX (you are here)
├── For Quick Start
│   └── QUICK_START.md
├── For Full Documentation  
│   └── UPDATE_DOMAIN_BUSINESS_RULES_CONSOLIDATED_README.md ⭐ START HERE
├── For Implementation Details
│   └── IMPLEMENTATION_SUMMARY.md
├── For File Details
│   └── CREATED_FILES_MANIFEST.md
└── The Script Itself
    └── update-domain-business-rules-consolidated.sh
```

## 🆘 Troubleshooting Quick Links

| Issue | Solution |
|-------|----------|
| "Missing OPENAI_API_KEY" | `export OPENAI_API_KEY="sk-..."` |
| "API error HTTP 429" | Use `--delay 5` or higher |
| "Empty response" | Increase `--max-tokens` |
| "No domain files found" | Run `consolidate-stories-by-domain.sh` first |
| More help | See [Troubleshooting section in README](UPDATE_DOMAIN_BUSINESS_RULES_CONSOLIDATED_README.md) |

---

**Ready to get started?** 
→ **[Read the Full Documentation](UPDATE_DOMAIN_BUSINESS_RULES_CONSOLIDATED_README.md)** or **[Quick Start Guide](QUICK_START.md)**
