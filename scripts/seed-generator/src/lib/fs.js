import fs from "node:fs/promises";
import path from "node:path";

export async function ensureDir(dirPath) {
  await fs.mkdir(dirPath, { recursive: true });
}

export async function writeFile(outDir, name, content) {
  await ensureDir(outDir);
  const target = path.join(outDir, name);
  await fs.writeFile(target, content, "utf8");
  return target;
}
