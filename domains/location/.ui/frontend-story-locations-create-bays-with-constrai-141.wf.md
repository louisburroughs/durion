# [FRONTEND] [STORY] Locations: Create Bays with Constraints and Capacity
## Purpose
Setup screen, Location → Bays (`/app/location/bays?locationId=…`). A shop administrator decides which workorders each bay at one location CAN be assigned: its type, whether it is in service, how many vehicles fit, the heaviest vehicle it takes, and the specialty services it claims. Specialty services are assigned by dragging them from a service catalog list onto a bay card, with an "Add service" button on every card as the keyboard and touch route.

This is not a live view. The page never shows the current workorder, occupancy, utilisation, technicians or a schedule; those belong to the dispatch board and bay detail views. Every sentence on the page describes setup: what a bay is allowed to take.

Implementation is tracked in louisburroughs/durion-positivity-frontend#395. Anything marked **PROVISIONAL** depends on an open question in louisburroughs/durion-positivity-backend#2245 and must not ship with its copy locked; see "Provisional (open in #2245)" at the end.

Replaces the earlier design: "Required Skills" is gone (skills moved to the catalog), the card-as-button grid is replaced by cards with distinct actions, and create/edit takes every field the API requires.

## Components
- **Page header**
  - Overline "LOCATION SETUP", H1 "Bays", one-line standfirst: "Decide which workorders each bay can be assigned. This page doesn't show what's in a bay right now."
  - Location picker (existing `app-location-picker`, bound to `?locationId=`).
  - Primary action "New bay" (manage only).
  - Toggle "Show test records" (off by default; shows the hidden count, e.g. "3 hidden"). State lives in the URL query (`?tests=1`), not browser storage.
- **"Who does what" band** (collapsible, below the header)
  - Collapsed: a one-line summary, e.g. "4 specialty services claimed · 2 have only one bay · 1 has no bay in service", with an Open/Close toggle.
  - Expanded: a ledger list with one row per claimed specialty service: service name, operation code (mono), the bays claiming it, a flag.
    - Flag "Only one bay": one active bay claims it.
    - Flag "Its only bay is out of service": the only claiming bays are out of service. Their claims don't count, so any bay except wash & detail can be assigned it for now. (PROVISIONAL, Q2)
  - Closing line: "Every other service is general work: any bay except wash & detail can be assigned it, general bays first."
- **Bay lanes** (main column). Lanes are derived from the data; a card is never dragged between lanes.
  - General: active, not wash & detail, no specialty services.
  - Specialty: active, not wash & detail, one or more specialty services.
  - Wash & detail: active, type WASH_DETAIL.
  - Out of service: status OUT_OF_SERVICE, any type. Collapsed by default, showing its count.
  - Each lane has an H2 with its count, and a card grid sorted by natural name order ("Bay 2" before "Bay 10").
- **Bay card** (see "Bay card anatomy" below).
- **Services catalog rail** (right column, sticky)
  - Overline "CATALOG", heading "Services", filter box "Filter services".
  - Groups by operation category (disclosure per group). Each row shows the service name, the operation code in mono, and on this page who claims it here ("General work", "Only Bay 3", "Bays 3, 5").
  - Each row has a drag handle, labelled "Drag Wheel alignment (4-wheel)" and described by "Or use Add service on a bay card".
  - While a chip is being dragged, the rail becomes a remove target ("Drop here to remove from Bay 3").
  - GAP: there is no catalog list endpoint yet, only a name search that needs a term and returns at most 100. Until a list endpoint exists, the rail starts as a search box with the prompt "Type at least 2 letters to find a service", and groups whatever the search returns.
- **Add service dialog** (keyboard/touch route; `dialog[appModalDialog]`)
  - Title "Add services to Bay 3". Uses the same filter and grouped list as the rail, with a checkbox per service; services already on the bay are shown checked and disabled, captioned "Already on this bay".
  - Consequence line updates as boxes are ticked (see "Drop and add consequences").
  - Actions "Add N services" and "Cancel".
