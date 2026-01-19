# LOCATION_DOMAIN_NOTES.md

## Summary

This document provides non-normative, verbose rationale and decision logs for the Location domain within the Durion enterprise truck service management system. It supports auditors, architects, and engineers by documenting the reasoning behind design choices, alternatives considered, architectural implications, and migration notes for key decisions referenced in the normative AGENT_GUIDE.md.

## Completed items

- [x] Linked each Decision ID to a detailed rationale
- [x] Documented alternatives considered for each decision
- [x] Provided architectural implications and migration notes
- [x] Included auditor-facing explanations with example queries

## Decision details

### DECISION-LOCATION-001 — Location Code Immutability

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-LOCATION-001)
- **Decision:** The `code` field of a Location entity is immutable after creation. Once a location is created with a specific code, that code cannot be changed through any update operation.
- **Alternatives considered:**
  - **Option A (Chosen):** Make code immutable after creation
    - Pros: Prevents breaking references, simplifies audit trails, ensures stable external integrations
    - Cons: Requires creating a new location if code needs to change
  - **Option B:** Allow code updates with cascading reference updates
    - Pros: Provides flexibility for correcting mistakes
    - Cons: High risk of breaking external system integrations, complex cascading update logic, audit trail complexity
  - **Option C:** Support code updates with a deprecation/migration period
    - Pros: Balances flexibility with safety
    - Cons: Adds significant complexity to API and data model, requires temporal versioning
- **Reasoning and evidence:**
  - Location codes serve as stable identifiers in external systems (scheduling, inventory, work execution)
  - Changing codes would require coordinated updates across multiple microservices
  - Historical data and audit trails rely on stable identifiers
  - Most enterprise systems treat location codes as immutable primary keys
  - Correction scenarios are rare and can be handled through location deactivation and recreation
- **Architectural implications:**
  - **Components affected:**
    - Location API: PUT/PATCH endpoints must reject code changes with 400 BAD_REQUEST
    - Database schema: Code field remains in location table with unique constraint
    - Events: LocationCreated includes code; LocationUpdated does not allow code changes
  - **Data integrity:**
    - Unique constraint on code at database level prevents duplicates
    - API layer validates that update requests do not attempt to modify code
  - **Integration contracts:**
    - External systems can safely cache location code mappings
    - No code-change events need to be supported in consumer services
- **Auditor-facing explanation:**
  - **What to inspect:** Verify that all Location update operations preserve the original code value
  - **Logs to query:**
    ```sql
    -- Verify no code changes in update events
    SELECT event_id, location_id, event_type, payload
    FROM location_events
    WHERE event_type = 'LocationUpdated'
      AND JSON_EXTRACT(payload, '$.code') IS NOT NULL
    ORDER BY created_at DESC;
    ```
  - **Expected outcome:** Zero records with code present in LocationUpdated payloads
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Document code immutability in API specification
    2. Add validation in API layer to reject code modification attempts
    3. Update client libraries and UI to remove code editing capability
  - **Safe-to-deploy order:**
    1. Backend validation deployment (returns 400 for code changes)
    2. UI updates to remove code editing
  - **Data migration:** No data migration required; existing codes remain unchanged
- **Governance & owner recommendations:**
  - **Owner:** Location domain team / API Platform team
  - **Review cadence:** Annual review of immutability policy
  - **Gating criteria:** Any code immutability relaxation requires architectural review board approval

### DECISION-LOCATION-002 — Location Name Uniqueness with Case-Insensitive Normalization

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-LOCATION-002)
- **Decision:** Location names must be unique across all locations using case-insensitive comparison with whitespace trimming. The system normalizes names to lowercase and trims whitespace before uniqueness checking.
- **Alternatives considered:**
  - **Option A (Chosen):** Case-insensitive, trimmed uniqueness
    - Pros: Prevents user confusion, handles common input variations, clear error messages
    - Cons: Requires normalization logic, may reject semantically distinct names
  - **Option B:** Case-sensitive uniqueness
    - Pros: Simpler implementation, allows "Shop A" and "shop a" as distinct
    - Cons: Confusing for users, prone to accidental duplicates
  - **Option C:** No uniqueness constraint
    - Pros: Maximum flexibility
    - Cons: Duplicate locations cause operational confusion, difficult to reference in UI
- **Reasoning and evidence:**
  - Human users naturally treat "Main Shop" and "main shop" as the same location
  - Case variations typically indicate data entry errors rather than distinct entities
  - Leading/trailing whitespace is almost always unintentional
  - Industry standard for location management systems
