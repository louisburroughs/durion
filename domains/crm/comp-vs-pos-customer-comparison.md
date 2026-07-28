# Odoo CRM vs pos-customer — Capability Comparison Map

> Purpose: a working checklist for comparing `durion-positivity-backend/pos-customer` against Odoo 19's CRM stack (`addons/crm` + `mass_mailing` + `utm` + siblings). Odoo detail is in `comp-crm-overview.md`. pos-customer references come from `pos-customer/README.md`, `src/main/java/com/positivity/customer/internal/**`, `src/main/resources/db/migration/`, and the CRM business-rules pack (`domains/crm/.business-rules/`) as of 2026-07-28.
>
> The two systems have different missions — Odoo is a general-purpose **sales-pipeline** CRM (capture → qualify → forecast → market); pos-customer is a **customer-master / party registry** for an enterprise tire-service platform where revenue is realized by executing workorders, choreographed through `pos-shop-manager` and `pos-workorder`. **"Gap" below means "Odoo has machinery pos-customer doesn't"; the right-most column judges whether Positivity actually wants it,** given that sales run through the workorder modules, product lists live in `pos-catalog`, promotions live in `pos-price`, and campaigns must distinguish commercial parties from individual parties.

## Legend for the "Verdict" column

- **BUILD** — genuine gap Positivity should close (detailed in `spec-pos-customer-crm-gaps.md`).
- **BUILD (adapted)** — build the capability, but reshaped to the service-shop model, not Odoo's shape.
- **ELSEWHERE** — the capability exists on the platform, just not in pos-customer (points to the owning service).
- **NON-GOAL** — intentionally not wanted; record the reason so it isn't re-litigated.
- **HAVE** — pos-customer already does this (sometimes better than Odoo).

## 1. Party / contact model