- **Bay create/edit dialog** (`dialog[appModalDialog]`, wide): form column and "What changes" preview column.
- **Confirm dialogs** (nested `dialog[appModalDialog]`): remove the last specialty service; delete bay.
- **Page-level live region** (`aria-live="polite"`) for save outcomes and lane moves; card-level `role="alert"` for refusals.
- **View-only notice** shown instead of edit controls when the user lacks `location:bay:manage`, or gets 403 `LOCATION_SCOPE_DENIED`: "View only: you can't change bays at Riverside Auto Service."

## Bay card anatomy
```
┌──────────────────────────────────────────────┐   surface: --cardBackground on --themeBackground
│ TIRE SERVICE                  [Out of service]│   overline = bay type in words; status chip ONLY when out of service
│ Bay 3                                         │   H3 name
│ Can be assigned 9 specialty services, plus    │   "Can be assigned" line (always; wording PROVISIONAL, Q1)
│ general services after the general bays ·     │
│ vehicles up to class 6 (medium)               │
│                                               │
│ [Tire install, set of 4 ×] [Wheel balance ×]  │   specialty chips by NAME, max 4, then "+5 more"
│ [TPMS sensor service  Only bay ×] [+5 more]   │   "Only bay" marker when this is the only active claimant
│                                               │
│ Vehicles at once 2      Max duty class 6      │   facts row (always)
│                                               │
│ ! Its specialty services don't count while    │   warnings (conditional; out-of-service line PROVISIONAL, Q2)
│   it's out of service.                        │
│                                               │
│ [+ Add service]            [Edit]  [⋯]        │   actions (manage only); ⋯ = Duplicate, Delete
└──────────────────────────────────────────────┘
```
| Slot | Always / conditional | Content |
| --- | --- | --- |
| Overline | Always | Bay type in words (General service, Alignment, Tire service, Heavy duty, Inspection, Wash & detail) |
| Status chip | Only when OUT_OF_SERVICE | "Out of service" (warning pair). Active is the normal state and carries no chip. |
| Name | Always | H3 |
| "Can be assigned" line | Always | One whole-sentence message per case (see Notes); ends with the duty class in words |
| Specialty chips | When the bay has codes | Service name; operation code in mono only if the name can't be read; "Only bay" marker; × remove (manage only) |
| Empty specialty slot | No codes, not wash | "General bay: no specialty services." |
| Facts row | Always | Vehicles at once; Max duty class as "6 (medium)" or "No limit" |
| Warnings | Conditional | Out of service: claims not counting (PROVISIONAL, Q2). Wash & detail with no services: can be assigned nothing (PROVISIONAL, Q4). A code the catalog reports inactive: "Tire repair is no longer active in the catalog." |
| Drop affordance | Only during a drag | Dashed outline and "Drop to add to Bay 3" |
| Actions | Manage only | Add service, Edit, overflow menu (Duplicate, Delete) |

