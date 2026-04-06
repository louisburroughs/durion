import { faker } from "@faker-js/faker";

import { boolLiteral, quoteLiteral, sqlHeader, stableUuid } from "../lib/sql.js";

export function emitCatalogPricingInventorySql(seed) {
  const lines = [sqlHeader("004_catalog_pricing_inventory.sql")];
  const products = [...seed.catalog.products];
  const services = [...seed.catalog.services];
  const basePrices = [...seed.pricing.base_prices];

  if (seed.faker.enabled) {
    faker.seed(seed.faker.seed);
    const productCodeSet = new Set(products.map((product) => product.sku));
    const serviceCodeSet = new Set(services.map((service) => service.code));
    const basePriceKeySet = new Set(basePrices.map((price) => `${price.item_type}:${price.item_code}`));

    for (let i = 0; i < seed.faker.counts.catalog_products; i += 1) {
      const sku = `DEMO-PROD-${String(i + 1).padStart(4, "0")}`;
      if (productCodeSet.has(sku)) {
        continue;
      }

      const product = {
        sku,
        name: faker.commerce.productName(),
        uom: "EACH",
        active: true
      };

      products.push(product);
      productCodeSet.add(sku);

      const priceKey = `PRODUCT:${sku}`;
      if (!basePriceKeySet.has(priceKey)) {
        basePrices.push({
          item_code: sku,
          item_type: "PRODUCT",
          amount: faker.number.float({ min: 5, max: 500, fractionDigits: 2 }),
          currency: seed.environment.currency
        });
        basePriceKeySet.add(priceKey);
      }
    }

    for (let i = 0; i < seed.faker.counts.catalog_services; i += 1) {
      const code = `DEMO-SVC-${String(i + 1).padStart(3, "0")}`;
      if (serviceCodeSet.has(code)) {
        continue;
      }

      const service = {
        code,
        name: `${faker.commerce.productAdjective()} ${faker.commerce.department()} Service`,
        active: true
      };

      services.push(service);
      serviceCodeSet.add(code);

      const priceKey = `SERVICE:${code}`;
      if (!basePriceKeySet.has(priceKey)) {
        basePrices.push({
          item_code: code,
          item_type: "SERVICE",
          amount: faker.number.float({ min: 40, max: 350, fractionDigits: 2 }),
          currency: seed.environment.currency
        });
        basePriceKeySet.add(priceKey);
      }
    }
  }

  lines.push("-- Catalog products");
  for (const product of products) {
    const id = stableUuid("product", product.sku);
    lines.push("INSERT INTO product (id, sku, name, active, created_at, updated_at)");
    lines.push(
      `VALUES (${quoteLiteral(id)}::uuid, ${quoteLiteral(product.sku)}, ${quoteLiteral(product.name)}, ${boolLiteral(
        product.active
      )}, NOW(), NOW())`
    );
    lines.push("ON CONFLICT (sku) DO UPDATE SET name = EXCLUDED.name, active = EXCLUDED.active, updated_at = NOW();");
  }

  lines.push("\n-- Catalog services");
  for (const service of services) {
    const id = stableUuid("service", service.code);
    lines.push("INSERT INTO service (id, code, name, active, created_at, updated_at)");
    lines.push(
      `VALUES (${quoteLiteral(id)}::uuid, ${quoteLiteral(service.code)}, ${quoteLiteral(service.name)}, ${boolLiteral(
        service.active
      )}, NOW(), NOW())`
    );
    lines.push("ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, active = EXCLUDED.active, updated_at = NOW();");
  }

  lines.push("\n-- Base prices");
  for (const price of basePrices) {
    const id = stableUuid("base-price", `${price.item_type}:${price.item_code}`);
    lines.push("INSERT INTO product_base_price (id, product_code, amount, currency, created_at, updated_at)");
    lines.push(
      `VALUES (${quoteLiteral(id)}::uuid, ${quoteLiteral(price.item_code)}, ${price.amount}, ${quoteLiteral(
        price.currency
      )}, NOW(), NOW())`
    );
    lines.push("ON CONFLICT (id) DO NOTHING;");
  }

  lines.push("\n-- Replenishment policies");
  for (const policy of seed.inventory.replenishment_policies) {
    const id = stableUuid("replenishment-policy", policy.policy_code);
    lines.push(
      "INSERT INTO replenishment_policy (id, location_id, sku, reorder_point, reorder_quantity, active, created_at, updated_at)"
    );
    lines.push(
      `VALUES (${quoteLiteral(id)}::uuid, ${quoteLiteral(stableUuid("site", policy.location_code))}::uuid, ${quoteLiteral(
        policy.sku
      )}, ${policy.reorder_point}, ${policy.reorder_quantity}, TRUE, NOW(), NOW())`
    );
    lines.push("ON CONFLICT (id) DO NOTHING;");
  }

  lines.push("\n-- Putaway rules");
  for (const rule of seed.inventory.putaway_rules) {
    const id = stableUuid("putaway-rule", rule.rule_code);
    lines.push("INSERT INTO putaway_rule (id, location_id, sku_prefix, destination_code, active, created_at, updated_at)");
    lines.push(
      `VALUES (${quoteLiteral(id)}::uuid, ${quoteLiteral(stableUuid("site", rule.location_code))}::uuid, ${quoteLiteral(
        rule.sku_prefix
      )}, ${quoteLiteral(rule.destination_storage_code)}, TRUE, NOW(), NOW())`
    );
    lines.push("ON CONFLICT (id) DO NOTHING;");
  }

  lines.push("\n-- Approval thresholds");
  for (const threshold of seed.inventory.approval_thresholds) {
    const id = stableUuid("approval-threshold", threshold.tier);
    lines.push("INSERT INTO approval_threshold_config (id, approval_tier, max_delta_value, active, created_at, updated_at)");
    lines.push(
      `VALUES (${quoteLiteral(id)}::uuid, ${quoteLiteral(threshold.tier)}, ${threshold.max_delta_value}, ${boolLiteral(
        threshold.active
      )}, NOW(), NOW())`
    );
    lines.push("ON CONFLICT (id) DO NOTHING;");
  }

  return `${lines.join("\n")}\n`;
}
