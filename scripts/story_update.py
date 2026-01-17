#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
story_update.py

Usage examples (exact):

python story_update.py --root ./stories --pattern "r\"Original Story\\s+Story:\\s+#\\d{1,3}\\s+-\\s+.+?:\\s+.+\"" --dry-run

python story_update.py --root ./stories --pattern "r\"Original Story\\s+Story:\\s+#\\d{1,3}\\s+-\\s+.+?:\\s+.+\"" --concurrency 4

# Process a single story folder (numeric IDs map to frontend/<id>):
python story_update.py --root ./stories --story 123 --concurrency 1

# Process a list:
python story_update.py --root ./stories --stories 101,102,frontend/103 --concurrency 4

# Process a numeric range (inclusive):
python story_update.py --root ./stories --range 200-250 --concurrency 4

# Reprocess (overwrite existing after.md):
python story_update.py --root ./stories --story 123 --reprocess

Description:
- Crawl `<root>/frontend/*/` for folders containing `before.md` but missing `after.md`.
- For each, extract a pattern from `before.md`, search `<root>/backend/` MD files for related matches.
- Build a Story Authoring Agent prompt (including agent file, frontend rewrite prompt, domain agents, business rules).
- Invoke an LLM provider via environment variables.
- Validate and write `after.md` atomically when valid, maintain a resumable plan at `<root>/.story_update_plan.json`.

Environment & safety:
- Uses `.lock` in `<root>` to avoid concurrent runs.
- Requires an LLM provider via `OPENAI_API_KEY` or `GEMINI_API_KEY`.
- `GITHUB_TOKEN` is not required for local updates.

Plan file format (JSON):
{
  "version": "1.0",
  "created_at": "...",
  "updated_at": "...",
  "agent_file": null,
  "pending": ["frontend/123"],
  "processing": [],
  "completed": {"frontend/123": {"file": "frontend/123/after.md","processed_at":"..."}},
  "failed": {}
}

"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import tempfile
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path
from threading import Lock
from typing import Any, Dict, List, Optional, Tuple

VERSION = "1.0"

# Always include safe defaults assumptions in prompts to keep behavior consistent.
SAFE_DEFAULTS_REL_PATH = Path("agents") / "assumptions" / "safe-defaults.md"

# Default pattern matches the example used in the prompt.
#
# IMPORTANT: this is the *regex* itself (not the outer `r"..."` wrapper).
# The CLI also accepts patterns passed in that wrapped form; see `extract_pattern()`.
DEFAULT_PATTERN = r"Original Story\s+Story:\s+#\d{1,3}\s+-\s+.+?:\s+.+"


def repo_root() -> Path:
    # This script lives at <repo>/scripts/story_update.py
    return Path(__file__).resolve().parents[1]


def iso_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def atomic_write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(dir=str(path.parent), prefix=".tmp-", text=True)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            f.write(content)
        os.replace(tmp, path)
    finally:
        if os.path.exists(tmp):
            try:
                os.remove(tmp)
            except Exception:
                pass


def load_json(path: Path) -> Dict[str, Any]:
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def write_json_atomic(path: Path, data: Dict[str, Any]) -> None:
    atomic_write(path, json.dumps(data, indent=2, ensure_ascii=False))


def ensure_lock(root: Path, force: bool = False) -> None:
    lock = root / ".lock"
    if lock.exists():
        mtime = lock.stat().st_mtime
        age = time.time() - mtime
        if age < 3600 and not force:
            raise SystemExit(f"Lock exists at {lock} (age {int(age)}s). Use --force to override")
    root.mkdir(parents=True, exist_ok=True)
    lock.write_text(f"pid:{os.getpid()}\n{iso_now()}\n")


def release_lock(root: Path) -> None:
    lock = root / ".lock"
    try:
        lock.unlink()
    except Exception:
        pass


def scan_frontend_candidates(root: Path, include_existing_after: bool = False) -> List[Path]:
    frontend_root = root / "frontend"
    if not frontend_root.exists():
        return []
    dirs = []
    for child in sorted(frontend_root.iterdir()):
        if not child.is_dir():
            continue
        before = child / "before.md"
        after = child / "after.md"
        if before.exists() and (include_existing_after or not after.exists()):
            dirs.append(child)
    return dirs


def scan_frontend_missing_after(root: Path) -> List[Path]:
    return scan_frontend_candidates(root, include_existing_after=False)