## Layout
```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│ LOCATION SETUP                                                                              │
│ Bays                                              [Location: Riverside Auto Service ▾]      │
│ Decide which workorders each bay can be assigned. This page doesn't show what's in a bay    │
│ right now.                                       [ ] Show test records (3 hidden)  [New bay]│
├─────────────────────────────────────────────────────────────────────────────────────────────┤
│ ▌WHO DOES WHAT   4 specialty services claimed · 2 have only one bay · 1 has no bay   [Open] │
├───────────────────────────────────────────────────────────────┬─────────────────────────────┤
│ General  2                                                    │ CATALOG                     │
│ ┌──────────────┐ ┌──────────────┐                             │ Services                    │
│ │ Bay 1        │ │ Bay 2        │                             │ [Filter services…        ]  │
│ └──────────────┘ └──────────────┘                             │ ▾ Alignment                 │
│                                                               │   ⠿ Wheel alignment, 4-wheel│
│ Specialty  2                                                  │     WHEEL-ALIGNMENT-4-WHEEL │
│ ┌──────────────┐ ┌──────────────┐                             │     Only Bay 3              │
│ │ Bay 3        │ │ Bay 5        │                             │ ▾ Tires                     │
│ └──────────────┘ └──────────────┘                             │   ⠿ Tire install, set of 4  │
│                                                               │     TIRE-INSTALL-SET-4      │
│ Wash & detail  1                                              │     General work            │
│ ┌──────────────┐                                              │ ▸ Inspection                │
│ │ Wash bay     │                                              │                             │
│ └──────────────┘                                              │                             │
│                                                               │                             │
│ ▸ Out of service  1                                           │                             │
└───────────────────────────────────────────────────────────────┴─────────────────────────────┘
```
- Asymmetric two columns: the main column takes the remaining width, with a wider left gutter (`--space-8`); the rail is fixed at about 20rem, sits on `--surface-inset`, and stays in view while the main column scrolls.
- The "Who does what" band is full width and sits on `--surface-inset`, with a 2px `--primaryA400` accent bar on its left edge. It is the only accent bar on the page.
- Lanes are separated by whitespace (`--space-8`), never by rules. Cards within a lane: `--space-6` gap.
- No location selected: the page shows only the header and the prompt "Choose a location to set up its bays." The band, lanes and rail are absent.
- Empty location: in place of the lanes, "No bays at Riverside Auto Service yet. Add one for each space you book work into." with a [New bay] button. The rail stays visible.

### Bay create/edit dialog
```
┌ New bay at Riverside Auto Service ──────────────────────────────────────────────── [×] ┐
│ Name *                                   │ WHAT CHANGES                                 │
│ [Bay 4                    ]              │ ▌ Bay 4 will be the only bay for Wheel       │
│ What staff call this bay, e.g. "Bay 3".  │   alignment (4-wheel) here. Bay 3 stops      │
│ No other bay here can have this name.    │   taking it.                                 │
│                                          │ ▌ Vehicles above class 6 will have no bay    │
│ Bay type *                               │   here. (PROVISIONAL, Q5)                    │
│ [Alignment                         ▾]    │                                              │
│ The kind of work this bay is set up for. │ Workorders already on this bay stay.         │
│ A new bay starts with that type's usual  │                                              │
│ specialty services; change them below.   │                                              │
│                                          │                                              │
│ Vehicles at once *     Max duty class    │                                              │
│ [ 1 ]                  [No limit     ▾]  │                                              │
│ (hint)                 (hint)            │                                              │
│                                          │                                              │
│ Status   (•) Active  ( ) Out of service  │                                              │
│ (hint)                                   │                                              │
│                                          │                                              │
│ Specialty services                       │                                              │
│ Filled in from Alignment.                │                                              │
│ [Wheel alignment, 4-wheel ×] [+ Add]     │                                              │
│ [ ] No specialty services (general bay)  │                                              │
│ (hint)                                   │                                              │
├──────────────────────────────────────────┴──────────────────────────────────────────────┤
│                                               [Cancel]  [Create bay]                    │
└──────────────────────────────────────────────────────────────────────────────────────────┘
```
- Field order: Name, Bay type, Vehicles at once and Max duty class (side by side when there is room), Status, Specialty services.
- Status is a two-option radio group, not a switch: out of service is a state with consequences, not an on/off preference.
- Max duty class is a select: "No limit", then "1 (light)" … "3 (light)", "4 (medium)" … "6 (medium)", "7 (heavy)", "8 (heavy)".
- The "What changes" column sits on `--surface-inset` and lists consequences one per line, each with a 2px `--primaryA400` left bar. When nothing changes for other bays or vehicles it reads "No change to what other bays can be assigned." On narrow screens it moves below the form, and the footer carries a link "2 changes to who does what: review" that moves focus to it.

