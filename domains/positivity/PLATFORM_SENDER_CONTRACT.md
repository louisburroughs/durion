---
type: Integration Contract
title: Platform Sender Contract (FI-2)
description: Governs the wire contract between pos-marketing and the shared platform sender, pos-platform-sender — the send API, the `sender.outcomes.v1` outcome events and the suppression hand-off to pos-customer.
status: current
---

# Platform Sender Contract (FI-2)

Contract between `pos-marketing` and the **shared platform sender** for campaign email/SMS
delivery and outcome feedback. Defines what `pos-marketing` builds against (durion#369,
plan decision O-1, stories #1149/#1150). The sender owns provider credentials, address
resolution, wire-level retries, and provider webhooks; `pos-marketing` owns orchestration
only — audience, consent/suppression gating, batching, per-recipient state.

The sender is **`pos-platform-sender`** (durion-positivity-backend, 2026-10-03): email through
Amazon SES, SMS through AWS End User Messaging. It is a domain module that `pos-marketing`'s
`PlatformSenderClient` alone may call synchronously: a class-scoped exception, ADR-0044 amendment
2026-10-03. Its README (`durion-positivity-backend/pos-platform-sender/README.md`) covers the
provider mapping, configuration and AWS setup; this document stays the wire contract.

## 1. Send API

`POST {pos.marketing.sender.base-url}/platform-sender/v1/messages`

Headers:

| Header | Value |
| --- | --- |
| `Content-Type` | `application/json` |
| `X-Pos-Sender-Secret` | shared secret (`pos.marketing.sender.api-secret`); same pattern as `X-Pos-Events-Secret` |
| `X-Tenant-Id` | the caller's bound tenant (ADR-0062 §3); the sender resolves and tags under it. No gateway sits on this hop, so the caller sends it whenever it has a bound tenant (pos-marketing's send worker always does). Without it the sender binds its transitional default tenant (`pos.tenancy.default-tenant-id`), or refuses with 401 `TENANT_REQUIRED` where none is configured; a caller must never invent one |

Request body:

```json
{
  "messageId": "0198c2f0-…",          // campaignSendId — idempotency key
  "channel": "EMAIL",                  // EMAIL | SMS
  "recipientPartyId": "0198c2f0-…",
  "contactId": "0198c2f0-…",           // optional; the contact audience resolution picked
  "campaignCode": "SPRING24",          // sender-side metadata / provider tagging
  "subject": "…",                      // null for SMS
  "body": "…"                          // fully rendered; sender does no templating
}
```

Semantics:

- **Address resolution belongs to the sender.** `pos-marketing` never sends or stores a raw
  address; the sender resolves the recipient's address from `contactId` (the person party the
  consent decision named), else `recipientPartyId`, at delivery time: CRM person party →
  pos-people-contact person → that person's email or `PHONE_MOBILE` number, through event-fed
  replicas of pos-customer and pos-people-contact (ADR-0044 R3; no synchronous domain call).
- **Idempotency:** a replayed `messageId` MUST NOT produce a second delivery; the sender
  answers `200` with the original response instead of `202`.

Responses:

| Status | Meaning | Body |
| --- | --- | --- |
| `202` (or `200` on idempotent replay) | accepted for delivery | `{"providerMessageId": "…", "addressHash": "…"}` |
| `4xx` | permanent refusal (malformed, unknown party, no resolvable address, provider refusal) — caller marks the send `FAILED`. A replay of a refused `messageId` answers the same refusal | `ApiError` |
| `5xx` | transient — caller retries with backoff up to `pos.marketing.send.max-attempts`. When the provider gave no answer after the request left (`PROVIDER_NO_RESPONSE`), the message may have been delivered: the key stays claimed and every replay answers `503 SEND_IN_FLIGHT`, so the retry can never deliver it a second time | `ApiError` |

`providerMessageId` is required on acceptance — it is the only correlation key for outcomes.
`addressHash` (SHA-256 of the normalized address) is optional; when present `pos-marketing`
stores it on the send record for bounce correlation.

## 2. Outcome feedback (Kafka)

Topic `sender.outcomes.v1` (`pos.marketing.kafka.sender-outcomes-topic`), standard domain
envelope (`eventId`, `eventType`, `payload`), at-least-once, keyed by `providerMessageId`.
The `occurredAt` timestamp (ISO-8601 instant) lives **inside `payload`**, next to
`providerMessageId`; a missing or malformed value makes pos-marketing fall back to its own
clock.

Event types and payload:

| `eventType` | Payload fields | Effect in pos-marketing |
| --- | --- | --- |
| `sender.message.delivered` | `providerMessageId`, `occurredAt` | send → `DELIVERED` |
| `sender.message.bounced` | `providerMessageId`, `reason`, `permanent` (bool, default `true`), `address`, `occurredAt` | send → `BOUNCED`; hard bounce → CRM suppression |
| `sender.message.complained` | `providerMessageId`, `reason`, `address`, `occurredAt` | send → `COMPLAINED`; always → CRM suppression |
| `sender.message.opened` | `providerMessageId`, `occurredAt` | stamps `openedAt` (first only) |
| `sender.message.clicked` | `providerMessageId`, `occurredAt` | stamps `clickedAt` (+ implies open) |

Open/click support is **optional** per channel/provider; consumers degrade gracefully when
these never arrive. `address` (raw, normalized) is REQUIRED on bounce/complaint so the
suppression hand-off can identify what to block; it is relayed to pos-customer and never
persisted by pos-marketing. The producer takes it from the recipient the provider event names,
else from the message's own destination; a provider event that names neither is not relayed.

