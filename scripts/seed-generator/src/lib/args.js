import path from "node:path";

function valueAfter(args, flag, defaultValue = undefined) {
  const idx = args.indexOf(flag);
  if (idx === -1) {
    return defaultValue;
  }
  if (idx === args.length - 1) {
    throw new Error(`Missing value for ${flag}`);
  }
  return args[idx + 1];
}

export function parseArgs(argv) {
  const args = argv.slice(2);

  const input = valueAfter(
    args,
    "--input",
    path.resolve(process.cwd(), "../../docs/architecture/deployment/data-migration/seed-input.example.yaml")
  );
  const out = valueAfter(args, "--out", path.resolve(process.cwd(), "./generated-seed-sql"));
  const profile = valueAfter(args, "--profile", undefined);
  const backendRoot = valueAfter(
    args,
    "--backend-root",
    path.resolve(process.cwd(), "../../../durion-positivity-backend")
  );

  return {
    input: path.resolve(input),
    out: path.resolve(out),
    profile,
    backendRoot: path.resolve(backendRoot)
  };
}
