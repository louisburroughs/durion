## Odoo Parity Plan — pos-customer (CRM)

> Status: **ACCEPTED** · Created 2026-07-28 · Accepted 2026-07-28 · Branch: `claude/odoo-crm-pos-customer-comparison-pc1ky6`
>
> Goal: bring the Durion CRM surface to functional parity with the Odoo 19 CRM capabilities that matter for an **enterprise tire-service management** platform — without
> importing the parts of Odoo's sales funnel that do not fit a service shop. Revenue is realized through the workorder flow (`pos-shop-manager` → `pos-workorder` →
> `pos-invoice`); this plan builds the **demand-generation and customer-relationship layer** that feeds it: marketing campaigns (differentiated commercial vs individual),
> segmentation, marketing-grade consent, interaction history, follow-up, and lightweight prospect capture.
>
> Sources: `spec-pos-customer-crm-gaps.md` (the accepted specification this plan executes), `comp-crm-overview.md`, `comp-vs-pos-customer-comparison.md`, code survey of
> `pos-customer` (2026-07-28), surrounding modules (`pos-price`, `pos-catalog`, `pos-workorder`, `pos-shop-manager`, `pos-people-contact`), CRM business rules
> (`domains/crm/.business-rules/`), platform ADRs.
>
> **Companion cross-domain issues (already created):** FI-1 `durion-positivity-backend#1134` (pricing eligibility), FI-2 `durion#369` (shared sender contract), FI-3
> `durion-positivity-backend#1133` (workorder→CRM facts), FI-4 `durion-positivity-backend#1135` (structured address). Several stories below depend on these.

---

## 0. Ground rules for the executing agent team

Non-negotiable constraints. Every story must be validated against them before merge.

1. **Module split (spec §0.3, ACCEPTED).** Two homes:
   - **[CRM] `pos-customer`** — party-native data campaigns _consume_: tags, segments, marketing consent, suppression, consent audit, interaction history, prospects,
     redemption attribution, service-history projection.
   - **[MKT] `pos-marketing`** — NEW bounded-context module owning campaign definition, audience binding, message templates, send orchestration, and campaign analytics.
     Standard `pos-{domain}` layout under `com.positivity.marketing`; own Postgres schema + Flyway baseline; **no compile-time dependency on pos-customer** — integration is
     gateway REST + `customer.events.v1` events only.
2. **ADR-0044 event-only domain walls.** Cross-service flows are Kafka events + local `ext_*` replicas, never new synchronous repository reads across modules. Reuse the
   pos-customer outbox (`event_outbox`/`OutboxPublisher`/`OutboxEventWriter`) + `ProcessedEvent`/`processing_log` idempotency rails. pos-marketing stands up its own equivalent
   outbox for `marketing.events.v1`.
3. **ADR-0013/0027**: UUID v7 IDs (`@UUIDv7Id`), UUID-typed identifiers in DTOs/services. **ADR-0024**: `createdAt`/`updatedAt` via Spring auditing with injected `Clock`.
   **ADR-0018**: actor fields from `SecurityContextHelper`, never request body. **ADR-0015**: person identity/contact points stay in `pos-people-contact`; pos-customer reads
   the `ext_people_contact_person` replica.
4. **ADR-0017/0042**: canonical status codes (incl. `202` for async command acceptance), `ApiError` envelope, full OpenAPI annotations. Any controller change ⇒ regenerate
   `openapi.yaml` ⇒ update the Angular SDK (per `durion/CLAUDE.md`).
5. **Module conventions** (`CLAUDE.md`/`AGENTS.md`): entities in `internal/entity`, public API behind `service/` interfaces (ArchUnit-enforced), `@EmitEvent` +
   `{Module}EventTypes` registry entry for every mutating endpoint, permissions in the code-first `{Module}PermissionRegistry` registered to `pos-security-service`,
   Spotless/Checkstyle/SpotBugs(High)/ArchUnit green, ArchUnit re-run after any package change.
6. **Flyway**: pos-customer migrations continue the V-series after the current max **V16** (start at **V17**); pos-marketing starts a fresh **V1** baseline. No cross-service
   FKs; enums stored as smallint ordinals with CHECK constraints per existing pattern.
