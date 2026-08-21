# Skill: Pull Request Babysitter

## Description
An automation skill for Claude that continuously monitors, triages, and manages an open GitHub pull request until it is successfully merged. It intercepts CI/CD pipeline failures, automatically handles flaky tests, resolves minor merge conflicts, and applies fixes based on reviewer comments without requiring constant human intervention.

## Triggers
* "Babysit my PR at https://github.com/owner/repo/pull/123"
* "Watch this pull request and merge it when CI passes"
* "Keep an eye on PR #45 and fix any test failures or review comments"

## Requirements
* GitHub personal access token (PAT) with `repo` scope configured in environment variables (`GITHUB_TOKEN`).
* GitHub CLI (`gh`) installed and authenticated locally.
* Active internet access to pull status updates from GitHub's REST/GraphQL APIs.

## Instructions / Workflow

### 1. Initialization & Context Gathering
* Extract the repository owner, name, and pull request number from the user's input URL or text.
* Authenticate against the GitHub API using `gh auth status` or the `GITHUB_TOKEN`.
* Fetch current PR metadata including:
  * Base and head branch names.
  * Latest commit SHA.
  * Current CI/CD check run status.
  * Open review comments or change requests.

### 2. Monitoring Loop (Execution)
Execute a polling loop (suggested interval: every 2 to 5 minutes) to watch the PR state until a terminal condition (Merged or Closed) is met.

#### A. Evaluate CI/CD Status
* Run `gh pr checks <pr-number>` to fetch the latest status of all required checks.
* **If all checks pass:** Move to step 2C (Review & Merge Evaluation).
* **If a check fails:**
  * Fetch execution logs for the failed step using `gh run view <run-id> --log`.
  * Analyze the log output to determine the failure category (e.g., compile error, lint issue, flaky network test, deterministic unit test failure).
  * **Action (Flaky/Transient Failure):** If identified as an intermittent or flaky test, trigger a rerun of the failed job using `gh run rerun <run-id> --failed`.
  * **Action (Code/Build Error):** If it is a deterministic code bug or lint failure, check out the PR head branch locally, apply a targeted code fix, commit, and push back to the PR branch.

#### B. Handle Reviewer Feedback
* Fetch recent review comments using the GitHub API (`GET /repos/{owner}/{repo}/pulls/{number}/comments`).
* Filter for unresolved comments or requests for changes.
* Parse the feedback:
  * For minor style adjustments, documentation updates, or straightforward fixes, modify the local files, verify changes, and push a commit.
  * For structural or architectural disputes that require human design choices, pause the loop and ping the user with a summary.

#### C. Review & Merge Evaluation
* Check if the PR satisfies the repository's branch protection rules (e.g., minimum required approvals, signed commits).
* If approvals are complete and CI is completely green, execute the merge:
  ```bash
  gh pr merge <pr-number> --squash --auto
  ```
* If the merge fails due to out-of-date branch structures or merge conflicts:
  * Fetch updates from the base branch (`git fetch origin <base-branch>`).
  * Attempt an automated merge or rebase (`git rebase origin/<base-branch>`).
  * Resolve structural merge conflicts if they are trivial (e.g., imports or version bumps); otherwise, alert the user.

### 3. Error Handling & Termination
* **Terminal Success:** Exit gracefully when the PR status updates to `Merged`.
* **Terminal Failure/Handover:** Exit and notify the human operator via stdout/UI if:
  * The PR is manually closed without merging.
  * CI failures persist after a maximum number of automatic retries (default: 2 retries per unique test failure).
  * Merge conflicts require complex manual refactoring or logical reconciliation.
