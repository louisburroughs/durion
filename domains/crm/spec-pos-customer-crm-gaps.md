# Specification — CRM Functionality Missing from pos-customer

> Status: DRAFT · Created 2026-07-28 · Companion docs: `comp-crm-overview.md` (Odoo CRM reference), `comp-vs-pos-customer-comparison.md` (capability map).
>
> Purpose: define, in enough detail to drive a subsequent implementation plan, the CRM capabilities that `pos-customer` (and the platform around it) is missing versus Odoo CRM — **scoped to what an enterprise tire-service management system actually needs.** This is a specification of *what to build and why*, not a task-by-task plan. The plan (`plan-odoo-parity-pos-customer.md`) will be derived from this.
>
> Sources: code survey of `pos-customer` (2026-07-28), Odoo 19 CRM survey (`comp-crm-overview.md`), CRM business-rules pack (`domains/crm/.business-rules/AGENT_GUIDE.md`, `DOMAIN_NOTES.md`, `CROSS_DOMAIN_INTEGRATION_CONTRACTS.md`), platform ADRs, and the surrounding modules (`pos-price`, `pos-catalog`, `pos-workorder`, `pos-shop-manager`, `pos-people-contact`, `pos-inquiry`).

---

## 0. Scope, framing, and the module-placement decision

### 0.1 Framing — Positivity's "pipeline" is not Odoo's

Odoo CRM optimizes the **pre-sale funnel**: capture strangers → qualify opportunities → route to salespeople → forecast → market. Positivity is a **service-execution** platform: revenue is realized by executing workorders on vehicles. The real "pipeline" is:

```
inquiry/appointment  →  estimate  →  workorder (WIP)  →  invoice
   (pos-shop-manager)   (pos-workorder) (pos-workorder)  (pos-invoice)
```

That pipeline already exists and is owned by `pos-shop-manager` (appointments, scheduling, operational context) and `pos-workorder` (estimate → WIP → complete → invoice, promotion application). **Therefore this spec deliberately does not add an opportunity/kanban/probability/forecast pipeline to pos-customer** (see §10 Non-Goals). What Positivity genuinely lacks, and what this spec covers, is the **demand-generation and customer-relationship layer that feeds that pipeline**: marketing campaigns, audience segmentation, marketing-grade consent, interaction history, follow-up, and lightweight prospect capture.

### 0.2 The five capability groups worth building

| # | Capability | Why Positivity needs it | Owning surface (see §0.3) |
|---|---|---|---|
| A | **Marketing campaigns** differentiated by **commercial vs individual** audience | Drive repeat service (oil-change reminders to individuals; fleet tire programs to commercial accounts) | new `pos-marketing` |
| B | **Segmentation, tags, and audiences** over party/vehicle/service data | Target the right customers; power A | `pos-customer` (data) + `pos-marketing` (audience binding) |
| C | **Marketing consent, suppression, and interaction history** | Legal defensibility (CAN-SPAM/TCPA), don't spam, know what we sent | `pos-customer` |
| D | **Follow-up / declined-service tasks** | Convert declined recommendations and service-due into bookings | `pos-customer` (hand off to `pos-shop-manager`) |
| E | **Prospect/inquiry capture** (adapted lead capture) | Onboard new fleet accounts and web inquiries | `pos-inquiry` or `pos-customer` |

### 0.3 DECISION (ACCEPTED) — a new `pos-marketing` module owns campaigns

**Decision:** campaign functionality lives in a **new `pos-marketing` bounded-context module**, not inside pos-customer. `pos-marketing` owns campaign definition, audience binding, send orchestration, message templates, and campaign analytics. `pos-customer` remains the customer master and owns the CRM-native data campaigns *consume* — segments, tags, marketing consent, suppression, interaction history, and prospects.

> Status: **ACCEPTED** (2026-07-28). This resolves open question O-8 and is binding on the plan derived from this spec. Every item below tagged **[MKT]** is built in `pos-marketing`; every **[CRM]** item is built in `pos-customer` (or `pos-inquiry` where noted).

pos-customer already owns the party master, communication preferences, and promotion-redemption ledger — the *data* campaigns target and attribute against. But full campaign orchestration (audience resolution, scheduled multi-channel sends, delivery tracking, per-recipient state) is a distinct lifecycle with its own heavy dependency — **outbound message delivery infrastructure (email/SMS), which pos-customer does not have** — and belongs in its own module.

Rationale:

- Respects ADR-0026 / ArchUnit domain walls: pos-customer stays a party registry; the marketing send-lifecycle is its own bounded context with its own schema and Flyway baseline.
- Isolates the delivery-infrastructure dependency (SMTP/SMS provider) in the new module instead of bolting it onto the customer master.
- pos-marketing reads party/segment/consent data via the existing `CrmSnapshotDTO` + `customer.events.v1` replica pattern (ADR-0044), exactly like every other consumer — no synchronous reach into pos-customer's tables.
- Keeps consent/suppression enforcement authoritative in pos-customer (the system of record for party contactability); pos-marketing *asks before sending* and never overrides it.