7. **CRM domain decisions** (`AGENT_GUIDE.md`): `partyId` canonical (DECISION-INVENTORY-001); CRM owns contactability + contact roles + redemption records; CRM does **not**
   own workorder/invoice/payment lifecycle or promotion _policy_ (discounts stay in `pos-price`, product lists in `pos-catalog`, booking in `pos-shop-manager`).
8. **Positivity naming:** `workorder` is one word everywhere.

**Where Odoo is the reference vs where Durion already wins.** Adopt from Odoo: the two-tier consent model (per-channel opt-out + global address-level suppression),
campaign/UTM-style attribution, `is_company`-driven audience differentiation, saved segments (static list + dynamic filter), interaction history (chatter analog). Keep
Durion's approach (do NOT import): the opportunity pipeline (it lives in shop-manager + workorder), predictive deal scoring, reseller geo-assignment,
denormalized-lead-then-materialize funnel, and the separate lightweight `mailing.contact` table (identity is centralized in pos-people-contact — target parties directly).

---

## 1. Gap register (evidence-based)

| #   | Gap vs Odoo (spec ref)                           | Current state (evidence)                                                                            | Workstream                             |
| --- | ------------------------------------------------ | --------------------------------------------------------------------------------------------------- | -------------------------------------- |
| G1  | No campaign object of any kind                   | `campaign` grep-clean across all `pos-*`; only per-party `marketingPreference` flag                 | B, C (new `pos-marketing`)             |
| G2  | No mass email/SMS send                           | No send infra in pos-customer; no provider client                                                   | C (+ FI-2)                             |
| G3  | No audience segmentation                         | Only `AccountTier` (6 levels) + `AccountStatus`; no tags, lists, or filters                         | A                                      |
| G4  | No party tags                                    | `AccountTier`/`AccountStatus` only                                                                  | A                                      |
| G5  | Marketing consent is one coarse flag             | `CommunicationPreference.marketingPreference` (single String OPT_IN/OPT_OUT/NA); `consentFlags` map | A                                      |
| G6  | No hard suppression list                         | Preference only; no address-level block; no bounce/complaint sink                                   | A (+ FI-2 feed)                        |
| G7  | No consent-change audit                          | `consentFlags` snapshot + `updateSource`; no history entity                                         | A                                      |
| G8  | No interaction/touch history                     | `PartyNote` is one-way workorder projection, no write API, no author/type/thread                    | A                                      |
| G9  | No campaign attribution                          | `PromotionRedemption.promotionCode` present; no `campaignCode`                                      | A (col) + C (analytics)                |
| G10 | No service-history signal for segments/follow-up | Consumes only `ContactPreferenceUpdated` + `PartyNoteAdded`                                         | D (+ FI-3)                             |
| G11 | No follow-up/task object                         | None; declined-service and service-due have no home                                                 | D                                      |
| G12 | No prospect/inquiry capture                      | Parties created already-as-customers; `pos-inquiry` is supplier-scoped                              | D                                      |
| G13 | Structured address absent                        | `primaryAddress` is a free-text `String`                                                            | Prereq FI-4 (pos-people-contact)       |
| G14 | Audience-type offer eligibility unverified       | `pos-price` eligibility exists; audience/campaign predicate unconfirmed                             | Prereq FI-1 (pos-price)                |
| —   | Opportunity pipeline / PLS / reseller assignment | Absent                                                                                              | **Non-goal** (spec §10) — do not build |

---

## 2. Workstream A — pos-customer CRM data foundations **[CRM]**

Spec §3, §4, §8. No external dependencies; unblocks everything else. Migrations start at **V17**.

### Story A1 — Party tags (#1136)

- **Change**: `PartyTag` (`tagId`, `name` unique, `category` nullable, `color`, `active`) + `PartyTagAssignment` (`partyId`, `tagId`, `assignedBy`, `assignedAt`, `source` enum
  MANUAL/CAMPAIGN/IMPORT/RULE). Flat taxonomy for v1.
- **Files**: `internal/entity/PartyTag.java`, `PartyTagAssignment.java`; `internal/repository/*`; `service/PartyTagService.java` + `internal/service/PartyTagServiceImpl.java`;
  `internal/controller/CrmTagController.java` (`/v1/crm/tags` CRUD) + tag endpoints on `/v1/crm/parties/{partyId}/tags`; DTOs; `V17__party_tags.sql`.
