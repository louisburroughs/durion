#!/usr/bin/env bash
# Convenience wrapper for publish_stories.py

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STORY_ROOT="${SCRIPT_DIR}/story-work/frontend"
OWNER="${OWNER:-louisburroughs}"
REPO="${REPO:-durion-moqui-frontend}"

# Default to script's local story-work directory
python3 "${SCRIPT_DIR}/publish_stories.py" \
  --story-root "$STORY_ROOT" \
  --owner "$OWNER" \
  --repo "$REPO" \
  "$@"
