# Odoo CRM — Functional Overview (Reference for pos-customer Comparison)

> Source: review of Odoo 19.0 (development series), `addons/crm` (~2,900-line `crm.lead` model + ~15 sibling models) plus the marketing/UTM stack (`addons/mass_mailing`, `addons/utm`), the sales-team base (`addons/sales_team`), and the CRM extension addons (`sale_crm`, `mass_mailing_crm`, `crm_sms`, `crm_iap_enrich`, `crm_iap_mine`, `website_crm`, `website_crm_partner_assign`, `event_crm`). Prepared as reference material for comparing against `durion-positivity-backend/pos-customer`. See also:
>
> - `comp-vs-pos-customer-comparison.md` — side-by-side capability map
> - `spec-pos-customer-crm-gaps.md` — specification for the functionality worth building
>
> **Mission mismatch, stated up front.** Odoo CRM is a general-purpose B2B/B2C *sales-pipeline* tool: it exists to capture leads, qualify them into opportunities, route them to salespeople, and forecast revenue. Positivity is an *enterprise tire-service execution* platform: revenue is realized by executing workorders on vehicles, choreographed through the shop-management appointment flow, not by working a deal pipeline. So "Odoo has X, pos-customer doesn't" is often *correct and intentional* — the value of this document is to separate the machinery Positivity genuinely needs (marketing campaigns, segmentation, consent, follow-up, interaction history) from the machinery it does not (opportunity kanban, predictive deal scoring, reseller geo-assignment).

## Architecture in one paragraph

Everything in Odoo CRM reduces to one object: **`crm.lead`**. A "lead" and an "opportunity" are the *same* record discriminated by a `type` field (`'lead'` → `'opportunity'`). That record carries denormalized customer data (company name, contact name, email, phone, address) until it is *converted*, at which point Odoo materializes real `res.partner` records (a company partner plus a child contact) from those fields. Around this single object sit five subsystems: a **pipeline** (`crm.stage` kanban columns + probability + expected/recurring revenue), an **assignment engine** (`crm.team` / `crm.team.member` round-robin with quotas and domains), a **capture-and-dedup** layer (website forms, email aliases, lead mining, IAP enrichment, duplicate detection/merge), a **predictive scoring** model (per-team Naive Bayes over configurable fields), and a **marketing** stack (`mass_mailing` email/SMS + `utm` campaign/source/medium attribution). The unifying party model underneath everything is **`res.partner`**, whose `is_company` boolean and `commercial_partner_id` hierarchy are what let a single mailing or a single dedup rule treat companies and individuals differently.

## 1. The pipeline core — `crm.lead` / `crm.stage`

- **One model for leads and opportunities.** `crm.lead` (`crm/models/crm_lead.py`) is the center of gravity. `type` ∈ {`lead`, `opportunity`}; `active`/`probability` encode the terminal states. Inherits a stack of mixins: `mail.thread` (chatter/message history), `mail.activity.mixin` (scheduled activities), `mail.thread.blacklist`/`mail.thread.phone` (email/phone quality + opt-out), `utm.mixin` (campaign attribution), `format.address.mixin`.
- **Lifecycle** is: created (as `lead` or directly `opportunity`) → qualified through stages → **won** (`stage_id.is_won AND probability==100`, via `action_set_won`) or **lost** (`active==False AND probability==0`, via `action_set_lost` + a `crm.lead.lost` wizard that attaches a `lost_reason_id`). `won_status` (`won`/`lost`/`pending`) is computed from those two conditions. Restoring a lost lead clears the lost reason and recomputes probability.
- **Conversion** (`convert_opportunity`, wizards `crm.lead2opportunity.partner[.mass]`) flips `type` to `opportunity`, stamps `date_conversion`, and optionally runs `_create_customer` — which reads `partner_name` (future company) and `contact_name` (future person) and creates a `res.partner` company (`is_company=True`) with a child contact under it. This is the moment a "prospect" becomes a real customer record.
- **Stages** (`crm.stage`): ordered by `sequence`; `is_won` flags the terminal column (toggling it forces every lead in the stage to 100%); `fold` folds empty kanban columns; `rotting_threshold_days` highlights stale opportunities; `team_ids` can scope a stage to specific teams. Default ladder: **New → Qualified → Proposition → Won**.
- **Revenue**: `expected_revenue`, `prorated_revenue` (= expected × probability/100). **Recurring/MRR**: `recurring_revenue` + `crm.recurring.plan.number_of_months` → `recurring_revenue_monthly` (monthly recurring revenue) and prorated variants; gated by group `crm.group_use_recurring_revenues`.
- **Classification**: `priority` (0 Low → 3 Very High), `tag_ids` (M2M `crm.tag`), `lost_reason_id` (M2O `crm.lost.reason`).
- **Views**: kanban (by stage), list, calendar, pivot/graph (Pipeline Analysis, Forecast, Lost Analysis), cohort. Rainbowman celebration on win.

