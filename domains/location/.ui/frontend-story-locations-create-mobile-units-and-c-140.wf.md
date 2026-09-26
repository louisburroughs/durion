# [FRONTEND] [STORY] Locations: Create Mobile Units and Coverage Rules
## Purpose
Setup screen, Location → Mobile Units (`/app/location/mobile-units?locationId=…`), plus two small supporting admin lists: Service areas and Travel buffer policies. A shop administrator decides which workorders each mobile unit based at one shop CAN be assigned.

Only three things decide that today:
- the unit is ACTIVE;
- it is based at the workorder's shop;
- one of its coverage rules, valid on the date, points at a service area that holds the customer's postal code.

Under the current API, capabilities, the travel buffer, the coverage rule type and max distance are stored but not used by eligibility or scheduling. The design shows them as "recorded", not as limits on assignment. (PROVISIONAL, Q9: if #2245 decides any of them should filter eligibility or affect scheduling, the "recorded" captions, hints and rail caption change.)

This is not a live view: no current job, route, location on a map, utilisation or schedule. Being set up and not active is a normal, calm state, not an error.

Implementation is tracked in louisburroughs/durion-positivity-frontend#395. Anything marked **PROVISIONAL** depends on an open question in louisburroughs/durion-positivity-backend#2245; see "Provisional (open in #2245)" at the end.

Replaces the earlier design: the free-text "Region" coverage field (which saved an invalid body), Max Daily Jobs (not in the API), and the FIXED_MINUTES / DISTANCE_TIER policy types (the policy types are now FLAT_MINUTES / PERCENTAGE_OF_TRAVEL / DISTANCE_MULTIPLIER).

## Components
- **Page header**
  - Overline "LOCATION SETUP", H1 "Mobile units", standfirst: "Decide where each unit based here can be sent. This page doesn't show where units are right now."
  - Base-location picker (existing `app-location-picker`, bound to `?locationId=`).
  - Primary action "New unit" (manage only).
  - Toggle "Show test records" (URL query `?tests=1`, off by default, shows the hidden count).
  - Setup links: "Service areas" and "Travel buffer policies", each shown only with the matching `:read` permission.
- **"Check coverage" band** (collapsible, below the header)
  - Postal code, country, a date (default today) and a [Check] button. The check calls the existing `GET /location/v1/mobile-units:eligible`, which requires `postalCode`, `countryCode` and `at`.
  - Country is a two-letter code field, hint "The country the postal code belongs to." It starts with the country most of the service areas' postal codes use; with no service areas, or none with codes, it starts as "US". The user can change it; the check never runs without a postal code.
  - The date is sent as `at` = noon UTC on the chosen day, so the backend reads the same date in any time zone.
  - The result is an ordered list: "1. Van 7, via Riverside north, priority 1", and so on. It closes with "Only active units based at Riverside Auto Service are included."
  - No match: "No active unit here covers A1B 2C3 on 1 Oct 2026."
- **Unit groups** (main column)
  - Active.
  - Set up, not active: every INACTIVE unit, with its checklist.
  - Each group has an H2 with its count, and cards sorted by natural name order.
- **Mobile-unit card** (see "Mobile-unit card anatomy").
- **Services catalog rail** (right column, sticky). The same component as the Bays page: overline "CATALOG", heading "Services", filter box, groups by operation category, a labelled drag handle per row.
  - On this page the rail's caption reads "Capabilities are recorded for each unit; assignment doesn't check them yet."
  - Same GAP as Bays: the rail starts as a search box until a catalog list endpoint exists.
- **Add capability dialog** (keyboard/touch route; `dialog[appModalDialog]`): the same filter and grouped checkbox list, with the button "Add N capabilities".
- **Unit create/edit dialog** (`dialog[appModalDialog]`).
- **Coverage editor dialog** (`dialog[appModalDialog]`, wide).
- **Confirm dialogs** (nested `dialog[appModalDialog]`): set inactive; delete unit; leave the coverage editor with unsaved changes.
- **Page-level live region** (`aria-live="polite"`); card-level `role="alert"` for refusals.
- **View-only notice** when `location:mobile-unit:manage` is missing: "View only: you can't change mobile units at Riverside Auto Service."
- **Service areas page** and **Travel buffer policies page** (see their sections below).

## Mobile-unit card anatomy
```
┌──────────────────────────────────────────────────┐
│ MOBILE UNIT                            [Inactive] │  overline; status chip (always)
│ Van 7                                             │  H3 name
│ Not sent out until it's active.                   │  "Can be sent" line (always)
│                                                   │
│ READY TO ACTIVATE                                 │  checklist (INACTIVE only)
│ ✓ Travel buffer policy     Standard 15 min        │
│ ✓ At least one capability  2 recorded             │
│ ✗ At least one coverage rule   [Add coverage]     │
│ [Activate]  (unavailable until all three are done)│
│                                                   │
│ COVERAGE                                          │  coverage chips (always; empty text if none)
│ Today  [Riverside north · 42 codes]               │
│ From 1 Oct  [Eastside · 18 codes]                 │
│ ▸ 1 past rule                                     │
│                                                   │
│ CAPABILITIES  Recorded, not used for assignment yet│ capability chips (always)
│ [Tire repair, patch/plug ×] [TPMS service ×]      │
│                                                   │
│ Travel buffer  Standard, 15 minutes flat ·        │  facts (always)
│ recorded, not applied yet                         │
│ Notes  Parks at the north lot overnight…          │  notes preview (only when set)
│                                                   │
│ [+ Add capability] [Edit coverage] [Edit] [⋯]     │  actions (manage only); ⋯ = Duplicate, Set inactive, Delete
└──────────────────────────────────────────────────┘
```
| Slot | Always / conditional | Content |
| --- | --- | --- |
| Overline | Always | "MOBILE UNIT" |
| Status chip | Always | "Active" (success pair) or "Inactive" (neutral pair) |
| Name | Always | H3 |
| "Can be sent" line | Always | One whole-sentence case (see Notes) |
| Activation checklist | INACTIVE only | Three rows, each a symbol plus words, with the current value or a fix-it button; then Activate |
| Coverage warnings | Conditional | ACTIVE with no rule in effect today; a rule points at a switched-off area ("still counts"); an area with no postal codes ("covers nobody") |
| Coverage chips | Always | Service area name and postal-code count, grouped as Today / From {date} / past (collapsed). Empty: "No coverage yet." |
| Capability chips | Always | Service names under the heading "Capabilities", captioned "Recorded, not used for assignment yet". Empty: "None recorded." |
| Travel buffer | Always | Policy name and its value in words, then "recorded, not applied yet". None: "No travel buffer policy." |
| Notes preview | When notes are set | Two lines, then "Show all notes" |
| Drop affordance | Only during a drag | Dashed outline and "Drop to record for Van 7" |
| Actions | Manage only | Add capability, Edit coverage, Edit, overflow (Duplicate, Set inactive or Activate, Delete) |

## Layout
```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│ LOCATION SETUP                                          Service areas · Travel buffer policies│
│ Mobile units                                        [Base location: Riverside Auto Service ▾]│
│ Decide where each unit based here can be sent. This page doesn't show where units are right │
│ now.                                              [ ] Show test records (1 hidden)  [New unit]│
├─────────────────────────────────────────────────────────────────────────────────────────────┤
│ ▌CHECK COVERAGE  Postal code [A1B 2C3 ]  Date [2026-10-01]  [Check]                   [Close]│
│   1. Van 7, via Riverside north, priority 1                                                 │
│   2. Van 9, via Eastside, priority 2                                                        │
├───────────────────────────────────────────────────────────────┬─────────────────────────────┤
│ Active  1                                                     │ CATALOG                     │
│ ┌────────────────────────┐                                    │ Services                    │
│ │ Van 9                  │                                    │ Capabilities are recorded   │
│ └────────────────────────┘                                    │ for each unit; assignment   │
│                                                               │ doesn't check them yet.     │
│ Set up, not active  2                                         │ [Filter services…        ]  │
│ ┌────────────────────────┐ ┌────────────────────────┐         │ ▾ Tires                     │
│ │ Van 7  (checklist)     │ │ Van 11 (checklist)     │         │   ⠿ Tire repair, patch/plug │
│ └────────────────────────┘ └────────────────────────┘         │     TIRE-REPAIR-PATCH-PLUG  │
└───────────────────────────────────────────────────────────────┴─────────────────────────────┘
```
- Same frame as the Bays page, so the two setup pages read as one family: an asymmetric main column with a `--space-8` left gutter, a 20rem sticky rail on `--surface-inset`, and a full-width band with the page's only `--primaryA400` accent bar.
- Unit cards are wider than bay cards (grid minimum about 22rem) because they carry more rows.
- No base location selected: show only the prompt "Choose the shop these units are based at."
- Empty: "No mobile units are based at Riverside Auto Service yet." and [New unit].

### Unit create/edit dialog
```
┌ New mobile unit at Riverside Auto Service ─────────────────────────────── [×] ┐
│ Name *                 [Van 12                          ]                      │
│                        (hint)                                                  │
│ Base location *        [Riverside Auto Service        ▾]                      │
│                        (hint)                                                  │
│ Travel buffer policy   [Standard (15 minutes flat)    ▾]  Recorded, not applied yet│
│                        (hint)                                                  │
│ Capabilities           [Tire repair ×] [+ Add]      Recorded, not used yet     │
│                        (hint)                                                  │
│ Notes                  [                                ]                      │
│                        (hint)                                                  │
│ ─ create only ─                                                                │
│ New units start inactive. Add coverage next, then activate from the card.     │
│ ─ edit only ─                                                                  │
│ Status  (•) Inactive  ( ) Active     checklist shown inline under Active       │
├────────────────────────────────────────────────────────────────────────────────┤
│                                             [Cancel]  [Create unit]            │
└────────────────────────────────────────────────────────────────────────────────┘
```
- Create never offers Active. Coverage can only be saved once the unit exists, and a unit without coverage can't be activated, so create goes straight on to "Add coverage for Van 12?" [Add coverage] [Later].
- Edit shows Status. The Active radio is `aria-disabled` while the checklist is incomplete, and is described by the checklist shown beneath it.
- Base location defaults to the page's picker. If the user changes it, a line appears: "Saving moves Van 12 to Eastside's list; it will leave this page."

### Coverage editor
```
┌ Coverage for Van 7 ──────────────────────────────────────────────────────────────── [×] ┐
│ Where this unit goes. A customer is matched by postal code: the rule's service area     │
│ must include it, on the dates the rule is valid.                                        │
│ In effect today: 1 rule · next change 1 Oct 2026                                        │
│                                                                                          │
│  #  Service area *            Priority *  Valid from   Valid to     Recorded only        │
│  1  [Riverside north ▾]       [ 1 ]       [2026-09-01] [          ] Service area ▾  [×] │
│     42 postal codes                                                                      │
│  2  [Eastside        ▾]       [ 2 ]       [2026-10-01] [          ] Distance tier ▾ [×] │
│     18 postal codes · switched off, still counts            Max distance [ 25 ] km       │
│                                                                                          │
│ [+ Add rule]                                          Manage service areas ↗             │
│                                                                                          │
│ Saving replaces all of this unit's coverage rules.                                       │
├──────────────────────────────────────────────────────────────────────────────────────────┤
│                                                     [Cancel]  [Save coverage]            │
└──────────────────────────────────────────────────────────────────────────────────────────┘
```
- Rules are ledger rows on alternating tints (`--cardBackground` / `--surface-inset`), never divider lines.
- Each row: Service area (select), Priority (number), Valid from, Valid to, then a "Recorded only" group of Rule type (Service area / Distance tier) and, for distance tiers only, Max distance.
- The "Recorded only" group is visually secondary: `--text-muted` labels, and its own group label "Recorded only: not used for matching yet".
- Under each area select: the area's postal-code count, plus flags "switched off, still counts" or "no postal codes, covers nobody".
- Narrow screens: each rule becomes a stacked group with its own heading, "Rule 1".

## Interaction Flow
1. **Open the page.** The picker restores `?locationId=`. The page loads, in parallel:
   - the units for the base location, each with its coverage rules;
   - the service areas list, for names, postal-code counts and the active flag;
   - the travel buffer policies, for names and values;
   - the catalog.

   Cards render when the units arrive, and the enrichment fills in as each read lands. A read that fails shows "name unavailable", never an id (ADR-0064).
2. **Create a unit.** "New unit" → dialog → Create. The unit is saved INACTIVE.
   - On 201 the card appears under "Set up, not active" with focus on it, and the dialog offers "Add coverage for Van 12?".
   - 409 on name: "Another unit based here is already called Van 12" appears under Name.
3. **Complete and activate.**
   - The checklist rows turn ✓ as each requirement is saved. Each ✗ row has its own fix-it button:
     - "Choose a policy" opens Edit with focus on Travel buffer policy;
     - "Add capability" opens the Add capability dialog;
     - "Add coverage" opens the coverage editor.
   - Activate is `aria-disabled` until all three are ✓. Pressing it early moves focus to the first ✗ row and announces "Van 7 needs a coverage rule before it can be activated."
   - When complete, Activate saves Status ACTIVE. The card moves to Active with focus, and the region announces "Van 7 is active and can be sent to customers in 1 area."
   - A 422 is shown as `role="alert"` in the card, naming the missing requirement.
4. **Drag a service onto a unit** (manage only; pointer devices). A drop adds the capability and saves the whole list at once. The chip is shown pending, and then:
   - Success: "Tire repair recorded for Van 7." [Undo]. The message never says the unit "can now take" the service.
   - Refusal: the chip is removed and a `role="alert"` message names the reason.
   - A duplicate drop is refused with "Already recorded for Van 7."
5. **Remove a capability** (chip ×, or drag the chip onto the rail).
   - Removing the last capability of an ACTIVE unit is refused in the UI before any request is sent. The message: "An active unit needs at least one capability. Set Van 7 inactive to remove its last one." [Set inactive]
   - On an INACTIVE unit, removal is immediate, with Undo.
6. **Edit coverage.** "Edit coverage" opens the editor with the unit's rules; Add rule appends a row with focus on its Service area. Client checks, each shown under its field:
   - Service area and priority are required, and priority must be a whole number. Any further range limits come from the API's 400/422 field errors.
   - Valid to can't be before valid from.
   - Among distance-tier rules, max distance must strictly increase and end with exactly one blank (catch-all).
   - Zero rows on an ACTIVE unit: Save is `aria-disabled`, with "An active unit needs at least one coverage rule. Set Van 7 inactive first, or add a rule."

   On Save, the full list replaces the old one, the card's coverage chips update, and focus returns to "Edit coverage". Following "Manage service areas" with unsaved rows asks "Leave without saving coverage?" first.
7. **Set inactive.** Overflow → Set inactive opens a confirm: "Van 7 won't be offered for new workorders. Any already assigned stay." PROVISIONAL (Q7): whether booked future appointments are re-checked.
8. **Duplicate.** Overflow → Duplicate opens the create dialog prefilled with the base location, policy, capabilities and notes; the name is "Van 7 (copy)". The copy is always created inactive. After create, the dialog offers "Copy Van 7's coverage too?", which saves the same rules on the copy.
9. **Delete.** Overflow → Delete opens a confirm: "Delete Van 7? It will no longer be offered for new work." (PROVISIONAL, Q6.) Focus then moves to the next card, or to the group heading.
10. **Check coverage.** Enter a postal code and date, then Check. The ordered result list is a polite live region. An empty postal code shows an inline error; the check never runs with a blank value.

## Field Hints
| Field | Hint |
| --- | --- |
| Name | What dispatchers call this unit, e.g. "Van 7". No other unit based at this shop can have the same name. |
| Base location | The shop this unit leaves from. It takes only that shop's workorders. |
| Status (edit) | Only active units can be assigned workorders. To activate, a unit needs a travel buffer policy, at least one capability and at least one coverage rule. |
| Travel buffer policy | Extra time allowed around each visit for driving. Recorded now; scheduling doesn't use it yet. (PROVISIONAL, Q9) |
| Capabilities | The services this unit is equipped for. Recorded now; assignment doesn't check them yet. (PROVISIONAL, Q9) |
| Notes | Anything dispatchers should know about this unit, e.g. where it parks overnight. |
| Coverage (editor intro) | Where this unit goes. A customer is matched by postal code: the rule's service area must include it, on the dates the rule is valid. |
| Service area | The postal codes this rule covers. Manage areas under Service areas. |
| Priority | Lower numbers are offered first. Compared across every unit here that covers the same postal code. |
| Valid from / Valid to | The dates this rule applies. Leave Valid to blank for no end date. |
| Rule type | Recorded for later. Matching uses the service area only. (PROVISIONAL, Q9) |
| Max distance | Recorded for later; not used for matching. Leave blank for the catch-all tier. (PROVISIONAL, Q9) |

## Service areas (supporting list)
- **Scope decision.** Per DECISION-LOCATION-011 (amended 2026-09-26), service areas are edited only on this page. pos-location already owns them and exposes the writes (`POST /v1/service-areas`, `PATCH /v1/service-areas/{id}`, `PUT /v1/service-areas/{id}/postal-codes`, all behind `location:service-area:manage`). The coverage editor stays picker-only and links here for changes. Delivered in louisburroughs/durion-positivity-frontend#402; it adds no new endpoint, entity or ownership.
- Route `/app/location/service-areas`, gated on `location:service-area:read`; edit controls need `:manage`.
- Linked from:
  - the Mobile Units header;
  - the coverage editor;
  - a new card in the Location landing "Resources" section, whose permissions must match the route.
- Page: overline "LOCATION SETUP", H1 "Service areas", standfirst "Named groups of postal codes that mobile-unit coverage rules point at."
- The list is ledger rows on alternating tints:
  - Name.
  - Description (1 line).
  - Status: "Active", or "Switched off" with the flag "Units covering it are still matched".
  - Postal codes: count and the first 3 samples.
  - An area with zero codes gets the warning "Covers nobody".
- Create/edit dialog:
  - Name: "A short name dispatchers recognise, e.g. "Riverside north". Must be unique."
  - Description: "Optional. What the area covers, in words."
  - Active: "Marks the area as in use. Today, units covering a switched-off area are still matched."
  - Postal codes: a Country select, then a paste box, "Paste codes one per line or separated by commas". The parsed codes appear as removable chips with a count, plus a "Remove all" button. Duplicates are dropped with the note "2 duplicates ignored".
  - Hint on postal codes: "Saving replaces the whole list. An area with no postal codes covers nobody."
- The paste box is plain text input; the codes are shown as text (ADR-0065).

## Travel buffer policies (supporting list)
- Route `/app/location/travel-buffer-policies`, gated on `location:travel-buffer-policy:read`; edit controls need `:manage`. Linked from the Mobile Units header, from the policy field hint in the unit dialog, and from a Location landing card.
- A neutral banner at the top: "Travel buffers are recorded on mobile units. Scheduling doesn't use them yet."
- The list is ledger rows: Name, Buffer in words ("15 minutes flat", "20% of travel time", "1.5 × distance"), Notes (1 line).
- Create/edit dialog:
  - Name: "Can't be changed after the policy is created." On edit it is read-only text with that hint.
  - Buffer type (select): Flat minutes / Percentage of travel time / Distance multiplier. Hint: "How the extra time is worked out."
  - Buffer value: a number whose suffix follows the type ("minutes", "%", "× distance"). Hint: "Minutes for flat, a percentage for travel time, a multiplier for distance."
  - Notes.
- Seeded policies whose stored type is the invalid "MINUTES" show a warning chip "Type needs fixing". Editing one opens with Buffer type empty and required. This affordance is temporary until the pending data fix lands; remove it afterwards.

## Notes
- **"Can be sent" line.** Each case is a whole-sentence i18n key with parameters (ADR-0030).
  - Active with coverage today: "Can be sent to customers in {n} areas ({codes} postal codes) today."
  - Active, no rule in effect today: "Active, but no coverage rule is in effect today, so no customer matches it." This case is a warning.
  - Inactive: "Not sent out until it's active."
  - When a date change is known, append "Next change {date}."
- **Deferred.** Overlap rank with other units: the Check coverage band answers it exactly from the backend. Also deferred: max vehicle class, plate/VIN/asset number, usual crew, changed-by (not returned today), and mobile-unit hours (PROVISIONAL, Q10: no field until answered).
- **Dates.** Valid from/to and the check date follow ADR-0038. If the contract field is date-only, never pass it through `new Date()`. "Today" means today in the base location's time zone.
- **Permissions.** Without `location:mobile-unit:manage`, the drag handles, chip ×, fix-it buttons, Activate, Edit, Edit coverage and the overflow menu are absent, and the view-only notice explains why. The checklist still shows, read-only.
- **Drag technology.** Native HTML5 drag and drop, as on the dispatch board and the Bays page. No new library.

## Accessibility
- Every drag has a pointer-free route: Add capability, chip × and the Add capability dialog (ADR-0029 rule 13; WCAG 2.5.7). Drag handles are labelled as handles and described by "Or use Add capability on a unit card".
- Cards: each group is a `section` with an H2 and a `ul`; each card is an `li` holding an `article` with an H3.
- Controls name their target (rule 12): "Activate Van 7", "Edit coverage for Van 7", "Remove Tire repair from Van 7", "Remove rule 2".
- The checklist is a `ul`. Each row shows a symbol and states the result in words ("Done" / "Missing"), which are included in its accessible text. Activate uses `aria-disabled`, not `disabled`, so it stays focusable, and it is described by the checklist.
- "Recorded, not used yet" captions are real text next to their fields, linked with `aria-describedby`. They are never tooltips (rule 4).
- Hints go through `aria-describedby`, never `aria-label` (rule 3). The Label in Name rule applies to every control (rule 6), and every target is at least 24×24 CSS px (rule 5).
- Live feedback: one polite page region; `role="alert"` for refusals; the Check coverage result is a polite region; the pending state has visually hidden "Saving" text (rule 8).
- Focus, following ADR-0029 rule 7:
  - it follows a card that moves group;
  - Add rule focuses the new row's Service area;
  - Remove rule focuses the next row's remove button, else Add rule;
  - dialogs return focus to their invoker.
- All dialogs, including nested confirms, are native `dialog[appModalDialog]`.
- Notes and area descriptions are untrusted text and are rendered as text, never as HTML (ADR-0065).

## Provisional (open in louisburroughs/durion-positivity-backend#2245)
Q numbers match the questions in louisburroughs/durion-positivity-backend#2245.

| Q | Open question | Design element marked PROVISIONAL |
| --- | --- | --- |
| Q6 | Delete is not blocked | Delete confirm copy and placement |
| Q7 | Future appointments aren't re-checked when status changes | Set-inactive confirm copy. If confirmed, add "Appointments already booked with Van 7 aren't moved." |
| Q9 | Mobile-unit eligibility: whether capabilities, `ruleType` / `maxDistance` and the travel buffer should filter eligibility or affect scheduling | The "recorded" captions on capabilities and travel buffer, the rail caption, the Purpose statement, and the Travel buffer policy / Capabilities / Rule type / Max distance hints. If any is applied, the copy says how. |
| Q10 | Mobile-unit hours | No hours field or copy until answered |