## Interaction Flow
1. **Open the page.** The picker restores `?locationId=`. The page loads the bays for the location and the catalog (list, or search while the GAP stands) in parallel. Cards render as soon as the bays arrive; specialty chip names fill in when the catalog read lands. Until then chips show a skeleton, never a raw id.
2. **Test records.** Names matching the SDK test pattern (e.g. "Itest bay …") are hidden by default and counted in the toggle. Turning the toggle on shows them in their lanes with a neutral "Test record" chip. Detection is by name until the backend flags test records.
3. **Drag a service onto a bay** (manage only; pointer devices).
   1. The user grabs a rail row by its handle. Valid bay cards show the drop affordance. Out of service bays accept drops. A card already holding the service shows "Already on Bay 3" and refuses the drop.
   2. On drop the chip appears at once in a pending state, and the bay's whole specialty list is saved (PUT replaces the list).
   3. Success: the chip settles and the page live region announces the consequence (see "Drop and add consequences") with an **Undo** button in the toast. Undo saves the previous list. If the bay changes lane (General → Specialty), the card moves after the save confirms, focus moves with it (ADR-0029 rule 7), and the announcement includes "Bay 2 moved to Specialty".
   4. Refusal (422 inactive code, 403 scope, 409): the chip is removed, and the card shows a `role="alert"` message, e.g. "Couldn't add Tire repair to Bay 3: it's no longer active in the catalog."
   5. Only one save runs per card at a time; a drop on a card that is still saving is refused with "Bay 3 is still saving."
4. **Add without dragging.** "Add service" on a card opens the Add service dialog with focus in the filter box. Ticking services shows the consequence line live; "Add N services" saves once, closes the dialog, and returns focus to the card's Add service button, or to the moved card.
5. **Remove a service.** Either press the chip's ×, or drag the chip onto the rail.
   - Not the last one: the service is removed at once, with an Undo toast and the consequence, e.g. "Wheel alignment is now general work: any bay except wash & detail can be assigned it."
   - The last one: a confirm opens first: "Remove Wheel alignment? Bay 3 becomes a general bay and can be assigned any general service." For a wash & detail bay the confirm reads: "Bay 7 will have no services and can't be assigned anything." (PROVISIONAL, Q4)
   - After removal, focus moves to the next chip, else to Add service.
6. **Create a bay.** "New bay" opens the dialog with Status Active and Vehicles at once 1.
   - Picking a type fills in that type's usual specialty services and shows the caption "Filled in from Tire service."
   - The user may remove any of them, add more, or tick "No specialty services (general bay)". The tick clears the list and disables the chip area, and the dialog always sends the list the user sees.
   - The preview column updates on every change.
   - Save: 201 closes the dialog, the card appears in its lane with focus on it, and the region announces "Bay 4 added to Specialty."
   - 409: "Another bay here is already called Bay 4" appears under Name, and focus moves to Name.
   - 422 on a code: a message under Specialty services names that service.
7. **Edit a bay.** "Edit" opens the same dialog, prefilled.
   - Changing the type shows a choice under Bay type:
     - (•) Use Alignment's usual services (1): Wheel alignment, 4-wheel
     - ( ) Keep this bay's current services (9)

     The first choice replaces the list in the preview. The second sends the current list in the same save, which stops the backend reset.
   - Status Out of service, lowering Max duty class, and type changes all feed the preview.
   - When the preview lists changes, the Save button reads "Save and apply 2 changes" (keeping the Label in Name rule), so a save that changes eligibility is never silent.
8. **Duplicate.** Overflow → Duplicate opens the create dialog prefilled from the bay: type, vehicles, duty class and specialty services. Name is "Bay 3 (copy)", selected for editing.
9. **Delete.** Overflow → Delete opens a confirm: "Delete Bay 3? It will no longer be offered for new work." (PROVISIONAL, Q6.) Only the bay itself is named in the copy until Q6 is answered. On success, focus moves to the next card in the lane, or else to the lane heading.
10. **Who does what.** The Open/Close toggle expands the band. Each row's bay names are buttons that scroll to that card and move focus to it.
11. **Changing location** clears the pending undo and reloads everything. Stale responses from the previous location are dropped (ADR-0063).