## 2. Capture and deduplication

- **Website forms** (`website_crm`): `crm.lead.website_form_input_filter` maps a public form submission to a new lead, sets `medium_id`=Website, applies team/user defaults, and chooses lead-vs-opportunity type by team config. Web-visitor page tracking links via `visitor_ids`.
- **Email alias** (`crm.team.alias_id`, via `mail.alias.mixin`): inbound email to a team's address creates a lead (`crm.lead.message_new`) and assigns it within the team.
- **Duplicate detection / merge**: `_compute_potential_lead_duplicates` matches on **email domain** (`email_domain_criterion`), **same commercial entity** (`partner_id child_of commercial_partner_id`), and **sanitized phone**. `merge_opportunity` picks a "head" record by `_sort_by_confidence_level` (opportunity > lead, higher stage, higher probability, newer id) and concatenates/first-non-null-merges the rest, folding in followers, messages, attachments, and calendar events. Mass-convert offers a `deduplicate` toggle.
- **Lead mining** (`crm_iap_mine`, `crm.iap.lead.mining.request`): generates *brand-new* leads from an external company database, filtered by country/state, company size, `industry_ids`, and (for contacts) `role`/`seniority`. Credit-metered via IAP.
- **IAP enrichment** (`crm_iap_enrich`): autofills an existing lead's company data (name, address, phone, country/state) from the email domain; `iap_enrich_done` flag; auto-cron on create.
- **Event leads** (`event_crm`): `event.lead.rule` creates leads from event registrations.

## 3. Assignment and routing

- **Basic**: `_compute_team_id` derives a team from user + type; `_handle_salesmen_assignment` round-robins a `user_ids` list; `_get_default_team_id` is a 5-step heuristic over memberships, context, and company.
- **Rule-based automatic assignment** (the "Lead Assign" engine, entirely in `crm.team` + `crm.team.member`):
  - `_allocate_leads` assigns **teams** to unassigned live leads by **weighted random** (weight = `assignment_max`), respecting each team's `assignment_domain`, deduplicating+merging as it goes.
  - `_assign_and_convert_leads` distributes each team's leads to **members** by **round-robin with quota** (`_get_assignment_quota` = `assignment_max`/30 − today's count), in two passes: each member's `assignment_domain_preferred` leads first, then `assignment_domain`. Assignment converts leads to opportunities. Members can `assignment_optout`.
  - Runs on `_cron_assign_leads` or on demand (manager-only).
- **Reseller / partner assignment** (`website_crm_partner_assign`): geolocation routing to *external* partners by proximity (lat/long tiers) weighted by `res.partner.partner_weight` (from `grade_id`), excluding `partner_declined_ids`. Portal partners accept (→ convert) or decline. Introduces `res.partner.grade` (reseller tiers) and `res.partner.activation`.

## 4. Activities, scheduling, and interaction history

