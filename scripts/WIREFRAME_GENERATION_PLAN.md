# Wireframe Generation Plan — Issue Extraction to OpenAI Wireframes

## Overview

Extract GitHub issues and comments from `durion-moqui-frontend` repository, summarize long threads locally using TextRank, call OpenAI API with a strict prompt to generate wireframes, and write outputs to `durion/domains/{domain}/.ui/{shortname}-{issue#}.wf.md`.

## Architecture

### Core Modules (workspace-agents/)

1. **github_client.py** — GitHub API integration
   - `fetch_issues(repo, since=None) -> List[dict]`
   - `fetch_comments(repo, issue_number) -> List[dict]`
   - `load_local_dump(path) -> List[dict]` (fallback for JSON exports)

2. **summarizer.py** — Local TextRank summarization with map-reduce
   - `preprocess_texts(title, body, comments) -> List[str]`
   - `chunk_texts(sentences, max_chars=3500, overlap_chars=200) -> List[str]`
   - `summarize_chunk(chunk_text, sentences=3) -> str`
   - `map_reduce_summarize(title, body, comments, map_sentences=3, reduce_sentences=6, max_total_chars=2000) -> str`
   - `summarize_locally(issue_obj, comments) -> str` (public entrypoint)

3. **prompt_builder.py** — Construct strict LLM prompts
   - `build_prompt(issue, comments, labels) -> dict` (returns messages for OpenAI)
   - `validate_summary(summary) -> bool`

4. **openai_client.py** — OpenAI API wrapper with retry logic
   - `call_openai(messages, model="gpt-4", temperature=0.0, max_tokens=900) -> str`
   - Uses `retry_with_backoff()` internally

5. **utils.py** — Utilities and retry logic
   - `retry_with_backoff(callable, max_attempts=5, base_delay=1.0, max_delay=60.0, jitter=True) -> Any`
   - `slugify_name(name) -> str`
   - `sanitize_markdown(md) -> str`
   - `map_labels_to_domain(labels, mapping) -> str`

6. **writer.py** — Atomic file writing
   - `write_wireframe(domain, shortname, issue_number, content, outdir_base="durion/domains") -> str`
   - `write_metadata(filepath, metadata) -> None` (sidecar .meta.json)

7. **issue_wireframe_extractor.py** — Main CLI orchestrator
   - CLI arguments: `--repo`, `--staging`, `--batch-size`, `--max-issues`
   - Load config, fetch issues, process sequentially, handle failures

8. **config.yml** — Configuration
   - `repo: louisburroughs/durion-moqui-frontend`
   - `label_domain_mapping: {...}`
   - `summarizer_params: {...}`
   - `openai_params: {...}`
   - `retry_params: {...}`

## Data Flow

```
GitHub Issues + Comments
    ↓
fetch_issues() + fetch_comments()
    ↓
map_labels_to_domain() → domain
    ↓
summarize_locally() → compact summary
    ↓
build_prompt() → OpenAI messages (system + user)
    ↓
call_openai() (with retry_with_backoff)
    ↓
Validate output (check for ERROR or required sections)
    ↓
slugify_name() + sanitize_markdown()
    ↓
write_wireframe() → durion/domains/{domain}/.ui/{shortname}-{issue#}.wf.md
    ↓
write_metadata() → .meta.json sidecar
```

## OpenAI Prompt Template

### System Message (exact)

```
You are a wireframe generator that converts GitHub issue data into a single, well-structured wireframe Markdown document. Follow all instructions exactly and do not deviate. Use the issue inputs only to produce the wireframe requested in the user message. If you cannot follow the output rules, return exactly: ERROR: CANNOT PRODUCE WIREFRAME
```

### User Message Template (exact; substitute placeholders)

```
INPUT DATA (will be injected here):  
- Issue title: {{issue_title}}  
- Issue body: {{issue_body}}  
- Comments (chronological): {{comments}} (each comment as: author, date, text)  
- Labels: {{labels}} (list)

FILENAME FORMAT: The target filename will be {shortname}-{issue_number}.wf.md where `shortname` is a slugified version of the issue title and `issue_number` is the GitHub issue number.

REQUIRED OUTPUT: Produce ONE Markdown wireframe document and NOTHING ELSE. The document must contain the following sections in this exact order, each as a top-level Markdown heading (use `#` for Title and `##` for other sections):

# Title
## Purpose
## Components
## Layout
## Interaction Flow
## Notes

CONTENT GUIDANCE:
- Title: use the issue title verbatim.
- Purpose: synthesize the issue body into 2–4 concise sentences stating the goal and user problem.
- Components: list UI components as short bullets (e.g., header, form fields, buttons, lists, modals) inferred from issue + comments.
- Layout: describe hierarchy and placement (top, left, main, right, footer) with short bullets or a simple ASCII-style layout sketch (no fenced code blocks).
- Interaction Flow: give step-by-step numbered bullets for primary user flows and key edge cases referenced in comments.
- Notes: include constraints, acceptance criteria, important comments, and TODOs for designers/developers. Use labels to influence tone/context but DO NOT print labels.

