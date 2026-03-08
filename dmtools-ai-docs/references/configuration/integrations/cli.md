# CLI Integration Configuration

The `cli` integration exposes `cli_execute_command` — a tool for running shell commands from JavaScript agents and MCP workflows. It uses a security whitelist to prevent unintended command execution.

## Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `CLI_ALLOWED_COMMANDS` | No | _(none)_ | Comma-separated list of extra commands to allow in addition to the base whitelist |

## Base Whitelist (always active)

The following commands are always allowed regardless of configuration:

```
git  gh  dmtools  npm  yarn  docker  kubectl  terraform  ansible  aws  gcloud  az
```

## Extending the Whitelist

### Environment-level (`dmtools.env`)

Applies to all agents and MCP tool calls in the environment:

```bash
CLI_ALLOWED_COMMANDS=find,ls,cat,mkdir,pytest,python3,pip3,curl,ffmpeg,bash
```

### Agent-level (agent JSON `envVariables`)

Applies only to the specific agent/job. Takes precedence over `dmtools.env`:

```json
{
  "name": "PythonTestRunner",
  "params": {
    "envVariables": {
      "CLI_ALLOWED_COMMANDS": "pytest,python3,pip3"
    }
  }
}
```

### Custom Scripts

Scripts are matched by **basename** (filename without path), so prefixes like `./` or absolute paths are handled transparently:

```bash
# dmtools.env — allow a custom script
CLI_ALLOWED_COMMANDS=run-cursor-agent.sh,build.sh

# In JS agent — all three forms work:
cli_execute_command("./run-cursor-agent.sh --mode ci", null)
cli_execute_command("/workspace/scripts/run-cursor-agent.sh", null)
cli_execute_command("run-cursor-agent.sh --mode ci", "/workspace")
```

## Examples

### Basic unix utilities

```bash
# dmtools.env
CLI_ALLOWED_COMMANDS=find,ls,cat,mkdir,cp,mv
```

```javascript
// In JS agent
const files = cli_execute_command("find . -name '*.json' -maxdepth 2", null);
const content = cli_execute_command("cat README.md", "/path/to/repo");
```

### Python test automation

```bash
# dmtools.env
CLI_ALLOWED_COMMANDS=pytest,python3,pip3
```

```javascript
const testOutput = cli_execute_command("pytest tests/ -v --tb=short", "/workspace");
```

### Running curl / API calls

```bash
# dmtools.env
CLI_ALLOWED_COMMANDS=curl
```

```javascript
const response = cli_execute_command(
  "curl -s -H 'Accept: application/json' https://api.example.com/data",
  null
);
```

### Full multimedia pipeline

```bash
# dmtools.env
CLI_ALLOWED_COMMANDS=ffmpeg,bash,python3,curl
```

## Troubleshooting

**`SecurityException: Command not allowed`**

The command's executable is not in the whitelist. Add it to `CLI_ALLOWED_COMMANDS`:

```bash
CLI_ALLOWED_COMMANDS=<the-command-you-need>
```

**Script not found even when allowed**

Make sure the script is executable (`chmod +x script.sh`) and the working directory or PATH is correct.

**Changes to `dmtools.env` not picked up**

The config is read fresh on each tool invocation, so changes take effect immediately without restart. If running in a long-lived server process, restart it.