**Considered and rejected:** a thin campaign model embedded in pos-customer that delegates only raw message delivery. Rejected because it couples an unbounded send-lifecycle (per-recipient state, provider webhooks, retry/backoff, delivery analytics) to the customer master, blurs the domain wall, and would have to be extracted later once open/click tracking and A/B testing arrive. The `[CRM]`/`[MKT]` tags below reflect the accepted split, not an either/or.

**New module bootstrap (`pos-marketing`):** standard `pos-{domain}` layout under `com.positivity.marketing` — `PosMarketingApplication` at root, `service/` public interfaces, `internal/{controller,service,repository,entity,dto,config,client,event}`, `MarketingEventTypes` + `MarketingEventTypeInitializer`, `MarketingPermissionRegistry`, own Postgres schema + Flyway baseline (`V1__baseline_marketing_schema.sql`), ArchUnit `ArchitectureTest`, `server.port: 0` + Eureka registration, gateway route `marketing/vN`. Depends on `pos-events`, `pos-shared-dtos`, `pos-domain-events`, `pos-security-common`; **no** compile-time dependency on pos-customer (integration is REST via gateway + `customer.events.v1` events only).

### 0.4 Platform ground rules every story must honor

1. **ADR-0044 event-only domain walls.** Cross-service data flows via Kafka events + local `ext_*` replicas, not new synchronous repository reads. pos-marketing consumes `customer.events.v1` (party/consent/segment facts); attribution flows back via `customer.events.v1` redemption/workorder facts. Reuse the outbox (`event_outbox`/`OutboxPublisher`) + `ProcessedEvent` idempotency rails already in pos-customer.
2. **ADR-0013/0027** UUID v7 IDs (`@UUIDv7Id`), UUID-typed identifiers in DTOs. **ADR-0024** `createdAt`/`updatedAt` via Spring auditing with injected `Clock`. **ADR-0018** actor fields from `SecurityContextHelper`, never request body.
3. **ADR-0017/0042** canonical status codes (incl. `202` for async command acceptance), `ApiError` envelope, full OpenAPI annotations. Controller change ⇒ regenerate `openapi.yaml` ⇒ update Angular SDK.
4. **Module conventions** (`CLAUDE.md` / `AGENTS.md`): entities in `internal/entity`, public API behind `service/` interfaces (ArchUnit-enforced), `@EmitEvent` + `{Module}EventTypes` registry entry for every mutating endpoint, permissions in the `{Module}PermissionRegistry` (code-first) registered to `pos-security-service`, Flyway migrations, Spotless/Checkstyle/SpotBugs/ArchUnit green.
5. **CRM domain decisions** (`AGENT_GUIDE.md`): `partyId` is canonical (DECISION-INVENTORY-001); CRM owns contact roles + contactability + redemption records; CRM does **not** own workorder/invoice/payment lifecycle or promotion *policy* enforcement.
6. **Positivity naming:** `workorder` is one word everywhere.

### 0.5 Cross-service ownership guardrails (do not violate)

- **Discounts/offers stay in `pos-price`** (`PromotionOffer`, eligibility rules, `applyPromotion`). Campaigns *reference* an offer id; they never define discount math.
- **Product lists stay in `pos-catalog`** (product master, price books). A campaign that promotes "winter tires" references a catalog product/collection id; it never copies product data.
- **Booking/scheduling stays in `pos-shop-manager`.** Follow-up tasks *hand off* to appointment creation; they don't schedule.
- **Person identity/contact points stay in `pos-people-contact`.** Recipient email/phone are resolved from the `ext_people_contact_person` replica, not stored anew.
- **Redemption recording stays in pos-customer** (`PromotionRedemption`) — it is the attribution sink campaigns plug into.

---

## 1. Capability A — Marketing Campaigns (commercial vs individual)  **[MKT]**

The headline capability. A **Campaign** is a planned, audience-targeted, multi-channel outreach that promotes a service/product offer over a time window, measured by reach and by attributed workorder redemptions.

### 1.1 Core requirement: audience-type differentiation

Every campaign declares an **`audienceType`** that fundamentally shapes it:

- **`COMMERCIAL`** — targets `CommercialParty` accounts (fleets, dealers, municipalities). Messaging is B2B: account-level, routed to account **contacts** by role (e.g. `PRIMARY_BUSINESS_CONTACT`, `OPERATIONS`, `BILLING`), volume/contract framing, PO-aware. Offers tend to be fleet/volume programs.
- **`INDIVIDUAL`** — targets `PersonParty` customers. Messaging is B2C: personal, vehicle-specific (oil change due, tire rotation, seasonal), single-recipient.

