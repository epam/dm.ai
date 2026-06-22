# MERMAID MCP Tools

**Total Tools**: 5

## Quick Reference

```bash
# List all mermaid tools
dmtools list | jq '.tools[] | select(.name | startswith("mermaid_"))'

# Render Mermaid text to SVG file
dmtools mermaid_to_svg "flowchart TD; A[Start] --> B[Done]" --output diagram.svg

# Render Mermaid text to PNG file
dmtools mermaid_to_png "flowchart TD; A[Start] --> B[Done]" --output diagram.png

# Generate diagrams from Confluence / Jira content
dmtools mermaid_index_generate [arguments]
```

## Usage in JavaScript Agents

```javascript
// Render a diagram to SVG or PNG
const svgPath = mermaid_to_svg("flowchart TD; A --> B", "--output diagram.svg");
const pngPath = mermaid_to_png("flowchart TD; A --> B", "--output diagram.png");

// Index-based helpers
const result = mermaid_index_generate(...);
const result = mermaid_index_read_list(...);
const result = mermaid_index_read(...);
```

## Available Tools

| Tool Name | Description | Parameters |
|-----------|-------------|------------|
| `mermaid_to_svg` | Render a Mermaid diagram definition to an SVG file using the headless GraalJS renderer (no browser required). Supports all major diagram types: flowchart, sequence, class, state, ER, user-journey, gantt, pie, git, mindmap, kanban, quadrant, requirement, timeline, venn, block, architecture, C4, sankey, radar, xy, treemap, treeview, ishikawa, packet, wardley. | `diagram_text` (string, **required**)<br>`--output` (string, **required**) |
| `mermaid_to_png` | Render a Mermaid diagram definition to a PNG image file using the headless GraalJS + Apache Batik renderer (no browser required). Same diagram-type coverage as `mermaid_to_svg`. | `diagram_text` (string, **required**)<br>`--output` (string, **required**) |
| `mermaid_index_generate` | Generate Mermaid diagrams from content sources (Confluence or Jira) based on include/exclude patterns. Processes content recursively and stores diagrams in hierarchical file structure. | `integration` (string, **required**)<br>`include_patterns` (array, **required**)<br>`exclude_patterns` (array, optional)<br>`storage_path` (string, **required**)<br>`custom_fields` (array, optional)<br>`include_comments` (boolean, optional) |
| `mermaid_index_read` | Read all Mermaid diagram files (.mmd) from storage path recursively. Returns list of diagrams with their paths and content. | `integration` (string, **required**)<br>`storage_path` (string, **required**) |
| `mermaid_index_read_list` | Read all Mermaid diagram files (.mmd) from storage path recursively. Returns list of ToText objects with paths and content. | `integration` (string, **required**)<br>`storage_path` (string, **required**) |

## Detailed Parameter Information

### `mermaid_to_svg`

Render a Mermaid diagram definition to an SVG vector-graphics file. Uses the embedded GraalJS Mermaid engine — no Playwright, no browser, no network required. Output is a self-contained SVG suitable for embedding in documents, web pages, or CI artefacts.

**Parameters:**

- **`diagram_text`** (string) 🔴 Required
  - Full Mermaid diagram source text (any supported diagram type)
  - Example: `flowchart TD; A[Start] --> B[Done]`

- **`--output`** (string) 🔴 Required
  - Destination file path for the SVG output
  - Example: `./output/diagram.svg`

**Supported diagram types:** flowchart, sequenceDiagram, classDiagram, stateDiagram, erDiagram, journey, gantt, pie, gitGraph, mindmap, kanban, quadrantChart, requirementDiagram, timeline, venn, block, architecture, C4Context, sankey, radar, xychart, treemap, treeview, ishikawa, packet, wardley

**Example:**
```bash
dmtools mermaid_to_svg "flowchart TD; A[Start] --> B{Decision} --> C[End]" --output out.svg
```

```javascript
const svgFile = mermaid_to_svg("sequenceDiagram\n  Alice->>Bob: Hello\n  Bob-->>Alice: Hi", "--output seq.svg");
```

---

### `mermaid_to_png`

Render a Mermaid diagram definition to a PNG raster image. Uses the embedded GraalJS Mermaid engine for SVG generation and Apache Batik for SVG→PNG rasterisation — no Playwright, no browser required. Output is a PNG file at screen resolution (~96 DPI).

**Parameters:**

- **`diagram_text`** (string) 🔴 Required
  - Full Mermaid diagram source text (any supported diagram type)
  - Example: `flowchart TD; A[Start] --> B[Done]`

- **`--output`** (string) 🔴 Required
  - Destination file path for the PNG output
  - Example: `./output/diagram.png`

**Example:**
```bash
dmtools mermaid_to_png "classDiagram\n  class Animal { +String name\n  +speak() }" --output class.png
```

```javascript
const pngFile = mermaid_to_png("pie title Pet adoption\n  \"Dogs\":45\n  \"Cats\":30", "--output pie.png");
```

---

### `mermaid_index_generate`

