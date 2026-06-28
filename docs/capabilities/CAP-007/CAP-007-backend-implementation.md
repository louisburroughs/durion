## CAP-007 — Invoice Finder Search Endpoint (Backend Implementation)

Parent capability: **CAP:007 — Convert Workorder to Invoice** (durion#7)
Parent story: durion#337 · Backend child: durion-positivity-backend#765
Branch: `cap/CAP007`

## Summary

Adds a free-text **invoice finder** backing the billing landing invoice-detail card.
A new endpoint `GET /v1/invoices/search?q=` matches the query against the **invoice
number**, **customer name**, and **workorder number**, returning a paginated
`InvoiceSearchResult` enriched with the resolved customer display name and human
workorder number.

The invoice row stores only `workorderId` (UUID) and `partyId` (string) — neither the
customer name nor the human workorder number — so the search resolves and enriches via
two cross-service clients, mirroring the proven pos-workorder finder
(`WorkorderSearchController` + `CustomerReferenceService`).

## Endpoint

```text
GET /v1/invoices/search?q={q}&page={p}&size={s}
Authority: invoice:manage   Event: INVOICE_SEARCH (fastRead)
200 → Page<InvoiceSearchResult>   400 invalid pageable   403 missing invoice:manage
```

`InvoiceSearchResult`: `invoiceId, invoiceNumber, customerName, workorderNumber,
status, total, createdAt`.

## Query semantics

`InvoiceRepository.searchByQuery`:

```jpql
SELECT i FROM Invoice i
WHERE LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :q, '%'))
   OR i.partyId IN :customerIds
   OR i.workorderId IN :workorderIds
```

- `customerIds` — party ids resolved from the customer-name leg (CRM).
- `workorderIds` — workorder ids resolved from the workorder-number leg (workorder svc).
- Empty legs pass a non-matching sentinel (`"__none__"` / `new UUID(0,0)`) so the JPQL
  `IN` clauses stay non-empty.

## Cross-service clients (RestClient, direct DNS)

| Client | Target | Calls |
| --- | --- | --- |
| `CustomerReferenceClient` | pos-customer `/v1/crm` | `GET /accounts/parties?name=` (id search), `GET /{partyId}` (name enrichment) |
| `WorkorderReferenceClient` | pos-workorder `/v1/workorders` | `GET /search?q=` (number→ids), `POST /numbers:resolve` (ids→numbers enrichment) |

`RestClientConfig` (new) declares a `@Primary RestClient.Builder` so the module's
outbound clients resolve in boots where the auto-configured builder is absent (e.g. the
`openapi`/`dev` profile with service discovery disabled).

## pos-workorder addition

`POST /v1/workorders/numbers:resolve` (`WorkorderSearchController.resolveNumbers`) —
batch id→number resolution consumed server-side by pos-invoice for row enrichment.
Event `WORKORDER_NUMBER_RESOLVE`.

## Files

### pos-invoice
- `internal/client/CustomerReferenceClient.java` (new)
- `internal/client/WorkorderReferenceClient.java` (new)
- `internal/config/RestClientConfig.java` (new)
- `internal/dto/InvoiceSearchResult.java` (new)
- `internal/controller/InvoiceSearchController.java` (new)
- `internal/service/InvoiceSearchServiceImpl.java` (new) · `service/InvoiceSearchService.java` (new)
- `internal/repository/InvoiceRepository.java` (+`searchByQuery`)
- `internal/config/EventTypes.java` (+`INVOICE_SEARCH`)
- tests: `InvoiceSearchServiceImplTest`, `InvoiceSearchControllerTest`

### pos-workorder
- `internal/dto/WorkorderNumberResolveRequest.java`, `WorkorderNumberRef.java` (new)
- `internal/controller/WorkorderSearchController.java` (+`resolveNumbers`)
- `internal/service/WorkorderSearchServiceImpl.java` + `service/WorkorderSearchService.java` (+`resolveNumbers`)
- `internal/config/EventTypes.java` (+`WORKORDER_NUMBER_RESOLVE`)
- test: `WorkorderNumberResolveTest`

## Contract chain

- `pos-invoice/openapi.yaml` + `openapi.json` regenerated (adds `/v1/invoices/search`,
  `InvoiceSearchResult`, `PageInvoiceSearchResult`).
- Angular SDK `@durion-sdk/invoice` regenerated (`InvoiceSearchService.searchInvoices`,
  `InvoiceSearchResult`); repacked tarball installed into the frontend.

## Test results

`InvoiceSearchServiceImplTest` (3), `InvoiceSearchControllerTest` (3),
`WorkorderNumberResolveTest` (2) — all green.
Frontend billing specs: 41 passing; dev build clean.
