import { faker } from "@faker-js/faker";

import { quoteLiteral, sqlHeader, stableUuid } from "../lib/sql.js";

export function emitDemoOptionalSql(seed) {
  const lines = [sqlHeader("900_demo_optional.sql")];

  if (!seed.faker.enabled) {
    lines.push("-- Faker disabled. No demo data generated.");
    return `${lines.join("\n")}\n`;
  }

  faker.seed(seed.faker.seed);

  lines.push("-- Demo users");
  for (let i = 0; i < seed.faker.counts.demo_users; i += 1) {
    const username = `demo.user.${i + 1}`;
    const userId = stableUuid("user", username);
    lines.push("INSERT INTO users (id, username, password, enabled, created_at, updated_at)");
    lines.push(
      `VALUES (${quoteLiteral(userId)}::uuid, ${quoteLiteral(username)}, ${quoteLiteral(
        faker.internet.password({ length: 20 })
      )}, TRUE, NOW(), NOW())`
    );
    lines.push("ON CONFLICT (username) DO NOTHING;");
  }

  lines.push("\n-- Demo customers");
  for (let i = 0; i < seed.faker.counts.demo_customers; i += 1) {
    const partyId = stableUuid("demo-party", `customer-${i + 1}`);
    lines.push("INSERT INTO person_party (party_id, party_type, created_at, updated_at)");
    lines.push(`VALUES (${quoteLiteral(partyId)}::uuid, 'INDIVIDUAL', NOW(), NOW())`);
    lines.push("ON CONFLICT (party_id) DO NOTHING;");
  }

  lines.push("\n-- Demo generators for vehicles/orders/invoices are placeholders.");
  lines.push("-- Extend this emitter when target schemas and foreign-key constraints are finalized.");

  return `${lines.join("\n")}\n`;
}
