---
repo: louisburroughs/durion
title: "[STORY] NLTI Preview Mode + Confirmation for High-Risk Actions"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language
---

## Story Intent
As a Positivity user, I want to preview and confirm high-risk actions before execution so that I avoid accidental destructive changes.

## Functional Behavior
- Provide preview of planned steps
- Highlight high-risk operations
- Require explicit confirmation before execution

## Acceptance Criteria
- Given a HIGH risk plan, when user reviews it, then confirmation is required.
- Given confirmation is declined, when execution is requested, then execution does not proceed.