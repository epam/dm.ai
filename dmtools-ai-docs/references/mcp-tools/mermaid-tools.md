# MERMAID MCP Tools

**Total Tools**: 3

## Quick Reference

```bash
# List all mermaid tools
dmtools list | jq '.tools[] | select(.name | startswith("mermaid_"))'

# Example usage
dmtools mermaid_index_generate [arguments]
```

## Usage in JavaScript Agents

```javascript
// Direct function calls for mermaid tools
const result = mermaid_index_generate(...);
const result = mermaid_index_read_list(...);
const result = mermaid_index_read(...);
```

## Available Tools

| Tool Name | Description | Parameters |
|-----------|-------------|------------|
| `mermaid_index_generate` | Generate Mermaid diagrams from content sources (Confluence, Jira, Jira Xray, or TestRail) based on include/exclude patterns. Processes content recursively and stores diagrams in hierarchical file structure. | `integration` (string, **required**)<br>`include_patterns` (array, **required**)<br>`exclude_patterns` (array, optional)<br>`storage_path` (string, **required**)<br>`custom_fields` (array, optional)<br>`include_comments` (boolean, optional) |
| `mermaid_index_read` | Read all Mermaid diagram files (.mmd) from storage path recursively. Returns list of diagrams with their paths and content. | `integration` (string, **required**)<br>`storage_path` (string, **required**) |
| `mermaid_index_read_list` | Read all Mermaid diagram files (.mmd) from storage path recursively. Returns list of ToText objects with paths and content. | `integration` (string, **required**)<br>`storage_path` (string, **required**) |

## Detailed Parameter Information

### `mermaid_index_generate`

Generate Mermaid diagrams from content sources (Confluence, Jira, Jira Xray, or TestRail) based on include/exclude patterns. Processes content recursively and stores diagrams in hierarchical file structure.

**Parameters:**

- **`integration`** (string) 🔴 Required
  - Integration type: 'confluence', 'jira', 'jira_xray', or 'testrail'
  - Example: `confluence`

- **`include_patterns`** (array) 🔴 Required
  - Array of include patterns. For Confluence: `["SPACE/pages/PAGE_ID/PAGE_NAME/**"]`. For Jira: `["JQL query"]`. For TestRail: `["project_id=5&suite_id=3"]`
  - Example: `["AINA/pages/11665522/Templates/**"]`

- **`exclude_patterns`** (array) ⚪ Optional
  - Optional array of exclude patterns to filter out specific content (not used for Jira or TestRail)
  - Example: `[]`

- **`storage_path`** (string) 🔴 Required
  - Base path for storing generated diagrams
  - Example: `./mermaid-diagrams`

- **`custom_fields`** (array) ⚪ Optional
  - Optional array of custom field names to include in content (only for Jira integrations)
  - Example: `["summary", "description", "customfield_10001"]`

- **`include_comments`** (boolean) ⚪ Optional
  - Whether to include comments in content (only for Jira integrations, default: false)
  - Example: `false`

**Examples:**

Confluence:
```bash
dmtools mermaid_index_generate confluence '["SPACE/pages/123/MyPage/**"]' '[]' ./mermaid-diagrams
```

Jira:
```bash
dmtools mermaid_index_generate jira '["project = PROJ AND type = Story"]' '[]' ./mermaid-diagrams
```

TestRail:
```bash
dmtools mermaid_index_generate testrail '["project_id=5&suite_id=3"]' '[]' ./mermaid-diagrams
```

```javascript
// In JavaScript agent
const result = mermaid_index_generate("confluence", ["SPACE/pages/123/MyPage/**"], [], "./mermaid-diagrams");
```

---

### `mermaid_index_read`

Read all Mermaid diagram files (.mmd) from storage path recursively. Returns list of diagrams with their paths and content.

**Parameters:**

- **`integration`** (string) 🔴 Required
  - Integration type used as subfolder name under `storage_path`. Supported: 'confluence', 'jira', 'jira_xray', 'testrail', or any integration subfolder you created.
  - Example: `confluence`

- **`storage_path`** (string) 🔴 Required
  - Base path where diagrams are stored
  - Example: `./mermaid-diagrams`

**Example:**
```bash
dmtools mermaid_index_read confluence ./mermaid-diagrams
```

```javascript
// In JavaScript agent
const result = mermaid_index_read("confluence", "./mermaid-diagrams");
```

---

### `mermaid_index_read_list`

Read all Mermaid diagram files (.mmd) from storage path recursively. Returns list of ToText objects with paths and content.

**Parameters:**

- **`integration`** (string) 🔴 Required
  - Integration type used as subfolder name under `storage_path`. Supported: 'confluence', 'jira', 'jira_xray', 'testrail', or any integration subfolder you created.
  - Example: `confluence`

- **`storage_path`** (string) 🔴 Required
  - Base path where diagrams are stored
  - Example: `./mermaid-diagrams`

**Example:**
```bash
dmtools mermaid_index_read_list confluence ./mermaid-diagrams
```

```javascript
// In JavaScript agent
const result = mermaid_index_read_list("confluence", "./mermaid-diagrams");
```

---

