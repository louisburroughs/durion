A structured way to do this is to treat your executed specifications as **observable behavior traces**, then systematically normalize and mine them into **journeys**, and finally analyze gaps.

Below is a method that fits your current GitHub-driven, agent-assisted workflow.

---

# 1) Normalize Executed Specifications into “Journey Events”

Your specs (stories, PRDs, execution logs) are too heterogeneous. First, convert them into a consistent event model.

### Define a canonical event schema

Each executed step becomes a **Journey Event**:

```
event_id (UUIDv7)
journey_candidate_id (nullable)
actor (role/persona)
trigger (what initiated this step)
action (verb)
object (domain entity)
context (state + inputs)
outcome (success/failure/branch)
timestamp
source_ref (story ID / PR / log)
```

Example (Durion-aligned):

```
actor: "Service Writer"
action: "Create"
object: "WorkOrder"
context: { vehicleId, customerId }
outcome: "Success"
source_ref: STORY-WORKEXEC-012
```

This aligns with your **event-driven architecture** and can later map to your domain event contracts.

---

# 2) Extract Event Streams from Executed Work

- durion-positivity-backend
- durion-moqui-frontend
- durion

1. Extract business requirements, capabilities and stories from durion issues (regardless of status) - <https://github.com/louisburroughs/durion/issues/>
2. Add to database in a hierarchy (using trackedIssues and trackedInIssues to form hierarchy)
3. Extract issues from durion-positivity-backend and durion-moqui-frontend using the links (trackedIssues) from the parent stories if possible
   A). Otherwise, extract issues and match after extraction
4. Need to attach clarification issues to the issue that they clarify - the link is defined in the title of the clarification issue
5. Take child issues, plus comments, plus clarification and rewrite cleanly (DO NOT ELABORATE OR ASK NEW QUESTIONS)
6. Add child issues to db, place in the hierarchy

```bash
# Sub-issues/children require the GraphQL API directly
gh api graphql -f query='
{
  repository(owner: "louisburroughs", name: "durion-positivity-backend") {
    issue(number: 100) {
      title
      body
      state
      labels(first: 20) { nodes { name } }
      comments(first: 50) { nodes { author { login } body createdAt } }
      trackedInIssues(first: 10) { nodes { number title state } }
      trackedIssues(first: 25) { nodes { number title state } }
    }
  }
}'
```

```bash
# Check if available (almost certainly yes)
sqlite3 --version

# Create DB and schema
sqlite3 /tmp/github-issues.db <<'EOF'
CREATE TABLE issues (
  number INTEGER PRIMARY KEY,
  title TEXT,
  state TEXT,
  body TEXT,
  url TEXT,
  labels TEXT,       -- JSON array
  milestone TEXT,
  created_at TEXT,
  updated_at TEXT
);

CREATE TABLE comments (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  issue_number INTEGER REFERENCES issues(number),
  author TEXT,
  body TEXT,
  created_at TEXT
);

CREATE TABLE sub_issues (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  parent_number INTEGER REFERENCES issues(number),
  child_number INTEGER,
  title TEXT,
  state TEXT
);
EOF

# Fetch and insert — pipe gh JSON into sqlite via Python
gh issue view 100 --json number,title,state,body,url,labels,milestone,createdAt,updatedAt \
  --repo louisburroughs/durion-positivity-backend \
  | python3 -c "
import json, sqlite3, sys
data = json.load(sys.stdin)
con = sqlite3.connect('/tmp/github-issues.db')
con.execute('INSERT OR REPLACE INTO issues VALUES (?,?,?,?,?,?,?,?,?)', (
    data['number'], data['title'], data['state'], data['body'], data['url'],
    json.dumps([l['name'] for l in data['labels']]),
    data['milestone']['title'] if data['milestone'] else None,
    data['createdAt'], data['updatedAt']
))
con.commit()
"
```

Sources:

- GitHub Issues (acceptance criteria → steps)
- PR descriptions / commits
- Agent execution logs
- Runtime telemetry (if available)

### Extraction approach

- Use an agent to:

  - Parse Gherkin (Given/When/Then → events)
  - Parse service calls (API → action/object)
  - Parse UI flows (Moqui screens → actions)

Output:

```
[Event1 → Event2 → Event3 → ...]
```

Each becomes a **raw journey trace**.

