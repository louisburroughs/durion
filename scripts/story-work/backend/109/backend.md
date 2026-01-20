Title: [BACKEND] [STORY] Party: Search and Merge Duplicate Parties (Basic)
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/109
Labels: type:story, domain:crm, status:needs-review

STOP: Clarification required before finalization

## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:crm
- status:draft

### Recommended
- agent:crm
- agent:story-authoring

### Blocking / Risk
- blocked:clarification

---
**Rewrite Variant:** crm-pragmatic
---

## Story Intent
As an Admin user, I need a system capability to find and merge duplicate customer (Party) records. This will ensure data integrity, reduce confusion for service staff when selecting customers for workorders, and consolidate a customer's history into a single authoritative record. The system must ensure that all historical references to the merged record remain valid.

## Actors & Stakeholders
- **Admin:** The primary actor who identifies, selects, and executes the merge operation.
- **System:** The backend service responsible for performing the data merge, re-associating related entities, creating audit trails, and managing ID redirections.
- **Workorder Execution System (Stakeholder):** A downstream consumer of Party data. It relies on the CRM domain to ensure that Party IDs referenced in historical workorders remain resolvable after a merge.

## Preconditions
- The Admin is authenticated and has authorization to perform Party merge operations.
- At least two Party records exist which are candidates for merging.
- The system provides a user interface for searching Parties and initiating the merge action.

## Functional Behavior
1.  **Trigger:** The Admin initiates a search for duplicate Parties using criteria such as name, email address, or phone number.
2.  The System returns a list of Party records matching the search criteria.
3.  The Admin selects exactly two records from the list to be merged:
    - One is designated the **Survivor Party** (the record to keep).
    - The other is designated the **Source Party** (the record to be absorbed and deactivated).
4.  The Admin confirms the merge action.
5.  The System executes the merge transaction:
    a. All child entities and relationships (e.g., Vehicles, Contacts, Workorders, Communications) associated with the `Source Party` are re-associated with the `Survivor Party`.
    b. The `Source Party`'s status is changed to `MERGED` or an equivalent terminal state, effectively deactivating it. It should no longer appear in standard searches.
    c. A `MergeAudit` record is created, immutably logging the `sourcePartyId`, `survivorPartyId`, the ID of the Admin who performed the merge, and the timestamp.
    d. A `PartyAlias` record is created, mapping the `sourcePartyId` to the `survivorPartyId` to ensure permanent resolvability.

## Alternate / Error Flows
- **No Duplicates Found:** If the search yields zero or one result, the System informs the user that no duplicates were found to merge.
- **Merge a Party with Itself:** If the Admin selects the same Party record as both the Source and Survivor, the System shall reject the operation with an error message: "Cannot merge a party with itself."
- **Merge Transaction Failure:** If any part of the merge transaction fails (e.g., database constraint violation during re-association), the entire operation must be rolled back to its pre-merge state. An error log with a transaction ID should be created for technical investigation.

## Business Rules
- A merge operation is considered permanent and cannot be undone through the standard user interface.
- Only two Party records can be merged at one time.
- The core attributes of the `Survivor Party` (e.g., name, primary address) are preserved. The disposition of conflicting or unique attributes from the `Source Party` must be defined (see Open Questions).
- After the merge, the `Source Party` must not be retrievable through standard search endpoints.
- All future requests for the `sourcePartyId` must be transparently redirected to the `survivorPartyId`.

## Data Requirements
- **Party:**
    - `partyId` (UUID, PK)
    - `status` (Enum: `ACTIVE`, `INACTIVE`, `MERGED`)
    - Customer attributes (name, etc.)
- **MergeAudit (New Entity):**
    - `mergeAuditId` (PK)
    - `survivorPartyId` (FK to Party)
    - `sourcePartyId` (FK to Party, but not enforced to allow for source record deletion policies)
    - `mergedByUserId`
    - `mergedAt` (Timestamp)
- **PartyAlias (New Entity):**
    - `sourcePartyId` (PK, Indexed)
    - `targetPartyId` (FK to Party)
    - `createdAt` (Timestamp)
