import fs from "node:fs/promises";
import YAML from "yaml";

import { seedSchema, validateCrossReferences } from "./seed-schema.js";

export async function loadSeedInput(inputPath, overrideProfile) {
  const raw = await fs.readFile(inputPath, "utf8");
  const parsed = YAML.parse(raw);

  if (overrideProfile) {
    parsed.profile = overrideProfile;
  }

  const validated = seedSchema.parse(parsed);
  validateCrossReferences(validated);
  return validated;
}