This is not merely a filter — it changes recipient resolution (account-contact fan-out vs single person), consent semantics (a commercial account's operations contact vs an individual's personal opt-in), offer eligibility, and reporting. A campaign is **exactly one** audience type; "both" is modeled as two campaigns (optionally grouped by a shared `campaignProgramId`).

> Rationale for hard split (user requirement): commercial and individual outreach differ in legal basis (business vs personal consent), channel norms, cadence, and success metric (contract renewal vs repeat visit). Conflating them produces mis-targeted, non-compliant sends.

### 1.2 Entity — `Campaign` **[MKT]**

| Field | Type | Notes |
|---|---|---|
| `campaignId` | UUID v7 | PK |
| `code` | String, unique | Human/attribution code (e.g. `WINTER-FLEET-2026`); carried onto redemptions/workorders for attribution (§8) |
| `name`, `description` | String | |
| `audienceType` | enum `COMMERCIAL` \| `INDIVIDUAL` | §1.1; immutable after creation |
| `campaignProgramId` | UUID, nullable | Optional grouping of a commercial+individual pair |
| `status` | enum (§1.3) | lifecycle |
| `channels` | Set<enum EMAIL, SMS> | which channels this campaign sends on |
| `segmentId` | UUID → `[CRM]` Segment (§3) | audience definition |
| `promotionOfferId` | UUID, nullable | reference to `pos-price` offer (§1.5) |
| `catalogFocusRef` | String/UUID, nullable | reference to `pos-catalog` product/collection the campaign promotes |
| `windowStart`, `windowEnd` | Instant | active window |
| `scheduleType` | enum `IMMEDIATE` \| `SCHEDULED` | |
| `scheduledAt` | Instant, nullable | |
| `messageTemplateIds` | per channel | §2 |
| audit | `createdAt/updatedAt/createdBy` | ADR-0024/0018 |

### 1.3 Lifecycle (state machine)

```
DRAFT → SCHEDULED → SENDING → SENT → (CLOSED)
   └──────────────→ CANCELLED
DRAFT → (edit freely)   SCHEDULED/SENDING → PAUSED → SENDING
```

- **DRAFT**: fully editable; audience preview allowed.
- **SCHEDULED**: validated (segment resolvable, offer active, templates present, consent gate reviewed); frozen except cancel/pause.
- **SENDING**: batch dispatch in progress (async, `202`).
- **SENT / CLOSED**: dispatch complete; window may still be open for attribution.
- **PAUSED / CANCELLED**: operator control; cancelled campaigns retain audit + partial stats.

Transitions are `@EmitEvent` command endpoints; SENDING/SENT emit facts on `marketing.events.v1`.

### 1.4 Endpoints **[MKT]** (`/v1/marketing/campaigns`)

| Method | Path | Purpose | Permission | @EmitEvent |
|---|---|---|---|---|
| POST | `/` | Create campaign (DRAFT) | `marketing:campaign:create` | `MARKETING_CAMPAIGN_CREATE` |
| GET | `/{id}` | Get campaign | `marketing:campaign:view` | — |
| GET | `/` | List/filter (audienceType, status, window) | `marketing:campaign:view` | — |
| PUT | `/{id}` | Update DRAFT | `marketing:campaign:edit` | `MARKETING_CAMPAIGN_UPDATE` |
| POST | `/{id}/audience-preview` | Resolve segment → recipient count + sample, post-consent-filter | `marketing:campaign:view` | `MARKETING_CAMPAIGN_AUDIENCE_PREVIEW` |
| POST | `/{id}/schedule` | DRAFT→SCHEDULED | `marketing:campaign:schedule` | `MARKETING_CAMPAIGN_SCHEDULE` |
| POST | `/{id}/send` | Trigger send (→202) | `marketing:campaign:send` | `MARKETING_CAMPAIGN_SEND` |
| POST | `/{id}/pause` · `/resume` · `/cancel` | Control | `marketing:campaign:manage` | respective |
| GET | `/{id}/stats` | Reach/redemption/attribution (§8) | `marketing:campaign:view` | — |

### 1.5 Offer & catalog linkage

- `promotionOfferId` references a `pos-price` `PromotionOffer` (validate it exists + is ACTIVE at schedule time via a load-balanced read or a replicated offer fact). The campaign never defines discount type/value.
- If audience-type-specific eligibility is required (e.g. a fleet-only offer), coordinate with `pos-price` so its eligibility rules can accept an `audienceType`/`partyType` or `campaignCode` input. **Open question O-3.**
- `catalogFocusRef` references a `pos-catalog` product or collection for content assembly (e.g. campaign body lists the promoted tire lines). Read-only reference.

### 1.6 Acceptance criteria (representative)

- A campaign cannot be SCHEDULED without a resolvable segment whose `audienceType` matches the campaign's.
- Audience preview returns counts **after** consent/suppression filtering (§4), split by channel, and never exposes raw PII beyond a masked sample.
- A COMMERCIAL campaign resolves recipients as account contacts by role; an INDIVIDUAL campaign resolves to the person's own primary contact point.
- Sending is idempotent per (campaignId, recipientId, channel); re-invoking `/send` never double-sends.

