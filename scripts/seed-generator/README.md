# Seed Generator

Generates ordered preload SQL files from:

- `seed-input.yaml` (business-context input)
- derived permission manifests (`permissions.yaml`)
- derived event type registries (`*EventTypes.java`)

## Install

```bash
cd scripts/seed-generator
npm install
```

## Run

```bash
npm run seed:generate -- \
  --input ../../docs/architecture/deployment/data-migration/seed-input.example.yaml \
  --out ./generated-seed-sql \
  --backend-root ../../../durion-positivity-backend \
  --profile prod-minimal
```

## Output

The generator writes:

1. `001_security.sql`
2. `002_event_types.sql`
3. `003_locations_people.sql`
4. `004_catalog_pricing_inventory.sql`
5. `005_accounting.sql`
6. `006_invoice.sql`
7. `900_demo_optional.sql`
8. `999_assertions.sql`

## Notes

- SQL is scaffold quality and idempotent-oriented (`ON CONFLICT` paths).
- Table/column names across modules may differ by service schema and migration version; refine emitters before production rollout.
- `faker.enabled` is blocked for `profile=prod-minimal`.
- In `staging-demo`, faker can also expand catalog volume via:
  - `faker.counts.catalog_products`
  - `faker.counts.catalog_services`
- The catalog emitter auto-generates base prices for faker-created products/services using `environment.currency`.

Demo target example:

- Set `profile: staging-demo`
- Set `faker.enabled: true`
- Set `faker.counts.catalog_products: 100`
- Set `faker.counts.catalog_services: 20`
