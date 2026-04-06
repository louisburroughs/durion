import { boolLiteral, quoteLiteral, sqlHeader, stableUuid } from "../lib/sql.js";

export function emitEventTypesSql(derived) {
  const lines = [sqlHeader("002_event_types.sql")];
  lines.push("-- Derived from *EventTypes.java registries");

  for (const eventType of derived.eventTypes) {
    const id = stableUuid("event-type", eventType.typeCode);
    lines.push(
      "INSERT INTO event_type (id, type_code, description, active, api_version, p50_micros, p95_micros, p99_micros)"
    );
    lines.push(
      `VALUES (${quoteLiteral(id)}::uuid, ${quoteLiteral(eventType.typeCode)}, ${quoteLiteral(
        eventType.description
      )}, ${boolLiteral(eventType.active)}, ${quoteLiteral(eventType.apiVersion)}, ${eventType.p50Micros}, ${
        eventType.p95Micros
      }, ${eventType.p99Micros})`
    );
    lines.push(
      "ON CONFLICT (type_code) DO UPDATE SET description = EXCLUDED.description, active = EXCLUDED.active, api_version = EXCLUDED.api_version, p50_micros = EXCLUDED.p50_micros, p95_micros = EXCLUDED.p95_micros, p99_micros = EXCLUDED.p99_micros;"
    );
  }

  return `${lines.join("\n")}\n`;
}