def _normalize_story_selector(root: Path, raw: str) -> str:
    """Normalize a user selector to a plan item key like 'frontend/<dir>'.

    Accepted forms:
    - '123' -> 'frontend/123'
    - 'frontend/123' -> 'frontend/123'
    - './frontend/123' -> 'frontend/123'
    - absolute path under <root> -> relative posix path
    """
    s = (raw or "").strip().strip("\"").strip("'")
    if not s:
        raise ValueError("Empty story selector")

    # Pure numeric ID => assume frontend/<id>
    if re.fullmatch(r"\d+", s):
        return f"frontend/{s}"

    # Strip leading ./
    if s.startswith("./"):
        s = s[2:]

    # Absolute path => make relative to root if possible
    p = Path(s)
    if p.is_absolute():
        try:
            rel = p.resolve().relative_to(root.resolve())
        except Exception as e:
            raise ValueError(f"Story path is not under --root: {s}") from e
        return rel.as_posix()

    # Already relative path
    return Path(s).as_posix()


def _parse_story_range(raw: str) -> Tuple[int, int]:
    # Accept "10-20" or "10..20" (inclusive)
    m = re.match(r"^\s*(\d+)\s*(?:-+|\.\.)\s*(\d+)\s*$", raw or "")
    if not m:
        raise ValueError(f"Invalid range: {raw!r}. Expected like '10-20' or '10..20'")
    a = int(m.group(1))
    b = int(m.group(2))
    if a <= 0 or b <= 0:
        raise ValueError(f"Invalid range: {raw!r}. Values must be positive")
    if a > b:
        a, b = b, a
    return a, b


def _expand_story_selectors(root: Path, story_args: List[str], stories_csv: Optional[str], range_arg: Optional[str]) -> Optional[List[str]]:
    """Return a list of normalized plan item keys, or None if no selectors provided."""
    selectors: List[str] = []

    for s in story_args or []:
        selectors.append(_normalize_story_selector(root, s))

    if stories_csv:
        # Split on comma, tolerate whitespace.
        for part in re.split(r"[\s,]+", stories_csv.strip()):
            if part:
                selectors.append(_normalize_story_selector(root, part))

    if range_arg:
        start, end = _parse_story_range(range_arg)
        for n in range(start, end + 1):
            selectors.append(f"frontend/{n}")

    if not selectors:
        return None

    # De-dup preserve order
    seen: set[str] = set()
    ordered: List[str] = []
    for s in selectors:
        if s not in seen:
            seen.add(s)
            ordered.append(s)
    return ordered


def extract_pattern(before_text: str, pattern: str) -> Optional[re.Match]:
    # Accept either the raw regex, or a Python-literal-ish wrapper like:
    #   r"..."  or  r'...'
    if len(pattern) >= 3 and pattern[0] == "r" and pattern[1] in ('"', "'") and pattern[-1] == pattern[1]:
        pattern = pattern[2:-1]
    try:
        rx = re.compile(pattern, re.MULTILINE)
    except re.error as e:
        raise ValueError(f"Invalid regex pattern: {e}")

    m = rx.search(before_text)
    if m:
        return m

    # Common case: extracted issues often render "Story" as markdown bold: **Story**:
    # If the user-provided pattern expects "Story:", try a tolerant variant.
    if "Story:" in pattern:
        tolerant = pattern.replace("Story:", r"\*\*?Story\*\*?:")
        try:
            rx2 = re.compile(tolerant, re.MULTILINE)
            m2 = rx2.search(before_text)
            if m2:
                return m2
        except re.error:
            # If the tolerant pattern becomes invalid, just fall back to no match.
            pass

    # Fallback: match story titles in the form:
    #   [STORY] <literal text> : <literal text>
    # Often appears as:
    #   **Original Story**: [STORY] GL: Provide Trial Balance and Drilldown to Source
    # Guard: do NOT match lines where [FRONTEND] appears before [STORY] (e.g., title lines).
    fallback = (
        r"^(?!.*\[FRONTEND\].*\[STORY\])"
        r"\s*(?:\*\*?Original Story\*\*?:\s*)?"
        r"\[STORY\]\s*[^:\n]+:\s*[^\n]+$"
    )
    try:
        rx3 = re.compile(fallback, re.MULTILINE)
        m3 = rx3.search(before_text)
        if m3:
            return m3
    except re.error:
        pass

    return None


def _derive_search_keys(matched_line: str) -> List[str]:
    # Normalize common prefixes and return a prioritized list of search keys.
    line = matched_line.strip()

    # Strip markdown bold and labels like "Story:" / "Original Story:".
    line = re.sub(r"^\*{0,2}(?:Original\s+Story|Story)\*{0,2}:\s*", "", line, flags=re.IGNORECASE)

    keys: List[str] = []
    if line:
        keys.append(line)

    # If it starts with [STORY], also search by the bare title after it.
    if line.startswith("[STORY]"):
        bare = line[len("[STORY]"):].strip()
        if bare:
            keys.append(bare)

    # If it contains a story id token like "#20", include that too.
    id_m = re.search(r"#\d{1,6}", line)
    if id_m:
        keys.append(id_m.group(0))

    # De-dup while preserving order.
    seen: set[str] = set()
    ordered: List[str] = []
    for k in keys:
        if k not in seen:
            seen.add(k)
            ordered.append(k)
    return ordered