- **Affected Child Entities (Examples):**
    - `Vehicle`: must have its `ownerPartyId` updated.
    - `Contact`: must have its `associatedPartyId` updated.
    - `Workorder`: must have its `customerPartyId` updated.

## Acceptance Criteria
- **AC1: Successful Merge Operation**
    - **Given** two distinct Party records, "Party A" (Source) and "Party B" (Survivor), exist
    - **And** "Party A" is associated with "Vehicle 123"
    - **When** an Admin merges "Party A" into "Party B"
    - **Then** a `MergeAudit` record is created linking Party A to Party B
    - **And** a `PartyAlias` record is created mapping Party A's ID to Party B's ID
    - **And** "Vehicle 123" is now associated with "Party B"
    - **And** the status of "Party A" is set to `MERGED`

- **AC2: Post-Merge ID Resolution**
    - **Given** Party A has been merged into Party B
    - **When** a system service requests data for Party A's ID
    - **Then** the service transparently receives the data for Party B

- **AC3: Prevent Merging a Party with Itself**
    - **Given** a Party record "Party C" exists
    - **When** an Admin attempts to merge "Party C" into itself
    - **Then** the system must display an error and prevent the merge operation

- **AC4: Search Excludes Merged Parties**
    - **Given** Party A has been merged into Party B
    - **When** an Admin performs a standard search that would have previously matched Party A
    - **Then** Party A is not included in the search results

## Audit & Observability
- **Audit Log:** Every successful merge event must be logged in the `MergeAudit` table.
- **Application Logging:**
    - **INFO:** Log the initiation and successful completion of a merge, including all relevant IDs (`sourcePartyId`, `survivorPartyId`, `adminUserId`).
    - **ERROR:** Log any failed merge attempts, including the reason for failure and a transaction correlation ID.
- **Metrics:**
    - `party_merges_total`: A counter for the number of successful merge operations.
    - `party_merge_failures_total`: A counter for failed merge attempts.
    - `party_alias_lookups_total`: A counter for how many times the `PartyAlias` table is used for redirection.

## Open Questions
1.  **Attribute Conflict Resolution:** When merging two parties, how are conflicting primitive attributes handled? For example, if both parties have a different `primaryEmail`.
    - **Option A (Survivor Wins):** The Survivor Party's data is kept, and the Source Party's conflicting data is discarded. (Simplest approach for a "basic" story).
    - **Option B (Manual Selection):** The UI presents conflicts to the Admin to choose which data to keep for each field. (More complex, likely a separate story).
    - **Recommendation:** Clarify if "Survivor Wins" is an acceptable rule for this initial implementation.
2.  **Source Party Final State:** What is the precise final state of the `source` Party record? Is it soft-deleted (e.g., `status = MERGED`), or is there a policy for eventual hard deletion? This impacts foreign key constraints.
3.  **Merge Reversibility:** While the user-facing operation is permanent, is there a requirement for an administrative "un-merge" or rollback capability for disaster recovery?

## Original Story (Unmodified – For Traceability)
# Issue #109 — [BACKEND] [STORY] Party: Search and Merge Duplicate Parties (Basic)

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Party: Search and Merge Duplicate Parties (Basic)

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Admin**, I want **to identify and merge obvious duplicate parties** so that **workorder selection remains clean and accurate**.

## Details
- Search by name/email/phone.
- Merge workflow: choose survivor, move relationships/vehicles/contacts, record merge audit.
- Optional alias/redirect record for merged IDs.

## Acceptance Criteria
- Can list possible duplicates.
- Can merge with an audit record.
- References remain resolvable after merge.

## Integration Points (Workorder Execution)
- Workorder Execution references must remain resolvable after merge (alias/redirect lookup).

## Data / Entities
- Party
- MergeAudit
- PartyAlias (optional)

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: CRM


### Backend Requirements

- Implement Spring Boot microservices
- Create REST API endpoints
- Implement business logic and data access
- Ensure proper security and validation

### Technical Stack

- Spring Boot 3.2.6
- Java 21
- Spring Data JPA
- PostgreSQL/MySQL

---
*This issue was automatically created by the Durion Workspace Agent*