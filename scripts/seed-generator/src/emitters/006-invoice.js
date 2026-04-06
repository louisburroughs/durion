import { quoteLiteral, sqlHeader, stableUuid } from "../lib/sql.js";

export function emitInvoiceSql(seed) {
  const lines = [sqlHeader("006_invoice.sql")];
  lines.push("-- Billing rules");

  for (const rule of seed.invoice.billing_rules) {
    const id = stableUuid("billing-rule", rule.party_code);
    lines.push(
      "INSERT INTO billing_rules (id, party_id, purchase_order_required, payment_terms_code, invoice_delivery_method, created_at, updated_at, updated_by)"
    );
    lines.push(
      `VALUES (${quoteLiteral(id)}::uuid, ${quoteLiteral(rule.party_code)}, ${
        rule.purchase_order_required ? "TRUE" : "FALSE"
      }, ${quoteLiteral(rule.payment_terms_code)}, ${quoteLiteral(rule.delivery_method)}, NOW(), NOW(), 'seed-generator')`
    );
    lines.push(
      "ON CONFLICT (party_id) DO UPDATE SET purchase_order_required = EXCLUDED.purchase_order_required, payment_terms_code = EXCLUDED.payment_terms_code, invoice_delivery_method = EXCLUDED.invoice_delivery_method, updated_at = NOW();"
    );
  }

  return `${lines.join("\n")}\n`;
}