def search_backend(root: Path, value: str, max_matches: int = 3) -> List[Dict[str, Any]]:
    backend_root = root / "backend"
    matches: List[Dict[str, Any]] = []
    if not backend_root.exists():
        return matches
    for p in backend_root.rglob("*.md"):
        try:
            txt = p.read_text(encoding="utf-8")
        except Exception:
            continue
        for m in re.finditer(re.escape(value), txt, re.IGNORECASE):
            # capture a small context
            lines = txt.splitlines()
            # find line number
            pos = m.start()
            upto = txt[:pos]
            line_idx = upto.count("\n")
            start = max(0, line_idx - 2)
            end = min(len(lines), line_idx + 3)
            excerpt = "\n".join(lines[start:end]).strip()
            # attempt to parse labels block (simple heuristics)
            labels = []
            lab_m = re.search(r"^Labels:\s*(.+)$", txt, re.MULTILINE | re.IGNORECASE)
            if lab_m:
                lab_line = lab_m.group(1).strip()
                labels = [s.strip() for s in lab_line.split(",") if s.strip()]
            matches.append({"path": str(p.relative_to(root)), "excerpt": excerpt, "labels": labels})
            if len(matches) >= max_matches:
                return matches
    return matches


def load_text_file(path: Path) -> str:
    return path.read_text(encoding="utf-8") if path.exists() else ""


def _extract_domains_from_backend_matches(backend_matches: List[Dict[str, Any]]) -> List[str]:
    domains: set[str] = set()
    for bm in backend_matches:
        for lbl in bm.get("labels", []):
            if isinstance(lbl, str) and lbl.startswith("domain:"):
                name = lbl.split("domain:", 1)[1].strip()
                if name:
                    domains.add(name)
    return sorted(domains)


def _resolve_domain_agent_file(root_repo: Path, domain: str) -> Path:
    domain_dir = root_repo / ".github" / "agents" / "domains"
    candidates = [
        domain_dir / f"{domain}-domain.agent.md",
        domain_dir / f"{domain}-domain.agent.txt",
        domain_dir / f"{domain}.md",
        domain_dir / f"{domain}.txt",
    ]
    for c in candidates:
        if c.exists():
            return c
    raise SystemExit(
        f"Missing domain agent for domain:{domain}. Expected one of: "
        + ", ".join(str(p.relative_to(root_repo)) for p in candidates)
    )


def _business_rules_dir_from_agent_text(agent_text: str) -> Optional[Path]:
    # Accept either:
    #   **Business Rules:** durion/domains/<domain>/.business-rules/
    # or the same path wrapped in backticks.
    m = re.search(r"^\*\*Business Rules:\*\*\s*(.+?)\s*$", agent_text, re.MULTILINE)
    if not m:
        return None
    raw = m.group(1).strip().strip("`")
    # Normalize leading repo name if included
    if raw.startswith("durion/"):
        raw = raw[len("durion/"):]
    return Path(raw)


def _load_business_rules_files(root_repo: Path, agent_text: str) -> List[Path]:
    rel = _business_rules_dir_from_agent_text(agent_text)
    if not rel:
        return []
    br_dir = root_repo / rel
    if not br_dir.exists() or not br_dir.is_dir():
        raise SystemExit(f"Business rules dir not found: {br_dir}")
    files = [p for p in sorted(br_dir.iterdir()) if p.is_file()]
    if not files:
        raise SystemExit(f"Business rules dir is empty: {br_dir}")
    return files