Generate Mermaid diagrams from content sources (Confluence or Jira) based on include/exclude patterns. Processes content recursively and stores diagrams in hierarchical file structure.

**Parameters:**

- **`integration`** (string) 🔴 Required
  - Integration type: 'confluence', 'jira', or 'jira_xray'
  - Example: `confluence`

- **`include_patterns`** (array) 🔴 Required
  - Array of include patterns. For Confluence: ["SPACE/pages/PAGE_ID/PAGE_NAME/**"]. For Jira: ["JQL query"]
  - Example: `["YOUR_SPACE/pages/PAGE_ID/Templates/**"]`

- **`exclude_patterns`** (array) ⚪ Optional
  - Optional array of exclude patterns to filter out specific content (not used for Jira)
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

**Example:**
```bash
dmtools mermaid_index_generate "confluence" '["MYSPACE/pages/123/Templates/**"]' --storage_path ./diagrams
```

```javascript
const result = mermaid_index_generate("integration", "include_patterns");
```

---

### `mermaid_index_read`

Read all Mermaid diagram files (.mmd) from storage path recursively. Returns list of diagrams with their paths and content.

**Parameters:**

- **`integration`** (string) 🔴 Required
  - Integration type (currently only 'confluence' is supported)
  - Example: `confluence`

- **`storage_path`** (string) 🔴 Required
  - Base path where diagrams are stored
  - Example: `./mermaid-diagrams`

**Example:**
```bash
dmtools mermaid_index_read "confluence" "./mermaid-diagrams"
```

---

### `mermaid_index_read_list`

Read all Mermaid diagram files (.mmd) from storage path recursively. Returns list of ToText objects with paths and content.

**Parameters:**

- **`integration`** (string) 🔴 Required
  - Integration type (currently only 'confluence' is supported)
  - Example: `confluence`

- **`storage_path`** (string) 🔴 Required
  - Base path where diagrams are stored
  - Example: `./mermaid-diagrams`

**Example:**
```bash
dmtools mermaid_index_read_list "confluence" "./mermaid-diagrams"
```

---


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
| `mermaid_index_generate` | Generate Mermaid diagrams from content sources (Confluence or Jira) based on include/exclude patterns. Processes content recursively and stores diagrams in hierarchical file structure. | `integration` (string, **required**)<br>`include_patterns` (array, **required**)<br>`exclude_patterns` (array, optional)<br>`storage_path` (string, **required**)<br>`custom_fields` (array, optional)<br>`include_comments` (boolean, optional) |
| `mermaid_index_read` | Read all Mermaid diagram files (.mmd) from storage path recursively. Returns list of diagrams with their paths and content. | `integration` (string, **required**)<br>`storage_path` (string, **required**) |
| `mermaid_index_read_list` | Read all Mermaid diagram files (.mmd) from storage path recursively. Returns list of ToText objects with paths and content. | `integration` (string, **required**)<br>`storage_path` (string, **required**) |

## Detailed Parameter Information

### `mermaid_index_generate`

Generate Mermaid diagrams from content sources (Confluence or Jira) based on include/exclude patterns. Processes content recursively and stores diagrams in hierarchical file structure.

**Parameters:**

- **`integration`** (string) 🔴 Required
  - Integration type: 'confluence', 'jira', or 'jira_xray'
  - Example: `confluence`

- **`include_patterns`** (array) 🔴 Required
  - Array of include patterns. For Confluence: ["SPACE/pages/PAGE_ID/PAGE_NAME/**"]. For Jira: ["JQL query"]
  - Example: `["AINA/pages/11665522/Templates/**"]`

- **`exclude_patterns`** (array) ⚪ Optional
  - Optional array of exclude patterns to filter out specific content (not used for Jira)
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

**Example:**
```bash
dmtools mermaid_index_generate "value" "value"
```

```javascript
// In JavaScript agent
const result = mermaid_index_generate("integration", "include_patterns");
```

---

### `mermaid_index_read`

Read all Mermaid diagram files (.mmd) from storage path recursively. Returns list of diagrams with their paths and content.

**Parameters:**

- **`integration`** (string) 🔴 Required
  - Integration type (currently only 'confluence' is supported)
  - Example: `confluence`

- **`storage_path`** (string) 🔴 Required
  - Base path where diagrams are stored
  - Example: `./mermaid-diagrams`

**Example:**
```bash
dmtools mermaid_index_read "value" "value"
```

```javascript
// In JavaScript agent
const result = mermaid_index_read("integration", "storage_path");
```

---

### `mermaid_index_read_list`

Read all Mermaid diagram files (.mmd) from storage path recursively. Returns list of ToText objects with paths and content.

**Parameters:**

- **`integration`** (string) 🔴 Required
  - Integration type (currently only 'confluence' is supported)
  - Example: `confluence`

- **`storage_path`** (string) 🔴 Required
  - Base path where diagrams are stored
  - Example: `./mermaid-diagrams`

**Example:**
```bash
dmtools mermaid_index_read_list "value" "value"
```

```javascript
// In JavaScript agent
const result = mermaid_index_read_list("integration", "storage_path");
```

---

