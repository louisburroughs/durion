import { boolLiteral, quoteLiteral, sqlHeader, stableUuid } from "../lib/sql.js";

export function emitAccountingSql(seed) {
  const lines = [sqlHeader("005_accounting.sql")];

  lines.push("-- GL accounts");
  for (const account of seed.accounting.gl_accounts) {
    const id = stableUuid("gl-account", account.code);
    lines.push(
      "INSERT INTO gl_account (gl_account_id, account_code, account_name, account_type, created_at, created_by)"
    );
    lines.push(
      `VALUES (${quoteLiteral(id)}::uuid, ${quoteLiteral(account.code)}, ${quoteLiteral(account.name)}, ${quoteLiteral(
        account.type
      )}, NOW(), 'seed-generator')`
    );
    lines.push("ON CONFLICT (account_code) DO UPDATE SET account_name = EXCLUDED.account_name;");
  }

  lines.push("\n-- Posting categories");
  for (const category of seed.accounting.posting_categories) {
    const id = stableUuid("posting-category", category.code);
    lines.push(
      "INSERT INTO posting_category (posting_category_id, category_name, description, is_active, created_at, created_by)"
    );
    lines.push(
      `VALUES (${quoteLiteral(id)}::uuid, ${quoteLiteral(category.name)}, ${quoteLiteral(
        category.code
      )}, TRUE, NOW(), 'seed-generator')`
    );
    lines.push("ON CONFLICT (posting_category_id) DO NOTHING;");
  }

  lines.push("\n-- Mapping keys");
  for (const key of seed.accounting.mapping_keys) {
    const id = stableUuid("mapping-key", key.code);
    const categoryId = stableUuid("posting-category", key.posting_category_code);
    lines.push(
      "INSERT INTO mapping_key (mapping_key_id, posting_category_id, key_name, description, is_active, created_at, created_by)"
    );
    lines.push(
      `VALUES (${quoteLiteral(id)}::uuid, ${quoteLiteral(categoryId)}::uuid, ${quoteLiteral(
        key.code
      )}, ${quoteLiteral(key.code)}, TRUE, NOW(), 'seed-generator')`
    );
    lines.push("ON CONFLICT (mapping_key_id) DO NOTHING;");
  }

  lines.push("\n-- GL mappings");
  for (const mapping of seed.accounting.gl_mappings) {
    const id = stableUuid("gl-mapping", `${mapping.source_system}:${mapping.external_code}`);
    const categoryId = stableUuid("posting-category", mapping.posting_category_code);
    const keyId = stableUuid("mapping-key", mapping.mapping_key_code);
    const accountId = stableUuid("gl-account", mapping.gl_account_code);
    lines.push(
      "INSERT INTO gl_mapping (gl_mapping_id, source_system, external_code, posting_category_id, mapping_key_id, gl_account_id, effective_start_date, created_at, created_by)"
    );
    lines.push(
      `VALUES (${quoteLiteral(id)}::uuid, ${quoteLiteral(mapping.source_system)}, ${quoteLiteral(
        mapping.external_code
      )}, ${quoteLiteral(categoryId)}::uuid, ${quoteLiteral(keyId)}::uuid, ${quoteLiteral(
        accountId
      )}::uuid, NOW(), NOW(), 'seed-generator')`
    );
    lines.push("ON CONFLICT (gl_mapping_id) DO NOTHING;");
  }

  lines.push("\n-- Default GL mappings");
  for (const defaultMapping of seed.accounting.default_gl_mappings) {
    const id = stableUuid("default-gl-mapping", defaultMapping.event_type);
    const debitId = stableUuid("gl-account", defaultMapping.debit_gl_account_code);
    const creditId = stableUuid("gl-account", defaultMapping.credit_gl_account_code);
    lines.push(
      "INSERT INTO default_gl_mapping (mapping_id, event_type, organization_id, debit_account_id, credit_account_id, description, active, created_at, created_by)"
    );
    lines.push(
      `VALUES (${quoteLiteral(id)}::uuid, ${quoteLiteral(defaultMapping.event_type)}, NULL, ${quoteLiteral(
        debitId
      )}::uuid, ${quoteLiteral(creditId)}::uuid, ${quoteLiteral(
        `${defaultMapping.event_type} default mapping`
      )}, ${boolLiteral(defaultMapping.active)}, NOW(), 'seed-generator')`
    );
    lines.push("ON CONFLICT (mapping_id) DO NOTHING;");
  }

  lines.push("\n-- Statement line mappings");
  for (const slm of seed.accounting.statement_line_mappings) {
    const id = stableUuid("statement-line-mapping", `${slm.statement_type}:${slm.line_code}:${slm.gl_account_code}`);
    const accountId = stableUuid("gl-account", slm.gl_account_code);
    lines.push(
      "INSERT INTO statement_line_mappings (mapping_id, gl_account_id, account_name, statement_type, statement_line_code, line_description, display_order, operation)"
    );
    lines.push(
      `VALUES (${quoteLiteral(id)}::uuid, ${quoteLiteral(accountId)}::uuid, ${quoteLiteral(
        slm.gl_account_code
      )}, ${quoteLiteral(slm.statement_type)}, ${quoteLiteral(slm.line_code)}, ${quoteLiteral(
        slm.line_code
      )}, 1, ${quoteLiteral(slm.operation)})`
    );
    lines.push("ON CONFLICT (mapping_id) DO NOTHING;");
  }

  return `${lines.join("\n")}\n`;
}