- **Permissions**: `crm:tag:view`, `crm:tag:manage`, `crm:tag:assign` (add to `CrmPermissionRegistry` + `permissions.yaml`).
- **Events/@EmitEvent**: `CRM_TAG_CREATE/UPDATE/DELETE`, `CRM_PARTY_TAG_ASSIGN/REMOVE`; emit `party.tag.changed` on `customer.events.v1` via outbox.
- **AC**: tags CRUD; assign/remove idempotent; assignment emits audit + fact; ArchUnit green.
- **Effort**: M. **Deps**: none.

### Story A2 — Segment model (static + dynamic) (#1137)

- **Change**: `Segment` (`segmentId`, `name`, `description`, `audienceType` COMMERCIAL/INDIVIDUAL, `type` STATIC/DYNAMIC, `predicate` JSON for DYNAMIC, audit) +
  `SegmentMember` (`segmentId`, `partyId`, `contactId?`) for STATIC. Predicate is a **validated boolean tree** (`{attribute, operator, value}` + AND/OR/NOT) over a whitelisted
  attribute catalog — **not** free SQL.
- **Attribute catalog (v1, from data pos-customer already holds)**: party (`partyType`, `accountTier`, `accountStatus`, `parentPartyId present`, `externalIdentifier[system]`,
  tags), billing rules (`taxExempt`, `creditHold`, `paymentTerms`), consent (`marketing <channel> opted-in`), vehicle from `ext_vehicle` (`make/model`, `year range`,
  `has active vehicle`, `vehicle count ≥ N`). Service-history + geography attributes deferred to A-follow (needs FI-3/FI-4).
- **Files**: `internal/entity/Segment.java`, `SegmentMember.java`; `internal/domain/SegmentPredicate*.java` (predicate model + validator);
  `internal/service/SegmentResolutionService*` (query builder over local tables + replicas, paginated + capped); `service/SegmentService.java`;
  `internal/controller/CrmSegmentController.java` (`/v1/crm/segments` CRUD + `POST /{id}/resolve`); `V18__segments.sql`.
- **Permissions**: `crm:segment:view/manage/resolve`.
- **AC**: invalid predicate rejected at save (`422`); resolution deterministic, paginated, consent-filtered; a COMMERCIAL segment never returns person parties (and
  vice-versa); resolve returns count + masked sample.
- **Effort**: L. **Deps**: A1 (tag attribute).

### Story A3 — Marketing consent enrichment (#1138)

- **Change**: extend `CommunicationPreference` with per-channel marketing consent (`marketingEmailConsent`, `marketingSmsConsent` — OPT_IN/OPT_OUT/UNSET) distinct from
  operational `emailPreference`/`smsPreference`; add opt-out reason (`OptOutReason` catalog: NOT_INTERESTED, TOO_FREQUENT, NEVER_SIGNED_UP, LEGAL_DNC, …); optional
  quiet-hours/cadence fields. Add **`CommercialParty.accountMarketingOptOut`** (hard master gate — O-2).
- **Consent resolution rule (O-2)**: for a COMMERCIAL account, if `accountMarketingOptOut` is set → suppress the whole account; else the **primary business contact's**
  personal per-channel consent governs account-level sends. Individuals use personal consent directly. Encapsulate in a `MarketingConsentResolver`.
- **Files**: edit `internal/entity/CommunicationPreference.java`, `CommercialParty.java`; `internal/enums/OptOutReason.java`; `internal/service/MarketingConsentResolver*`;
  extend comm-pref upsert DTO/endpoints; `V19__marketing_consent.sql`.
- **Permissions**: reuse `crm:contact_preference:edit/view`; add `crm:consent:view/manage`.
- **AC**: consent resolver returns correct allow/deny per channel for both party types under all account-flag states; migration backfills `marketingPreference` → per-channel
  consent.
- **Effort**: M. **Deps**: none (A2 references the consent attribute; sequence A3 before A2 resolve-filtering hardens, but can proceed in parallel with a stub).

### Story A4 — Consent-change audit (`ConsentEvent`) (#1139)

- **Change**: append-only `ConsentEvent` (`partyId`, `channel`, `oldValue`, `newValue`, `reason`, `source`, `actor`, `at`). Written on every marketing-consent change.
  Compliance export endpoint.
- **Files**: `internal/entity/ConsentEvent.java`; repo; hook in `MarketingConsentResolver`/comm-pref service; `GET /v1/crm/parties/{partyId}/consent-history`;
  `V20__consent_event.sql`.