- **Architectural implications:**
  - **Components affected:**
    - Location API: Create/update endpoints normalize names before uniqueness checks
    - Database: Add functional index on LOWER(TRIM(name))
    - UI: Display names preserve original casing for readability
  - **Database schema:**
    ```sql
    -- Functional unique index for normalized names
    CREATE UNIQUE INDEX idx_location_name_normalized
    ON location (LOWER(TRIM(name)));
    ```
  - **Error handling:**
    - Return 409 CONFLICT with error code `LOCATION_NAME_TAKEN`
    - Error payload includes normalized conflicting name and existing location ID
- **Auditor-facing explanation:**
  - **What to inspect:** Verify no locations exist with names that differ only by case or whitespace
  - **Query example:**
    ```sql
    -- Find potential duplicate names
    SELECT LOWER(TRIM(name)) as normalized_name, COUNT(*) as count
    FROM location
    GROUP BY LOWER(TRIM(name))
    HAVING COUNT(*) > 1;
    ```
  - **Expected outcome:** Zero records returned
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Audit existing location names for conflicts
    2. Manually resolve any existing duplicates before deploying unique index
    3. Deploy database migration with functional unique index
    4. Deploy API validation logic
  - **Migration command:**
    ```sql
    -- Identify existing conflicts before migration
    SELECT name, COUNT(*)
    FROM location
    GROUP BY LOWER(TRIM(name))
    HAVING COUNT(*) > 1;
    ```
  - **Rollback strategy:** Drop functional index if issues arise; name changes remain valid
- **Governance & owner recommendations:**
  - **Owner:** Location domain team
  - **Review cadence:** No regular review needed; stable decision
  - **Escalation:** Data team involvement for resolving discovered conflicts

### DECISION-LOCATION-003 — Timezone Validation Using IANA Time Zone Database

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-LOCATION-003)
- **Decision:** Location timezone values must be valid IANA time zone identifiers (e.g., "America/New_York", "Europe/London"). The backend validates against the IANA tz database. The frontend uses a backend-provided endpoint that returns the current allowed list of time zones.
- **Alternatives considered:**
  - **Option A (Chosen):** Backend-provided dynamic timezone list
    - Pros: Always current, supports IANA updates, consistent validation
    - Cons: Requires backend endpoint, frontend dependency on backend for picker
  - **Option B:** Static frontend timezone list
    - Pros: No runtime dependency, faster UI load
    - Cons: Becomes stale, requires frontend updates for new timezones
  - **Option C:** Offset-based timezones (+05:00)
    - Pros: Simple representation
    - Cons: Loses DST handling, no semantic location context
- **Reasoning and evidence:**
  - IANA tz database is the global standard for timezone representation
  - DST rules change frequently and vary by jurisdiction
  - Offset-only representation breaks during DST transitions
  - Most programming languages and databases have built-in IANA tz support
  - Backend validation ensures data integrity regardless of client behavior
- **Architectural implications:**
  - **Components affected:**
    - Location API: Validation middleware checks timezone against IANA database
    - New endpoint: `GET /v1/timezones` returns list of allowed timezone identifiers
    - Database: Store timezone as VARCHAR with CHECK constraint or enum type
  - **API contract:**
    ```json
    // GET /v1/timezones response
    {
      "timezones": [
        {"id": "America/New_York", "displayName": "America/New York (EST/EDT)", "offset": "-05:00"},
        {"id": "Europe/London", "displayName": "Europe/London (GMT/BST)", "offset": "+00:00"}
      ]
    }
    ```
  - **Error handling:**
    - Return 400 with error code `INVALID_TIMEZONE` when timezone is not in allowed list
    - Error payload includes list of suggestions based on string similarity
- **Auditor-facing explanation:**
  - **What to inspect:** Verify all location timezone values are valid IANA identifiers
  - **Query example:**
    ```sql
    -- Identify invalid timezones (PostgreSQL example)
    SELECT location_id, code, timezone
    FROM location
    WHERE timezone IS NOT NULL
      AND timezone NOT IN (SELECT name FROM pg_timezone_names);
    ```
  - **Expected outcome:** Zero records with invalid timezones
  - **Audit artifact:** API request/response logs showing timezone validation
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Audit existing location timezone values
    2. Migrate any invalid or offset-based values to IANA identifiers
    3. Deploy timezone validation endpoint
    4. Deploy location API validation
    5. Update frontend to use timezone picker endpoint
  - **Migration script example:**
    ```sql
    -- Convert common offset patterns to IANA identifiers
    UPDATE location
    SET timezone = 'America/New_York'
    WHERE timezone IN ('EST', 'Eastern', '-05:00', 'US/Eastern');
    ```
  - **Safe-to-deploy order:**
    1. Deploy timezone list endpoint
    2. Deploy validation in location API (initially as warning, not blocking)
    3. Complete data migration
    4. Enable strict validation
