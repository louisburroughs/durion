#!/usr/bin/env node
import path from "node:path";

import { parseArgs } from "./lib/args.js";
import { extractEventTypes } from "./lib/extract-event-types.js";
import { extractPermissionBitIndexes } from "./lib/extract-permission-bits.js";
import { extractPermissions } from "./lib/extract-permissions.js";
import { writeFile } from "./lib/fs.js";
import { loadSeedInput } from "./lib/load-seed-input.js";
import { emitSecuritySql } from "./emitters/001-security.js";
import { emitEventTypesSql } from "./emitters/002-event-types.js";
import { emitLocationsPeopleSql } from "./emitters/003-locations-people.js";
import { emitCatalogPricingInventorySql } from "./emitters/004-catalog-pricing-inventory.js";
import { emitAccountingSql } from "./emitters/005-accounting.js";
import { emitInvoiceSql } from "./emitters/006-invoice.js";
import { emitDemoOptionalSql } from "./emitters/900-demo-optional.js";
import { emitAssertionsSql } from "./emitters/999-assertions.js";

async function main() {
  const args = parseArgs(process.argv);

  const seed = await loadSeedInput(args.input, args.profile);
  const [permissions, eventTypes, permissionBitIndexes] = await Promise.all([
    extractPermissions(args.backendRoot),
    extractEventTypes(args.backendRoot),
    extractPermissionBitIndexes(args.backendRoot)
  ]);

  const derived = { permissions, eventTypes, permissionBitIndexes };

  const files = [
    ["001_security.sql", emitSecuritySql(seed, derived)],
    ["002_event_types.sql", emitEventTypesSql(derived)],
    ["003_locations_people.sql", emitLocationsPeopleSql(seed)],
    ["004_catalog_pricing_inventory.sql", emitCatalogPricingInventorySql(seed)],
    ["005_accounting.sql", emitAccountingSql(seed)],
    ["006_invoice.sql", emitInvoiceSql(seed)],
    ["900_demo_optional.sql", emitDemoOptionalSql(seed)],
    ["999_assertions.sql", emitAssertionsSql(seed, derived)]
  ];

  const writes = files.map(([name, content]) => writeFile(args.out, name, content));
  const written = await Promise.all(writes);

  console.log(`Generated ${written.length} SQL files.`);
  console.log(`Output directory: ${args.out}`);
  console.log(`Input file: ${args.input}`);
  console.log(`Backend root: ${path.resolve(args.backendRoot)}`);
  console.log(`Derived permissions: ${permissions.length}`);
  console.log(`Derived event types: ${eventTypes.length}`);
}

main().catch((error) => {
  console.error("Seed generation failed.");
  console.error(error.stack || error.message || error);
  process.exitCode = 1;
});
