#!/usr/bin/env bash
set -euo pipefail

ALLOWED_FILE="$HOME/IdeaProjects/durion/Durion-Processing.md"

if [[ "$1" != "$ALLOWED_FILE" ]]; then
  echo "Deletion not permitted"
  exit 1
fi

rm -- "$ALLOWED_FILE"