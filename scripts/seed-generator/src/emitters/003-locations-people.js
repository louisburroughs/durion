import { boolLiteral, quoteLiteral, sqlHeader, stableUuid } from "../lib/sql.js";

export function emitLocationsPeopleSql(seed) {
  const lines = [sqlHeader("003_locations_people.sql")];

  lines.push("-- Location types");
  for (const type of seed.locations.location_types) {
    const id = stableUuid("location-type", type.code);
    lines.push("INSERT INTO location_type (id, name, description, created_at, updated_at)");
    lines.push(
      `VALUES (${quoteLiteral(id)}::uuid, ${quoteLiteral(type.name)}, ${quoteLiteral(
        type.description || null
      )}, NOW(), NOW())`
    );
    lines.push("ON CONFLICT (name) DO NOTHING;");
  }

  lines.push("\n-- Service areas");
  for (const area of seed.locations.service_areas) {
    const id = stableUuid("service-area", area.code);
    lines.push("INSERT INTO service_areas (id, name, description, active, created_at, updated_at)");
    lines.push(
      `VALUES (${quoteLiteral(id)}::uuid, ${quoteLiteral(area.name)}, ${quoteLiteral(
        `${area.code} service area`
      )}, TRUE, NOW(), NOW())`
    );
    lines.push("ON CONFLICT (name) DO NOTHING;");
  }

  lines.push("\n-- Capabilities");
  for (const cap of seed.locations.capabilities) {
    const id = stableUuid("capability", cap.code);
    lines.push("INSERT INTO service_location_capabilities (id, code, name, active, created_at, updated_at)");
    lines.push(
      `VALUES (${quoteLiteral(id)}::uuid, ${quoteLiteral(cap.code)}, ${quoteLiteral(cap.name)}, TRUE, NOW(), NOW())`
    );
    lines.push("ON CONFLICT (code) DO NOTHING;");
  }

  lines.push("\n-- Travel buffer policies");
  for (const p of seed.locations.travel_buffer_policies) {
    const id = stableUuid("travel-buffer-policy", p.code);
    lines.push("INSERT INTO travel_buffer_policies (id, name, buffer_type, buffer_value, created_at, updated_at)");
    lines.push(
      `VALUES (${quoteLiteral(id)}::uuid, ${quoteLiteral(p.name)}, ${quoteLiteral(p.buffer_type)}, ${
        p.buffer_value
      }, NOW(), NOW())`
    );
    lines.push("ON CONFLICT (name) DO NOTHING;");
  }

  lines.push("\n-- Sites (location)");
  for (const site of seed.locations.sites) {
    const id = stableUuid("site", site.code);
    const locationTypeId = stableUuid("location-type", site.location_type_code);
    lines.push(
      "INSERT INTO location (id, code, name, city, state, country, address_line1, postal_code, active, location_type_id, created_at, updated_at)"
    );
    lines.push(
      `VALUES (${quoteLiteral(id)}::uuid, ${quoteLiteral(site.code)}, ${quoteLiteral(site.name)}, ${quoteLiteral(
        site.address.city
      )}, ${quoteLiteral(site.address.state)}, ${quoteLiteral(site.address.country)}, ${quoteLiteral(
        site.address.line1
      )}, ${quoteLiteral(site.address.postal_code)}, TRUE, ${quoteLiteral(locationTypeId)}::uuid, NOW(), NOW())`
    );
    lines.push("ON CONFLICT (code) DO NOTHING;");
  }

  lines.push("\n-- user_person_links");
  for (const link of seed.people.user_person_links) {
    const id = stableUuid("user-person-link", `${link.username}:${link.person_ref}`);
    const userId = stableUuid("user", link.username);
    const personId = stableUuid("person", link.person_ref);
    lines.push("INSERT INTO user_person_links (id, user_id, person_id, link_type, status, created_at, updated_at, created_by)");
    lines.push(
      `VALUES (${quoteLiteral(id)}::uuid, ${quoteLiteral(userId)}::uuid, ${quoteLiteral(
        personId
      )}::uuid, ${quoteLiteral(link.link_type)}, 'ACTIVE', NOW(), NOW(), 'seed-generator')`
    );
    lines.push("ON CONFLICT (id) DO NOTHING;");
  }

  lines.push("\n-- person_location_assignment");
  for (const assignment of seed.people.person_location_assignments) {
    const id = stableUuid("person-location-assignment", `${assignment.person_ref}:${assignment.location_code}`);
    const personId = stableUuid("person", assignment.person_ref);
    const locationId = stableUuid("site", assignment.location_code);
    lines.push(
      "INSERT INTO person_location_assignment (id, person_id, location_id, role, is_primary, status, effective_from, created_at, updated_at, created_by)"
    );
    lines.push(
      `VALUES (${quoteLiteral(id)}::uuid, ${quoteLiteral(personId)}::uuid, ${quoteLiteral(
        locationId
      )}::uuid, ${quoteLiteral(assignment.role)}, ${boolLiteral(assignment.is_primary)}, 'ACTIVE', CURRENT_DATE, NOW(), NOW(), 'seed-generator')`
    );
    lines.push("ON CONFLICT (id) DO NOTHING;");
  }

  lines.push("\n-- timekeeping_policy");
  for (const policy of seed.people.timekeeping_policies) {
    const id = stableUuid("timekeeping-policy", policy.code);
    const scopeId =
      policy.scope_type === "LOCATION" && policy.scope_ref ? `${quoteLiteral(stableUuid("site", policy.scope_ref))}::uuid` : "NULL";
    lines.push(
      "INSERT INTO timekeeping_policy (timekeeping_policy_id, scope_type, scope_id, job_time_discrepancy_threshold_minutes, effective_start_at, updated_by, created_at, updated_at)"
    );
    lines.push(
      `VALUES (${quoteLiteral(id)}::uuid, ${quoteLiteral(policy.scope_type)}, ${scopeId}, ${
        policy.threshold_minutes
      }, NOW(), 'seed-generator', NOW(), NOW())`
    );
    lines.push("ON CONFLICT (timekeeping_policy_id) DO NOTHING;");
  }

  return `${lines.join("\n")}\n`;
}