STRICT OUTPUT RULE (MUST BE FOLLOWED):
OUTPUT ONLY THE WIREFRAME. NO EXPLANATION, NO PREAMBLE, NO FOOTER, NO METADATA, NO JSON, NO CODE BLOCKS, NO MARKDOWN FENCED BLOCKS, NO ADDITIONAL TEXT OR CHATTER. If you cannot comply with this exact rule, output exactly:
ERROR: CANNOT PRODUCE WIREFRAME

ADDITIONAL RULES:
- Keep the wireframe concise and human-readable (roughly 100–800 words).
- Use plain Markdown headings and lists only.
- Do not include the filename, file path, token counts, or any system/debug info in the output.
- Do not ask questions — produce the wireframe from the provided inputs.

Now generate the wireframe using the provided inputs.
```

## Local Summarization (TextRank Map-Reduce)

### Purpose
Reduce long issue threads (body + comments) into compact summaries to save tokens and cost before sending to OpenAI.

### Method
Use **sumy** TextRank (lightweight, local, no external API calls).

### Hyperparameters (defaults)
- `chunk_max_chars = 3500`
- `overlap_chars = 200`
- `map_sentences = 3` (per chunk)
- `reduce_sentences = 6` (final summary)
- `max_total_chars = 2000`

### Map-Reduce Flow

1. **Preprocess**: 
   - Flatten: title + body + comments (chronological) → sentence list
   - Clean: strip control chars, remove code blocks, dedupe identical comments
   - Tokenize: use `nltk.sent_tokenize` for deterministic sentence splitting

2. **Short thread path** (length ≤ chunk_max_chars):
   - Single pass: `summarize_chunk(text, sentences=reduce_sentences)`

3. **Long thread path** (length > chunk_max_chars):
   - **Chunk**: `chunks = chunk_texts(sentences, chunk_max_chars, overlap_chars)`
   - **Map**: `map_summaries = [summarize_chunk(c, map_sentences) for c in chunks]`
   - **Reduce**: `reduced = summarize_chunk(" ".join(map_summaries), reduce_sentences)`
   - **Second reduce** (if still > max_total_chars): `reduced = summarize_chunk(reduced, max(1, reduce_sentences//2))`

4. **Output**: Return `reduced.strip()` as `issue_summary`

### Determinism
- Preserve original chronological order when selecting sentences
- Use stable tokenization (nltk)
- No randomness in TextRank (deterministic graph building)

## Retry Strategy

### retry_with_backoff() Implementation

**Triggers**: Retry on HTTP 429, 502, 503, 504, network timeouts, transient client errors

**Parameters**:
- `max_attempts = 5`
- `base_delay = 1.0` seconds
- `max_delay = 60.0` seconds
- `jitter = True` (add uniform(0, base_delay) randomness)

**Backoff Formula**:
```python
delay = min(max_delay, base_delay * 2**(attempt-1))
if jitter:
    delay += random.uniform(0, base_delay)
```

**Retry-After Handling**:
- If response includes `Retry-After` header, use its value instead of computed delay

**Failure Handling**:
- After `max_attempts`, raise exception
- Log attempt count, HTTP status, error message
- Mark issue as `failed` in `staging/failures.csv`

**Usage**:
```python
response = retry_with_backoff(lambda: call_openai(messages))
```

## Output File Structure

### Wireframe Files
**Path**: `durion/domains/{domain}/.ui/{shortname}-{issue#}.wf.md`

**Example**: `durion/domains/shop/.ui/payment-flow-123.wf.md`

**Content**: Pure Markdown wireframe (no frontmatter, no metadata)

### Metadata Sidecars
**Path**: `durion/domains/{domain}/.ui/{shortname}-{issue#}.wf.meta.json`

**Schema**:
```json
{
  "issue_number": 123,
  "issue_title": "Payment Flow Improvements",
  "domain": "shop",
  "shortname": "payment-flow",
  "model": "gpt-4",
  "temperature": 0.0,
  "max_tokens": 900,
  "prompt_hash": "sha256:...",
  "tokens_used": 450,
  "timestamp": "2026-01-25T10:30:00Z",
  "original_issue_url": "https://github.com/...",
  "labels": ["ui", "shop", "enhancement"],
  "summarized": true,
  "summary_method": "textrank_mapreduce"
}
```

### Staging Directory
**Path**: `durion/domains/{domain}/.ui/staging/`

**Purpose**: Write outputs here first for manual QA before finalizing

**Review CSV**: `staging/staging-review.csv`
```csv
issue_number,domain,shortname,file_path,status,reviewer,notes
123,shop,payment-flow,durion/domains/shop/.ui/staging/payment-flow-123.wf.md,pending,,"needs review"
```

## Configuration (config.yml)

```yaml
# Repository to fetch issues from
repo: louisburroughs/durion-moqui-frontend

# Label to domain mapping
label_domain_mapping:
  ui-shop: shop
  ui-crm: crm
  ui-accounting: accounting
  ui-inventory: inventory
  ui-orders: orders
  # fallback for unmapped labels
  default: general

# Summarizer parameters
summarizer:
  chunk_max_chars: 3500
  overlap_chars: 200
  map_sentences: 3
  reduce_sentences: 6
  max_total_chars: 2000

# OpenAI parameters
openai:
  model: gpt-4
  temperature: 0.0
  max_tokens: 900

# Retry parameters
retry:
  max_attempts: 5
  base_delay: 1.0
  max_delay: 60.0
  jitter: true

# Output parameters
output:
  base_dir: durion/domains
  staging: true
  staging_dir: staging
```

## Dependencies (requirements.txt)

```
openai>=1.0.0
PyYAML>=6.0
python-slugify>=8.0.0
sumy>=0.11.0
nltk>=3.8
requests>=2.31.0
tqdm>=4.66.0
```

## CLI Usage

### Basic Usage
```bash
cd workspace-agents
python issue_wireframe_extractor.py --repo louisburroughs/durion-moqui-frontend
```

### With Staging
```bash
python issue_wireframe_extractor.py --staging
```

### Batch Processing
```bash
python issue_wireframe_extractor.py --batch-size 10 --max-issues 50
```

### From Local Dump
```bash
python issue_wireframe_extractor.py --local-dump issues.json
```

### Full Options
```bash
python issue_wireframe_extractor.py \
  --repo louisburroughs/durion-moqui-frontend \
  --staging \
  --batch-size 10 \
  --max-issues 100 \
  --since 2025-01-01 \
  --config config.yml \
  --verbose
```

## Environment Variables

```bash
export GITHUB_TOKEN=ghp_xxx...
export OPENAI_API_KEY=sk-xxx...
```

## Implementation Steps

### Phase 1: Core Infrastructure
1. Create `utils.py` with `retry_with_backoff()`, `slugify_name()`, `sanitize_markdown()`
2. Create `config.yml` with defaults
3. Implement `github_client.py` with issue/comment fetching
4. Write tests for retry logic and utilities

### Phase 2: Summarization
1. Implement `summarizer.py` with TextRank + map-reduce
2. Add `preprocess_texts()`, `chunk_texts()`, `summarize_chunk()`
3. Implement `map_reduce_summarize()` orchestrator
4. Write tests for short/long thread summarization

### Phase 3: OpenAI Integration
1. Implement `prompt_builder.py` with strict template
2. Integrate `summarize_locally()` into prompt building
3. Implement `openai_client.py` with retry wrapper
4. Add output validation (check for ERROR, required sections)

### Phase 4: File Writing
1. Implement `writer.py` with atomic writes
2. Add metadata sidecar generation
3. Implement staging directory support
4. Add review CSV generation

### Phase 5: Main Orchestrator
1. Implement `issue_wireframe_extractor.py` CLI
2. Add argument parsing, config loading
3. Implement sequential processing loop
4. Add progress tracking and error handling

### Phase 6: Testing & Validation
1. Unit tests for all modules
2. Integration test with sample issues
3. Validate generated wireframes manually
4. Tune hyperparameters based on results

## Validation Checklist

### Output Validation
- [ ] Wireframe contains all required sections (Title, Purpose, Components, Layout, Interaction Flow, Notes)
- [ ] No extra text outside wireframe structure
- [ ] File written to correct path: `durion/domains/{domain}/.ui/{shortname}-{issue#}.wf.md`
- [ ] Metadata sidecar created with correct schema
- [ ] Original sentence ordering preserved in summaries

### Quality Checks
- [ ] Summaries are coherent and capture key decisions
- [ ] Wireframes reflect issue content accurately
- [ ] No PII leaked (emails, phones, SSNs) — manual spot check
- [ ] Token usage reasonable (< 1000 per wireframe)
- [ ] Retry logic handles rate limits gracefully

### Operational Checks
- [ ] Staging workflow functional (files in staging/, CSV generated)
- [ ] Atomic writes work (no partial files on crash)
- [ ] Failures logged to `staging/failures.csv`
- [ ] Environment variables required and validated
- [ ] Config file loaded correctly

## Notes

- **No concurrency**: Process issues sequentially to simplify retry logic and avoid rate limit complications
- **No PII management in MVP**: Manual review required; add regex-based PII detection in future iteration
- **No caching in MVP**: Add prompt hash caching and idempotence checks in Phase 2
- **Deterministic**: All operations (tokenization, chunking, slugification) must be deterministic for reproducibility

## Future Enhancements

1. **Structured Output**: Use OpenAI function calling to enforce wireframe schema
2. **One-shot Examples**: Include example wireframe in prompt for better model alignment
3. **Validation & Retry**: Validate output structure, retry with stricter prompt on failure
4. **PII Detection**: Pre-scan and redact emails/phones before sending to API
5. **Caching**: Hash-based cache to skip duplicate API calls
6. **Concurrency**: Add configurable parallel processing with rate limit pooling
7. **GitHub Action**: Automate wireframe generation on new issues via CI
8. **Sourcemaps**: Track which comments contributed to which wireframe sections