- **Governance & owner recommendations:**
  - **Owner:** Location domain team with infrastructure support for IANA database updates
  - **Review cadence:** Annual review after IANA database updates
  - **Update process:** Backend automatically picks up new IANA versions through library updates

### DECISION-LOCATION-004 — Operating Hours Validation Rules

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-LOCATION-004)
- **Decision:** Operating hours must be represented as day-of-week entries with local time ranges. Each day can have at most one entry. Open time must be strictly less than close time (no overnight ranges). Closed days are represented by omission. An empty array is allowed and indicates the location has no standard operating hours defined.
- **Alternatives considered:**
  - **Option A (Chosen):** Single time range per day, no overnight
    - Pros: Simple representation, clear semantics, easier to validate and display
    - Cons: Cannot represent split shifts or overnight operations
  - **Option B:** Multiple time ranges per day
    - Pros: Supports split shifts (e.g., 8am-noon, 2pm-6pm)
    - Cons: Complex validation, UI complexity, rarely needed for location-level hours
  - **Option C:** Overnight ranges (close < open)
    - Pros: Natural representation for 24-hour or overnight operations
    - Cons: Complex timezone handling, ambiguous day boundary, validation complexity
- **Reasoning and evidence:**
  - Location-level operating hours define when a facility is generally available
  - Split shifts and overnight operations are typically managed at the bay or appointment level
  - Simple model reduces validation complexity and UI errors
  - 24-hour operations can be represented as "00:00-23:59" or omitting hours constraints
  - Most locations operate within a single continuous daily window
- **Architectural implications:**
  - **Components affected:**
    - Location API: Validation logic for operating hours structure
    - Database: Store as JSONB with schema validation
    - UI: Day-of-week picker with time range inputs
  - **Payload structure:**
    ```json
    {
      "operatingHours": [
        {"dayOfWeek": "MONDAY", "open": "08:00", "close": "17:00"},
        {"dayOfWeek": "TUESDAY", "open": "08:00", "close": "17:00"}
      ]
    }
    ```
  - **Validation rules:**
    1. No duplicate `dayOfWeek` values
    2. `open` and `close` must be valid local times (HH:MM format)
    3. `open` < `close` (string comparison works for HH:MM)
    4. Empty array is valid (no hours defined)
  - **Database constraint:**
    ```sql
    -- JSONB schema validation (PostgreSQL)
    ALTER TABLE location ADD CONSTRAINT operating_hours_schema
    CHECK (
      jsonb_typeof(operating_hours) = 'array'
      AND (
        operating_hours = '[]'::jsonb
        OR (
          SELECT COUNT(*) = COUNT(DISTINCT elem->>'dayOfWeek')
          FROM jsonb_array_elements(operating_hours) AS elem
        )
      )
    );
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify all operating hours entries follow validation rules
  - **Query example:**
    ```sql
    -- Find locations with invalid operating hours
    SELECT location_id, code, operating_hours
    FROM location
    WHERE operating_hours IS NOT NULL
      AND (
        -- Check for duplicates
        jsonb_array_length(operating_hours) != 
          (SELECT COUNT(DISTINCT value->>'dayOfWeek') 
           FROM jsonb_array_elements(operating_hours))
        -- Check for invalid time ranges
        OR EXISTS (
          SELECT 1 FROM jsonb_array_elements(operating_hours) elem
          WHERE elem->>'open' >= elem->>'close'
        )
      );
    ```
  - **Expected outcome:** Zero locations with validation violations
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Audit existing operating hours for overnight ranges or duplicates
    2. Normalize overnight ranges to 23:59 close or remove hours constraint
    3. Deploy schema validation
    4. Update frontend to prevent invalid input
  - **Migration considerations:**
    - Locations with overnight operations may need manual review
    - Consider adding a boolean flag `operates24x7` if needed
- **Governance & owner recommendations:**
  - **Owner:** Location domain team
  - **Review cadence:** Review if overnight operations become a common requirement
  - **Extension path:** If split shifts needed, add as separate `specialHours` field

### DECISION-LOCATION-005 — Holiday Closures Representation

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-LOCATION-005)
- **Decision:** Holiday closures are represented as an array of date-only entries (YYYY-MM-DD format) with optional reason strings. No duplicate dates are allowed. The field is nullable; null indicates "no data" while an empty array indicates "no closures defined." The `reason` field is optional, max 255 characters.
- **Alternatives considered:**
  - **Option A (Chosen):** Nullable array with date-only entries
    - Pros: Clear semantics, simple validation, supports partial data
    - Cons: Requires null vs empty array distinction
  - **Option B:** Always require array (empty = no closures)
    - Pros: Simpler (no null handling)
    - Cons: Cannot distinguish "not yet configured" from "no closures"
  - **Option C:** Store as date ranges (closed from X to Y)
    - Pros: Supports multi-day closures efficiently
    - Cons: More complex validation and display, rarely needed for single-day holidays
- **Reasoning and evidence:**
  - Most holiday closures are single-day events
  - Multi-day closures (e.g., Christmas week) can be represented as multiple entries
  - Nullable field allows gradual rollout (null = legacy, not yet configured)
  - Empty array explicitly communicates "no closures" after configuration
  - Reason field supports display in UI ("Closed for Thanksgiving")
- **Architectural implications:**
  - **Components affected:**
    - Location API: Validation for closure structure
    - Database: JSONB array field with schema validation
    - UI: Date picker with multi-select capability and optional reason input
  - **Payload structure:**
    ```json
    {
      "holidayClosures": [
        {"date": "2026-07-04", "reason": "Independence Day"},
        {"date": "2026-12-25", "reason": "Christmas Day"}
      ]
    }
    ```
  - **Validation rules:**
    1. All dates must be valid ISO 8601 date format (YYYY-MM-DD)
    2. No duplicate dates
    3. Reason field optional; if present, max 255 characters
    4. Null or array (no other types)
  - **Database constraint:**
    ```sql
    ALTER TABLE location ADD CONSTRAINT holiday_closures_schema
    CHECK (
      holiday_closures IS NULL
      OR (
        jsonb_typeof(holiday_closures) = 'array'
        AND (
          SELECT COUNT(*) = COUNT(DISTINCT elem->>'date')
          FROM jsonb_array_elements(holiday_closures) AS elem
        )
      )
    );
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify no duplicate closure dates and valid date formats
  - **Query example:**
    ```sql
    -- Find locations with invalid holiday closures
    SELECT location_id, code, holiday_closures
    FROM location
    WHERE holiday_closures IS NOT NULL
      AND jsonb_array_length(holiday_closures) != 
        (SELECT COUNT(DISTINCT value->>'date') 
         FROM jsonb_array_elements(holiday_closures));
    ```
  - **Expected outcome:** Zero locations with duplicate dates
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Set existing locations without closure data to NULL
    2. Validate any existing closure data for duplicates
    3. Deploy schema validation
    4. Update UI to handle null vs empty array
  - **Rollout strategy:**
    - Phase 1: Allow NULL (not yet configured)
    - Phase 2: Gradually populate with historical closure data
    - Phase 3: Optionally require non-null for active locations (future)
