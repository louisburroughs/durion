# AWS Access for Claude Code

This repo is wired so Claude Code can both run on AWS and act on your AWS account.
There are two independent layers — set up whichever you need.

## 1. Resource access via AWS MCP servers (configured here)

`.mcp.json` registers two official AWS Labs MCP servers:

| Server     | Package                                     | Purpose                                   |
| ---------- | ------------------------------------------- | ----------------------------------------- |
| `aws-api`  | `awslabs.aws-api-mcp-server@latest`         | Call any AWS API (structured, scoped)     |
| `aws-docs` | `awslabs.aws-documentation-mcp-server@latest` | Retrieve current AWS docs at query time |

These run via `uvx` (the `uv` Python tool runner). The `SessionStart` hook
`.claude/hooks/aws-setup-hook.sh` ensures `uv` is installed in ephemeral web
sandboxes and reports credential status (never the secret values).

Safety default: `aws-api` runs with `REQUIRE_MUTATION_CONSENT=true`, so write
operations require approval. To make it read-only, set `READ_OPERATIONS_ONLY=true`
in `.mcp.json`.

### Credentials

The MCP server uses the standard AWS SDK credential chain. Provide credentials as
**environment variables / secrets in your Claude Code web environment settings** —
never commit them:

- `AWS_ACCESS_KEY_ID` + `AWS_SECRET_ACCESS_KEY` (+ optional `AWS_SESSION_TOKEN`)
- or `AWS_PROFILE` with a mounted `~/.aws/credentials`

Use least-privilege, ideally short-lived (STS) credentials — the sandbox is
disposable.

### Network policy

The web environment's outbound network policy must allow reaching AWS API/MCP
endpoints, or calls will fail. See
https://code.claude.com/docs/en/claude-code-on-the-web

## 2. Run the Claude model on Amazon Bedrock (optional, opt-in)

This changes *where the model runs* (Anthropic API → your Bedrock account). It is
**not** enabled by default because it requires valid Bedrock access. To enable, set
in your environment:

```bash
export CLAUDE_CODE_USE_BEDROCK=1
export AWS_REGION=us-east-1
# plus AWS credentials as above
```

Your IAM principal needs `bedrock:InvokeModel*` and inference-profile permissions.
