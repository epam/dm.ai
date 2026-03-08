# CLI MCP Tools

**Total Tools**: 1

## Quick Reference

```bash
# List all cli tools
dmtools list | jq '.tools[] | select(.name | startswith("cli_"))'

# Example usage
dmtools cli_execute_command [arguments]
```

## Usage in JavaScript Agents

```javascript
// Direct function calls for cli tools
const result = cli_execute_command(...);
```

## Available Tools

| Tool Name | Description | Parameters |
|-----------|-------------|------------|
| `cli_execute_command` | Execute CLI commands from JavaScript post-actions. Base whitelist: git, gh, dmtools, npm, yarn, docker, kubectl, terraform, ansible, aws, gcloud, az. Additional commands can be enabled via `CLI_ALLOWED_COMMANDS` in dmtools.env or agent `envVariables`. Returns command output as string. | `command` (string, **required**)<br>`workingDirectory` (string, optional) |

## Detailed Parameter Information

### `cli_execute_command`

Execute CLI commands from JavaScript post-actions. Base whitelist is always active; extra commands are opt-in via configuration.

**Parameters:**

- **`command`** (string) 🔴 Required
  - CLI command to execute. Must start with a whitelisted command. Extend the whitelist via `CLI_ALLOWED_COMMANDS` in dmtools.env.
  - Example: `git commit -m 'Automated update'`

- **`workingDirectory`** (string) ⚪ Optional
  - Working directory for command execution. Defaults to repository root if not specified. Use absolute path or path relative to current directory.
  - Example: `/path/to/repo`

**Example:**
```bash
dmtools cli_execute_command "git status --short" "/path/to/repo"
```

```javascript
// In JavaScript agent
const result = cli_execute_command("git status --short", "/path/to/repo");
// or with working directory omitted
const version = cli_execute_command("git --version", null);
```

---

## Security & Whitelist Configuration

### Base Whitelist (always active)

```
git  gh  dmtools  npm  yarn  docker  kubectl  terraform  ansible  aws  gcloud  az
```

### Extending the Whitelist

Extra commands are **opt-in** per environment or per agent. They never override the base list — they add to it.

#### Option 1 — `dmtools.env` (environment-level)

```bash
# Add Unix utilities, language runtimes, custom scripts
CLI_ALLOWED_COMMANDS=find,ls,cat,mkdir,pytest,python3,pip3,curl,ffmpeg,bash,run-cursor-agent.sh
```

#### Option 2 — Agent JSON `envVariables` (agent-level)

```json
{
  "name": "MyJob",
  "params": {
    "envVariables": {
      "CLI_ALLOWED_COMMANDS": "pytest,python3,pip3"
    }
  }
}
```

Agent-level overrides take precedence over `dmtools.env`, so different agents can have different allowed command sets without changing the shared env file.

### Running Custom Scripts

Scripts (`.sh` or any executable) are matched by **basename**, so you can call them with or without a path prefix:

```bash
# In dmtools.env
CLI_ALLOWED_COMMANDS=run-cursor-agent.sh

# All of these work in CLI_ALLOWED_COMMANDS-permitted agents:
cli_execute_command("./run-cursor-agent.sh --arg", null)
cli_execute_command("/full/path/to/run-cursor-agent.sh", null)
cli_execute_command("run-cursor-agent.sh", "/working/dir")
```

### Error When Command Is Blocked

```
SecurityException: Command not allowed. Whitelisted commands: git, gh, ...
  Add more via CLI_ALLOWED_COMMANDS in dmtools.env or agent envVariables.
```

---

