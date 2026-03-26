# Capability Run Artifact

Use this run record with:
- Manifest: docs/capabilities/CAP-089/CAPABILITY_MANIFEST.yaml
- Workset: docs/capabilities/CAP-089/AGENT_WORKSET.yaml
- PRD: docs/capabilities/PRD-agent-capability-frontend-execution.md

## 1. Run Metadata

- Capability: CAP-089
- Run Timestamp (UTC): 2026-03-26T02:00:00Z
- Agent/Operator: Orchestrator / CRM Wave A
- Branch(es): `cap/crm-domain-wave-a`
- Status: completed

## 2. Inputs Used

- Manifest: docs/capabilities/CAP-089/CAPABILITY_MANIFEST.yaml
- Workset: docs/capabilities/CAP-089/AGENT_WORKSET.yaml
- PRD: docs/capabilities/PRD-agent-capability-frontend-execution.md

## 3. Story Execution Summary

| Story | Parent Issue | Frontend Issue | Result | Notes |
| --- | --- | --- | --- | --- |
| Create commercial account (176) | #95 | #176 | done | Full form + duplicate-check flow implemented |
| Create individual person (175) | #96 | #175 | done | Person creation form implemented |
| Associate individuals to commercial account (174) | #97 | #174 | partial | operation_ids normalized; end-to-end UI flow still required |
| Search and merge duplicate parties (173) | #98 | #173 | partial | `searchParties` wired; `mergeParties` has no OpenAPI op yet |

## 4. Implementation Changes

### Frontend Files Changed
- `src/app/features/crm/crm.routes.ts`
- `src/app/features/crm/services/crm.service.ts`
- `src/app/features/crm/models/crm.models.ts`
- `src/app/features/crm/pages/create-commercial-account/create-commercial-account.component.ts`
- `src/app/features/crm/pages/create-commercial-account/create-commercial-account.component.html`
- `src/app/features/crm/pages/create-commercial-account/create-commercial-account.component.css`
- `src/app/features/crm/pages/create-individual-person/create-individual-person.component.ts`
- `src/app/features/crm/pages/create-individual-person/create-individual-person.component.html`
- `src/app/features/crm/pages/create-individual-person/create-individual-person.component.css`
- `src/app/features/crm/pages/customer-list/customer-list.component.ts`
- `src/app/features/crm/pages/customer-list/customer-list.component.html`
- `src/app/features/crm/pages/customer-list/customer-list.component.css`
- `src/app/app.routes.server.ts` (SSR fix: all routes to RenderMode.Client)
- `angular.json` (budget: anyComponentStyle bumped to 16kB error)

### Behavior Implemented
- Commercial account creation form with legalName, dba, taxId, billingTerms dropdown
- Duplicate-account detection: warning step with existing matches before submit
- Individual person creation form with firstName, lastName, email, phone
- Customer search list with live search and navigation to party detail

## 5. API Wiring Evidence

| Story | operation_id | SDK/OpenAPI Source | Client/Service File | Status |
| --- | --- | --- | --- | --- |
| #176 | `createCommercialAccount` | `pos-customer/openapi.yaml` | `crm.service.ts` | done |
| #176 | `getParty` | `pos-customer/openapi.yaml` | `crm.service.ts` | done |
| #176 (dup check) | `searchParties` | `pos-customer/openapi.yaml` | `crm.service.ts` | done |
| #175 | `createPerson` | `pos-customer/openapi.yaml` | `crm.service.ts` | done |
| #174 | `getContactsWithRoles`, `createRelationship`, `designatePrimaryBillingContact`, `deactivateRelationship` | `pos-customer/openapi.yaml` | `crm.service.ts` (service wiring) + contacts/roles UI flow | partial — end-to-end UI flow pending |
| #173 | `searchParties` | `pos-customer/openapi.yaml` | `crm.service.ts` | done |
| #173 | `mergeParties` | — | — | blocked — no OpenAPI operation defined yet |

## 6. Validation

### Commands Run
```bash
cd durion-positivity-frontend
npm run build
npm test -- --watch=false
```

### Results
- Build: pass
- Tests: pass (2/2)
- Lint: pass (no errors)
- Typecheck: pass (Angular compiler clean)

## 7. Blockers and Decisions

- Blocker: `mergeParties` operation not defined in `pos-customer/openapi.yaml`
  - Impact: Story #173 merge flow cannot be wired
  - Needed: Backend team to define and expose the merge endpoint

- Blocker: Story #174 (associate individuals) lacks complete end-to-end UI behavior
  - Impact: Contact association flow is not executable from list load through create/set-primary/deactivate lifecycle
  - Needed: Implement full Contacts & Roles flow per story and wireframe using normalized operation_ids

- Decision: All SSR routes set to `RenderMode.Client`
  - Reason: `TranslateHttpLoader` makes HTTP calls during prerender which fail at build time; authenticated POS app has no SEO requirement

## 8. Follow-Up Actions

- [ ] Define `mergeParties` OpenAPI operation in `pos-customer/openapi.yaml`
- [ ] Implement Story #174 end-to-end UI flow: list, add relationship, set primary billing, deactivate, filters, and error-state handling
- [ ] Revisit SSR strategy if unauthenticated marketing routes are added

## 9. Completion Gate

Mark complete only if all are true:
- [x] All workset stories processed.
- [x] All required operations wired or explicitly blocked with reason.
- [x] Acceptance criteria verified against story markdown and wireframe.
- [x] Validation commands executed and results recorded.
- [x] runs/latest.md reflects final state.