def build_agent_prompt(
    agent_file: Optional[Path],
    root_repo: Path,
    frontend_prompt_file: Path,
    before_text: str,
    synopsis: str,
    backend_matches: List[Dict[str, Any]],
    matched_value: str,
    matched_location: str,
) -> str:
    parts: List[str] = []
    # 1. Story Authoring Agent
    if agent_file:
        af = load_text_file(agent_file)
        parts.append(af)
    # 2. Story Writing Prompt
    if not frontend_prompt_file.exists():
        raise SystemExit(f"Missing frontend rewrite prompt: {frontend_prompt_file}")
    parts.append(load_text_file(frontend_prompt_file))
    # 3 & 4. Domain agents and business rules
    domains = _extract_domains_from_backend_matches(backend_matches)
    for d in domains:
        agent_path = _resolve_domain_agent_file(root_repo, d)
        agent_text = load_text_file(agent_path)
        parts.append(agent_text)

        for br_file in _load_business_rules_files(root_repo, agent_text):
            parts.append(f"--- Business Rules: {br_file.relative_to(root_repo)} ---\n")
            parts.append(load_text_file(br_file))
    # 5. Story Synopsis
    parts.append("""Story Synopsis:
""")
    parts.append(synopsis)
    # 6. Backend Labels & Context
    if backend_matches:
        parts.append("Backend matches:\n")
        for bm in backend_matches:
            labels = ", ".join(bm.get("labels", []))
            parts.append(
                f"- Path: {bm['path']}\n"
                f"  Excerpt:\n{bm['excerpt']}\n"
                f"  Labels: {labels}\n"
            )
    else:
        parts.append("No backend matches found.\n")
    # 7. Matched pattern details
    parts.append(f"Matched value: {matched_value}\nLocation: {matched_location}\n")
    # 8. Final instruction block
    parts.append(
        "Review and update the issue: produce an implementation-ready rewrite (after.md) following the Story Authoring Agent rules and any "
        "appended domain-specific guidance and business rules. Preserve the original before.md content in the Original Story (Unmodified – "
        "For Traceability) section at the end. Ensure output contains required section headers."
    )

    # Final: Assumptions / safe defaults (mandatory)
    safe_defaults_path = root_repo / SAFE_DEFAULTS_REL_PATH
    if not safe_defaults_path.exists():
        raise SystemExit(f"Missing required prompt include: {safe_defaults_path}")
    parts.append(f"--- Assumptions / Safe Defaults: {SAFE_DEFAULTS_REL_PATH.as_posix()} ---\n")
    parts.append(load_text_file(safe_defaults_path))

    return "\n\n".join(parts)


def validate_agent_output(out_text: str) -> Tuple[bool, List[str]]:
    missing = []
    required = [
        "## 🏷️ Labels (Proposed)",
        "**Rewrite Variant:",
        "## Original Story (Unmodified – For Traceability)",
    ]
    for r in required:
        if r not in out_text:
            missing.append(r)
    return (len(missing) == 0, missing)


class RateLimiter:
    def __init__(self, min_interval_s: float) -> None:
        self._min_interval_s = float(min_interval_s)
        self._lock = Lock()
        self._next_allowed = 0.0

    def wait(self) -> None:
        if self._min_interval_s <= 0:
            return
        with self._lock:
            now = time.time()
            if now < self._next_allowed:
                time.sleep(self._next_allowed - now)
                now = time.time()
            self._next_allowed = now + self._min_interval_s


def _http_post_json(url: str, headers: Dict[str, str], payload: Dict[str, Any], timeout_s: int = 60) -> Dict[str, Any]:
    body = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=body, method="POST")
    for k, v in headers.items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=timeout_s) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as e:
        raw = ""
        try:
            raw = e.read().decode("utf-8", errors="replace")
        except Exception:
            pass
        raise RuntimeError(f"HTTP {e.code} from {url}: {raw[:2000]}") from e
    except Exception as e:
        raise RuntimeError(f"HTTP request failed to {url}: {e}") from e

    try:
        return json.loads(raw) if raw else {}
    except Exception as e:
        raise RuntimeError(f"Invalid JSON response from {url}: {raw[:2000]}") from e


def _ensure_provider_config(dry_run: bool) -> None:
    if dry_run:
        return
    if os.environ.get("OPENAI_API_KEY") or os.environ.get("GEMINI_API_KEY"):
        return
    raise SystemExit(
        "No LLM provider configured. Set OPENAI_API_KEY (optionally OPENAI_MODEL/OPENAI_API_BASE/OPENAI_ORG) "
        "or set GEMINI_API_KEY (optionally GEMINI_MODEL)."
    )


def _should_retry_http_error(msg: str) -> bool:
    # Our _http_post_json error strings start with "HTTP <code>".
    return msg.startswith("HTTP 429 ") or msg.startswith("HTTP 500 ") or msg.startswith("HTTP 502 ") or msg.startswith("HTTP 503 ")


def _sleep_backoff(attempt: int) -> None:
    # Exponential backoff with small deterministic jitter.
    delay = min(30, 2 ** attempt)
    time.sleep(delay + (attempt % 3) * 0.25)


def _preflight_llm() -> None:
    # Single low-cost call to catch misconfiguration early (model/endpoint/params).
    # We do not validate the content; only that the provider responds successfully.
    try:
        _ = call_agent("ping")
    except Exception as e:
        raise SystemExit(f"LLM preflight failed: {e}")


