# ROVO MCP Tools

**Total Tools**: 4

## Quick Reference

```bash
# List all rovo tools
dmtools list | jq '.tools[] | select(.name | startswith("rovo_"))'

# Example usage
dmtools rovo_search "deployment architecture for payment service"
```

## Usage in JavaScript Agents

```javascript
// Direct function calls for rovo tools
const result = rovo_ask(...);
const result = rovo_context(...);
const result = rovo_search(...);
```

## Available Tools

| Tool Name | Description | Parameters |
|-----------|-------------|------------|
| `rovo_ask` | Ask a natural-language question via Atlassian Rovo MCP using AGENTIC search mode; automatically falls back to STANDARD semantic search when Agentic search returns 403 or is not enabled. | `question` (string, **required**)<br>`cloudId` (string, optional) |
| `rovo_context` | Retrieve Atlassian Teamwork Graph relationships (linked Jira issues, Confluence pages, pull requests, repositories, goals, and people) for an object via getGraphContext. | `objectType` (string, **required**)<br>`identifier` (string, **required**)<br>`cloudId` (string, optional) |
| `rovo_search` | Semantic natural-language search across Jira, Confluence, and connected third-party sources via Atlassian Rovo MCP in STANDARD mode. | `query` (string, **required**)<br>`cloudId` (string, optional)<br>`targetApp` (string, optional)<br>`limit` (number, optional) |
| `rovo_summarize` | Fetch a pre-generated AI summary of a Confluence page via Atlassian Rovo MCP (getConfluenceContent with detail=ai_summary). | `contentId` (string, **required**)<br>`cloudId` (string, optional) |

## Detailed Parameter Information

### `rovo_ask`

Ask a natural-language question via Atlassian Rovo MCP using AGENTIC search mode; automatically falls back to STANDARD semantic search when Agentic search returns 403 or is not enabled.

**Parameters:**

- **`question`** (string) 🔴 Required
  - Natural-language question to ask Rovo
  - Example: `How does the authentication token refresh flow work?`

- **`cloudId`** (string) ⚪ Optional
  - Atlassian Cloud ID (optional, defaults to ROVO_CLOUD_ID)
  - Example: `a1b2c3d4-e5f6-7890-abcd-ef1234567890`

**Example:**
```bash
dmtools rovo_ask "value" "value"
```

```javascript
// In JavaScript agent
const result = rovo_ask("question", "cloudId");
```

---

### `rovo_context`

Retrieve Atlassian Teamwork Graph relationships (linked Jira issues, Confluence pages, pull requests, repositories, goals, and people) for an object via getGraphContext.

**Parameters:**

- **`objectType`** (string) 🔴 Required
  - Type of the Atlassian object (e.g. jira_issue, confluence_page, pull_request, team, goal)
  - Example: `jira_issue`

- **`identifier`** (string) 🔴 Required
  - Identifier or ARI/key of the object (e.g. PROJ-123 or page ID)
  - Example: `PROJ-123`

- **`cloudId`** (string) ⚪ Optional
  - Atlassian Cloud ID (optional, defaults to ROVO_CLOUD_ID)
  - Example: `a1b2c3d4-e5f6-7890-abcd-ef1234567890`

**Example:**
```bash
dmtools rovo_context "value" "value"
```

```javascript
// In JavaScript agent
const result = rovo_context("objectType", "identifier");
```

---

### `rovo_search`

Semantic natural-language search across Jira, Confluence, and connected third-party sources via Atlassian Rovo MCP in STANDARD mode.

**Parameters:**

- **`query`** (string) 🔴 Required
  - Natural-language search query
  - Example: `deployment architecture for payment service`

- **`cloudId`** (string) ⚪ Optional
  - Atlassian Cloud ID (optional, defaults to ROVO_CLOUD_ID)
  - Example: `a1b2c3d4-e5f6-7890-abcd-ef1234567890`

- **`targetApp`** (string) ⚪ Optional
  - Optional target application filter (e.g. confluence, jira)
  - Example: `confluence`

- **`limit`** (number) ⚪ Optional
  - Maximum number of results to return
  - Example: `10`

**Example:**
```bash
dmtools rovo_search "value" "value"
```

```javascript
// In JavaScript agent
const result = rovo_search("query", "cloudId");
```

---

### `rovo_summarize`

Fetch a pre-generated AI summary of a Confluence page via Atlassian Rovo MCP (getConfluenceContent with detail=ai_summary).

**Parameters:**

- **`contentId`** (string) 🔴 Required
  - Confluence page or content ID
  - Example: `123456789`

- **`cloudId`** (string) ⚪ Optional
  - Atlassian Cloud ID (optional, defaults to ROVO_CLOUD_ID)
  - Example: `a1b2c3d4-e5f6-7890-abcd-ef1234567890`

**Example:**
```bash
dmtools rovo_summarize "value" "value"
```

```javascript
// In JavaScript agent
const result = rovo_summarize("contentId", "cloudId");
```

---