**Producer:** `sender.outcomes.v1` is produced by **pos-platform-sender**, through its
transactional outbox: Kafka record key `providerMessageId`, header `tenantId` (the tenant the
message was sent under), envelope aggregate the request's `messageId`, payload
`SenderMessageOutcomeV1` (`pos-domain-events`), which also carries `messageId` and `channel` and
omits absent fields rather than writing `null`. `pos-marketing`'s `DeliveryOutcomeListener` is
the sole consumer. The producer side is pinned by `ProviderOutcomeMapperTest` (provider events to
these rows) and `SenderMessageOutcomeV1Test` (field names, ISO `occurredAt`, no nulls); the
automated check that keeps the consumer honest against this table is
`PlatformSenderContractTest`
(`pos-marketing/src/test/java/com/positivity/marketing/internal/service/PlatformSenderContractTest.java`),
which drives `DeliveryOutcomeListener` with JSON fixtures built from the field names above and
pins each row's effect, including the `permanent`/`occurredAt` defaults (issue #1537).

## 3. Suppression feedback (pos-marketing → pos-customer)

On hard bounce or complaint, `pos-marketing` queues a command on `customer.commands.v1`
(transactional outbox, same transaction as the send-state change):

```json
{
  "commandType": "customer.suppression.add-requested",
  "eventId": "…",                      // reuses the sender outcome eventId → replay-safe
  "payload": {
    "channel": "EMAIL",
    "address": "jane@example.com",
    "partyId": "0198c2f0-…",
    "reason": "HARD_BOUNCE"            // HARD_BOUNCE | SPAM_COMPLAINT
  }
}
```

`pos-customer` applies it via `SuppressionService.add` (idempotent; stores only a normalized
hash + masked hint; source recorded as `SYSTEM`) and republishes
`customer.suppression.changed`, which flows back into pos-marketing's `ext_suppression`
replica — closing the loop so the next audience build and send-time re-check both exclude
the address.

## 4. Facts published by pos-marketing

On each applied terminal outcome, `marketing.events.v1` carries
`marketing.campaign.send.delivered|bounced|complained`
(`MarketingCampaignSendOutcomeV1`; aggregateId = recipient party).

## 5. Config reference (pos-marketing)

| Property | Default | Purpose |
| --- | --- | --- |
| `pos.marketing.send.transport` | `stub` | `stub` logs sends; `platform-sender` activates the adapter |
| `pos.marketing.sender.base-url` | `http://pos-platform-sender:8080` | sender endpoint (its Compose/alpha container address) |
| `pos.marketing.sender.api-secret` | _(empty)_ | shared secret header |
| `pos.marketing.kafka.sender-outcomes-topic` | `sender.outcomes.v1` | outcome feed |
| `pos.marketing.kafka.customer-commands-topic` | `customer.commands.v1` | suppression hand-off |

## 6. What holds both sides to this document

The two sides compile against different types (the producer's `SenderMessageOutcomeV1`, the
consumer's raw `JsonNode`), so nothing about the wire format is type-checked across them. Each
side is pinned against this document instead. The consumer side is pinned by
`durion-positivity-backend/pos-marketing/src/test/java/com/positivity/marketing/internal/service/PlatformSenderContractTest.java`
(issue #1537 / D5), which drives `DeliveryOutcomeListener` with **raw JSON built from §2's
documented field names** rather than a mock DTO, so a field rename that §2 is not also
updated for fails the build. Its nine cases cover all five §2 rows plus three prose rules:
`occurredAt` absent or malformed falls back to the clock; `permanent` defaults to `true` on
a bounce; a soft bounce does not suppress; a complaint always suppresses; `address` absent
on bounce/complaint skips the §3 hand-off; `opened` stamps first occurrence only; `clicked`
implies open; and an `eventType` outside the five documented rows is ignored rather than
recorded.

§1's send API is pinned separately by
`durion-positivity-backend/pos-marketing/src/test/java/com/positivity/marketing/internal/client/PlatformSenderClientTest.java`,
which stands a `MockRestServiceServer` in front of `PlatformSenderClient` and asserts the
documented URL `POST {base-url}/platform-sender/v1/messages`, the `X-Pos-Sender-Secret`
header, `X-Tenant-Id` from the bound tenant (and none invented when unbound), `messageId` as the
idempotency key and `campaignCode` in the body, that `202` plus `providerMessageId` is
acceptance, and that 4xx is a permanent refusal while 5xx, an IO failure, or an acceptance
missing `providerMessageId` are transient and retried.

On the producer side, `pos-platform-sender`'s `MessageControllerWebMvcTest` pins §1's status
classes through the production security chain (202, 200 on replay, 422, 503, 400, and 401
without the secret); `MessageSendServiceImplTest` pins the §1 replay rule (an accepted
`messageId` answers `200` with the original ids and never reaches the provider again; a refused
one answers the same refusal; an unsettled or unanswered one answers `503` and is never re-sent);
`ProviderOutcomeMapperTest` and `SenderMessageOutcomeV1Test` pin §2 as above, including that every
bounce and complaint carries `address` (from the event's recipient, else the message's destination)
and that a provider event naming neither is not relayed.

**Scope limit — read this before treating these tests as proof of the whole contract.**
They are unit and slice tests; nothing here exercises a live provider or runs both modules
together. Unpinned: the §3 round trip through pos-customer's `SuppressionService` back into
pos-marketing's `ext_suppression` replica, which crosses a module boundary no test spans; and
the real AWS event payloads, which the producer's mapper tests reproduce from the published
schemas rather than from captured events.
