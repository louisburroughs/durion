# Story Wireframe Generator

Generate wireframes from GitHub issues using OpenAI API with local TextRank summarization.

## Setup

1. Install dependencies:
```bash
pip install -r requirements.txt
```

2. Set environment variables:
```bash
export GITHUB_TOKEN=ghp_xxx...
export OPENAI_API_KEY=sk-xxx...
```

3. Configure settings in `config.yml` if needed.

## Usage

### Basic Usage
```bash
python issue_wireframe_extractor.py
```

This will:
- Fetch all issues from the configured repository
- Generate wireframes using OpenAI
- Write to staging directory for review

### With Options
```bash
# Limit number of issues
python issue_wireframe_extractor.py --max-issues 10

# Filter by date
python issue_wireframe_extractor.py --since 2025-01-01T00:00:00Z

# Use local JSON dump
python issue_wireframe_extractor.py --local-dump issues.json

# Specify repository
python issue_wireframe_extractor.py --repo owner/repo
```

### Full Options
```bash
python issue_wireframe_extractor.py \
  --repo louisburroughs/durion-moqui-frontend \
  --staging \
  --auto-approve \
  --max-issues 100 \
  --since 2025-01-01 \
  --config config.yml \
  --verbose
```

### Auto-Approve Mode
Use `--auto-approve` to automatically promote wireframes from staging to parent `.ui/` directory:
```bash
python issue_wireframe_extractor.py --auto-approve --max-issues 10
```

## Output

Wireframes are written to:
```
durion/domains/{domain}/.ui/staging/{shortname}-{issue#}.wf.md
```

Metadata sidecars:
```
durion/domains/{domain}/.ui/staging/{shortname}-{issue#}.wf.meta.json
```

Review CSV:
```
durion/domains/{domain}/.ui/staging/staging-review.csv
```

## Review Process

1. Check `staging-review.csv` for list of generated wireframes
2. Review each wireframe file
3. Move approved wireframes from `staging/` to parent `.ui/` directory
4. Update CSV with review status

## Configuration

Edit `config.yml` to customize:
- Label to domain mapping
- Summarizer hyperparameters
- OpenAI model and parameters
- Retry settings
- Output paths

## Architecture

- `github_client.py` — Fetch issues and comments from GitHub
- `summarizer.py` — TextRank map-reduce summarization
- `prompt_builder.py` — Build strict OpenAI prompts
- `openai_client.py` — Call OpenAI API with retries
- `writer.py` — Atomic file writing
- `utils.py` — Utilities and retry logic
- `issue_wireframe_extractor.py` — Main CLI orchestrator

## Troubleshooting

### Rate Limits
The script includes exponential backoff retry logic. If you hit rate limits:
- Wait and retry
- Reduce batch size
- Use `--max-issues` to limit processing

### Invalid Wireframes
If wireframes fail validation:
- Check OpenAI API status
- Review prompt template in `prompt_builder.py`
- Check failed wireframes in `failures.csv`

### Summary Quality
To tune summarization:
- Adjust `chunk_max_chars` in config
- Change `map_sentences` and `reduce_sentences`
- Review summarized content in metadata

## Dependencies

- openai>=1.0.0 — OpenAI API client
- PyYAML>=6.0 — Config parsing
- python-slugify>=8.0.0 — URL slug generation
- sumy>=0.11.0 — TextRank summarization
- nltk>=3.8 — Sentence tokenization
- requests>=2.31.0 — HTTP client
- tqdm>=4.66.0 — Progress bars
