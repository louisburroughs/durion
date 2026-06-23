#!/usr/bin/env bash
# SessionStart hook: prepare AWS access for Claude Code (web/ephemeral sandboxes).
#
# Purpose:
# - Ensure `uv`/`uvx` is available so the AWS MCP servers in .mcp.json can launch.
# - Report whether AWS credentials are present, without leaking secret values.
#
# This hook is advisory only: it never fails the session (always exits 0) and
# never prints credential values. Configure AWS credentials as environment
# variables / secrets in your Claude Code web environment settings, NOT in the
# repository. See AGENTS.md and .claude/README-aws.md.

set -uo pipefail

log() { printf '[aws-setup] %s\n' "$1"; }

# 1) Ensure uvx is available (needed to run the awslabs.* MCP servers).
if ! command -v uvx >/dev/null 2>&1; then
  log "uvx not found; attempting to install uv (Astral)..."
  if command -v curl >/dev/null 2>&1; then
    curl -LsSf https://astral.sh/uv/install.sh | sh >/dev/null 2>&1 || true
    # uv installs to ~/.local/bin or ~/.cargo/bin depending on platform.
    for d in "$HOME/.local/bin" "$HOME/.cargo/bin"; do
      [ -d "$d" ] && case ":$PATH:" in *":$d:"*) ;; *) PATH="$d:$PATH" ;; esac
    done
    export PATH
  fi
fi

if command -v uvx >/dev/null 2>&1; then
  log "uvx available: $(command -v uvx)"
else
  log "WARNING: uvx still unavailable -- AWS MCP servers in .mcp.json will not start."
  log "         Install uv manually: curl -LsSf https://astral.sh/uv/install.sh | sh"
fi

# 2) Report AWS credential availability (presence only -- never the values).
cred_source=""
if [ -n "${AWS_BEARER_TOKEN_BEDROCK:-}" ]; then
  cred_source="AWS_BEARER_TOKEN_BEDROCK (Bedrock API key)"
elif [ -n "${AWS_ACCESS_KEY_ID:-}" ] && [ -n "${AWS_SECRET_ACCESS_KEY:-}" ]; then
  cred_source="AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY env vars"
elif [ -n "${AWS_PROFILE:-}" ]; then
  cred_source="AWS_PROFILE=${AWS_PROFILE}"
elif [ -f "$HOME/.aws/credentials" ]; then
  cred_source="~/.aws/credentials file"
fi

if [ -n "$cred_source" ]; then
  log "AWS credentials detected via: ${cred_source}"
  log "AWS_REGION=${AWS_REGION:-${AWS_DEFAULT_REGION:-<unset, MCP defaults to us-east-1>}}"
  if [ "${CLAUDE_CODE_USE_BEDROCK:-0}" = "1" ]; then
    log "Model routing: Amazon Bedrock (CLAUDE_CODE_USE_BEDROCK=1)."
  fi
else
  log "No AWS credentials detected."
  log "Set them as environment variables/secrets in your web environment settings"
  log "(AWS_ACCESS_KEY_ID + AWS_SECRET_ACCESS_KEY, or AWS_PROFILE). Do NOT commit secrets."
fi

exit 0
