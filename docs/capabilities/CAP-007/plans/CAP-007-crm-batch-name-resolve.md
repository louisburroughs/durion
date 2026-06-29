# CAP-007 — Batch CRM customer-name resolve (issue #768)

Status: in progress (Phase A)
Repos: durion-positivity-backend (pos-customer producer, pos-invoice consumer)
Tracking issue: louisburroughs/durion-positivity-backend#768
Origin: PR #767 review, MAJOR finding #1

## Problem

Invoice finder (`GET /v1/invoices/search`) enriches each row with a customer display name. The
invoice row stores only `partyId`, so `CustomerReferenceClient.resolveNames` resolves names from
pos-customer. The current implementation issues **one CRM HTTP GET per partyId** (N+1): a 50-row
page = 50 sequential calls, each with a 5000 ms read timeout — worst case ~250 s on a fast-read
endpoint. The workorder leg of the same feature already batches
(`POST /v1/workorders/numbers:resolve`); CRM does not.

Additional latent bug discovered: the current `resolveName` calls `/v1/crm/{partyId}`, which does
not map to any controller (the party-by-id endpoint is `/v1/crm/accounts/parties/{partyId}`), so
customer-name enrichment is effectively dead today. The batch endpoint replaces this path entirely.

## Design decisions (locked)

- Endpoint: `POST /v1/crm/accounts/parties:resolve` on `CrmAccountsController` (already injects
  `PartyService`, co-located with the other `/parties*` routes). Consumer base-url is `/v1/crm`, so
  it calls `/accounts/parties:resolve`. AIP colon-method naming, consistent with `numbers:resolve`.
- Request: `{ "partyIds": [...] }`, `@NotEmpty @Size(max=200)`. Invoice page cap is 50.
- Response: `200` `[{ partyId, displayName }]`. Unknown/blank ids omitted.
- Auth: reuse `crm:party:view` (`CrmPermissionRegistry.PARTY_VIEW`). No new perm-bit, no catalog bump.
- Name derivation, fully batched:
  - Commercial (`CommercialPartyRepository.findAllById`): `displayName ?? legalName`.
  - Person (`PersonPartyRepository.findAllById`): collect non-null `personId`s, then ONE
    `PeopleClient.fetchPersonIdentitiesQuietly(personIds)` → `PersonIdentity.displayName()`.
- Transaction safety: `resolveNames` is NOT `@Transactional` — repo `findAllById` calls run in their
  own short transactions; the pos-people HTTP call happens with no DB connection held (the #766
  harden lesson). Only scalar fields are read off the (detached) entities.

## Phase A — pos-customer (producer)  [THIS PR]

1. DTO `PartyNameResolveRequest` (record, `List<UUID> partyIds`, `@NotEmpty @Size(max=200)`, defensive copy). Mirror `WorkorderNumberResolveRequest`.
2. DTO `PartyNameRef` (record, `UUID partyId`, `String displayName`). Mirror `WorkorderNumberRef`.
3. `PartyService.resolveNames(List<UUID>) : List<PartyNameRef>` + `PartyServiceImpl` impl.
4. `CrmAccountsController`: `POST /parties:resolve` — `@Operation`/`@ApiResponse` (200 + 400/403),
   `@PreAuthorize(PARTY_VIEW)`, `@EmitEvent("CUSTOMER_PARTY_RESOLVE", v1)`, `@Valid @RequestBody`.
5. `EventTypes.java`: register `CUSTOMER_PARTY_RESOLVE` (search type).
6. Regenerate pos-customer `openapi.yaml`; sync `openapi.json`.
7. Tests: `PartyServiceImpl.resolveNames` (commercial fallback, person batch via mocked PeopleClient,
   unknown omitted, dedupe) + controller test (200/400/403).

## Phase B — contract / SDK

8. Regenerate Angular SDK for pos-customer (policy: controller change → SDK). No frontend consumer
   (server-to-server only); contract-sync hygiene.

## Phase C — pos-invoice (consumer)

9. Rewrite `CustomerReferenceClient.resolveNames` → single `POST /accounts/parties:resolve`. Mirror
   `WorkorderReferenceClient.resolveNumbers`.
10. Remove now-dead `resolveName` / `unwrapData` / `composeName` / `firstNonBlank` (pre-prod policy).
    Keep `searchIdsByName`.
11. Update `CustomerReferenceClientTest` to the batch POST shape.

## Phase D — tests / verify / PR

12. `mvn -pl pos-customer,pos-invoice test`; module-verify; openapi validation.
13. Update `pos-customer/README.md` (new endpoint).
14. PR per repo; close #768.

## Risks / watch

- `PersonParty.personId` nullable → skip (no name).
- A partyId is commercial XOR person; union the two `findAllById` results.
- Keep the pos-people HTTP call outside any DB transaction.
