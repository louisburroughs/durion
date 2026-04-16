# PRD: Journey Gap Discovery V2 (Behavioral Auto-Mapping & Sequence Validation)

## Problem Statement

The V1 implementation of the Journey Gap Discovery tool achieved the goal of extracting, consolidating, and normalizing GitHub issues into a local database. However, the downstream validation (`baseline_parser.py` and `gap_detector.py`) drifted from the original vision of "bottom-up" behavioral analysis into a "top-down" static link checker.

Because V1 expects manual hardcoding of GitHub Issue IDs (e.g., `#123`) in the `JOURNEY_MAP_SEED.md` tables:

1. **Orphan Overload:** Any issue not manually linked ends up isolated, leading to hundreds of false-positive "orphans".
2. **False Gap Detection:** A "missing step" currently just means a markdown link was broken, not that a logical domain state transition was missed.
3. **High Maintenance:** Product owners must manually update markdown tables every time a new story is authored in GitHub.

## Solution

Evolve the pipeline into a **Semantic Behavioral Analyzer**. Instead of matching hardcoded Issue IDs, the baseline documents will define _Expected Events_ (Actor, Action, Object).

The script will automatically slot extracted GitHub issues into the correct journey stages by matching their LLM-extracted `journey_events` against these expectations. This enables true gap detection: verifying that all required logical steps of a business sequence have covering capabilities in the backlog.

## User Stories

1. **Semantic Auto-Slotting**: As a product owner, I want the tool to automatically group extracted stories into journey stages by matching their extracted events (Actor/Action/Object) against the expected events defined in the baseline, so I don't have to manually link issue IDs in markdown.
2. **Behavioral Baselines**: As a product owner, I want to define my journey swimlane tables in markdown using human-readable semantic events (e.g., `Service Advisor | Create | Estimate`) rather than GitHub Issue IDs, so the journey definitions remain decoupled from transient GitHub data.
3. **True Sequence Validation**: As a product owner, I want the gap detector to flag a stage as "Missing" if an expected semantic event is defined in the baseline, but no extracted GitHub issue matches that event.
4. **Intelligent Orphan Resolution**: As a product owner, I want unmatched issues to be inherently categorized as "Orphans", highlighting capabilities we are building that don't serve any known user journey.
5. **Journey Sandbox Engine**: As a product owner, I want to be able to drop a new `draft-returns-journey.md` file into the `docs/journeys/` folder defining a new theoretical workflow, and have the tool automatically output exactly which new stories I need to write to achieve that prototype.

## Implementation Plan

The V2 upgrade requires targeted modifications, specifically replacing the `baseline_parser` and `gap_detector` modules. The extraction and normalizer modules are already perfectly positioned for this, as they currently extract `journey_events`!

### 1. Update `docs/journeys/*.md` Format (The Baseline)

Migrate the swimlane schema from Issue IDs to Expected Events.
**V1 (Current):**
`| Intake | Service Advisor | #68, #79 | Notes... |`

**V2 (Target):**
`| Intake | Service Advisor | [Create, Estimate], [Read, CustomerHistory] | Notes... |`

### 2. Update `baseline_parser.py`

- Modify the regex/table parser to extract lists of `(Action, Object)` pairs from the third column instead of GitHub Issue references.
- `JourneyStage` dataclass updated: `expected_events: list[tuple[str, str]]` replaces `story_ids: list[str]`.

### 3. Update `gap_detector.py`

Rewrite the core logic to be a **Coverage Matcher**:

- **Auto-Slotting Loop**:
  - For each `JourneyStage`, iterate through its `expected_events`.
  - Query the SQLite `journey_events` table for extracted issues where `Actor == stage.persona` AND `Action == expected.action` AND `Object == expected.object`.
  - If matches found: The story covers the stage (link it dynamically).
  - If no matches found: Log a **Missing Step Gap**.
- **Orphan Loop**:
  - Any issue in the SQLite `issues` table that was _not_ slotted during the auto-slotting loop becomes a true _Orphan Gap_ (a story being built that doesn't fit the map).

### 4. Update `reporter.py`

- Adjust the Traceability Matrix generation to output the auto-mapped Story IDs calculated during the gap detection phase, rather than expecting them from the baseline.

## Acceptance Criteria

- [ ] V2 successfully runs against the existing SQLite database (`github-issues.db`) without requiring re-extraction from GitHub.
- [ ] Orphan count drops significantly as stories are auto-binned based on semantic payload.
- [ ] Missing step gaps highlight exactly which `(Actor, Action, Object)` tuple is not covered by any capability in the current GitHub backlog.
- [ ] Adding a new markdown file with hypothetical steps accurately generates gaps for those specific missing capabilities.