---

## 2. Capability A' — Message templates & multi-channel send  **[MKT]**

### 2.1 Templates — `MessageTemplate`

Per-channel content with token substitution (`{{firstName}}`, `{{vehicleYearMakeModel}}`, `{{promoCode}}`, `{{shopName}}`, `{{offerDetails}}`). Email: subject + HTML/text body; SMS: 1-3 segment text with link. Templates validate available tokens against the campaign's `audienceType` (commercial tokens like `{{accountName}}` vs individual `{{vehicle...}}`).

### 2.2 Send orchestration

- Audience resolution → per-recipient **`CampaignSend`** rows (`campaignId`, `recipientPartyId`, `contactId`, `channel`, `resolvedAddress` [transient/hashed], `status`, `providerMessageId`, `failureReason`, timestamps).
- Dispatch is **async & batched** (reuse the outbox/worker pattern), rate-limited, and **re-checks consent + suppression at send time** (not just at preview — consent can change between schedule and send).
- **Channel delivery is provider-backed.** Decision O-1: use an email/SMS provider client (SES/SendGrid/Twilio-style) behind a `MessageChannel` interface, or route through an existing platform sender. This module owns the *orchestration*; the raw transport may be a thin adapter. Bounces/complaints feed the suppression list (§4.3).
- Provider webhooks (delivered/bounced/complained/opened/clicked) update `CampaignSend` and feed §8 stats. Open/click tracking is provider-dependent — degrade gracefully if unavailable (v1 may track only sent/delivered/bounced + redemption).

### 2.3 Acceptance criteria

- No `CampaignSend` is dispatched to a recipient who is suppressed or opted-out for that channel/audience type at dispatch time.
- Every send carries the campaign `code` so downstream redemption/workorder can attribute it (§8).
- Provider failures are retried with backoff; permanent failures mark the send `FAILED` with reason and do not block the batch.

---

## 3. Capability B — Segmentation, tags, audiences

Campaigns are only as good as their targeting. pos-customer today has no tags and no reusable segments — only `AccountTier` + `AccountStatus`.

### 3.1 Entity — `PartyTag` and `PartyTagAssignment`  **[CRM]**

- `PartyTag`: `tagId`, `name` (unique), `category` (optional grouping, e.g. `INTEREST`, `LIFECYCLE`, `SOURCE`), `color`, `active`. Optionally hierarchical (parent/child) mirroring `res.partner.category`; **recommend flat for v1**.
- `PartyTagAssignment`: `(partyId, tagId)`, `assignedBy`, `assignedAt`, optional `source` (MANUAL/CAMPAIGN/IMPORT/RULE). Tags apply to both party subtypes.
- Endpoints under `/v1/crm/parties/{partyId}/tags` (list/add/remove) + `/v1/crm/tags` (catalog CRUD). Permissions `crm:tag:{view,manage,assign}`.

### 3.2 Entity — `Segment`  **[CRM]**

A **named, reusable audience definition**. Two flavors (Odoo parity: `mailing.list` static vs `mailing.filter` dynamic):

- **STATIC** — an explicit membership set (`SegmentMember(segmentId, partyId, contactId?)`), e.g. an imported list of fleet prospects.
- **DYNAMIC** — a **predicate** evaluated against party + related data at resolution time.

`Segment` fields: `segmentId`, `name`, `description`, `audienceType` (COMMERCIAL/INDIVIDUAL — a segment is single-type so it can only feed matching campaigns), `type` (STATIC/DYNAMIC), `predicate` (structured JSON for DYNAMIC), audit.

### 3.3 Segment predicate model (DYNAMIC)

A safe, validated predicate tree (**not** free SQL) over a fixed attribute catalog. Attributes span data pos-customer owns or replicates:

| Attribute source | Examples |
|---|---|
| Party | `partyType`, `accountTier`, `accountStatus`, `parentPartyId present`, `externalIdentifier[system]`, tags |
| Billing rules | `taxExempt`, `creditHold`, `paymentTerms` |
| Consent (§4) | `marketing email opted-in`, `channel allowed` (segments should pre-filter, though send-time re-checks anyway) |
| Vehicle (from `ext_vehicle`) | `owns make/model`, `vehicle year range`, `has active vehicle`, `vehicle count ≥ N` (fleet size) |
| Service history (needs data — see O-4) | `last service > N months` (win-back), `service-due` (care preference interval elapsed), `declined service in last N days` |
| Geography | shop/location association, region (needs structured address — O-5) |

- Predicate = boolean tree of `{attribute, operator, value}` leaves with AND/OR/NOT.
- Resolution is a query builder over local tables + replicas; must paginate and cap.
- **Some high-value attributes (service history, structured geography) require data pos-customer doesn't hold today** — see Open Questions O-4/O-5. v1 can ship with the party/tier/tag/vehicle attributes it already has and add service-history predicates when the data feed exists.

