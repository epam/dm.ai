# FILE MCP Tools

**Total Tools**: 5

## Quick Reference

```bash
# List all file tools
dmtools list | jq '.tools[] | select(.name | startswith("file_"))'

# Example usage
dmtools file_read [arguments]
```

## Usage in JavaScript Agents

```javascript
// Direct function calls for file tools
const result = file_read(...);
const result = file_write(...);
const result = file_delete(...);
const result = file_validate_json(...);
```

## Available Tools

| Tool Name | Description | Parameters |
|-----------|-------------|------------|
| `file_delete` | Delete file or directory from working directory. Returns success message or null on failure. | `path` (string, **required**) |
| `file_read` | Read file content from working directory (supports input/ and outputs/ folders). Returns file content as string or null if file doesn't exist or is inaccessible. All file formats supported as UTF-8 text. | `path` (string, **required**) |
| `file_validate_json` | Validate JSON string and return detailed error information if invalid. Returns JSON string with validation result: {"valid": true} for valid JSON, or {"valid": false, "error": "error message", "line": line_number, "column": column_number, "position": character_position, "context": "context around error"} for invalid JSON. | `json` (string, **required**) |
| `file_validate_json_file` | Validate JSON file and return detailed error information if invalid. Reads file from working directory and validates its JSON content. Returns JSON string with validation result including file path. ⚠️ Requires active job context — not usable as a standalone CLI call. | `path` (string, **required**) |
| `file_write` | Write content to file in working directory. Creates parent directories automatically. Returns success message or null on failure. | `path` (string, **required**)<br>`content` (string, **required**) |

## Detailed Parameter Information

### `file_delete`

Delete file or directory from working directory. Returns success message or null on failure.

**Parameters:**

- **`path`** (string) 🔴 Required
  - File path relative to working directory or absolute path within working directory
  - Example: `temp/unused_file.txt`

**Example:**
```bash
dmtools file_delete "value"
```

```javascript
// In JavaScript agent
const result = file_delete("path");
```

---

### `file_read`

Read file content from working directory (supports input/ and outputs/ folders). Returns file content as string or null if file doesn't exist or is inaccessible. All file formats supported as UTF-8 text.

**Parameters:**

- **`path`** (string) 🔴 Required
  - File path relative to working directory or absolute path within working directory
  - Example: `outputs/response.md`

**Example:**
```bash
dmtools file_read "value"
```

```javascript
// In JavaScript agent
const result = file_read("path");
```

#### Allowlist for paths outside the working directory

By default, `file_read` blocks any path that resolves outside the working directory (path-traversal protection). In submodule/monorepo layouts where agents legitimately need to read files one level up (e.g. `../.dmtools/config.js`), you can whitelist specific glob patterns:

**Environment variable / `dmtools.env` field:**

```bash
# Single pattern
DMTOOLS_FILE_READ_ALLOWED_PATHS=../.dmtools/**

# Multiple patterns (comma-separated)
DMTOOLS_FILE_READ_ALLOWED_PATHS=../.dmtools/**,../config/**
```

**Behaviour:**

| Situation | Result |
|-----------|--------|
| No config set | Default: block anything outside working dir |
| Path matches a pattern | Allowed, even outside working dir |
| Path does NOT match any pattern | Still blocked |

**Pattern rules:**
- Patterns are resolved **relative to the working directory**, so `../.dmtools/**` expands to the `.dmtools/` sibling of the checkout root, regardless of where the JVM is started.
- `**` matches any number of path segments (e.g. `../.dmtools/**` allows all files recursively under `.dmtools/`).
- `*` matches within a single path segment only (e.g. `../config/*.json` allows only `.json` files directly inside `config/`).
- Patterns with no wildcard are treated as exact file paths.
- Only `file_read` respects this allowlist — `file_write` and `file_delete` always require the path to be inside the working directory.

**Security note:** Only exact whitelisted patterns bypass the check. A global `../**` is intentionally permitted if written explicitly, so keep patterns as narrow as possible.

**Typical use case:**

```
/home/runner/work/my-org/my-repo/        ← working dir (repo root)
  .dmtools/config.js                     ← config one level up from an agent submodule
  agents/                                ← submodule (scripts run here)
```

Set `DMTOOLS_FILE_READ_ALLOWED_PATHS=../.dmtools/**` when agents run from within `agents/` and need to read `../.dmtools/config.js`.

---

### `file_validate_json`

Validate JSON string and return detailed error information if invalid. Returns JSON string with validation result: {"valid": true} for valid JSON, or {"valid": false, "error": "error message", "line": line_number, "column": column_number, "position": character_position, "context": "context around error"} for invalid JSON.

**Parameters:**

- **`json`** (string) 🔴 Required
  - JSON string to validate
  - Example: `{"key": "value"}`

**Example:**
```bash
dmtools file_validate_json "value"

# To validate a file from shell, pass its content directly:
dmtools file_validate_json "$(cat outputs/file.json)"
```

```javascript
// In JavaScript agent
const result = file_validate_json("json");
```

---

### `file_validate_json_file`

Validate JSON file and return detailed error information if invalid. Reads file from working directory and validates its JSON content. Returns JSON string with validation result including file path.

> ⚠️ **CLI limitation**: This tool requires an active job working directory context. Calling it standalone via `dmtools file_validate_json_file` will hang. Use it only inside a running agent job (e.g., a JavaScript agent). For shell validation, use `file_validate_json` instead:
> ```bash
> dmtools file_validate_json "$(cat outputs/file.json)"
> ```

**Parameters:**

- **`path`** (string) 🔴 Required
  - File path relative to working directory or absolute path within working directory
  - Example: `outputs/response.json`

**Example:**
```javascript
// In JavaScript agent only (requires active job context)
const result = file_validate_json_file("path");
```

---

### `file_write`

Write content to file in working directory. Creates parent directories automatically. Returns success message or null on failure.

**Parameters:**

- **`path`** (string) 🔴 Required
  - File path relative to working directory or absolute path within working directory
  - Example: `inbox/raw/teams_messages/1729766400000-messages.json`

- **`content`** (string) 🔴 Required
  - Content to write to the file as UTF-8 string
  - Example: `{"messages": []}`

**Example:**
```bash
dmtools file_write "value" "value"
```

```javascript
// In JavaScript agent
const result = file_write("path", "content");
```

---