def call_agent(prompt: str) -> str:
    """Invoke an LLM provider (environment-driven).

    Supported providers:
    - OpenAI: set OPENAI_API_KEY (optional OPENAI_MODEL, optional OPENAI_API_BASE, optional OPENAI_ORG)
      - Gemini: set GEMINI_API_KEY (optional GEMINI_MODEL, default: gemini-1.5-flash)

        Notes:
            - Uses HTTPS endpoints.
            - Does not log secrets.
            - Uses Python stdlib (no external HTTP dependency).
            - Set LLM_TIMEOUT_S to allow long-running generations (default: 900 seconds).
    """

    def _get_llm_timeout_s() -> int:
        raw = (os.environ.get("LLM_TIMEOUT_S") or "").strip()
        if not raw:
            return 900
        try:
            val = int(raw)
        except Exception:
            raise SystemExit(f"Invalid LLM_TIMEOUT_S={raw!r}; expected integer seconds")
        if val < 30:
            return 30
        if val > 3600:
            return 3600
        return val

    llm_timeout_s = _get_llm_timeout_s()

    openai_key = os.environ.get("OPENAI_API_KEY")
    if openai_key:
        model = os.environ.get("OPENAI_MODEL", "gpt-5.2")
        api_base = os.environ.get("OPENAI_API_BASE", "https://api.openai.com/v1").rstrip("/")
        url = f"{api_base}/chat/completions"
        headers = {
            "Authorization": f"Bearer {openai_key}",
            "Content-Type": "application/json",
        }
        openai_org = os.environ.get("OPENAI_ORG")
        if openai_org:
            headers["OpenAI-Organization"] = openai_org
        max_completion_tokens = int(os.environ.get("OPENAI_MAX_COMPLETION_TOKENS", "2000"))
        payload = {
            "model": model,
            "messages": [{"role": "user", "content": prompt}],
            "max_completion_tokens": max_completion_tokens,
        }
        last_err: Optional[Exception] = None
        for attempt in range(0, 4):
            try:
                data = _http_post_json(url, headers=headers, payload=payload, timeout_s=llm_timeout_s)
                break
            except RuntimeError as e:
                last_err = e
                if _should_retry_http_error(str(e)) and attempt < 3:
                    _sleep_backoff(attempt)
                    continue
                raise
        else:
            raise RuntimeError(str(last_err) if last_err else "OpenAI request failed")

        choices = data.get("choices") or []
        if choices:
            msg = (choices[0].get("message") or {})
            return (msg.get("content") or "").strip()
        return (data.get("text") or "").strip()

    gemini_key = os.environ.get("GEMINI_API_KEY")
    if gemini_key:
        model = os.environ.get("GEMINI_MODEL", "gemini-1.5-flash")
        url = (
            "https://generativelanguage.googleapis.com/v1beta/models/"
            f"{model}:generateContent?key={gemini_key}"
        )
        headers = {"Content-Type": "application/json"}
        payload = {
            "contents": [{"role": "user", "parts": [{"text": prompt}]}],
            "generationConfig": {"maxOutputTokens": 2000},
        }
        last_err2: Optional[Exception] = None
        for attempt in range(0, 4):
            try:
                data = _http_post_json(url, headers=headers, payload=payload, timeout_s=llm_timeout_s)
                break
            except RuntimeError as e:
                last_err2 = e
                if _should_retry_http_error(str(e)) and attempt < 3:
                    _sleep_backoff(attempt)
                    continue
                raise
        else:
            raise RuntimeError(str(last_err2) if last_err2 else "Gemini request failed")

        candidates = data.get("candidates") or []
        if not candidates:
            return ""
        content = candidates[0].get("content") or {}
        parts = content.get("parts") or []
        texts = [p.get("text", "") for p in parts if isinstance(p, dict)]
        return "".join(texts).strip()

    raise RuntimeError("No LLM provider configured: set OPENAI_API_KEY or GEMINI_API_KEY")


def ensure_plan(
    root: Path,
    agent_file: Optional[str],
    force_refresh: bool = False,
    reprocess: bool = False,
) -> Dict[str, Any]:
    plan_path = root / ".story_update_plan.json"
    if plan_path.exists() and not force_refresh:
        try:
            return load_json(plan_path)
        except Exception:
            raise SystemExit("Plan file corrupt; remove or use --force-refresh-plan")
    # create plan
    pending_dirs = [str(p.relative_to(root)) for p in scan_frontend_candidates(root, include_existing_after=reprocess)]
    plan = {
        "version": VERSION,
        "created_at": iso_now(),
        "updated_at": iso_now(),
        "agent_file": agent_file,
        "pending": pending_dirs,
        "processing": [],
        "completed": {},
        "failed": {},
        "skipped_no_pattern": {},
    }
    write_json_atomic(plan_path, plan)
    return plan


