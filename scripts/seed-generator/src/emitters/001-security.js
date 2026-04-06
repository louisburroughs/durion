import { boolLiteral, quoteLiteral, sqlHeader, stableUuid } from "../lib/sql.js";

const DEFAULT_ROLES = [
  // Keep admin/customer roles unchanged.
  "ADMIN",
  "CUSTOMER",
  "SELF_SERVICE_CUSTOMER",
  // Only roles explicitly listed as "- Canonical persona:" in docs/journeys/personas.md
  "SERVICE_ADVISOR",
  "ACCOUNT_MANAGER",
  "LOCATION_MANAGER",
  "DISPATCHER",
  "TECHNICIAN",
  "ACCOUNTING_ASSOCIATE",
  "SYSTEM_ADMINISTRATOR"
];

const ROLE_DESCRIPTIONS = {
  ADMIN: "ADMIN seeded role",
  CUSTOMER: "CUSTOMER seeded role",
  SELF_SERVICE_CUSTOMER: "SELF_SERVICE_CUSTOMER seeded role",
  SERVICE_ADVISOR: "Canonical persona: Service Advisor",
  ACCOUNT_MANAGER: "Canonical persona: Account Manager",
  LOCATION_MANAGER: "Canonical persona: Location Manager",
  DISPATCHER: "Canonical persona: Dispatcher",
  TECHNICIAN: "Canonical persona: Technician",
  ACCOUNTING_ASSOCIATE: "Canonical persona: Accounting Associate",
  SYSTEM_ADMINISTRATOR: "Canonical persona: System Administrator"
};

function permissionTriplet(permissionName) {
  const parts = permissionName.split(":");
  if (parts.length === 3) {
    return { domain: parts[0], resource: parts[1], action: parts[2] };
  }
  if (parts.length === 2) {
    return { domain: parts[0], resource: "", action: parts[1] };
  }
  return { domain: "unknown", resource: "", action: "unknown" };
}

export function emitSecuritySql(seed, derived) {
  const lines = [sqlHeader("001_security.sql")];

  lines.push("-- Roles");
  const roles = new Set(DEFAULT_ROLES);
  for (const ra of seed.security.role_assignments) {
    roles.add(ra.role);
  }

  for (const roleName of [...roles].sort()) {
    const roleId = stableUuid("role", roleName);
    lines.push("INSERT INTO roles (id, name, description, created_at, created_by)");
    lines.push(
      `VALUES (${quoteLiteral(roleId)}::uuid, ${quoteLiteral(roleName)}, ${quoteLiteral(
        ROLE_DESCRIPTIONS[roleName] || `${roleName} seeded role`
      )}, NOW(), 'seed-generator')`
    );
    lines.push("ON CONFLICT (name) DO NOTHING;");
  }

  lines.push("\n-- Users");
  for (const user of seed.security.users) {
    const userId = stableUuid("user", user.username);
    lines.push("INSERT INTO users (id, username, password, enabled, created_at, updated_at)");
    lines.push(
      `VALUES (${quoteLiteral(userId)}::uuid, ${quoteLiteral(user.username)}, ${quoteLiteral(
        user.password.value
      )}, ${boolLiteral(user.enabled)}, NOW(), NOW())`
    );
    lines.push("ON CONFLICT (username) DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = NOW();");
  }

  lines.push("\n-- Permissions (derived from permissions.yaml)");
  for (const permission of derived.permissions) {
    const id = stableUuid("permission", permission.name);
    const triplet = permissionTriplet(permission.name);
    const bitIndex = derived.permissionBitIndexes.get(permission.name);

    lines.push(
      "INSERT INTO permissions (id, name, description, domain, resource, action, registered_at, registered_by_service, version, bit_index, deprecated, created_at, updated_at)"
    );
    lines.push(
      `VALUES (${quoteLiteral(id)}::uuid, ${quoteLiteral(permission.name)}, ${quoteLiteral(
        permission.description
      )}, ${quoteLiteral(triplet.domain)}, ${quoteLiteral(triplet.resource)}, ${quoteLiteral(
        triplet.action
      )}, NOW(), ${quoteLiteral(permission.serviceName)}, ${quoteLiteral(permission.version || "1.0")}, ${
        bitIndex === undefined ? "NULL" : String(bitIndex)
      }, FALSE, NOW(), NOW())`
    );
    lines.push(
      "ON CONFLICT (name) DO UPDATE SET description = EXCLUDED.description, bit_index = COALESCE(EXCLUDED.bit_index, permissions.bit_index), updated_at = NOW();"
    );
  }

  lines.push("\n-- Role assignments");
  for (const ra of seed.security.role_assignments) {
    const assignmentId = stableUuid("role-assignment", `${ra.username}:${ra.role}:${ra.scope_type}`);
    const userId = stableUuid("user", ra.username);
    const roleId = stableUuid("role", ra.role);
    lines.push(
      "INSERT INTO role_assignments (id, user_id, role_id, scope_type, effective_start_date, created_at, created_by)"
    );
    lines.push(
      `VALUES (${quoteLiteral(assignmentId)}::uuid, ${quoteLiteral(userId)}::uuid, ${quoteLiteral(
        roleId
      )}::uuid, ${quoteLiteral(ra.scope_type)}, CURRENT_TIMESTAMP, NOW(), 'seed-generator')`
    );
    lines.push("ON CONFLICT (id) DO NOTHING;");
  }

  lines.push("\n-- Role/permission mapping");
  lines.push("-- Intentionally minimal by default; populate security.role_permission_overrides for strict curation.");

  return `${lines.join("\n")}\n`;
}