- **Governance & owner recommendations:**
  - **Owner:** Location domain team
  - **Review cadence:** Annual review before holiday season
  - **Future enhancement:** Consider API endpoint to populate common holidays automatically

### DECISION-LOCATION-006 — Bay Name Uniqueness Within Location

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-LOCATION-006)
- **Decision:** Bay names must be unique within their parent location. Bay names are case-sensitive and whitespace is trimmed before uniqueness checking. Names can be reused across different locations.
- **Alternatives considered:**
  - **Option A (Chosen):** Unique within location, case-sensitive after trimming
    - Pros: Prevents confusion at a given site, allows "Bay 1" at multiple locations
    - Cons: Still allows "bay 1" and "Bay 1" as distinct within same location
  - **Option B:** Globally unique bay names
    - Pros: Unambiguous references across system
    - Cons: Artificially constrains naming, difficult for multi-site operations
  - **Option C:** No uniqueness constraint
    - Pros: Maximum flexibility
    - Cons: Confusing for users, difficult UI selection
- **Reasoning and evidence:**
  - Within a single location, duplicate bay names cause operational confusion
  - Cross-location bay name reuse is common and expected (every shop has "Bay 1")
  - Case sensitivity preserved for display purposes (e.g., "Bay A" vs "Bay a")
  - Trimming whitespace prevents accidental duplicates from data entry
- **Architectural implications:**
  - **Components affected:**
    - Bay creation API: Validation checks for name uniqueness within location
    - Database: Unique constraint on (location_id, TRIM(name))
    - UI: Error handling for duplicate names during bay creation
  - **Database schema:**
    ```sql
    CREATE UNIQUE INDEX idx_bay_name_per_location
    ON bay (location_id, TRIM(name));
    ```
  - **Error handling:**
    - Return 409 CONFLICT with error code `BAY_NAME_TAKEN_IN_LOCATION`
    - Error payload includes conflicting name and existing bay ID