| Concern | Odoo (`res.partner`) | pos-customer | Notes / Verdict |
| --- | --- | --- | --- |
| Company vs individual | One `res.partner` model; `is_company` boolean; `commercial_partner_id` computed hierarchy root | Two subtypes: `CommercialParty` + `PersonParty` (TABLE_PER_CLASS), `PartyType` enum {PERSON, COMMERCIAL, UNKNOWN} | Both express the split; pos-customer's is explicit-typed. **HAVE.** This split is exactly what differentiated campaigns will key on. |
| Company hierarchy | `parent_id`/`child_ids`, recursive commercial entity | `CommercialParty.parentParty`/`childParties` self-reference | **HAVE** (commercial only; persons don't nest). |
| Person identity ownership | Names/emails/phones on the partner itself | **Not owned here** — `PersonParty.personId` links to `pos-people-contact` (ADR-0015); local read-only replica `ext_people_contact_person` | Architectural divergence: Positivity centralizes person identity in `pos-people-contact`. Relevant to campaigns: recipient contact points must be resolved via the replica. **HAVE (by design).** |
| Contact tags / categories | `res.partner.category` (hierarchical) | **Absent** — only `AccountTier` (6 levels) + `AccountStatus` | No general tag/segment taxonomy on parties. **BUILD** (segmentation, spec §3). |
| Multiple structured addresses | Address `type` (contact/invoice/delivery/other), full address fields | `primaryAddress` is a single free-text `String`; `billingAddressId` is a bare UUID reference | pos-customer has no structured/multi-address model. **BUILD (adapted)** if campaigns need mail/geo targeting; otherwise defer. |
| External identifiers | `ref`, `vat` | `CommercialParty.externalIdentifiers` `Map<String,String>`, `taxId` | **HAVE** (richer key/value map). |
| Duplicate detection & merge | Lead-level dedup + partner data-merge | First-class: `checkPartyDuplicates`, `mergeParties`, `PartyAlias` redirection, `MergeAudit`, `409 PARTY_MERGED` | **HAVE** — arguably stronger and more auditable than Odoo's partner merge. |
| Account tiers / grading | `res.partner.grade` (reseller weight); `customer_rank` | `AccountTier` engine (STANDARD→ENTERPRISE) via `AccountTierService` scoring on revenue/contracts/age | Different intent (loyalty tier vs reseller weight). **HAVE** (a Positivity strength; feeds segmentation). |
| Billing configuration on party | On invoicing layer | `BillingRulesEmbeddable` (poRequired, taxExempt, creditHold, creditLimit, paymentTerms, invoiceDeliveryMethod…) | **HAVE** — no Odoo-CRM equivalent; a service-B2B strength. |

## 2. Leads and the sales funnel

| Concern | Odoo | pos-customer | Notes / Verdict |
| --- | --- | --- | --- |
| Lead object | `crm.lead` (denormalized prospect; `type='lead'`) | **Absent** — parties are created already-as-customers | Positivity has no "stranger capture" object. |
| Lead capture (web form / email alias) | `website_crm`, team `alias_id` | **Absent**; `pos-inquiry` module is an empty placeholder | Fleet/individual inquiry capture is real but unbuilt. **BUILD (adapted)** — lightweight "prospect party" + convert, spec §5. Note `pos-inquiry` exists as a reserved home. |
| Lead → opportunity conversion | `convert_opportunity` materializes `res.partner` | N/A | **NON-GOAL** in Odoo's shape (no denormalized-then-materialize funnel); the adapted prospect→customer flow in spec §5 replaces it. |
| Opportunity pipeline / kanban stages | `crm.stage`, `probability`, `expected_revenue`, forecast | **Absent** | The Positivity "pipeline" is *appointment → estimate → workorder → invoice*, owned by `pos-shop-manager` + `pos-workorder`. **ELSEWHERE / NON-GOAL** — do **not** import an opportunity kanban into pos-customer. |
| Recurring revenue / MRR | `crm.recurring.plan`, `recurring_revenue_monthly` | **Absent** | Fleet service contracts are the analog, but they live with billing/pricing, not CRM. **NON-GOAL** for pos-customer. |
| Lost/won reasons | `crm.lost.reason`, `action_set_won/lost` | Only `MergeAudit.mergeReason` | Won/lost is meaningless without opportunities. For *declined recommended service* (the real shop analog) see follow-up, spec §4. **BUILD (adapted)** as decline-reason on service opportunities, not lost-reason on deals. |
| Sales team / salesperson / territory | `crm.team`, `crm.team.member`, ownership | **Absent** — only audit `createdBy`/`modifiedBy` | No account-owner/CSR-assignment concept. **BUILD (light)** if account ownership is wanted (spec §6, optional); otherwise NON-GOAL. |
| Rule-based lead assignment / round-robin | Weighted-random team + quota member assignment | **Absent** | Tied to lead routing; **NON-GOAL** (no leads to route at volume). |
| Reseller geo-assignment | `website_crm_partner_assign` | **Absent** | **NON-GOAL** (Positivity operates its own shops, not a reseller channel). |
| Predictive lead scoring (PLS) | Per-team Naive Bayes | **Absent** (tier scoring is deterministic classification, not ML) | **NON-GOAL** as deal-scoring. A *service-due / churn-risk* signal is a different, optional idea (spec §7, backlog). |

## 3. Marketing and campaigns — **the headline gap**

| Concern | Odoo | pos-customer | Notes / Verdict |
| --- | --- | --- | --- |
| Campaign object | `utm.campaign` (+ `crm_lead_count`, stats) | **Absent** — no `campaign` entity anywhere in the backend (grep-clean across all `pos-*`) | **BUILD** — spec §1. Must be **differentiated by audience type: COMMERCIAL vs INDIVIDUAL parties** (user requirement). |
| Mass mailing (email) | `mailing.mailing` (targeting, schedule, A/B, stats) | **Absent** | **BUILD** — spec §2 (send orchestration; delivery via a provider/`pos-documents`-style channel). |
| Mass SMS | `mass_mailing_sms` | **Absent** | **BUILD** — same campaign engine, SMS channel; honor per-channel consent. |
| Audience targeting — list based | `mailing.list` + `mailing.contact` (lightweight, separate from partners) | **Absent** | **BUILD (adapted)** — static + dynamic segments over *party* attributes; no need for a separate lightweight-contact table since identity is centralized. Spec §3. |
| Audience targeting — model-domain | `mailing_domain` over `res.partner`/`crm.lead`; saved `mailing.filter` | **Absent** | **BUILD** — segment predicate builder over party type, tier, tags, vehicle/service history, geography. Spec §3. |
| Company-vs-individual segmentation | `is_company` domain filter on `res.partner` mailings | Party subtype exists but no targeting layer consumes it | The split is *present in data* but *unused for marketing*. **BUILD** — audience type is a first-class campaign dimension. Spec §1/§3. |
| Product/offer linkage | Loose (mailing content); promotions elsewhere | Redemptions recorded (`PromotionRedemption`) but no campaign→offer link | **BUILD (adapted)** — campaign references a `pos-price` promotion offer and/or `pos-catalog` product list; redemption attribution closes the loop. Spec §1/§8. |
| UTM source/medium attribution | `utm.source`/`utm.medium`/`utm.mixin` | **Absent** | **BUILD (light)** — campaign code carried onto workorder/redemption for attribution; full UTM URL-cookie capture is a **NON-GOAL**. Spec §8. |
| A/B testing | `ab_testing_*` on mailings | **Absent** | **NON-GOAL** for v1 (revisit after base campaigns ship). |
| Campaign statistics | `mailing.trace` → sent/open/click/reply/bounce | **Absent** | **BUILD (adapted)** — send/delivery outcomes + redemption/revenue attribution; open/click tracking depends on the email provider (spec §8). |

## 4. Consent, opt-out, and suppression

| Concern | Odoo | pos-customer | Notes / Verdict |
| --- | --- | --- | --- |
| Per-party channel preference | Via mixins/blacklist | `CommunicationPreference`: email/sms/phone/marketing preference (OPT_IN/OPT_OUT/NOT_APPLICABLE), `consentFlags` map, `@Version` | **HAVE** — a solid base to build marketing consent on. |
| Marketing opt-in specifically | `mailing.subscription.opt_out` per list + global blacklist | `marketingPreference` single flag; `BillingPreferences.marketingOptOut`/`doNotContact` on snapshot | Present but coarse (one global marketing flag). **BUILD** — per-channel + per-audience/campaign-type consent, quiet hours. Spec §4. |
| Opt-out reasons | `mailing.subscription.optout` (reason catalog) | **Absent** | **BUILD (light)** — capture reason on opt-out for compliance. Spec §4. |
| Global suppression list | `mail.blacklist` (address-level, all sends) | **Absent** — no hard suppression separate from preference | **BUILD** — a suppression list the send pipeline must consult (bounces, complaints, legal DNC). Spec §4. |
| Consent history / audit | Chatter + tracking | `consentFlags` snapshot only (no history); `updateSource` field | **BUILD (light)** — consent-change audit trail (who/when/source), needed for CAN-SPAM/TCPA defensibility. Spec §4. |
| Double opt-in | Subscription-management page | **Absent** | **NON-GOAL** v1; backlog. |

## 5. Activities, follow-up, and interaction history

| Concern | Odoo | pos-customer | Notes / Verdict |
| --- | --- | --- | --- |
| Scheduled activities / tasks | `mail.activity.mixin` (assignee, due date, type) | **Absent** as first-class tasks | **BUILD (adapted)** — CSR/service follow-up tasks: *declined-service follow-up*, *service-due reminder*, *fleet check-in*. Spec §4/§6. |
| Meetings / calendar | `calendar.event` link | **Absent** (appointments live in `pos-shop-manager`) | **ELSEWHERE** — booking is shop-manager's job; CRM follow-up should *hand off* to it, not duplicate it. |
| Interaction / communication log | `mail.thread` chatter (messages, emails, tracked changes) per record | **Absent** — `CommunicationPreference` stores *consent*, not *history*. `PartyNote` exists but is a one-way projection from workorder events (no write API, no author/type/thread) | Big gap: no record of *what we sent the customer or said to them*. **BUILD (adapted)** — customer interaction/touch history (campaign sends, calls, follow-ups). Spec §4. |
| Field-change / stage audit | `mail.tracking` + `_track_duration_field` | Audit events via `@EmitEvent` to `pos-event-receiver`; `updatedAt`/`modifiedBy` | **HAVE** (platform audit rail differs but exists). |

## 6. Segmentation and reporting

| Concern | Odoo | pos-customer | Notes / Verdict |
| --- | --- | --- | --- |
| Tag taxonomies | `crm.tag`, `res.partner.category`, `utm.tag` | **Absent** (tier/status only) | **BUILD** — party tags/labels feeding segments. Spec §3. |
| Saved segments / filters | `mailing.filter`, `ir.filters` | **Absent** | **BUILD** — named, reusable, static-or-dynamic segments. Spec §3. |
| Pipeline / forecast analysis | pivot/graph on `crm.lead` | **Absent** (no pipeline) | **NON-GOAL**; service-throughput analytics belong to shop-manager/workorder reporting. |
| Campaign analysis | `utm.campaign` stats, `crm.activity.report` | **Absent** | **BUILD (adapted)** — campaign reach/redemption/attributed-revenue report. Spec §8. |
| Customer 360 / snapshot | Partner form + smart buttons | `CrmSnapshotDTO` (account + contacts + vehicles + billing prefs) | **HAVE** — richer consolidated read model than Odoo's form; extend it with campaign/consent/interaction summaries (spec §4/§8). |

## 7. Promotions / offers (cross-service)

| Concern | Odoo | pos-customer / platform | Notes / Verdict |
| --- | --- | --- | --- |
| Promotion / discount offers | Coupons/loyalty in `sale`/`loyalty` addons | **`pos-price`** owns `PromotionOffer` (promo code, discount type/value, dates, usage limit, storeCode) + eligibility rules + `applyPromotion` | **ELSEWHERE** (`pos-price`). Campaigns should *reference* these offers, not redefine discounts. Spec §1/§8. |
| Product lists / catalogs | Pricelists, product templates | **`pos-catalog`** owns product master + price books | **ELSEWHERE** (`pos-catalog`). A campaign targeting "winter tires" references a catalog product list. |
| Redemption tracking | Coupon usage | **`pos-customer`** already records `PromotionRedemption` (idempotent, per promotion+workorder) + `PromotionCounter` | **HAVE** — the attribution sink already exists; campaigns plug into it. |
| Eligibility by audience | Loyalty rules | `pos-price` eligibility rules (estimate/line context) | Audience-type eligibility (commercial vs individual) may need a rule input; coordinate with pricing. Spec §1 note. |

## 8. Integration and platform mechanics

| Concern | Odoo | pos-customer | Notes / Verdict |
| --- | --- | --- | --- |
| Eventing / async integration | ORM + mail bus | Transactional outbox → `customer.events.v1`, replica consumers (`vehicle.events.v1`, `people-contact.events.v1`), manifests + replay, `ProcessedEvent` idempotency | **HAVE** — strong ADR-0044 rails; new campaign facts/commands ride the same outbox. |
| Multi-channel send infra | Outgoing mail servers, IAP SMS | **Absent** in pos-customer | **BUILD** in the **new `pos-marketing` module** (ACCEPTED, spec §0.3) — needs an email/SMS delivery channel (provider client or a `pos-notifications`/`pos-documents`-style sender). Provider selection is the remaining open item (spec §2, O-1). |
| Idempotent redemption ingest | — | `recordRedemption` (dup → 409), `PromotionCounter` `@Version` | **HAVE.** |
| Permissions model | Security groups | `crm:*` permission taxonomy (party/contact/relationship/promotion_redemption/…) registered to `pos-security-service` | **HAVE** — extend with `crm:campaign:*`, `crm:segment:*`, `crm:consent:*`. Spec each section. |

## 9. Suggested comparison exercises (to pressure-test the spec)

1. **Differentiated campaign walk-through.** Take one "commercial winter-tire fleet promo" and one "individual oil-change reminder." Trace how each selects its audience (segment predicate over `PartyType` + tier + vehicle/service history), which consent flags gate it, which `pos-price` offer it references, and how a resulting workorder redemption attributes back to the campaign. Confirm the model cleanly separates COMMERCIAL and INDIVIDUAL audiences end-to-end.
2. **Consent enforcement.** Walk a send through the suppression checks: per-channel `CommunicationPreference`, marketing opt-out, global suppression list, quiet hours. Compare against Odoo's two-tier (per-list opt-out + global blacklist) and decide the equivalent tiers for Positivity.
3. **"Pipeline" boundary.** Confirm explicitly that appointment→estimate→workorder→invoice (shop-manager + workorder) is the sales pipeline and that pos-customer will *not* grow an opportunity kanban — record it as a non-goal so it isn't reintroduced.
4. **Follow-up vs booking boundary.** Model a "declined brake service" follow-up task and show it hands off to `pos-shop-manager` for the actual re-booking rather than duplicating scheduling.
5. **Where campaigns live — DECIDED.** Campaign orchestration + send live in a **new `pos-marketing` module**; segments/tags/consent/suppression/interaction-history/prospects stay in `pos-customer` (ACCEPTED 2026-07-28, spec §0.3). The exercise now is to validate the split against ArchUnit boundaries and confirm pos-marketing integrates with pos-customer via gateway REST + `customer.events.v1` only (no compile-time coupling).
6. **Interaction history retro-fit.** Decide whether the customer interaction log generalizes the existing one-way `PartyNote` projection or is a new entity, and what write API it needs.
