import { z } from "zod";

const stringReq = z.string().min(1);

const userSchema = z.object({
  username: stringReq,
  email: stringReq.email(),
  person_ref: stringReq,
  enabled: z.boolean().default(true),
  password: z.object({
    mode: z.enum(["bcrypt_hash", "plaintext_for_hashing"]),
    value: stringReq
  })
});

export const seedSchema = z.object({
  version: z.literal(1),
  profile: z.enum(["prod-minimal", "staging-demo"]),
  tenant: z.object({
    id: stringReq,
    code: stringReq,
    name: stringReq
  }),
  environment: z.object({
    name: stringReq,
    default_timezone: stringReq,
    currency: z.string().length(3)
  }),
  security: z.object({
    users: z.array(userSchema).min(1),
    role_assignments: z.array(
      z.object({
        username: stringReq,
        role: stringReq,
        scope_type: z.enum(["GLOBAL", "LOCATION"]),
        location_codes: z.array(stringReq).default([])
      })
    ),
    role_permission_overrides: z.array(z.any()).default([])
  }),
  locations: z.object({
    location_types: z.array(z.object({ code: stringReq, name: stringReq, description: z.string().optional() })),
    sites: z.array(
      z.object({
        code: stringReq,
        name: stringReq,
        timezone: stringReq,
        location_type_code: stringReq,
        address: z.object({
          line1: stringReq,
          city: stringReq,
          state: stringReq,
          postal_code: stringReq,
          country: stringReq
        })
      })
    ),
    service_areas: z.array(z.object({ code: stringReq, name: stringReq })),
    capabilities: z.array(z.object({ code: stringReq, name: stringReq })),
    travel_buffer_policies: z.array(
      z.object({
        code: stringReq,
        name: stringReq,
        buffer_type: stringReq,
        buffer_value: z.number()
      })
    )
  }),
  people: z.object({
    persons: z.array(
      z.object({
        ref: stringReq,
        first_name: stringReq,
        last_name: stringReq,
        email: stringReq.email()
      })
    ),
    user_person_links: z.array(z.object({ username: stringReq, person_ref: stringReq, link_type: stringReq })),
    person_location_assignments: z.array(
      z.object({
        person_ref: stringReq,
        location_code: stringReq,
        role: stringReq,
        is_primary: z.boolean().default(false)
      })
    ),
    timekeeping_policies: z.array(
      z.object({
        code: stringReq,
        scope_type: z.enum(["GLOBAL", "LOCATION"]),
        scope_ref: z.string().nullable().optional(),
        threshold_minutes: z.number().int().nonnegative()
      })
    )
  }),
  catalog: z.object({
    products: z.array(z.object({ sku: stringReq, name: stringReq, uom: stringReq, active: z.boolean().default(true) })),
    services: z.array(z.object({ code: stringReq, name: stringReq, active: z.boolean().default(true) }))
  }),
  pricing: z.object({
    base_prices: z.array(
      z.object({
        item_code: stringReq,
        item_type: z.enum(["SERVICE", "PRODUCT"]),
        amount: z.number().nonnegative(),
        currency: z.string().length(3)
      })
    )
  }),
  inventory: z.object({
    replenishment_policies: z.array(
      z.object({
        policy_code: stringReq,
        location_code: stringReq,
        sku: stringReq,
        reorder_point: z.number().int().nonnegative(),
        reorder_quantity: z.number().int().positive()
      })
    ),
    putaway_rules: z.array(
      z.object({
        rule_code: stringReq,
        location_code: stringReq,
        sku_prefix: stringReq,
        destination_storage_code: stringReq
      })
    ),
    approval_thresholds: z.array(
      z.object({ tier: stringReq, max_delta_value: z.number().nonnegative(), active: z.boolean().default(true) })
    )
  }),
  accounting: z.object({
    gl_accounts: z.array(z.object({ code: stringReq, name: stringReq, type: stringReq })),
    posting_categories: z.array(z.object({ code: stringReq, name: stringReq })),
    mapping_keys: z.array(z.object({ code: stringReq, posting_category_code: stringReq })),
    gl_mappings: z.array(
      z.object({
        source_system: stringReq,
        external_code: stringReq,
        posting_category_code: stringReq,
        mapping_key_code: stringReq,
        gl_account_code: stringReq
      })
    ),
    default_gl_mappings: z.array(
      z.object({
        event_type: stringReq,
        debit_gl_account_code: stringReq,
        credit_gl_account_code: stringReq,
        active: z.boolean().default(true)
      })
    ),
    statement_line_mappings: z.array(
      z.object({
        statement_type: stringReq,
        line_code: stringReq,
        gl_account_code: stringReq,
        operation: stringReq
      })
    )
  }),
  invoice: z.object({
    billing_rules: z.array(
      z.object({
        party_code: stringReq,
        purchase_order_required: z.boolean().default(false),
        payment_terms_code: stringReq,
        delivery_method: stringReq
      })
    )
  }),
  faker: z.object({
    enabled: z.boolean().default(false),
    seed: z.number().int().default(42),
    locale: stringReq.default("en"),
    counts: z.object({
      catalog_products: z.number().int().nonnegative().default(0),
      catalog_services: z.number().int().nonnegative().default(0),
      demo_users: z.number().int().nonnegative().default(0),
      demo_customers: z.number().int().nonnegative().default(0),
      demo_vehicles: z.number().int().nonnegative().default(0),
      demo_orders: z.number().int().nonnegative().default(0),
      demo_invoices: z.number().int().nonnegative().default(0)
    })
  })
});

