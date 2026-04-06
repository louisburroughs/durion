import { quoteLiteral, sqlHeader } from "../lib/sql.js";

export function emitAssertionsSql(seed, derived) {
  const lines = [sqlHeader("999_assertions.sql")];

  lines.push("-- Critical assertions");
  lines.push("DO $$");
  lines.push("BEGIN");
  lines.push("  IF (SELECT COUNT(*) FROM users) = 0 THEN");
  lines.push("    RAISE EXCEPTION 'Seed assertion failed: users is empty';");
  lines.push("  END IF;");
  lines.push("  IF (SELECT COUNT(*) FROM roles) = 0 THEN");
  lines.push("    RAISE EXCEPTION 'Seed assertion failed: roles is empty';");
  lines.push("  END IF;");
  lines.push("  IF (SELECT COUNT(*) FROM permissions) < 1 THEN");
  lines.push("    RAISE EXCEPTION 'Seed assertion failed: permissions is empty';");
  lines.push("  END IF;");
  lines.push("END $$;");

  lines.push("\n-- Deterministic counts (informational checks)");
  lines.push(
    `-- Expected minimum event_type rows from derived registries: ${quoteLiteral(String(derived.eventTypes.length))}`
  );
  lines.push(
    `-- Expected minimum permission rows from manifests: ${quoteLiteral(String(derived.permissions.length))}`
  );
  lines.push(`-- Profile: ${quoteLiteral(seed.profile)}`);

  return `${lines.join("\n")}\n`;
}
