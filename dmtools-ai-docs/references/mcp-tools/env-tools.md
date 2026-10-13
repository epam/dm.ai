# ENV MCP Tools

**Total Tools**: 2

Read-only access to **non-secret** configuration values for JS agents. The policy that decides what is readable lives in one place, `JsEnvPolicy`: the default list is derived from the names `PropertyReader` itself declares (so it cannot drift), minus anything that looks like a credential. Extend it with `JS_ENV_ALLOWLIST=NAME1,NAME2` in `dmtools.env` or the environment.

## Quick Reference

```bash
# List all env tools
dmtools list | jq '.tools[] | select(.name | startswith("env_"))'

# Example usage
dmtools env_get --data '{"name":"ADO_PROJECT"}'
dmtools env_list
```

## Usage in JavaScript Agents

```javascript
// Direct function calls for env tools
const r = JSON.parse(env_get({ name: 'ADO_PROJECT' }));   // { name, value } | { name, error }
const names = JSON.parse(env_list()).names;
```

## Available Tools

| Tool Name | Description | Parameters |
|-----------|-------------|------------|
| `env_get` | Read one non-secret configuration value (for example ADO_PROJECT, DEFAULT_TRACKER, JIRA_BASE_PATH). Credentials (names containing TOKEN, KEY, SECRET, PASSWORD, PAT, AUTH, ...) are never returned. Names outside the built-in list can be allowed with JS_ENV_ALLOWLIST=NAME1,NAME2. Returns {"name":..,"value":..} or {"name":..,"error":..}; value is null when the variable is unset. | `name` (string, **required**) |
| `env_list` | List the configuration names env_get will answer for: the built-in non-secret names plus those added through JS_ENV_ALLOWLIST. Names only, never values. | None |

## Detailed Parameter Information

### `env_get`

Read one non-secret configuration value. Credentials are never returned, even if listed in `JS_ENV_ALLOWLIST`.

**Parameters:**

- **`name`** (string) 🔴 Required
  - Configuration variable name (case-insensitive), e.g. `ADO_PROJECT`

**Answers:**

| Situation | Answer |
|---|---|
| allowed and set | `{"name":"ADO_PROJECT","value":"my-project"}` |
| allowed but unset | `{"name":"ADO_PROJECT","value":null}` |
| looks like a credential | `{"name":"...","error":"not readable: the name looks like a credential"}` |
| not built in and not allowlisted | `{"name":"...","error":"not allowed: add it to JS_ENV_ALLOWLIST ..."}` |
| not a valid name | `{"name":"...","error":"invalid name: ..."}` |

**Example:**
```javascript
// In JavaScript agent
const project = JSON.parse(env_get({ name: 'ADO_PROJECT' })).value;
```

---

### `env_list`

List the names `env_get` answers for (built-in non-secret names plus `JS_ENV_ALLOWLIST`). Names only. Secret-looking entries in `JS_ENV_ALLOWLIST` are reported under `ignoredSecretLikeNames`.

**Example:**
```javascript
const { names } = JSON.parse(env_list());
```

---

## What counts as a secret

A name is a credential when any `_`-separated word is `TOKEN`, `KEY`, `SECRET`, `PASSWORD`, `PASS`, `PAT`, `AUTH`, `LOGIN`, `CREDENTIAL(S)`, `BEARER`, `COOKIE`, `SESSION`, `PRIVATE`, `CERT`, `SIGNATURE`, `PASSPHRASE` (or ends in `TOKEN` / `SECRET` / `PASSWORD`). Limits such as `OPENAI_MAX_TOKENS` and `PROMPT_CHUNK_TOKEN_LIMIT` are configuration and stay readable.

## Limits of this protection

This stops accidental leaks and careless scripts. It is **not** a sandbox against a hostile script: a script that may run arbitrary shell commands (`cli_execute_command` with `bash` in `CLI_ALLOWED_COMMANDS`) can read the environment another way. Keep `CLI_ALLOWED_COMMANDS` tight.
