---
type: ADR
title: 'ADR-0071: Per-Tenant Pluggable Tax Providers Behind pos-tax, Reached Through Front Doors'
description: Keeps pos-tax as the one internal tax port, which picks a provider plug-in per tenant and country on platform-held provider accounts, owns tenant tax-profile data and publishes tax registrations by outbox, while every operation a person starts reaches it only through an external-facing domain module.
status: stable
adr_status: accepted
created: '2026-10-08'
related: [ADR-0014, ADR-0017, ADR-0018, ADR-0021, ADR-0044, ADR-0062, ADR-0067]
tags: [adr, accounting, multitenancy]
---
# ADR-0071: Per-Tenant Pluggable Tax Providers Behind pos-tax, Reached Through Front Doors

**Status:** ACCEPTED **Date:** 2026-10-08 **Deciders:** Platform Owner, Chief Architect
**Affected Issues:** louisburroughs/durion#553 (questions 1–2), louisburroughs/durion-positivity-backend#2522 (S31), louisburroughs/durion-positivity-backend#2523 (S32)

> **How to read this.** ✅ **Resolved** marks a decision this ADR makes. The Chief Architect answered louisburroughs/durion#553 questions 1–2 on 2026-10-08
> (registrations travel by outbox; people never reach pos-tax directly; providers are pluggable per tenant), and the Platform Owner accepted the seven
> recommendations that turned those answers into the decisions below the same day. The specification records them as AW58 and AW59 in
> [SPEC-accounting-workspace.md](../../domains/accounting/SPEC-accounting-workspace.md) §10. pos-tax remains a collection of stubs (AW48): nothing here
> makes a placeholder rate, rule or answer into tax law.

---

## Context

### Current state

- `pos-tax` is one internal service with no gateway route, no Eureka registration and no SDK package (ADR-0021 §3). pos-order, pos-workorder, pos-invoice
  and pos-mcp-server call it directly at a fixed base URL to calculate tax; pos-invoice also commits and voids provider documents.
- It already has a provider seam: `TaxProviderClient` with a test-mode calculator, a generic external client and an Avalara AvaTax adapter. The provider is
  chosen **once per deployment** (`TAX_TEST_MODE`, `pos.tax.provider`); every environment runs test mode.
- It already holds tenant data under row-level security (exemption certificates, the provider transaction log), yet ADR-0044 §1 classes it as a utility
  because it is "stateless computation … not data lookups".
- pos-tax publishes no events. The exemption registry has no caller outside pos-tax, because a person has no way to reach it.

### The problem

The Canada work (S31, S32, S33) needs tax registrations that pos-accounting and pos-order read at posting and at the drawer, and a form where a person
records them. louisburroughs/durion#553 asked how registrations travel and where the form sends its request. The Chief Architect answered:

1. Registrations travel by **outbox**, as the story recommended.
2. ADR-0021's purpose is that pos-tax is never reached by the frontend or exposed through the SDK or public APIs. Every call a person makes goes through
   another, external-facing module (assumption: primarily accounting). Calls to pos-tax should be **highly pluggable**: a tenant may be served by a different
   tax module (US-self, US-Avalara, US-EY, CA-self and so on).

### Drivers

- Tenants in different countries, and one tenant with shops in two countries, need different tax engines at once; stubs and real providers must be
  interchangeable without callers changing (AW48).
- Postings must not depend on pos-tax being up (AW49), so consumers need local copies of registrations.
- Checkout must not depend on pos-accounting, and domain modules must not call each other synchronously (ADR-0044 R1).
- Third-party provider credentials must stay in as few places as possible.

### Scope

pos-tax, its callers, and the three external-facing modules that front the tax data people maintain: pos-accounting, pos-customer and pos-tenant. Tax law
is out of scope (AW48).

---

## Decision

### 1. pos-tax is the one tax port

**Decision:** ✅ **Resolved** — One deployed `pos-tax` with one internal REST contract (`pos-tax-common`). Callers never choose, name or know the provider; the
"module per tenant" of the Chief Architect's answer is a **provider plug-in inside pos-tax**, not a separate deployable per provider.

### 2. Provider plug-ins

**Decision:** ✅ **Resolved** — A plug-in implements the existing `TaxProviderClient` seam (estimate, refund, commit, void) and declares the optional
capabilities it supports (rate lookup, the AW57 Canadian stubs such as plausibility and evidence rules). Plug-ins have stable ids:

| Plug-in | What it is |
| --- | --- |
| `US_SELF` | Today's test-mode calculator: configured placeholder rates (stubs, AW48) |
| `CA_SELF` | The Canadian stubs of AW57: typed placeholder rates, registration status, number shape, evidence rule, plausibility |
| `AVALARA` | The existing AvaTax adapter |
| others (`EY`, …) | Added the same way when a provider is chosen |

A plug-in may call a remote service — a vendor API or a separately deployed adapter — behind the seam; that stays an implementation detail of the plug-in.
A capability the bound plug-in lacks is refused with 422 `TAX_CAPABILITY_UNSUPPORTED` naming it: the request is well formed and the tenant's
configuration refuses it (ADR-0017 §2). `501` stays reserved for documented stub endpoints (ADR-0017 §1), so today's 501 `TAX_RATE_LOOKUP_UNSUPPORTED`
moves to this code when the binding lands.

