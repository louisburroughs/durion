import fs from "node:fs/promises";
import path from "node:path";

export async function extractPermissionBitIndexes(backendRoot) {
  const target = path.join(
    backendRoot,
    "pos-security-service/src/main/java/com/positivity/securityservice/internal/enums/PermissionCode.java"
  );

  const text = await fs.readFile(target, "utf8");
  const regex = /[A-Z0-9_]+\((\d+)\s*,\s*"([a-z0-9:_-]+)"\)/g;

  const map = new Map();
  let match;
  while ((match = regex.exec(text)) !== null) {
    map.set(match[2], Number(match[1]));
  }

  return map;
}
