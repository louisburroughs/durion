---
type: ADR
title: 'ADR-0070: Bill Intake Ownership, Inbound Untrusted Files and the Vendor Master'
description: Places every human channel for supplier invoices (upload, photo, spreadsheet, email-in, vendor statements) in pos-accounting behind one bill-creation path, keeps pos-supplier to credentialed machine channels plus the vendor master, and adds an isolated extraction worker and an inbound-mail edge for untrusted input.
resource: https://github.com/louisburroughs/durion/blob/master/docs/adr/0070-bill-intake-ownership-and-vendor-master.adr.md
path: durion/docs/adr/0070-bill-intake-ownership-and-vendor-master.adr.md
tags: [adr, accounting, billing, positivity, supplier, events, security, rbac]
status: stable
sources: [docs/adr/0070-bill-intake-ownership-and-vendor-master.adr.md]
generated: {by: 'script:generate-knowledge-catalog.py', at: '2026-10-08T13:50:14+00:00'}
---

[Canonical ADR](https://github.com/louisburroughs/durion/blob/master/docs/adr/0070-bill-intake-ownership-and-vendor-master.adr.md) — `docs/adr/0070-bill-intake-ownership-and-vendor-master.adr.md`

**Status:** Accepted since 2026-10-05

**Related:**

* [ADR-0020](../adr/0020-documents-centralized-creation.md)
* [ADR-0044](../adr/0044-platform-event-only-domain-walls.md)
* [ADR-0049](../adr/0049-supplier-integration-module-boundary.md)
* [ADR-0050](../adr/0050-supplier-vendor-profile-configuration.md)
* [ADR-0051](../adr/0051-supplier-protocol-adapter-versioning.md)
* [ADR-0062](../adr/0062-postgres-row-level-multitenancy.md)
* [ADR-0064](../adr/0064-frontend-read-outcome-and-placeholder-copy-policy.md)
* [ADR-0065](../adr/0065-frontend-untrusted-content-and-browser-persistence-policy.md)