## Field Hints
Every field has a visible hint below it, attached with `aria-describedby`. Hints are plain statements of effect; none mention scheduling behaviour that isn't built.

| Field | Hint |
| --- | --- |
| Name | What staff call this bay, e.g. "Bay 3". No other bay at this location can have the same name. |
| Bay type (create) | The kind of work this bay is set up for. A new bay starts with that type's usual specialty services; change them below. |
| Bay type (edit) | The kind of work this bay is set up for. Changing it replaces the specialty services with the new type's usual ones, unless you keep the current ones. |
| Bay type = Wash & detail | Wash & detail bays take only the services you add here, never general services. |
| Status | Out of service stops new workorders coming to this bay; any already on it stay. While it's out, its specialty services open up to other bays. (PROVISIONAL, Q2) |
| Vehicles at once | How many vehicles physically fit. The bay still takes one open workorder at a time, so add a separate bay for each stall you book separately. |
| Max duty class | The heaviest vehicle this bay takes: classes 1–3 light, 4–6 medium, 7–8 heavy. Choose No limit if any vehicle fits. Vehicles with no class on record aren't checked. (PROVISIONAL, Q5) |
| Specialty services | Services that only bays claiming them can be assigned at this location. Leave empty for a general bay, which takes any service no bay here claims. |

## Drop and add consequences
Always name the service, the bay and the location in the message.

| Situation | Message |
| --- | --- |
| First active bay here to claim the service | Only Bay 3 can now be assigned Wheel alignment (4-wheel) at Riverside Auto Service. Other bays stop taking it. [Undo] |
| Another active bay already claims it | Bays 3 and 5 can be assigned Wheel alignment (4-wheel). [Undo] |
| The drop is onto an out-of-service bay | Added to Bay 3. It won't count until Bay 3 is back in service. [Undo] |
| Removal leaves no active claimant | Wheel alignment (4-wheel) is now general work: any bay except wash & detail can be assigned it. [Undo] |

## Notes
- **"Can be assigned" line.** Each case is its own whole-sentence i18n key with parameters (ADR-0030); fragments are never concatenated.
  - General: "Can be assigned any general service, offered before specialty bays · {duty}"
  - Specialty: "Can be assigned {n} specialty services, plus general services after the general bays · {duty}". When the location has no general bays, the sentence ends "plus general services · {duty}".
  - Wash with services: "Can be assigned only its {n} services, never general services · {duty}"
  - Wash without services: "Can't be assigned anything yet. Add the services this bay does." (PROVISIONAL, Q4)
  - Out of service: "Out of service: no new workorders. Its specialty services are open to other bays until it's back." (PROVISIONAL, Q2)
  - `{duty}` is "vehicles up to class 6 (medium)" or "any vehicle".
- **Derivation.** The line, the lanes, the "Only bay" markers, the band and the preview are computed client-side from this location's bay list, using only the confirmed rules in the brief. Put the rules in one pure function with unit tests per branch, so the rules exist once in the frontend.
- **Type defaults.** No API exposes the type → default specialty codes map. Until one does, the map lives in one frontend constant that mirrors pos-location. Create always sends an explicit list, so what the user sees is what gets saved. GAP: ask for the defaults to be exposed.
- **Read outcomes (ADR-0064).**
  - If the catalog read fails, chips show the operation code in `--font-mono` with "(name unavailable)". Operation codes are business codes the catalog itself displays. A UUID is never shown.
  - The rail shows "Couldn't load services. [Retry]", never an empty list.
  - Without `catalog:service_type:view`, the rail reads "You can't view the service catalog", and Add service is hidden.
