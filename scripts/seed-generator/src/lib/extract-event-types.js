import fs from "node:fs/promises";
import path from "node:path";

import { glob } from "glob";

const PRESETS = {
  fastRead: { p50: 50_000, p95: 200_000, p99: 500_000 },
  search: { p50: 100_000, p95: 500_000, p99: 1_000_000 },
  write: { p50: 200_000, p95: 1_000_000, p99: 3_000_000 },
  approval: { p50: 500_000, p95: 2_000_000, p99: 5_000_000 }
};

function parseEventRegistrations(text) {
  const out = [];

  const chainRegex = /EventTypeRegistration\.(fastRead|search|write|approval)\(\s*"([A-Z0-9_]+)"\s*,\s*"([^"]+)"\s*\)([\s\S]{0,200}?)\.build\(\)/g;
  let match;
  while ((match = chainRegex.exec(text)) !== null) {
    const presetName = match[1];
    const typeCode = match[2];
    const description = match[3];
    const tail = match[4] || "";
    const apiMatch = tail.match(/\.apiVersion\(\s*"([^"]+)"\s*\)/);
    const preset = PRESETS[presetName];
    out.push({
      typeCode,
      description,
      apiVersion: apiMatch ? apiMatch[1] : "1",
      active: true,
      p50Micros: preset.p50,
      p95Micros: preset.p95,
      p99Micros: preset.p99
    });
  }

  return out;
}

export async function extractEventTypes(backendRoot) {
  const pattern = path.join(backendRoot, "pos-*", "src/main/java", "**", "*EventTypes.java");
  const files = await glob(pattern.replace(/\\/g, "/"));

  const map = new Map();

  for (const file of files.sort()) {
    const text = await fs.readFile(file, "utf8");
    const list = parseEventRegistrations(text);

    for (const e of list) {
      if (!map.has(e.typeCode)) {
        map.set(e.typeCode, e);
      }
    }
  }

  return [...map.values()].sort((a, b) => a.typeCode.localeCompare(b.typeCode));
}