- **Auditor-facing explanation:**
  - **What to inspect:** Verify no duplicate bay names within any location
  - **Query example:**
    ```sql
    SELECT location_id, TRIM(name) as bay_name, COUNT(*) as count
    FROM bay
    GROUP BY location_id, TRIM(name)
    HAVING COUNT(*) > 1;
    ```
  - **Expected outcome:** Zero records with duplicate names per location
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Audit existing bays for duplicate names within locations
    2. Manually resolve conflicts (rename or deactivate duplicates)
    3. Deploy unique constraint
  - **Conflict resolution strategy:**
    - Append suffix to duplicates: "Bay 1" → "Bay 1 (2)"
    - Coordinate with operations team for renaming
- **Governance & owner recommendations:**
  - **Owner:** Location domain team
  - **Review cadence:** No regular review needed
  - **Naming convention:** Document recommended bay naming patterns (e.g., "Bay [Number]", "Rack [Letter]")

### DECISION-LOCATION-007 — Mobile Unit Travel Buffer Policy Requirement

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-LOCATION-007)
- **Decision:** Mobile units must have a `travelBufferPolicyId` assigned when their status is ACTIVE. The policy is optional (may be null) when status is INACTIVE or OUT_OF_SERVICE. The backend enforces this constraint and returns 400 BAD_REQUEST when attempting to activate a mobile unit without a policy.
- **Alternatives considered:**
  - **Option A (Chosen):** Policy required only for ACTIVE status
    - Pros: Allows configuration in stages, clear activation requirement, prevents scheduling with undefined buffers
    - Cons: Must coordinate policy creation before activation
  - **Option B:** Policy always required
    - Pros: Simpler invariant
    - Cons: Cannot create mobile unit without policy, increases setup complexity
  - **Option C:** Policy optional, use default if missing
    - Pros: Easier setup
    - Cons: Implicit defaults can lead to misconfiguration, unclear which default applies
- **Reasoning and evidence:**
  - Travel buffer policies directly impact scheduling and customer commitments
  - Active mobile units must have defined buffer rules to prevent incorrect time estimates
  - Inactive/out-of-service units don't need policies as they're not being scheduled
  - Allows creating and configuring mobile units before making them operational
  - Explicit policy assignment prevents reliance on "magic" defaults
- **Architectural implications:**
  - **Components affected:**
    - Mobile Unit API: Validation on create/update when status=ACTIVE
    - Status transition logic: Activating a mobile unit validates policy presence
    - Database: Foreign key with NULL allowed; constraint checking at application layer
  - **Validation rules:**
    ```
    if (status == ACTIVE && travelBufferPolicyId == null):
      return 400 BAD_REQUEST with TRAVEL_BUFFER_POLICY_REQUIRED
    ```
  - **Status transition table:**
    | From | To | Policy Required |
    |------|-----|-----------------|
    | null | ACTIVE | Yes |
    | null | INACTIVE | No |
    | INACTIVE | ACTIVE | Yes |
    | ACTIVE | INACTIVE | No |
    | ACTIVE | OUT_OF_SERVICE | No |
- **Auditor-facing explanation:**
  - **What to inspect:** Verify no active mobile units exist without travel buffer policies
  - **Query example:**
    ```sql
    SELECT mobile_unit_id, name, status, travel_buffer_policy_id
    FROM mobile_unit
    WHERE status = 'ACTIVE'
      AND travel_buffer_policy_id IS NULL;
    ```
  - **Expected outcome:** Zero records returned
  - **Invariant check:** Automated test runs daily to verify constraint
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Create default travel buffer policies for existing active mobile units without policies
    2. Update mobile unit records to reference default policies
    3. Deploy validation enforcement
  - **Default policy creation:**
    ```sql
    -- Create system default policy if needed
    INSERT INTO travel_buffer_policy (id, name, policy_type, policy_configuration)
    VALUES ('default-fixed-30', 'Default 30 Minute Buffer', 'FIXED_MINUTES', '{"minutes": 30}');
    
    -- Assign to active units without policies
    UPDATE mobile_unit
    SET travel_buffer_policy_id = 'default-fixed-30'
    WHERE status = 'ACTIVE' AND travel_buffer_policy_id IS NULL;
    ```
  - **Safe-to-deploy order:**
    1. Create default policies
    2. Backfill existing active units
    3. Deploy validation
- **Governance & owner recommendations:**
  - **Owner:** Location domain team with coordination from scheduling team
  - **Review cadence:** Review during mobile operations planning cycles
  - **Exception process:** No exceptions; proper policy must be defined before activation