def process_item(
    root: Path,
    item_rel: str,
    pattern: str,
    agent_file: Optional[Path],
    frontend_prompt_file: Path,
    dry_run: bool,
    rate_limiter: Optional[RateLimiter] = None,
) -> Tuple[str, Dict[str, Any]]:
    """Process a single frontend directory. Returns key and result dict for plan updates."""
    item_path = root / item_rel
    before_path = item_path / "before.md"
    if not before_path.exists():
        return item_rel, {
            "status": "failed",
            "error": "missing before.md",
            "processed_at": iso_now(),
        }
    before_text = before_path.read_text(encoding="utf-8")
    m = extract_pattern(before_text, pattern)
    if not m:
        return item_rel, {"status": "skipped_no_pattern", "processed_at": iso_now()}

    matched_block = m.group(0)
    matched_line = matched_block.strip().splitlines()[-1].strip()
    # Prefer searching by a stable key derived from the matched line.
    search_keys = _derive_search_keys(matched_line)
    search_key = search_keys[0] if search_keys else matched_line

    # search backend
    backend_matches: List[Dict[str, Any]] = []
    for key in search_keys:
        backend_matches = search_backend(root, key, max_matches=3)
        if backend_matches:
            break
    # synopsis: title + first paragraph
    title = before_text.splitlines()[0] if before_text.splitlines() else ""
    paragraphs = [p for p in before_text.splitlines() if p.strip()]
    first_para = paragraphs[1] if len(paragraphs) > 1 else paragraphs[0] if paragraphs else ""
    synopsis = f"{title} — {first_para[:200]}"
    # location for traceability
    line_no = before_text[:m.start()].count("\n") + 1
    prompt = build_agent_prompt(
        agent_file,
        repo_root(),
        frontend_prompt_file,
        before_text,
        synopsis,
        backend_matches,
        search_key,
        f"{before_path}:L{line_no} (match@{m.start()})",
    )
    if dry_run:
        # in dry-run return the prompt and do not call agent
        return item_rel, {
            "status": "dry-run",
            "preview_prompt": prompt[:4000],
            "prompt": prompt,
            "processed_at": iso_now(),
        }
    try:
        if rate_limiter is not None:
            rate_limiter.wait()
        out = call_agent(prompt)
    except Exception as e:
        return item_rel, {"status": "failed", "error": str(e), "processed_at": iso_now(), "agent_output": ""}
    ok, missing = validate_agent_output(out)
    if not ok:
        return item_rel, {
            "status": "failed",
            "error": f"agent missing sections: {missing}",
            "processed_at": iso_now(),
            "agent_output": out,
        }
    # write after.md
    after_path = item_path / "after.md"
    try:
        atomic_write(after_path, out)
    except Exception as e:
        return item_rel, {"status": "failed", "error": f"write error: {e}", "processed_at": iso_now()}
    return item_rel, {
        "status": "completed",
        "file": str(after_path.relative_to(root)),
        "processed_at": iso_now(),
    }