- **AC**: every consent change writes exactly one `ConsentEvent`; history queryable + exportable; append-only (no update/delete path).
- **Effort**: S. **Deps**: A3.

### Story A5 — Suppression list (`SuppressionEntry`) (#1140)

- **Change**: hard address-level block consulted by every send. `SuppressionEntry` (`suppressionId`, `channel`, `addressHash` normalized+hashed, `partyId?`, `reason`
  HARD_BOUNCE/SPAM_COMPLAINT/LEGAL_DNC/MANUAL/UNSUBSCRIBE_LINK, `source`, `createdAt`). Populated by manual admin action + unsubscribe-link + (later) provider bounce/complaint
  feed. Read API for the send pipeline.
- **Files**: `internal/entity/SuppressionEntry.java`; repo; `service/SuppressionService.java`; `internal/controller/CrmSuppressionController.java` (`/v1/crm/suppression`
  add/list/remove) + a suppression-check read used by pos-marketing (via gateway or `customer.events.v1` replica of suppression facts); `V21__suppression.sql`.
- **Permissions**: `crm:suppression:view/manage`.
- **Events**: emit `party.suppressed`/`party.unsuppressed` on `customer.events.v1` so pos-marketing can maintain a suppression replica for fast send-time checks.
- **AC**: suppression is honored independent of preference; address hashing consistent with lookup; manual add/remove audited.
- **Effort**: M. **Deps**: none.

### Story A6 — Interaction / touch history (`CustomerInteraction`) (#1141)

- **Change**: generalize the one-way `PartyNote` into `CustomerInteraction` (`interactionId`, `partyId`, `contactId?`, `type`
  CAMPAIGN_SEND/EMAIL/SMS/CALL/FOLLOW_UP/NOTE/WORKORDER_NOTE, `channel?`, `direction` OUTBOUND/INBOUND, `campaignId?`, `subject/summary`, `body?` redaction-aware per
  DECISION-INVENTORY-006, `actor`, `occurredAt`, `sourceEventId`). Written by: campaign-send facts (from `marketing.events.v1`), CSR follow-up actions (WS-D), and the existing
  workorder `PartyNoteAdded` projection.
- **Files**: `internal/entity/CustomerInteraction.java`; repo; `service/CustomerInteractionService.java`; migrate `WorkorderEventHandler` `PartyNoteAdded` path to write
  `CustomerInteraction` (keep `party_note` or fold in); `GET /v1/crm/parties/{partyId}/interactions` (paged, filterable); consume `marketing.events.v1` campaign-send facts
  into interactions; `V22__customer_interaction.sql`.
- **Permissions**: `crm:interaction:view`.
- **AC**: campaign sends appear in party history within event SLA; existing PartyNote projection preserved; redaction applied to `body`.
- **Effort**: M. **Deps**: A-tier; the `marketing.events.v1` consumer half depends on WS-C (can land as a follow-up).

### Story A7 — Redemption attribution column (#1142)

- **Change**: add `campaignCode` to `PromotionRedemption` (already has `promotionCode`); populate from the redemption-recording path when a campaign code is present; emit on
  the redemption fact for pos-marketing attribution.
- **Files**: edit `internal/entity/PromotionRedemption.java`, `RecordRedemptionRequest`, mapper, `PromotionRedemptionServiceImpl`; `V23__redemption_campaign_code.sql`.
- **AC**: redemptions carry `campaignCode`; existing idempotency (dup → 409) unchanged; attribution fact emitted.
- **Effort**: S. **Deps**: none.

### Story A8 — Snapshot integration (#1143)

- **Change**: extend `CrmSnapshotDTO` with a consent summary (per-channel marketing consent + suppression flags) and a recent-interaction summary.
- **Files**: edit `dto/snapshot/CrmSnapshotDTO.java`, snapshot builder in `PartyServiceImpl`/`CrmVehicleServiceImpl`; snapshot controller unchanged.
- **AC**: snapshot exposes contactability + touch summary without raw PII beyond policy; existing consumers unaffected.
- **Effort**: S. **Deps**: A3, A5, A6.

### Story A-follow — Service-history & geography segment attributes (deferred) (#1144)

- **Change**: once FI-3 (service-history feed) and FI-4 (structured address) land, add the `last service > N months`, `service-due`, `declined service in last N days`, and
  region/geo attributes to the A2 predicate catalog + resolver.