- **Scheduled activities** via `mail.activity.mixin`: `activity_ids`, `activity_date_deadline`, next-activity sorting. A CRM override links a lead activity to a `calendar.event` meeting (`action_create_calendar_event`, prefilling the partner as attendee); `action_schedule_meeting`/`log_meeting` and next/last-meeting computed fields.
- **Interaction history** via `mail.thread` (chatter): every lead carries a threaded log of messages, notes, emails sent/received, and tracked field changes (`_track_duration_field='stage_id'` measures time-in-stage). This is Odoo's "communication log" — it is a first-class, per-record audit + conversation stream, not just a consent flag.
- **Analysis**: `crm.activity.report` (SQL view over `mail_message` joined to `crm_lead` where an activity type is set) powers activity pivot/graph reporting.

## 5. Marketing and campaigns

- **Mailings** (`mailing.mailing`, email; SMS via `mass_mailing_sms`/`mass_mailing_crm_sms`): a single send with `subject`, `body_html`, scheduling (`schedule_type` now/scheduled), and `state` (draft/in_queue/sending/done). Two mutually exclusive **targeting** paradigms:
  1. **Mailing-list based** — `contact_list_ids` (M2M `mailing.list`) of lightweight `mailing.contact` records (name + email only, *deliberately separate from `res.partner`* to avoid bloating the partner base for large blasts).
  2. **Model-domain based** — `mailing_model_id` (any model with `_mailing_enabled=True`, including `res.partner` and `crm.lead`) + `mailing_domain`, reusable via a saved `mailing.filter`.
- **Company-vs-individual targeting lives here.** Because `res.partner` carries `is_company`, a mailing on the `res.partner` model can filter `[('is_company','=',True)]` (companies) vs `[('is_company','=',False)]` (individuals). `mailing.contact` has a `company_name` string but *no* company/individual boolean, so B2B-vs-B2C segmentation is fundamentally a `res.partner`-model concept. On leads, `partner_name` vs `contact_name`/`commercial_partner_id` carry the same distinction.
- **UTM attribution** (`utm.campaign` / `utm.source` / `utm.medium`): every mailing is auto-tagged with a campaign, a source (auto-named from the subject), and a medium. `utm.campaign` groups related sends, holds A/B config, and aggregates statistics (received/opened/replied/bounced ratios from `mailing.trace`). Leads generated by a mailing link back by `source_id` (`mass_mailing_crm` adds `crm_lead_count` to campaigns and mailings).
- **A/B testing**: `ab_testing_enabled`, `ab_testing_pc` (% of recipients per variant), `ab_testing_winner_selection` (opened/click/reply ratio — or, CRM-specific, **`crm_lead_count`**: the variant that generated the most leads wins), scheduled by cron.
- **Consent, two tiers** (a subtle but important design):
  1. **Per-list opt-out** — `mailing.subscription.opt_out` (+ `opt_out_reason_id` from `mailing.subscription.optout`, `opt_out_datetime`) on the `mailing_subscription` join between a contact and a list. Opting out of one list does not affect others.
  2. **Global blacklist** — `mail.blacklist` (+ `opt_out_reason_id`), an *address-level* suppression that removes an email/phone from *all* sends. Partners/leads/contacts expose `is_blacklisted` and `email_normalized` via `mail.thread.blacklist`; mailings honor both tiers via `use_exclusion_list` and `_mailing_get_opt_out_list`.
- **Segmentation primitives**: `mailing.filter` (saved domains), mailing-list membership, `mailing.contact.tag_ids` (reusing `res.partner.category`).

## 6. Segmentation and tags

- **Three separate tag taxonomies**, deliberately not merged: `crm.tag` (leads/opportunities), `res.partner.category` (partners — *hierarchical*, `_parent_store`, "Parent / Child" display), and `utm.tag` (campaigns). `mailing.contact` reuses `res.partner.category`.
- Lead-side classification also via `priority`, `stage_id`, `won_status`, `lost_reason_id`, and PLS score.
- Partner-side segmentation via `category_id`, `industry_id`, `is_company`, `commercial_partner_id` grouping, `res.partner.grade` (reseller tier), and `customer_rank`/`supplier_rank` (added by the sale/accounting layer).