function assertRef(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

export function validateCrossReferences(input) {
  const userSet = new Set(input.security.users.map((u) => u.username));
  const personSet = new Set(input.people.persons.map((p) => p.ref));
  const locationSet = new Set(input.locations.sites.map((s) => s.code));
  const locationTypeSet = new Set(input.locations.location_types.map((t) => t.code));
  const accountSet = new Set(input.accounting.gl_accounts.map((a) => a.code));
  const categorySet = new Set(input.accounting.posting_categories.map((c) => c.code));
  const keySet = new Set(input.accounting.mapping_keys.map((k) => k.code));

  assertRef(
    input.people.timekeeping_policies.some((p) => p.scope_type === "GLOBAL"),
    "At least one GLOBAL timekeeping policy is required"
  );

  for (const assignment of input.security.role_assignments) {
    assertRef(userSet.has(assignment.username), `Unknown username in role_assignments: ${assignment.username}`);
    if (assignment.scope_type === "LOCATION") {
      for (const code of assignment.location_codes) {
        assertRef(locationSet.has(code), `Unknown location code in role_assignments: ${code}`);
      }
    }
  }

  for (const site of input.locations.sites) {
    assertRef(
      locationTypeSet.has(site.location_type_code),
      `Unknown location_type_code for site ${site.code}: ${site.location_type_code}`
    );
  }

  for (const link of input.people.user_person_links) {
    assertRef(userSet.has(link.username), `Unknown username in user_person_links: ${link.username}`);
    assertRef(personSet.has(link.person_ref), `Unknown person_ref in user_person_links: ${link.person_ref}`);
  }

  for (const assignment of input.people.person_location_assignments) {
    assertRef(personSet.has(assignment.person_ref), `Unknown person_ref in person_location_assignments: ${assignment.person_ref}`);
    assertRef(locationSet.has(assignment.location_code), `Unknown location_code in person_location_assignments: ${assignment.location_code}`);
  }

  for (const mapping of input.accounting.gl_mappings) {
    assertRef(categorySet.has(mapping.posting_category_code), `Unknown posting_category_code: ${mapping.posting_category_code}`);
    assertRef(keySet.has(mapping.mapping_key_code), `Unknown mapping_key_code: ${mapping.mapping_key_code}`);
    assertRef(accountSet.has(mapping.gl_account_code), `Unknown gl_account_code: ${mapping.gl_account_code}`);
  }

  for (const mapping of input.accounting.default_gl_mappings) {
    assertRef(accountSet.has(mapping.debit_gl_account_code), `Unknown debit_gl_account_code: ${mapping.debit_gl_account_code}`);
    assertRef(accountSet.has(mapping.credit_gl_account_code), `Unknown credit_gl_account_code: ${mapping.credit_gl_account_code}`);
  }

  for (const slm of input.accounting.statement_line_mappings) {
    assertRef(accountSet.has(slm.gl_account_code), `Unknown gl_account_code in statement_line_mappings: ${slm.gl_account_code}`);
  }

  if (input.profile === "prod-minimal" && input.faker.enabled) {
    throw new Error("faker.enabled cannot be true for profile=prod-minimal unless generator override is added");
  }
}