def main(argv: Optional[List[str]] = None) -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--root", required=True)
    p.add_argument(
        "--pattern",
        default=DEFAULT_PATTERN,
        help='Regex pattern string, e.g. r"Original Story ..."',
    )
    p.add_argument("--agent-file", default=None)
    p.add_argument("--concurrency", type=int, default=1)
    p.add_argument("--dry-run", action="store_true")
    p.add_argument(
        "--dry-run-prompt-log",
        default=None,
        help=(
            "When --dry-run is set, append each generated prompt to this logfile. "
            "Defaults to <root>/.story_update_dry_run_prompts.log"
        ),
    )
    p.add_argument("--force-refresh-plan", action="store_true")
    p.add_argument(
        "--reprocess",
        action="store_true",
        help="Re-run even if after.md exists (overwrites after.md when output validates).",
    )
    p.add_argument(
        "--story",
        action="append",
        default=[],
        help="Process a specific story folder (e.g. '123' or 'frontend/123'). May be repeated.",
    )
    p.add_argument(
        "--stories",
        default=None,
        help="Comma/space-separated list of stories (e.g. '101,102,frontend/103').",
    )
    p.add_argument(
        "--range",
        dest="story_range",
        default=None,
        help="Numeric inclusive range of story IDs to process (e.g. '200-250' or '200..250').",
    )
    p.add_argument(
        "--pause-seconds",
        type=float,
        default=15.0,
        help="Minimum pause (seconds) between LLM calls across threads (0 disables).",
    )
    p.add_argument(
        "--max-consecutive-429",
        type=int,
        default=3,
        help="Stop scheduling new items after this many consecutive HTTP 429 failures.",
    )
    p.add_argument("--verbose", action="store_true")
    args = p.parse_args(argv)

    def _log(msg: str) -> None:
        print(msg, flush=True)

    def _append_prompt_log(log_path: Path, key: str, prompt: str) -> None:
        log_path.parent.mkdir(parents=True, exist_ok=True)
        with log_path.open("a", encoding="utf-8") as f:
            f.write("\n" + ("=" * 100) + "\n")
            f.write(f"item: {key}\n")
            f.write(f"processed_at: {iso_now()}\n")
            f.write("-" * 100 + "\n")
            f.write(prompt)
            if not prompt.endswith("\n"):
                f.write("\n")

    try:
        _ensure_provider_config(args.dry_run)
    except SystemExit as e:
        print(str(e), file=sys.stderr)
        return 4

    if not args.dry_run:
        try:
            _preflight_llm()
        except SystemExit as e:
            print(str(e), file=sys.stderr)
            return 5

    root = Path(args.root).resolve()

    dry_run_log_path: Optional[Path] = None
    if args.dry_run:
        dry_run_log_path = Path(args.dry_run_prompt_log) if args.dry_run_prompt_log else (root / ".story_update_dry_run_prompts.log")

    try:
        selected_items = _expand_story_selectors(root, args.story, args.stories, args.story_range)
    except ValueError as e:
        print(str(e), file=sys.stderr)
        return 1
    frontend_prompt_file = (
        repo_root()
        / ".github"
        / "prompts"
        / "story-frontend-rewrite.prompt.md"
    )

    try:
        ensure_lock(root, force=args.force_refresh_plan)
    except SystemExit as e:
        print(str(e), file=sys.stderr)
        return 2

    try:
        plan = ensure_plan(root, args.agent_file, force_refresh=args.force_refresh_plan, reprocess=args.reprocess)
    except SystemExit as e:
        release_lock(root)
        print(str(e), file=sys.stderr)
        return 3

    plan_path = root / ".story_update_plan.json"

    # If reprocess is requested without selectors, refresh pending from disk (keep history maps).
    # This is intentionally non-destructive: completed/failed/skipped remain as history.
    if args.reprocess and not selected_items:
        plan["pending"] = [str(p.relative_to(root)) for p in scan_frontend_candidates(root, include_existing_after=True)]
        plan["processing"] = []
        plan["updated_at"] = iso_now()
        write_json_atomic(plan_path, plan)

    # If the user selected specific items, ensure they are present in plan["pending"] if eligible.
    # By default, we only auto-add items missing after.md; with --reprocess we also allow existing after.md.
    if selected_items:
        pending_list = plan.get("pending", [])
        processing_list = plan.get("processing", [])
        completed_map = plan.get("completed", {})
        failed_map = plan.get("failed", {})
        skipped_map = plan.get("skipped_no_pattern", {})
        changed = False
        for sel in selected_items:
            # Skip if already tracked in any state.
            if sel in pending_list or sel in processing_list or sel in completed_map or sel in failed_map or sel in skipped_map:
                continue
            # Only add if it is a frontend/<dir> path.
            if not sel.startswith("frontend/"):
                continue
            item_path = root / sel
            before_path = item_path / "before.md"
            after_path = item_path / "after.md"
            if before_path.exists() and (args.reprocess or not after_path.exists()):
                pending_list.append(sel)
                changed = True
        if changed:
            # Keep deterministic-ish ordering for humans.
            try:
                plan["pending"] = sorted(set(pending_list), key=lambda x: (x.split("/", 1)[0], x.split("/", 1)[1]))
            except Exception:
                plan["pending"] = pending_list
            plan["updated_at"] = iso_now()
            write_json_atomic(plan_path, plan)

    # If dry-run, print up to first 3 prompts for preview
    pending = list(plan.get("pending", []))
    if selected_items:
        selected_set = set(selected_items)
        pending = [p for p in pending if p in selected_set]
    summary = {"processed": 0, "updated": 0, "skipped_no_pattern": 0, "failed": 0}
    dry_run_previews_printed = 0

    if args.dry_run and dry_run_log_path:
        _log(f"[dry-run] prompt log: {dry_run_log_path}")

    if selected_items and args.verbose:
        # Report selectors that are not currently schedulable (not pending).
        schedulable_set = set(pending)
        not_schedulable = [s for s in selected_items if s not in schedulable_set]
        if not_schedulable:
            print(f"[select] {len(not_schedulable)} selected item(s) not pending; they may be completed/failed/skipped or already have after.md")
            for s in not_schedulable[:50]:
                print(f"- {s}")
        print(f"[select] scheduling {len(pending)} item(s)")

    rate_limiter = RateLimiter(args.pause_seconds) if (not args.dry_run and args.pause_seconds > 0) else None
    consecutive_429 = 0
    stop_scheduling = False

    try:
        with ThreadPoolExecutor(max_workers=max(1, args.concurrency)) as ex:
            futures: Dict[Any, str] = {}
            pending_iter = iter(pending)

            def submit_next() -> None:
                if stop_scheduling:
                    return
                try:
                    nxt = next(pending_iter)
                except StopIteration:
                    return
                plan["processing"].append(nxt)
                write_json_atomic(plan_path, plan)
                if not args.dry_run:
                    _log(f"[start] {nxt}")
                fut2 = ex.submit(
                    process_item,
                    root,
                    nxt,
                    args.pattern,
                    Path(args.agent_file) if args.agent_file else None,
                    frontend_prompt_file,
                    args.dry_run,
                    rate_limiter,
                )
                futures[fut2] = nxt

            # Prime the pool
            for _ in range(max(1, args.concurrency)):
                submit_next()

            while futures:
                # Wait for any in-flight task to complete, then process exactly one.
                fut = next(as_completed(list(futures.keys())))
                item = futures.pop(fut)
                try:
                    key, res = fut.result()
                except Exception as e:
                    key = item
                    res = {"status": "failed", "error": str(e), "processed_at": iso_now()}

                # update plan
                if res.get("status") == "completed":
                    plan["completed"][key] = {"file": res.get("file"), "processed_at": res.get("processed_at")}
                    summary["updated"] += 1
                    consecutive_429 = 0
                    _log(f"[done] {key} -> {res.get('file')}")
                    if args.verbose:
                        print(f"[completed] {key} -> {res.get('file')}")
                elif res.get("status") == "dry-run":
                    if dry_run_log_path and res.get("prompt"):
                        _append_prompt_log(dry_run_log_path, key, res.get("prompt"))
                    # print preview for first 3
                    if dry_run_previews_printed < 3:
                        print(f"[dry-run] would write {key}/after.md")
                        print("--- prompt preview ---")
                        print(res.get("preview_prompt")[:2000])
                        print("--- end preview ---")
                        dry_run_previews_printed += 1
                    if args.verbose:
                        print(f"[dry-run] {key}")
                elif res.get("status") == "skipped_no_pattern":
                    plan["skipped_no_pattern"][key] = {"processed_at": res.get("processed_at")}
                    summary["skipped_no_pattern"] += 1
                    consecutive_429 = 0
                    _log(f"[skip] {key} (no pattern match)")
                    if args.verbose:
                        print(f"[skipped_no_pattern] {key}")
                else:
                    plan["failed"][key] = {
                        "error": res.get("error"),
                        "processed_at": res.get("processed_at"),
                        "agent_output": res.get("agent_output"),
                    }
                    summary["failed"] += 1
                    err = (res.get("error") or "").strip()
                    _log(f"[fail] {key}: {err[:240]}")
                    if args.verbose:
                        print(f"[failed] {key}: {err[:240]}")

                    err_msg = (res.get("error") or "").strip()
                    if err_msg.startswith("HTTP 429 "):
                        consecutive_429 += 1
                        if args.max_consecutive_429 > 0 and consecutive_429 >= args.max_consecutive_429:
                            stop_scheduling = True
                            if args.verbose:
                                print(
                                    f"[stop] hit {consecutive_429} consecutive HTTP 429 errors; "
                                    "stopping scheduling new items"
                                )
                    else:
                        consecutive_429 = 0

                # Remove from pending once we have a terminal result.
                try:
                    if key in plan.get("pending", []):
                        plan["pending"].remove(key)
                except Exception:
                    pass
                # remove from processing if present
                try:
                    if key in plan.get("processing", []):
                        plan["processing"].remove(key)
                except Exception:
                    pass
                # mark processed count
                summary["processed"] += 1
                plan["updated_at"] = iso_now()
                write_json_atomic(plan_path, plan)

                # Submit next only if we are still running.
                submit_next()

                # Break out early if stop condition triggered and nothing is in-flight.
                if stop_scheduling and not futures:
                    break

    except KeyboardInterrupt:
        # Return processing items back to pending and release lock for resumability.
        _log("[interrupt] received Ctrl+C; saving plan and releasing lock")
        try:
            pending_set = set(plan.get("pending", []))
            for it in plan.get("processing", []) or []:
                if it not in pending_set:
                    plan["pending"].insert(0, it)
            plan["processing"] = []
            plan["updated_at"] = iso_now()
            write_json_atomic(plan_path, plan)
        finally:
            release_lock(root)
        return 130

    release_lock(root)
    if args.verbose and summary.get("failed", 0):
        # Print a small aggregated error summary to make failures obvious.
        counts: Dict[str, int] = {}
        for v in plan.get("failed", {}).values():
            msg = (v.get("error") or "").strip()
            if msg:
                counts[msg] = counts.get(msg, 0) + 1
        top = sorted(counts.items(), key=lambda kv: kv[1], reverse=True)[:5]
        if top:
            print("[failed-summary]")
            for msg, n in top:
                print(f"- {n}x {msg[:240]}")
    print(json.dumps(summary))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