### 3. Binding by tenant and country

**Decision:** ✅ **Resolved** — pos-tax picks the plug-in per **tenant and country**, so a tenant with shops in the United States and Canada uses `US_SELF`
for one and `CA_SELF` for the other.

- A tenant-scoped `tax_provider_binding` (ADR-0062 row-level security): `countryCode`, `providerId`, `providerProfile` (the tenant's company or profile code
  on the provider's account; not a secret), `effectiveFrom`, optional `effectiveTo`; bindings for one tenant and country never overlap.
- Resolution uses the request's tenant and the country of its `destinationAddress` (ADR-0021 §1), as of the transaction date.
- With no binding, a per-country platform default applies (`pos.tax.default-providers.<country>`: `US` → `US_SELF`, `CA` → `CA_SELF`). With neither, the
  request is refused with 422 `TAX_JURISDICTION_NOT_CONFIGURED`; a country's tax is never priced by another country's plug-in.
- The provider transaction log records the plug-in that priced each document, so its commit, void and refund go to the same plug-in after a binding change.
- The deployment-wide switches `TAX_TEST_MODE` and `pos.tax.provider` are replaced by the default map (pre-production: no compatibility shim).

### 4. Provider accounts are the platform's

**Decision:** ✅ **Resolved** — Each provider has **one platform-held account**, its credential in the secret store, never per tenant. A tenant's binding names
only its non-secret profile on that account. A tenant bringing its own provider account needs an amendment to this ADR.

### 5. People reach pos-tax only through front doors

**Decision:** ✅ **Resolved** — pos-tax keeps no gateway route, no SDK package and no frontend caller (ADR-0021 §3, amended 2026-10-08). Every operation a
person starts reaches it through the external-facing domain module that owns the person's permission:

| Tax data people maintain | Front door | Permissions (names final in the implementing story) |
| --- | --- | --- |
| Tax registrations | pos-accounting | `accounting:tax_registration:view`, `accounting:tax_registration:manage` |
| Exemption certificates | pos-customer | `crm:tax_exemption:view`, `crm:tax_exemption:manage` |
| Provider bindings | pos-tenant (platform-tenant callers only, ADR-0062 §7) | `platform:tenant_tax_provider:manage` |

- The front door checks the person's permission, validates, then calls pos-tax synchronously (ADR-0044 R2: pos-tax remains a utility), forwarding the actor
  for pos-tax's audit rows. The actor travels only in the `X-User-Id` header the front door itself received from the gateway, never in a request body;
  pos-tax trusts it only on a request authenticated as that front door (Decision 6) and binds it as the request's principal, so ADR-0018's
  service-layer rule applies unchanged and any actor field in a body is ignored.
- Reads for screens come from the front door: its own replica where one exists (registrations, Decision 7), otherwise a synchronous read of pos-tax.
- **Service-to-service tax operations stay direct**: calculate, refund, commit, void, rate lookup and plausibility, from the allowlisted callers (pos-order,
  pos-workorder, pos-invoice, pos-mcp-server, pos-accounting). Routing them through pos-accounting would make checkout depend on accounting and break
  ADR-0044 R1.

### 6. Write endpoints accept only their front door

**Decision:** ✅ **Resolved** — pos-tax's write endpoints for registrations, exemption certificates and bindings each accept exactly one caller, its front
door, authenticated by a per-caller shared secret (the pos-platform-sender pattern) until a platform service identity replaces it. A missing or wrong secret
answers 401, and a blank configured secret refuses every request (ADR-0017 §1). No human role is granted `tax:*` write permissions; the person's permission lives at the front door. The computation endpoints keep today's
authorization.

### 7. Registrations belong to pos-tax and travel by outbox

**Decision:** ✅ **Resolved** — pos-tax is the source of truth for tax registrations (regime, number, effective dates) and publishes
`tax.registration.changed` v1 on `tax.events.v1` through a transactional outbox, with a per-tenant manifest `tax.manifest.v1` and re-send (ADR-0044 §4).
pos-accounting and pos-order keep `ext_tax_registration` replicas (ADR-0044 R3) and read them as of a business date (AW49). Exemption certificates and
bindings publish nothing until a consumer needs them.

### 8. pos-tax's classification

**Decision:** ✅ **Resolved** — pos-tax stays in ADR-0044's **Utility** class for computation calls, and is also the owner of tenant **tax-profile data**
(registrations, exemption certificates, provider bindings). It is the one utility that publishes a domain fact (ADR-0044 §1, amended 2026-10-08).

---

## Alternatives Considered

1. **A deployable per provider, with callers routing by tenant.** Rejected: every caller would learn routing, and each deployable would repeat tenancy, the
   outbox, the caller allowlist and secrets. pos-tax is not on Eureka, so routing would need a new discovery path.
2. **The provider chosen per deployment (today).** Rejected: a tenant mix — `US_SELF` beside `CA_SELF`, or AvaTax for one tenant only — is impossible.
3. **Every tax call through pos-accounting.** Rejected: checkout would depend on accounting being up, and order, workorder and invoice would call a domain
   module synchronously (ADR-0044 R1).
4. **Registrations owned by pos-accounting, pos-tax data-free.** Rejected: plug-ins and pos-order need them too, and the Chief Architect chose pos-tax's fact.
5. **Each tenant brings its own provider account.** Deferred (Decision 4): it multiplies secrets and support cases.
6. **A gateway route to pos-tax with authorization.** Rejected again (ADR-0021 Alternative 2): it widens the attack surface and puts tax behind no domain
   that owns the person's permission.

---

## Consequences

### Positive ✅

- A tenant mixes providers by country, and stubs and real providers share one contract: replacing `CA_SELF` with a real engine changes a binding, never a
  caller.
- Callers keep one base URL and one contract.
- Every change a person makes is authorized and audited by the domain that owns their permission, and pos-tax records the actor.
- Postings read registrations locally and never wait on pos-tax (AW49).

### Negative ⚠️

- pos-tax becomes stateful and a Kafka producer: an outbox, a topic, a manifest and re-sends to run (mitigated by ADR-0044 §4's standard mechanisms).
- Three front doors to build and three per-caller secrets to manage.
- Plug-ins must declare capabilities, and callers must handle a 422 `TAX_CAPABILITY_UNSUPPORTED` for a capability their tenant's plug-in lacks.

### Neutral

- ADR-0021 §1–§2 (the calculation contract and strict address validation) are unchanged.
- The stub register in `durion-positivity-backend/pos-tax/README.md` lists each plug-in's stubs (AW48).

---

## Implementation Notes

- **Order:** (1) the binding table and resolver with `US_SELF` as the US default, which changes nothing for existing tenants; (2) `CA_SELF` and the AW57
  stubs (S31); (3) registrations with the outbox, the manifest and the pos-accounting front door (S31, S32), and the registration panel posting to
  pos-accounting (S33); (4) the pos-customer and pos-tenant front doors (no story yet; specification OI-23).
- **Error codes:** 422 `TAX_JURISDICTION_NOT_CONFIGURED`; 422 `TAX_CAPABILITY_UNSUPPORTED` (replacing 501 `TAX_RATE_LOOKUP_UNSUPPORTED`); 409
  `TAX_REGISTRATION_OVERLAP` (relayed by the front door); 401 for a write without its front door's secret.
- **Testing:** resolver unit tests (binding, default, none); a contract test that a US request returns the same JSON under `US_SELF` as under today's test
  mode; `TenantIsolationIT` for bindings and registrations; outbox and manifest tests per ADR-0044 §4; a security test that each write endpoint refuses
  every caller but its front door.
- **API Artifacts Sync** after each controller or permission change in pos-tax, pos-accounting, pos-customer and pos-tenant.

### Changes required in other ADRs

Applied 2026-10-08 as dated amendments:

- **ADR-0021:** people reach pos-tax only through front doors; the caller allowlist; the provider is chosen per tenant and country (this ADR).
- **ADR-0044 §1:** pos-tax owns tenant tax-profile data and publishes `tax.registration.changed`; it stays a utility for computation.

No change: ADR-0014 (pos-tax still has no route), ADR-0062 (the new tables are tenant-scoped as usual).

---

## References

- **Related Issues:** louisburroughs/durion#553; louisburroughs/durion-positivity-backend#2522, #2523
- **Related ADRs:** [ADR-0014](0014-gateway-internal-service-security.adr.md), [ADR-0017](0017-api-controller-http-response-codes.adr.md), [ADR-0018](0018-audit-actor-fields-from-security-context.adr.md),
  [ADR-0021](0021-tax-api-consumption-and-internal-access-policy.adr.md), [ADR-0044](0044-platform-event-only-domain-walls.adr.md),
  [ADR-0062](0062-postgres-row-level-multitenancy.adr.md), [ADR-0067](0067-platform-tenant-functional-currency-and-multi-currency.adr.md)
- **Related Documentation:** [SPEC-accounting-workspace.md](../../domains/accounting/SPEC-accounting-workspace.md) §4.7, §7.1, §7.3, §10 (AW48, AW57–AW59);
  `durion-positivity-backend/pos-tax/README.md` (provider plug-ins, stub register)

---

## Sign-Off

| Role | Name | Date | Notes |
| --- | --- | --- | --- |
| Chief Architect | Chief Architect | 2026-10-08 | Answers to louisburroughs/durion#553 questions 1–2 |
| Platform Owner | Louis Burroughs | 2026-10-08 | Accepted the seven recommendations behind Decisions 1–8 |

---

## Timeline

- **Proposed**: 2026-10-08
- **Accepted**: 2026-10-08

---

## Changelog

- **2026-10-08**: Initial version from the Chief Architect's answers on louisburroughs/durion#553 and the Platform Owner's acceptance; ADR-0021 and
  ADR-0044 §1 amended.