- **AC**: new attributes validate and resolve; documented as available.
- **Effort**: M. **Deps**: A2, **FI-3** (`#1133`), **FI-4** (`#1135`).

---

## 3. Workstream B — pos-marketing module + campaign core **[MKT]**

Spec §0.3, §1, §2.1. Stand up the new module, then campaign definition/lifecycle and templates. `marketing.events.v1` + fresh Flyway baseline.

### Story B0 — Module bootstrap (#1145)

- **Change**: create `pos-marketing` Maven module under the reactor: `PosMarketingApplication` (root of `com.positivity.marketing`),
  `internal/{controller,service,repository,entity,dto,config,client,event}`, `service/` public interfaces, `MarketingEventTypes` + `MarketingEventTypeInitializer`,
  `MarketingPermissionRegistry`, `MarketingApplicationConfig` (security filter, DB, Kafka), `ArchitectureTest`, `application.yml` (`server.port: 0`, Eureka), gateway route
  `marketing/vN`, Docker/pom wiring, `V1__baseline_marketing_schema.sql` (empty baseline + outbox tables mirroring pos-customer's `event_outbox`/`processed_events`).
- **Files**: new module tree; add to root `pom.xml` `<modules>`; `pos-api-gateway` route config.
- **AC**: module builds, registers with Eureka, health endpoint green, ArchUnit passes, permission/event initializers run without blocking startup.
- **Effort**: M. **Deps**: none (parallel with WS-A).

### Story B1 — Campaign entity + lifecycle (#1146)

- **Change**: `Campaign` (`campaignId`, `code` unique, `name`, `description`, `audienceType` immutable, `campaignProgramId?`, `status`, `channels` Set<EMAIL,SMS>, `segmentId`,
  `promotionOfferId?`, `catalogFocusRef?`, `windowStart/End`, `scheduleType`, `scheduledAt?`, template refs, audit). Lifecycle DRAFT→SCHEDULED→SENDING→SENT/CLOSED with
  PAUSED/CANCELLED; transitions are command endpoints.
- **Files**: `internal/entity/Campaign.java`, `internal/enums/{AudienceType,CampaignStatus,CampaignChannel,ScheduleType}.java`; repo; `service/CampaignService.java` + impl
  (state machine + guards); `internal/controller/CampaignController.java` (`/v1/marketing/campaigns` create/get/list/update/schedule/pause/resume/cancel); DTOs;
  `V2__campaign.sql`.
- **Permissions**: `marketing:campaign:view/create/edit/schedule/send/manage`.
- **@EmitEvent/facts**: `MARKETING_CAMPAIGN_CREATE/UPDATE/SCHEDULE/...`; emit `campaign.scheduled` on `marketing.events.v1`.
- **AC**: `audienceType` immutable after create; cannot SCHEDULE without a resolvable matching-audience segment + present templates + (if referenced) an ACTIVE offer; illegal
  transitions rejected.
- **Effort**: L. **Deps**: B0; segment validation reads pos-customer segment via gateway (A2).

### Story B2 — Message templates (#1147)

- **Change**: `MessageTemplate` per channel with token substitution; token catalog validated against `audienceType` (commercial `{{accountName}}` vs individual
  `{{vehicle...}}`).
- **Files**: `internal/entity/MessageTemplate.java`; repo; `service/MessageTemplateService.java`; `internal/service/TemplateRenderService*` (token substitution, safe
  rendering); `/v1/marketing/templates` CRUD; `V3__message_template.sql`.
- **Permissions**: `marketing:template:view/manage`.
- **AC**: unknown/invalid tokens rejected at save; render produces channel-appropriate output; SMS length/segment guard.
- **Effort**: M. **Deps**: B0.

### Story B3 — Audience binding + preview + offer/catalog validation (#1148)

- **Change**: bind a campaign to a segment; `POST /{id}/audience-preview` resolves the segment (via pos-customer gateway REST or a segment replica) and returns per-channel
  recipient counts **after** consent + suppression filtering, with a masked sample. Validate `promotionOfferId` is ACTIVE at schedule time (read `pos-price`);
  `catalogFocusRef` resolvable (read `pos-catalog`).
- **Files**: `internal/client/{CustomerClient,PriceClient,CatalogClient}.java` (load-balanced RestClients); `internal/service/AudienceResolutionService*`; preview DTOs.
- **AC**: preview counts reflect consent/suppression; COMMERCIAL resolves to account contacts by role, INDIVIDUAL to the person's primary contact; offer/catalog refs
  validated; no raw PII beyond masked sample.
- **Effort**: L. **Deps**: B1, A2/A3/A5; **FI-1** (`#1134`) for audience-specific offer eligibility (soft dep — preview works without it).

---

## 4. Workstream C — Send orchestration + attribution **[MKT]**

Spec §2.2, §8. Async batched send through the **shared platform sender** (O-1); attribution from redemptions.

### Story C1 — CampaignSend model + async dispatch (#1149)

- **Change**: `CampaignSend` (`campaignId`, `recipientPartyId`, `contactId`, `channel`, `resolvedAddress` transient/hashed, `status`, `providerMessageId`, `failureReason`,
  timestamps). Async batched dispatch (outbox/worker), rate-limited, **re-checks consent + suppression at send time**. Idempotent per (campaignId, recipientId, channel).
  `POST /{id}/send` → `202`.
- **Files**: `internal/entity/CampaignSend.java`; repo; `internal/service/CampaignSendOrchestrator*` + worker; `internal/config/…` scheduling; DTOs.
- **@EmitEvent/facts**: `MARKETING_CAMPAIGN_SEND`; emit `campaign.sent`.
- **AC**: no send to a suppressed/opted-out recipient at dispatch time; every send carries the campaign `code`; re-invoking `/send` never double-sends; provider failures
  retried with backoff, permanent → `FAILED` without blocking the batch.
- **Effort**: L. **Deps**: B3, A5.

### Story C2 — Shared sender adapter + outcome feedback (#1150)

- **Change**: `MessageChannel` interface + adapter to the shared platform sender (per FI-2 contract). Ingest delivery/bounce/complaint outcomes (event or callback) → update
  `CampaignSend` + push bounce/complaint into CRM suppression (emit to `customer.commands.v1`/suppression API). Emit `campaign.send.delivered/bounced/complained`.
- **Files**: `internal/client/PlatformSenderClient.java`; `internal/service/DeliveryOutcomeListener*`; suppression feedback path.
- **AC**: outcomes update send state; bounces/complaints reach CRM suppression (A5); open/click tracked when the sender supports it, degrade gracefully otherwise.
- **Effort**: L. **Deps**: C1, **FI-2** (`durion#369`), A5.

### Story C3 — Interaction logging of sends (CRM side) (#1151)

- **Change**: pos-customer consumes `campaign.sent`/send facts from `marketing.events.v1` → writes `CAMPAIGN_SEND` `CustomerInteraction` rows (closes A6's marketing half).
- **Files**: `internal/service/MarketingEventsListener` (pos-customer), idempotent via `processed_events`.
- **AC**: each campaign send appears in the recipient's interaction history within SLA; idempotent.
- **Effort**: S. **Deps**: A6, C1.

### Story C4 — Campaign stats + attribution (#1152)

- **Change**: `GET /campaigns/{id}/stats` — reach funnel per channel (targeted → eligible → sent → delivered → opened/clicked → redeemed → attributed workorders/revenue),
  split by `audienceType`, with a `campaignProgramId` rollup comparing commercial vs individual arms. Attribution: consume redemption facts (with `campaignCode`, A7) →
  conversion counters, idempotent by `redemptionId`.
- **Files**: `internal/service/CampaignStatsService*`; `internal/service/RedemptionAttributionListener*` (consume `customer.events.v1` redemption facts); stats DTOs.
- **Permissions**: `marketing:stats:view`.
- **AC**: a campaign-tied redemption is attributed to exactly that campaign; no double counting (idempotent by `redemptionId`); commercial vs individual arms separately
  measurable.
- **Effort**: M. **Deps**: C1, A7.

---

## 5. Workstream D — Follow-up tasks & prospect capture **[CRM]**

Spec §5, §6. Depends on cross-domain facts (FI-3) and is lower priority than A–C.

### Story D1 — FollowUpTask (#1153)

- **Change**: `FollowUpTask` (`taskId`, `partyId`, `vehicleId?`, `type` DECLINED_SERVICE_FOLLOWUP/SERVICE_DUE_REMINDER/FLEET_CHECKIN/CAMPAIGN_RESPONSE/GENERAL, `dueDate`,
  `assignedTo?`, `status` OPEN/DONE/DISMISSED, `sourceWorkorderId?`, `reason?`, `outcome?`, `notes`). Created from workorder `serviceLine.declined` facts (one per declined
  line, idempotent) and from `VehicleCarePreference` service-due. Completing a task can deep-link to `pos-shop-manager` appointment creation (hand-off, never books).
- **Files**: `internal/entity/FollowUpTask.java`; repo; `service/FollowUpTaskService.java`; consume declined-service facts in a listener (idempotent via `processing_log`);
  `/v1/crm/parties/{partyId}/follow-ups` + CSR queue `/v1/crm/follow-ups?assignedTo=&status=`; `V24__follow_up_task.sql`.
- **Permissions**: `crm:followup:view/manage`.
- **AC**: a declined-service event yields exactly one task; completion records outcome and links the resulting appointment/workorder when booked.
- **Effort**: M. **Deps**: **FI-3** (`#1133`) for the declined-service + care-preference feed.

### Story D2 — Prospect lifecycle + Inquiry capture (#1154)

- **Change**: add `lifecycleStage` (PROSPECT → ACTIVE → DORMANT) to the party; `Inquiry` entity in **pos-customer** (`inquiryId`, `channel`, `audienceType`, captured contact
  fields, `vehicleOfInterest?`, `serviceOfInterest?`, `campaignCode?`, `status` NEW→CONTACTED→CONVERTED→CLOSED, `partyId?`, `assignedTo?`). Public inbound "request service /
  fleet quote" endpoint (rate-limited, captcha, no auth) fronted through the gateway. Conversion reuses `checkPartyDuplicates` + create-party, optional shop-manager hand-off.
  **Not** in `pos-inquiry` (supplier-scoped).
- **Files**: edit `AbstractParty`/status handling for `lifecycleStage`; `internal/entity/Inquiry.java`; repo; `service/InquiryService.java`;
  `internal/controller/{InquiryController, PublicInquiryController}.java`; `V25__prospect_inquiry.sql`.
- **Permissions**: `crm:inquiry:view/manage`; public capture endpoint unauthenticated but rate-limited.
- **AC**: prospect parties excluded from active-customer default queries; inquiry converts to a party without duplicating scheduling; campaign attribution captured.
- **Effort**: L. **Deps**: none hard (attribution ties to A7); ship after A–C.

---

## 6. Workstream E — Cross-domain prerequisites (tracked issues)

These are owned outside this plan's core modules but gate specific stories. Already filed:

| Issue                                                                                             | Enables                                                     | Gating story                 |
| ------------------------------------------------------------------------------------------------- | ----------------------------------------------------------- | ---------------------------- |
| **FI-1** `durion-positivity-backend#1134` — pos-price audience/campaign eligibility               | audience-specific offers                                    | B3 (soft), full offer gating |
| **FI-2** `durion#369` — shared sender contract + outcome feedback                                 | actual email/SMS send + bounce/complaint suppression        | C2 (hard)                    |
| **FI-3** `durion-positivity-backend#1133` — workorder service-completion + declined-service facts | service-history segments, service-due + declined follow-ups | A-follow, D1 (hard)          |
| **FI-4** `durion-positivity-backend#1135` — structured address in pos-people-contact              | region/geo segmentation                                     | A-follow (hard)              |

---

## 7. Sequencing (waves for the agent team)

- **Wave 1 — CRM foundations [CRM] (no external deps):** A1 tags, A3 consent enrichment, A5 suppression, A4 consent audit, A7 redemption `campaignCode`, then A2 segments, A6
  interaction history, A8 snapshot. Ships standalone customer value; unblocks everything.
- **Wave 2 — Campaign core [MKT]:** B0 module bootstrap (parallelizable with Wave 1), then B1 campaign, B2 templates, B3 audience binding + preview. Depends on Wave 1
  (segments/consent/suppression) + FI-1 (soft).
- **Wave 3 — Send + attribution [MKT]:** C1 send model, C2 sender adapter (needs FI-2), C3 interaction logging, C4 stats + attribution. Depends on Wave 2 + FI-2.
- **Wave 4 — Follow-up & prospects [CRM]:** D1 follow-up tasks (needs FI-3), D2 prospect/inquiry; plus A-follow segment attributes (needs FI-3/FI-4).
- **Backlog (spec §7, §10):** service-due/churn signal, A/B testing, double opt-in, account ownership (§6.2), open/click deep analytics.

Critical path: Wave 1 (A2/A3/A5) → B3 → C1 → C2(FI-2) → C4. FI-2 is the main external blocker for actual sending; Waves 1–2 and campaign definition/preview proceed without it.

---

## 8. Decisions (resolved 2026-07-28; supersede the spec's open questions)

| ID  | Decision                                                                                                                                                                                                    |
| --- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| O-1 | Send via the **shared platform sender**; pos-marketing owns orchestration only; sender owns transport + bounce/complaint webhooks and relays outcomes back (FI-2).                                          |
| O-2 | Commercial consent = `CommercialParty.accountMarketingOptOut` **hard master gate** + **primary business contact's** personal per-channel consent for account-level sends; individuals use personal consent. |
| O-3 | `pos-price` eligibility _can_ take audience/campaign input; confirm/implement via FI-1.                                                                                                                     |
| O-4 | Workorder **will emit** service-completion facts to pos-customer (FI-3).                                                                                                                                    |
| O-5 | Structured address built in **pos-people-contact** for **persons + organizations** (FI-4); pos-customer reads via replica.                                                                                  |
| O-6 | Prospect/inquiry capture lives in **pos-customer**, not `pos-inquiry` (supplier-scoped).                                                                                                                    |
| O-7 | Workorder **will emit** `serviceLine.declined` facts (FI-3, combined with O-4).                                                                                                                             |
| O-8 | Campaigns live in a **new `pos-marketing` module**.                                                                                                                                                         |

**Non-goals (do not build):** opportunity pipeline/kanban/probability/forecast, recurring-revenue/MRR on a lead, predictive (Naive-Bayes) lead scoring, reseller
geo-assignment, rule-based lead round-robin, lead mining/IAP enrichment, A/B testing (v1), full UTM URL/cookie web attribution, double opt-in portal (v1), a separate
lightweight `mailing.contact` table.

---

## 9. Issue tracking

- **Cross-domain prerequisites (created):** FI-1 `durion-positivity-backend#1134`, FI-2 `durion#369`, FI-3 `durion-positivity-backend#1133`, FI-4
  `durion-positivity-backend#1135`.
- **Implementation stories (created 2026-07-28, all in `durion-positivity-backend`):**

  | Story                              | Issue | Story                         | Issue |
  | ---------------------------------- | ----- | ----------------------------- | ----- |
  | A1 Party tags                      | #1136 | B0 pos-marketing bootstrap    | #1145 |
  | A2 Segment model                   | #1137 | B1 Campaign + lifecycle       | #1146 |
  | A3 Marketing consent enrichment    | #1138 | B2 Message templates          | #1147 |
  | A4 Consent-change audit            | #1139 | B3 Audience binding + preview | #1148 |
  | A5 Suppression list                | #1140 | C1 CampaignSend + dispatch    | #1149 |
  | A6 Interaction history             | #1141 | C2 Shared sender adapter      | #1150 |
  | A7 Redemption `campaignCode`       | #1142 | C3 Log sends to interactions  | #1151 |
  | A8 Snapshot integration            | #1143 | C4 Stats + attribution        | #1152 |
  | A-follow Service-history/geo attrs | #1144 | D1 FollowUpTask               | #1153 |
  |                                    |       | D2 Prospect + Inquiry         | #1154 |

- **Module addition:** `pos-marketing` (B0, #1145) requires a reactor `pom.xml` change + gateway route + CI wiring — call out in the PR description.

---

## 10. Acceptance for the plan as a whole

The plan is "done" when, per wave:

1. **Wave 1**: a CSR can tag parties, define static/dynamic segments over party/tier/tag/vehicle data, manage per-channel marketing consent with a commercial master gate, add
   suppression entries, and view a party's interaction history + consent audit — all behind `crm:*` permissions with events on `customer.events.v1`.
2. **Wave 2**: a marketer can create a commercial or individual campaign, bind it to a matching-audience segment, attach templates, and preview a consent/suppression-filtered
   audience — in the new `pos-marketing` module.
3. **Wave 3**: a scheduled campaign sends through the shared sender, honors consent/suppression at dispatch, records per-recipient outcomes, logs sends to CRM interaction
   history, and attributes workorder redemptions back to the campaign — split by commercial vs individual.
4. **Wave 4**: declined-service and service-due generate follow-up tasks that hand off to booking; new prospects/inquiries onboard into parties with campaign attribution.