### 3.4 Endpoints (`/v1/crm/segments`)

CRUD + `POST /{id}/resolve` (returns count + paginated sample, consent-filtered). Permissions `crm:segment:{view,manage,resolve}`.

### 3.5 Acceptance criteria

- A DYNAMIC segment predicate only references whitelisted attributes/operators; invalid predicates are rejected at save (`422`), never executed.
- Segment resolution is deterministic and paginated; resolving a COMMERCIAL segment never returns person parties and vice-versa.
- Tag assignment/removal emits audit events and is idempotent.

---

## 4. Capability C — Marketing consent, suppression, interaction history  **[CRM]**

pos-customer has a solid base (`CommunicationPreference` with per-channel preference + `consentFlags` + `@Version`) but three gaps: (a) marketing consent is a single coarse flag, (b) no hard suppression list, (c) no record of what we actually sent/said.

### 4.1 Enrich consent — extend `CommunicationPreference`

- Model **per-channel marketing consent** separately from transactional contactability: `marketingEmailConsent`, `marketingSmsConsent` (OPT_IN/OPT_OUT/UNSET) — distinct from the operational `emailPreference`/`smsPreference`.
- For **COMMERCIAL** accounts, consent may be held at the account level and/or per contact-role; define whether an operations contact's consent is personal or account-delegated. **Open question O-2.**
- Add **quiet-hours / cadence** guidance (optional v1): max marketing touches per party per window, do-not-disturb hours (respect timezone).
- Capture **opt-out reason** (catalog `OptOutReason`: NOT_INTERESTED, TOO_FREQUENT, NEVER_SIGNED_UP, LEGAL_DNC, …) — Odoo parity with `mailing.subscription.optout`.

### 4.2 Consent change audit — `ConsentEvent`

Append-only history: `(partyId, channel, oldValue, newValue, reason, source, actor, at)`. Required for CAN-SPAM/TCPA defensibility ("prove they opted in / when they opted out"). Feeds the snapshot and a compliance export.

### 4.3 Suppression list — `SuppressionEntry`

