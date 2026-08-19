# Shared Agent Configuration

This directory is the canonical source for durion-authored agent definitions, command workflows, instruction files, and shared MCP defaults.

## Contents

- `agents/` — reusable agent definitions used by workspace repos
- `commands/` — command-level task workflows
- `instructions/` — coding and review instructions
- `mcp.json` — shared MCP server defaults

## Usage

Repos should consume these paths via the shared `durion` repo or a pinned submodule, while keeping repo-local overrides in their own `.claude/` folders where needed.