### DECISION-LOCATION-008 — Coverage Rule Effective Window Semantics

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-LOCATION-008)
- **Decision:** Coverage rule effective windows use UTC timestamps for `effectiveStartAt` and `effectiveEndAt`. If both are provided, `effectiveEndAt` must be after `effectiveStartAt`. Null start means "effective immediately/from beginning of time." Null end means "effective indefinitely/until further notice." Both null means "always effective." Display in UI uses the mobile unit's base location timezone for readability.
- **Alternatives considered:**
  - **Option A (Chosen):** UTC storage, location timezone display
    - Pros: Unambiguous storage, portable across timezones, clear comparison logic
    - Cons: UI must convert for display, user sees timezone offset
  - **Option B:** Store in base location timezone
    - Pros: "Natural" representation for operations team
    - Cons: Ambiguous during DST transitions, breaks when base location changes
  - **Option C:** Store as date-only (no time component)
    - Pros: Simple, avoids timezone complexity
    - Cons: Loses precision for time-specific rules, cannot handle intraday changes
- **Reasoning and evidence:**
  - Coverage rules are long-lived configuration (weeks to months)
  - Precise time boundaries are rarely needed (day-level granularity sufficient)
  - UTC storage is industry standard for timestamp fields
  - DST transitions create ambiguous "local" times (e.g., 2am on DST change day occurs twice)
  - Mobile unit base location is stable but can change, making local time storage brittle
- **Architectural implications:**
  - **Components affected:**
    - Mobile Unit Coverage API: Stores timestamps as UTC in database
    - UI: Converts UTC to base location timezone for display/input
    - Scheduling service: Queries rules using UTC comparison
  - **Database schema:**
    ```sql
    CREATE TABLE mobile_unit_coverage_rule (
      id UUID PRIMARY KEY,
      mobile_unit_id UUID NOT NULL REFERENCES mobile_unit(id),
      service_area_id UUID NOT NULL,
      priority INTEGER NOT NULL,
      effective_start_at TIMESTAMPTZ,  -- UTC
      effective_end_at TIMESTAMPTZ,    -- UTC
      CHECK (effective_end_at IS NULL OR effective_start_at IS NULL OR effective_end_at > effective_start_at)
    );
    ```
  - **API contract:**
    ```json
    // Request (from UI)
    {
      "effectiveStartAt": "2026-01-01T00:00:00-05:00",  // EST
      "effectiveEndAt": "2026-12-31T23:59:59-05:00"
    }
    
    // Stored (in DB)
    {
      "effectiveStartAt": "2026-01-01T05:00:00Z",  // UTC
      "effectiveEndAt": "2027-01-01T04:59:59Z"
    }
    ```
  - **UI behavior:**
    - Date/time picker shows times in base location timezone
    - API requests include timezone offset (ISO 8601 format)
    - Backend converts to UTC for storage
    - Response includes UTC; UI converts for display
- **Auditor-facing explanation:**
  - **What to inspect:** Verify effective windows are logically ordered and stored in UTC
  - **Query example:**
    ```sql
    -- Find rules with invalid effective windows
    SELECT id, mobile_unit_id, effective_start_at, effective_end_at
    FROM mobile_unit_coverage_rule
    WHERE effective_start_at IS NOT NULL
      AND effective_end_at IS NOT NULL
      AND effective_end_at <= effective_start_at;
    ```
  - **Expected outcome:** Zero records with invalid windows
  - **Audit considerations:**
    - Verify all timestamps stored in UTC
    - Check for DST-related anomalies in rule creation logs
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Audit existing coverage rules for timezone consistency
    2. Migrate any local time values to UTC (requires knowing original timezone)
    3. Update API to accept/return ISO 8601 timestamps with timezone
    4. Update UI to handle timezone conversion
  - **Migration challenges:**
    - If existing data lacks timezone context, must infer from base location
    - DST-aware conversion required for accurate migration
  - **Migration script:**
    ```sql
    -- Convert local times to UTC (example for EST -> UTC)
    UPDATE mobile_unit_coverage_rule cr
    SET 
      effective_start_at = effective_start_at AT TIME ZONE 'America/New_York',
      effective_end_at = effective_end_at AT TIME ZONE 'America/New_York'
    WHERE EXISTS (
      SELECT 1 FROM mobile_unit mu
      JOIN location l ON l.id = mu.base_location_id
      WHERE mu.id = cr.mobile_unit_id
        AND l.timezone = 'America/New_York'
    );
    ```
- **Governance & owner recommendations:**
  - **Owner:** Location domain team with UI/UX review
  - **Review cadence:** Review if DST-related issues reported
  - **User guidance:** Document timezone handling in user help docs