- **Permissions.** `location:bay:read` shows the page.
  - Without `location:bay:manage`: New bay, drag handles, chip ×, Add service, Edit and the overflow menu are all absent, and the view-only notice explains why.
  - A 403 `LOCATION_SCOPE_DENIED` on a write rolls back and switches the page to view-only for that location.
- **Deferred.** These are not on the card because they need backend data: default-vs-customised flag, out-of-service reason and expected return, floor position, equipment, changed-by, and skills needed vs held.
- **Bay "Test it" tool** (service + vehicle class → bays in order) is deferred until pos-shop-manager exposes the ranking, so the ordering logic isn't copied into the frontend. The "Who does what" band answers the common question in the meantime.
- **Quick start** for an empty location is deferred; the empty state has one clear action.
- **Drag technology.** Use native HTML5 drag and drop, as the dispatch board already does. Do not add a drag library for this page. Native drag doesn't fire on most touch browsers, which is one more reason the Add service route must be complete on its own.

## Accessibility
- The Add service button, chip ×, the Add service dialog and the drag handles make every drag operation available without a pointer (ADR-0029 rule 13; WCAG 2.5.7). The handles are labelled as handles and point at Add service.
- Card structure: each lane is a `section` with an H2 and a `ul` of cards; each card is an `li` holding an `article` with an H3. The card itself is not a button.
- Controls in a card name their target (ADR-0029 rule 12): "Add service to Bay 3", "Edit Bay 3", "Remove Wheel alignment (4-wheel) from Bay 3", "More actions for Bay 3". Visible text starts each accessible name (rule 6).
- Every target is at least 24×24 CSS px, including the chip × (rule 5).
- Hints and warnings are attached with `aria-describedby`, never put in `aria-label` (rule 3). Tooltips (`title`) never carry information (rule 4).
- Live feedback:
  - One polite page region announces save outcomes and lane moves.
  - Refusals use `role="alert"` inside the card.
  - The pending state has visually hidden "Saving" text (rule 8).
  - The "What changes" preview is a polite region.
- Focus handling follows ADR-0029 rule 7 as written in Interaction Flow: it follows a moved card, goes to the next chip after a removal, goes to the next card after a delete, and returns to the invoker when a dialog closes.
- All dialogs are native `dialog[appModalDialog]`, including the nested confirms (rule 1).
- The "Who does what" and Out-of-service disclosures name the action ("Open who does what" / "Close who does what") (rule 10), and use `aria-expanded`.
- Status and flags are always words, never colour alone. Motion when a card changes lanes respects `prefers-reduced-motion`.
- The page reflows to a single column at 320px wide with no horizontal scrolling.

## Provisional (open in louisburroughs/durion-positivity-backend#2245)
Q numbers match the questions in louisburroughs/durion-positivity-backend#2245.

| Q | Open question | Design element marked PROVISIONAL |
| --- | --- | --- |
| Q1 | Bay eligibility only shapes the appointment times offered; it isn't enforced when a workorder is placed | Verb in the "Can be assigned" line and the consequence messages. If confirmed, change "can be assigned" to "is offered for". |
| Q2 | When the only bay claiming a service goes out of service, the service becomes general work | The "Its only bay is out of service" flag, the out-of-service card warning, and the preview/outcome lines saying a claim is on hold or a service becomes general work because of a status change. If (b) or (c) is chosen, the copy says the service can't be booked, or flags the conflict. |
| Q4 | A new wash & detail bay can be assigned nothing | Wash-without-services warning, "Can't be assigned anything" sentence, last-service confirm copy for wash bays |
| Q5 | Duty class is a maximum only | Max duty class hint and the "no bay above class N" preview line |
| Q6 | Delete is not blocked | Delete confirm copy; whether Delete sits in the overflow menu or needs a guard |
| Q7 | Future appointments aren't re-checked when status changes | The preview says nothing about booked appointments until answered. If confirmed, add "Appointments already booked on this bay aren't moved." |