## 7. Predictive lead scoring (PLS)

- Naive Bayes over configurable fields, per sales team. `crm.lead.scoring.frequency` stores per-(field, value) `won_count`/`lost_count` (with +0.1 smoothing); `crm.lead.scoring.frequency.field` configures which `crm.lead` fields feed the model (defaults: `phone_state, email_state, state_id, country_id, source_id, lang_id, tag_ids`).
- `_pls_get_naive_bayes_probabilities` → `automated_probability` (clamped 0.01–99.99; won-stage=100, no-stage=0). A manual `probability` edit overrides the automated value (`is_automated_probability`).
- Maintained live on every won/lost transition (`_handle_won_lost` → `_pls_increment_frequencies`) and rebuilt nightly in 50k-row batches (`_cron_update_automated_probabilities`).

## 8. The party model underneath — `res.partner`

- **One model for companies and people.** `is_company` (bool) is the discriminator; `company_type` is a UI-only selection mirroring it. Hierarchy via `parent_id`/`child_ids`. The **commercial entity** (`commercial_partner_id`, computed+stored+recursive) is the root company used for grouping, dedup ("same commercial entity"), and invoicing; commercial fields (`vat`, `industry_id`, `company_registry`) sync from the commercial entity down to child contacts (`_commercial_fields`).
- Address `type` (`contact`/`invoice`/`delivery`/`other`) supports multiple addresses per company. Tags via hierarchical `res.partner.category`. CRM adds `opportunity_ids`/`opportunity_count` (hierarchy-aware) to the partner; `sale`/`account` add `customer_rank`/`supplier_rank`.
- **On the lead, customer data is denormalized** (`partner_name`, `contact_name`, `commercial_partner_id` as a UX helper, plus email/phone/address) until conversion materializes real partners. This "capture loose, materialize on qualify" pattern is central to Odoo's funnel and is exactly the pattern a service shop does *not* need in the same shape (see the comparison doc).

## 9. Ecosystem map (addon → capability)

| Addon | Adds |
|---|---|
| `crm` | `crm.lead`, stages, tags, lost reasons, recurring plans, PLS, dedup/merge, conversion, activity report |
| `sales_team` | `crm.team`, `crm.team.member`, `crm.tag` base |
| `sale_crm` | links opportunities to quotations/orders (`order_ids`, `sale_amount_total`), auto-bumps expected revenue from linked SO |
| `mass_mailing` | `mailing.mailing`, `mailing.list`, `mailing.contact`, `mailing.subscription`(+optout), `mail.blacklist`, `mailing.filter`, campaign stats/A-B |
| `mass_mailing_crm` / `_sms` | makes `crm.lead` a mailing target; adds `crm_lead_count` A/B winner criterion |
| `crm_sms` | SMS send action on leads |
| `utm` | `utm.campaign`, `utm.source`, `utm.medium`, `utm.stage`, `utm.tag`, `utm.mixin` |
| `crm_iap_enrich` / `crm_iap_mine` | IAP company enrichment; external lead generation |
| `website_crm` | website form → lead; visitor tracking |
| `website_crm_partner_assign` | reseller geo-assignment; `res.partner.grade`/`activation` |
| `event_crm` | event registration → lead rules |

## Overall assessment

A mature, funnel-centric design whose center of gravity is the *pre-sale* motion: capture strangers, qualify them, route them, score them, forecast them, and market to them at scale. Its genuine strengths for Positivity are the **marketing/UTM/consent stack** (§5) and the **company-vs-individual party discrimination** (§8) that makes differentiated campaigns possible — precisely the areas the user flagged. Its opportunity-pipeline, predictive-scoring, and reseller-geo-assignment machinery (§1, §3, §7) is largely orthogonal to a shop where "the pipeline" is really *appointment → estimate → workorder → invoice* and already lives in `pos-shop-manager` + `pos-workorder`. The comparison and specification documents treat those two categories very differently.