### DECISION-LOCATION-009 — Distance Tier Travel Buffer Policy Validation

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-LOCATION-009)
- **Decision:** DISTANCE_TIER travel buffer policies must have strictly increasing `maxDistance` values (for non-null tiers) and exactly one catch-all tier with `maxDistance = null`. Backend stores all distances in kilometers (KM). UI may accept miles (MI) and converts to KM before submission. Buffer minutes must be non-negative integers.
- **Alternatives considered:**
  - **Option A (Chosen):** Store in KM, UI converts from MI
    - Pros: Standard international unit, consistent storage, precise conversion
    - Cons: Requires conversion logic in UI, risk of rounding errors
  - **Option B:** Store in user-preferred unit
    - Pros: No conversion needed, "natural" representation
    - Cons: Requires storing unit with each policy, comparison complexity, inconsistent data
  - **Option C:** Store both KM and MI
    - Pros: Flexible display
    - Cons: Redundant data, sync complexity, storage overhead
- **Reasoning and evidence:**
  - Kilometer is SI standard unit for distance measurement
  - Backend calculations (routing, scheduling) typically use metric units
  - Most mapping APIs return distances in meters/kilometers
  - Conversion from MI to KM is deterministic (1 MI = 1.609344 KM)
  - US-based UI can display in miles without affecting data integrity
