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
| `file_validate_json_file` | Validate JSON file and return detailed error information if invalid. Reads file from working directory and validates its JSON content. Returns JSON string with validation result including file path. | `path` (string, **required**) |
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
```

```javascript
// In JavaScript agent
const result = file_validate_json("json");
```

---

### `file_validate_json_file`

Validate JSON file and return detailed error information if invalid. Reads file from working directory and validates its JSON content. Returns JSON string with validation result including file path.

**Parameters:**

- **`path`** (string) 🔴 Required
  - File path relative to working directory or absolute path within working directory
  - Example: `outputs/response.json`

**Example:**
```bash
dmtools file_validate_json_file "value"
```

```javascript
// In JavaScript agent
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

