import fs from "node:fs/promises";
import path from "node:path";

import { glob } from "glob";
import YAML from "yaml";

export async function extractPermissions(backendRoot) {
  const pattern = path.join(backendRoot, "pos-*", "src/main/resources/permissions.yaml");
  const files = await glob(pattern.replace(/\\/g, "/"));

  const permissions = [];

  for (const file of files.sort()) {
    const raw = await fs.readFile(file, "utf8");
    const doc = YAML.parse(raw);
    const serviceName = doc.serviceName || path.basename(path.dirname(path.dirname(path.dirname(file))));
    const version = doc.version || "1.0";

    for (const p of doc.permissions || []) {
      permissions.push({
        name: p.name,
        description: p.description || "",
        serviceName,
        version
      });
    }
  }

  const unique = new Map();
  for (const p of permissions) {
    if (!unique.has(p.name)) {
      unique.set(p.name, p);
    }
  }

  return [...unique.values()].sort((a, b) => a.name.localeCompare(b.name));
}