- **Architectural implications:**
  - **Components affected:**
    - Travel Buffer Policy API: Validates tier ordering and catch-all presence
    - Database: Stores policy configuration as JSONB with KM unit
    - UI: Converts MI to KM for submission, KM to MI for display (if US locale)
  - **Policy configuration schema:**
    ```json
    {
      "policyType": "DISTANCE_TIER",
      "policyConfiguration": {
        "unit": "KM",
        "tiers": [
          {"maxDistance": 10.0, "bufferMinutes": 15},
          {"maxDistance": 50.0, "bufferMinutes": 30},
          {"maxDistance": null, "bufferMinutes": 60}
        ]
      }
    }
    ```
  - **Validation rules:**
    1. At least one tier present
    2. Exactly one tier with `maxDistance = null`
    3. Non-null maxDistance values strictly increasing
    4. All bufferMinutes >= 0
    5. Unit must be "KM" (if stored)
  - **UI conversion:**
    ```javascript
    // MI to KM conversion
    const kmDistance = miDistance * 1.609344;
    
    // KM to MI for display
    const miDistance = kmDistance / 1.609344;
    ```
  - **Database constraint:**
    ```sql
    ALTER TABLE travel_buffer_policy ADD CONSTRAINT distance_tier_validation
    CHECK (
      policy_type != 'DISTANCE_TIER'
      OR (
        -- Must have at least one tier
        jsonb_array_length(policy_configuration->'tiers') >= 1
        -- Must have exactly one catch-all
        AND (
          SELECT COUNT(*) = 1
          FROM jsonb_array_elements(policy_configuration->'tiers') t
          WHERE t->>'maxDistance' IS NULL
        )
        -- All buffer minutes non-negative
        AND NOT EXISTS (
          SELECT 1
          FROM jsonb_array_elements(policy_configuration->'tiers') t
          WHERE (t->>'bufferMinutes')::int < 0
        )
      )
    );
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify distance tier policies have valid tier structures
  - **Query example:**
    ```sql
    -- Find policies with invalid tier configurations
    SELECT id, name, policy_configuration
    FROM travel_buffer_policy
    WHERE policy_type = 'DISTANCE_TIER'
      AND (
        -- No catch-all tier
        (SELECT COUNT(*) FROM jsonb_array_elements(policy_configuration->'tiers') t 
         WHERE t->>'maxDistance' IS NULL) != 1
        -- Non-increasing distances
        OR EXISTS (
          SELECT 1
          FROM jsonb_array_elements(policy_configuration->'tiers') WITH ORDINALITY AS t1(tier, ord1)
          JOIN jsonb_array_elements(policy_configuration->'tiers') WITH ORDINALITY AS t2(tier, ord2)
            ON t2.ord2 = t1.ord1 + 1
          WHERE t1.tier->>'maxDistance' IS NOT NULL
            AND t2.tier->>'maxDistance' IS NOT NULL
            AND (t1.tier->>'maxDistance')::numeric >= (t2.tier->>'maxDistance')::numeric
        )
      );
    ```
  - **Expected outcome:** Zero policies with validation violations
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Audit existing DISTANCE_TIER policies for unit consistency
    2. Convert any MI values to KM
    3. Validate all policies meet new constraints
    4. Deploy validation logic
    5. Update UI for unit conversion
  - **Migration script:**
    ```sql
    -- Convert MI to KM (if any policies stored in MI)
    UPDATE travel_buffer_policy
    SET policy_configuration = jsonb_set(
      policy_configuration,
      '{tiers}',
      (
        SELECT jsonb_agg(
          jsonb_set(tier, '{maxDistance}', 
            to_jsonb((tier->>'maxDistance')::numeric * 1.609344))
        )
        FROM jsonb_array_elements(policy_configuration->'tiers') tier
        WHERE tier->>'maxDistance' IS NOT NULL
      )
    )
    WHERE policy_type = 'DISTANCE_TIER'
      AND policy_configuration->>'unit' = 'MI';
    ```
- **Governance & owner recommendations:**
  - **Owner:** Location domain team with input from scheduling team
  - **Review cadence:** Annual review of tier definitions
  - **Best practices:** Document recommended tier breakpoints for different operational contexts

### DECISION-LOCATION-010 — Site Default Storage Location Distinctness

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-LOCATION-010)
- **Decision:** A site's default staging storage location and default quarantine storage location must be distinct (different IDs). The backend enforces this constraint and returns 400 BAD_REQUEST with error code `DEFAULT_LOCATION_ROLE_CONFLICT` when attempting to set the same location for both roles. Both defaults are required (non-null) except during initial migration period.
- **Alternatives considered:**
  - **Option A (Chosen):** Require distinct storage locations for staging vs quarantine
    - Pros: Clear physical separation, prevents process confusion, supports different security requirements
    - Cons: Requires two storage locations per site, may be overkill for small sites
  - **Option B:** Allow same location for both roles
    - Pros: Simpler for small sites, reduced storage location setup
    - Cons: Risk of mixing staged and quarantined inventory, unclear workflow
  - **Option C:** Make one or both optional
    - Pros: Flexible for sites without receiving operations
    - Cons: Complicates downstream logic, unclear defaults
- **Reasoning and evidence:**
  - Staged inventory (awaiting putaway) has different handling rules than quarantined (quality hold, returns)
  - Physical separation prevents accidental mixing during putaway operations
  - Quarantine may have access restrictions not applicable to staging
  - Industry best practice in warehouse management systems
  - Small sites can use small physical areas but still logically separate
- **Architectural implications:**
  - **Components affected:**
    - Site configuration API: Validates distinctness on PUT
    - Database: Stores two foreign keys to storage_location table
    - Inventory domain: Queries default locations when creating receiving transactions
  - **API contract:**
    ```json
    // PUT /api/v1/sites/{siteId}/default-locations
    {
      "defaultStagingLocationId": "stor-loc-001",
      "defaultQuarantineLocationId": "stor-loc-002"
    }
    
    // Error response if same
    {
      "code": "DEFAULT_LOCATION_ROLE_CONFLICT",
      "message": "Staging and quarantine default locations must be distinct",
      "field": "defaultQuarantineLocationId"
    }
    ```
  - **Validation logic:**
    ```javascript
    if (defaultStagingLocationId === defaultQuarantineLocationId) {
      return 400 BAD_REQUEST with DEFAULT_LOCATION_ROLE_CONFLICT
    }
    ```
  - **Database schema:**
    ```sql
    ALTER TABLE site ADD COLUMN default_staging_location_id UUID REFERENCES storage_location(id);
    ALTER TABLE site ADD COLUMN default_quarantine_location_id UUID REFERENCES storage_location(id);
    ALTER TABLE site ADD CONSTRAINT distinct_default_locations
    CHECK (
      default_staging_location_id IS NULL 
      OR default_quarantine_location_id IS NULL
      OR default_staging_location_id != default_quarantine_location_id
    );
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify no sites have the same storage location for both roles
  - **Query example:**
    ```sql
    SELECT site_id, default_staging_location_id, default_quarantine_location_id
    FROM site
    WHERE default_staging_location_id IS NOT NULL
      AND default_quarantine_location_id IS NOT NULL
      AND default_staging_location_id = default_quarantine_location_id;
    ```
  - **Expected outcome:** Zero records with duplicate locations
  - **Operational check:** Periodic audit reports for warehouse managers
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Create default staging and quarantine storage locations for existing sites without them
    2. Update site records with distinct default locations
    3. Deploy API validation
  - **Migration considerations:**
    - Sites may not have storage location topology defined yet; allow NULL during migration
    - Require explicit configuration before enabling receiving workflows
  - **Migration checklist per site:**
    1. Verify storage location topology exists
    2. Identify or create staging storage location
    3. Identify or create quarantine storage location (distinct from staging)
    4. Update site default location configuration
    5. Test receiving workflow with defaults
- **Governance & owner recommendations:**
  - **Owner:** Location domain team with coordination from Inventory domain
  - **Review cadence:** Review during site setup and annually
  - **Exception process:** No exceptions; sites requiring shared staging/quarantine must use workflow overrides (not defaults)

## End

End of document.