---

# 3) Cluster Event Streams into Candidate Journeys

Now group similar traces.

### Technique options (practical → advanced)

**A. Heuristic grouping (start here)**

- Group by:

  - Primary actor
  - Primary object (e.g., WorkOrder, Invoice)
  - Entry trigger

Example clusters:

- “Create Work Order”
- “Execute Work Order”
- “Invoice & Payment”

**B. Sequence similarity**

- Compare traces using:

  - Longest Common Subsequence (LCS)
  - Edit distance

**C. Process mining (ideal end-state)**

- Use process mining tools (e.g., PM4Py)
- Input: event logs
- Output: inferred process graphs

---

# 4) Derive Canonical User Journeys

For each cluster:

### Build a canonical journey

```
Journey: Work Order Execution

1. Create Work Order
2. Add Line Items
3. Assign Technician
4. Execute Service
5. Capture Parts Consumption
6. Complete Work
7. Generate Invoice
```

### Normalize steps

- Merge equivalent actions
- Remove noise (logging, retries)
- Keep **user-visible intent steps**

---

# 5) Map Journeys Back to Specifications (Traceability Matrix)

Create a matrix:

| Journey Step      | Supporting Stories | Coverage | Notes               |
| ----------------- | ------------------ | -------- | ------------------- |
| Create Work Order | STORY-001, 014     | Full     | —                   |
| Assign Technician | STORY-022          | Partial  | Missing mobile flow |
| Capture Parts     | —                  | Missing  | GAP                 |

This is critical for your workflow:

- Links directly to GitHub Issues
- Enables agent-driven gap detection

---

# 6) Identify Gaps (Core Objective)

### A. Missing Steps within Journeys

Look for:

- Broken sequences (A → C, missing B)
- State transitions without actions
- Required domain invariants not enforced

Example:

- WorkOrder → Completed
- No “Quality Check” step → **gap**

---

### B. Missing Journeys

Look for:

- Domain objects with no full lifecycle
- Events that never connect into a full flow

Example:

- Inventory Adjustment exists
- No “Cycle Count Journey” → **missing journey**

---

### C. Role Coverage Gaps

- Each persona should have coherent journeys
- Detect orphan actions

---

### D. Integration Gaps (important for Durion)

- Events exist but:

  - No upstream trigger
  - No downstream consumer

---

# 7) Visualize Journeys

Use simple artifacts:

### A. Linear Journey Map

```
[Create] → [Schedule] → [Execute] → [Invoice]
```

### B. State + Action Overlay

```
Draft → (Add Items) → Ready
Ready → (Assign Tech) → In Progress
In Progress → (Complete) → Completed
```

### C. Swimlanes (recommended)

- Actor lanes:

  - Service Writer
  - Technician
  - System
  - External (Supplier API)

---

# 8) Feed Back into Your GitHub Workflow

For each gap:

### Create new issues

- Type: `type:story`
- Domain: enforce single domain
- Label: `blocked:clarification` if unclear

### Add

- Missing step as a STORY
- Missing journey as CAPABILITY or EPIC

---

# 9) Automate with Agents (fits your architecture)

You can implement 3 agents:

### 1. Extraction Agent

- Input: stories, PRs, logs
- Output: normalized event streams

### 2. Journey Builder Agent

- Clusters events
- Produces canonical journeys

### 3. Gap Analysis Agent

- Builds traceability matrix
- Flags:

  - Missing steps
  - Missing journeys
  - Domain conflicts

---

# 10) Optional: Formal Model (Higher Rigor)

Represent journeys as:

### State Machine per domain

- States = business states
- Transitions = actions

Then validate:

- Every state reachable
- No dead-end states
- All required transitions exist

This aligns well with your **domain ownership + SoR discipline**.

---

# Summary (Condensed Method)

1. Normalize specs → event model
2. Extract event sequences from execution
3. Cluster into candidate journeys
4. Build canonical journeys
5. Map steps ↔ stories (traceability)
6. Identify:

   - Missing steps
   - Missing journeys
7. Feed gaps back into GitHub as structured issues
8. Automate via agents

---

If needed, this can be converted into:

- A concrete GitHub automation workflow
- A schema aligned to your `Durion Accounting Event Contract v1`
- Or a process-mining pipeline using your event data streams