A hard, address-level block consulted by **every** send, independent of preference (Odoo's `mail.blacklist`):

- `suppressionId`, `channel`, `addressHash` (email/phone normalized+hashed), `partyId?`, `reason` (HARD_BOUNCE, SPAM_COMPLAINT, LEGAL_DNC, MANUAL, UNSUBSCRIBE_LINK), `source`, `createdAt`.
- Populated by provider bounce/complaint webhooks (§2.2), unsubscribe-link clicks, and manual admin action.
- pos-marketing must query suppression (via CRM read/replica) at send time; suppressed addresses are never contacted.

### 4.4 Interaction / touch history — `CustomerInteraction`

Today pos-customer has **no record of outbound touches**; `PartyNote` is a one-way projection from workorder events with no write API. Generalize into a customer interaction log:

- `interactionId`, `partyId`, `contactId?`, `type` (CAMPAIGN_SEND, EMAIL, SMS, CALL, FOLLOW_UP, NOTE, WORKORDER_NOTE), `channel?`, `direction` (OUTBOUND/INBOUND), `campaignId?`, `subject/summary`, `body?` (redaction-aware per DECISION-INVENTORY-006), `actor`, `occurredAt`, `sourceEventId`.
- Written by: campaign sends (fact-driven from `marketing.events.v1`), CSR follow-up actions (§6), and the existing workorder `PartyNoteAdded` projection (migrate `PartyNote` into this or keep as a typed subset).
- Read endpoint `/v1/crm/parties/{partyId}/interactions` (paged, filterable). Feeds a "customer 360" timeline and the snapshot.

### 4.5 Snapshot integration

Extend `CrmSnapshotDTO` with a consent summary (per-channel marketing consent + suppression flags) and a recent-interaction summary, so downstream consumers (and CSRs) see contactability and touch history at a glance.

### 4.6 Acceptance criteria

- A send is blocked if the recipient is on the suppression list for that channel, regardless of preference.
- Every marketing consent change writes a `ConsentEvent`; the history is queryable and exportable.
- An unsubscribe link resolves to a per-channel opt-out + suppression entry + `ConsentEvent`, with an opt-out reason where provided.
- Campaign sends appear in the party's interaction history within the event-processing SLA.

---

## 5. Capability E — Prospect / inquiry capture (adapted lead capture)  **[pos-inquiry or CRM]**

Positivity has no "stranger" object — parties are created already-as-customers, and `pos-inquiry` is an empty placeholder. New fleet accounts and web "request a quote" inquiries have no home.

### 5.1 Adapted model — **do not** import `crm.lead`

Instead of Odoo's denormalized-lead-then-materialize funnel, model a lightweight **prospect state on the party** plus an inquiry capture:

- Add `PARTY_STATUS = PROSPECT` (or a `lifecycleStage` field: `PROSPECT → ACTIVE → DORMANT`) so a party can exist pre-first-service without polluting active-customer queries.
- **`Inquiry`** (in `pos-inquiry`, which is reserved for exactly this): `inquiryId`, `channel` (WEB_FORM, PHONE, WALK_IN, REFERRAL, CAMPAIGN), `audienceType`, captured contact fields, `vehicleOfInterest?`, `serviceOfInterest?`, `campaignCode?` (attribution), `status` (NEW → CONTACTED → CONVERTED → CLOSED), `partyId?` (once linked/created), `assignedTo?`.
- **Conversion**: an inquiry converts by creating/linking a party (reuse `checkPartyDuplicates` + `createCommercialAccount`/person create) and optionally handing off to `pos-shop-manager` to book an appointment. No opportunity/stage machinery.

### 5.2 Web form / email capture

- A public "request service / fleet quote" entry point posts an `Inquiry` (rate-limited, captcha, no auth). This is the Positivity analog of Odoo's `website_crm` form + team alias.
- Fleet inquiries (COMMERCIAL) route to an account-onboarding queue; individual inquiries can auto-suggest the nearest shop.

### 5.3 Verdict / scope

**BUILD (light, adapted).** This is genuinely useful but lower priority than campaigns/consent. It can ship after A–C. If `pos-inquiry` ownership is unclear, the minimal version is just the `PROSPECT` lifecycle stage + `campaignCode` attribution on party creation, deferring the full inquiry object. **Open question O-6.**

---

## 6. Capability D — Follow-up tasks & (optional) account ownership  **[CRM]**

The service-shop analog of Odoo's activities/next-actions and lost-reason — reshaped around *declined recommendations* and *service-due*, not deal stages.

### 6.1 `FollowUpTask`

- `taskId`, `partyId`, `vehicleId?`, `type` (DECLINED_SERVICE_FOLLOWUP, SERVICE_DUE_REMINDER, FLEET_CHECKIN, CAMPAIGN_RESPONSE, GENERAL), `dueDate`, `assignedTo?` (CSR), `status` (OPEN/DONE/DISMISSED), `sourceWorkorderId?`, `reason?` (decline reason), `outcome?`, `notes`.
- **Created from workorder events**: when a recommended service line is declined on a workorder, `pos-workorder` emits a fact → pos-customer creates a `DECLINED_SERVICE_FOLLOWUP` task (parity with Odoo lost-reason, but at line granularity and actionable). Requires a workorder event (**O-7**).
- **Created from service-due**: `VehicleCarePreference` interval elapsed (care-preference feature referenced in DECISION-INVENTORY-012) → `SERVICE_DUE_REMINDER` task and/or campaign audience membership.
- **Hand-off, not scheduling**: completing a task can deep-link to `pos-shop-manager` appointment creation. pos-customer never books.
- Endpoints `/v1/crm/parties/{partyId}/follow-ups` + a CSR work-queue view `/v1/crm/follow-ups?assignedTo=&status=`. Permissions `crm:followup:{view,manage}`.

### 6.2 Account ownership (optional)

Odoo has salesperson/team ownership; Positivity may want a CSR/account-manager assignment on commercial accounts (`accountOwnerUserId` on `CommercialParty` + reassignment audit). **Optional, low priority** — only if the business wants named account managers. Otherwise NON-GOAL.

### 6.3 Acceptance criteria

- A declined-service workorder event produces exactly one follow-up task (idempotent via `processing_log`).
- Completing a follow-up records an outcome and, when it books service, links the resulting appointment/workorder for closed-loop reporting.

---

## 7. Capability (backlog) — Service-opportunity / churn signal

Odoo's PLS predicts *deal* win probability — not applicable. The Positivity-relevant analog is a **service-due / churn-risk signal**: which customers are overdue for service or lapsing. This is derivable from vehicle care preferences + service history rather than ML.

- **Backlog / NON-GOAL for v1.** Once service-history data (O-4) is available, a simple rules-based "next-service-due" and "at-risk (no visit in N months)" scoring can drive campaign segments (§3) and follow-up tasks (§6). No Naive-Bayes/ML needed. Record as a fast-follow, not a v1 deliverable.

---

## 8. Capability A'' — Campaign attribution & analytics  **[MKT]**

Closing the loop from send → workorder → revenue is what makes campaigns worth running in a service shop.

### 8.1 Attribution mechanism

- Every campaign has a `code`; every send references it. When a promoted offer is redeemed, the campaign code must land on the **`PromotionRedemption`** record (pos-customer already stores `promotionCode`; add/populate `campaignCode`). Since redemptions are keyed to a `workorderId`, this yields **attributed workorder revenue per campaign**.
- Alternative/additional: campaign-specific promo codes (one offer, per-campaign codes) so attribution is exact.
- pos-marketing consumes redemption facts from `customer.events.v1` (or reads the redemption list) to compute attributed conversions/revenue.

### 8.2 Campaign stats — `GET /campaigns/{id}/stats`

Reach funnel per channel + attribution:

```
targeted → eligible(after consent/suppression) → sent → delivered → (opened/clicked) → redeemed → attributed workorders / revenue
```

- Delivery metrics from `CampaignSend` + provider webhooks; open/click where the provider supports it.
- Conversion metrics from redemption attribution (§8.1).
- Split by `audienceType`; a `campaignProgramId` rollup compares the commercial vs individual arms.

### 8.3 Attribution facts flow (ADR-0044)

`pos-workorder`/pos-customer emit redemption facts → pos-marketing updates campaign conversion counters (idempotent). No synchronous cross-service joins.

### 8.4 Acceptance criteria

- A workorder redemption tied to a campaign's offer/code is attributed to exactly that campaign in stats within the event SLA.
- Stats never double-count a redemption; attribution is idempotent by `redemptionId`.
- Commercial and individual arms of a program are separately measurable.

---

## 9. Consolidated data model, permissions, events

### 9.1 New/changed entities

| Entity | Owner | New/Change |
|---|---|---|
| `PartyTag`, `PartyTagAssignment` | [CRM] | new |
| `Segment`, `SegmentMember` | [CRM] | new |
| `CommunicationPreference` (marketing consent fields, quiet hours) | [CRM] | change |
| `ConsentEvent` | [CRM] | new (append-only) |
| `SuppressionEntry` | [CRM] | new |
| `CustomerInteraction` | [CRM] | new (generalizes `PartyNote`) |
| `FollowUpTask` | [CRM] | new |
| `AbstractParty.lifecycleStage` (PROSPECT/ACTIVE/DORMANT) | [CRM] | change |
| `PromotionRedemption.campaignCode` | [CRM] | change (attribution) |
| `Campaign`, `CampaignSend` | [MKT] | new |
| `MessageTemplate` | [MKT] | new |
| `Inquiry` | pos-inquiry | new |

### 9.2 Permissions (code-first registries)

- CRM: `crm:tag:{view,manage,assign}`, `crm:segment:{view,manage,resolve}`, `crm:consent:{view,manage}`, `crm:suppression:{view,manage}`, `crm:interaction:view`, `crm:followup:{view,manage}`.
- Marketing: `marketing:campaign:{view,create,edit,schedule,send,manage}`, `marketing:template:{view,manage}`, `marketing:stats:view`.
- Split CSR vs Marketing-manager access per DECISION-INVENTORY-007 (least privilege; e.g. CSR can view consent + create follow-ups but not send campaigns).

### 9.3 Events (ADR-0044)

- **New facts** `marketing.events.v1`: `campaign.scheduled`, `campaign.sent`, `campaign.send.delivered/bounced/complained`, `campaign.converted`.
- **CRM facts** on `customer.events.v1`: `party.tag.changed`, `party.consent.changed`, `party.suppressed`, `segment.changed`, `interaction.logged` (for consumers/replicas).
- **Consumed by CRM**: workorder `serviceLine.declined` (→ follow-up task, **O-7**), `promotion.redeemed`/existing redemption path (→ attribution).
- **Consumed by MKT**: `customer.events.v1` party/segment/consent facts (audience replica), redemption facts (attribution).
- Reuse outbox + `ProcessedEvent` idempotency.

### 9.4 Flyway

New tables in pos-customer migrations (continue the V-series after current max V16) and a fresh migration baseline in the new pos-marketing module. No cross-service FKs.

---

## 10. Non-goals (record so they aren't re-litigated)

| Odoo capability | Why it's a non-goal for Positivity |
|---|---|
| Opportunity/deal **pipeline, kanban stages, probability, forecast** | The sales pipeline is appointment→estimate→workorder→invoice, owned by shop-manager + workorder. Do not add an opportunity kanban to pos-customer. |
| **Recurring-revenue/MRR** on a lead | Fleet service contracts belong to billing/pricing, not CRM. |
| **Predictive (Naive-Bayes) lead scoring** | No deal funnel to score; the useful analog (service-due/churn) is rules-based and backlog (§7). |
| **Reseller geo-assignment** (`website_crm_partner_assign`) | Positivity runs its own shops, not a reseller channel. |
| **Rule-based lead round-robin assignment** | No high-volume lead routing; account ownership (§6.2) is the only ownership concept, and it's optional. |
| **Lead mining / IAP enrichment** (`crm_iap_mine/enrich`) | External prospect-buying is out of scope for a service shop. |
| **A/B testing** of mailings (v1) | Ship base campaigns first; revisit. |
| **Full UTM URL/cookie web attribution** | Campaign-code attribution onto redemptions is sufficient; web-analytics UTM capture is out of scope. |
| **Double opt-in subscription portal** (v1) | Consent capture + unsubscribe suffices for v1; backlog. |
| Separate lightweight **`mailing.contact`** table | Identity is centralized in pos-people-contact; target parties directly. |

---

## 11. Open questions / cross-domain contracts needed

| # | Question | Owner(s) | Blocks | Answer |
|---|---|---|---|---|
| O-1 | Email/SMS **delivery provider & transport** — dedicated provider client vs a shared platform sender? Owns bounce/complaint webhooks? | Platform/Positivity-integrations + Marketing | §2 send, §4.3 suppression |Assume we will use a shared platform sender|
| O-2 | **Commercial consent model** — is marketing consent held at the account level, per contact-role, or personally by each contact? Legal basis for B2B sends. | CRM + Security/Legal | §4.1 |Personally, by primary contact and a flag at account level|
| O-3 | Can `pos-price` **eligibility rules accept an `audienceType`/`campaignCode`** input for audience-specific offers? | Pricing | §1.5 |It can; I don't know if it does.  Make a follow-up issue|
| O-4 | **Service-history data feed** — does pos-customer get workorder/service-completion facts to power "last service", "service-due", "declined service" segments? Today it only consumes `ContactPreferenceUpdated` + `PartyNoteAdded`. | Workexec/Workorder + CRM | §3.3, §6, §7 |Yes, create an issue for emitting these events and consuming them|
| O-5 | **Structured address / geography** — needed for geo/region segmentation and mail campaigns; today `primaryAddress` is free text. Build a structured address, or rely on shop/location association? | CRM + Location | §3.3 geo attributes |Build a structured address in pos-people-contact, create issue to address this gap|
| O-6 | **Prospect/inquiry ownership** — does `pos-inquiry` own the Inquiry object, or is a `PROSPECT` lifecycle stage on the party sufficient for v1? | CRM + product | §5 |pos-inquiry is a holder for inquiries to suppliers, not prospects|
| O-7 | **Declined-service event** — will `pos-workorder` emit a `serviceLine.declined` fact to drive follow-up tasks? | Workorder + CRM | §6.1 | yes - make a follow-up issue to address (closely related to O-4, maybe it's the same issue?)|
| O-8 | ~~**Module placement** — new `pos-marketing` module vs campaign-in-pos-customer.~~ **RESOLVED (ACCEPTED 2026-07-28): new `pos-marketing` module** (§0.3). | Architecture | — (closed) |chose pos-marketing|

---

## 12. Suggested build sequencing (for the plan to formalize)

Waves are dependency-ordered; each is independently shippable.

- **Wave 1 — CRM data foundations [CRM]:** `PartyTag`(+assignment), `Segment` (static + dynamic over already-available party/tier/tag/vehicle attributes), marketing-consent enrichment on `CommunicationPreference`, `SuppressionEntry`, `ConsentEvent`, `CustomerInteraction` (generalize `PartyNote`). Snapshot integration. *No external dependencies; unblocks everything.*
- **Wave 2 — Campaign core [MKT]:** stand up the `pos-marketing` module (§0.3 bootstrap), then `Campaign` + lifecycle, `MessageTemplate`, audience binding to segments, audience preview (consent-filtered), attribution `campaignCode` on redemptions. *Depends on Wave 1 + O-1 for actual send.*
- **Wave 3 — Send + attribution [MKT]:** `CampaignSend`, provider channel adapter(s), async batched dispatch with send-time consent/suppression re-check, provider webhooks → suppression + delivery stats, `GET /stats` with redemption attribution. *Depends on O-1, O-3.*
- **Wave 4 — Follow-up & prospects [CRM/inquiry]:** `FollowUpTask` (declined-service + service-due), `Inquiry`/`PROSPECT` lifecycle, hand-off to shop-manager. *Depends on O-4, O-6, O-7.*
- **Backlog:** service-due/churn signal (§7), A/B testing, double opt-in, structured address (O-5), account ownership (§6.2).

---

## 13. Summary — what "augmenting customer functionality" means here

pos-customer is a strong **customer master** (parties, hierarchy, contacts, consent base, tiers, redemptions, merge, event rails) that lacks the **relationship-and-demand layer** on top of it. The gap versus Odoo is not the sales pipeline (Positivity has that in shop-manager + workorder) — it is:

1. **Marketing campaigns differentiated by commercial vs individual audience** (the headline), referencing `pos-price` offers and `pos-catalog` product lists, attributed via the existing redemption ledger.
2. **Segmentation, tags, and audiences** to target them.
3. **Marketing-grade consent, suppression, and interaction history** to send them lawfully and remember what we sent.
4. **Follow-up tasks** for declined service and service-due, handing off to booking.
5. **Lightweight prospect/inquiry capture** for onboarding new (especially fleet) customers.

Everything else in Odoo CRM's funnel is intentionally out of scope (§10).
