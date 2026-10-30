# Atlassian Rovo MCP Configuration Guide

## 🎯 Overview

DMtools provides 4 MCP tools for **Atlassian Rovo** via Atlassian's official remote MCP server (`https://mcp.atlassian.com/v2/mcp`). It enables:
- Semantic natural-language search across Jira, Confluence, and connected third-party sources (`rovo_search`)
- Pre-generated Confluence page AI summaries without downloading full page bodies (`rovo_summarize`)
- Atlassian Teamwork Graph relationship lookups across issues, pages, PRs, repositories, goals, and people (`rovo_context`)
- Natural-language Q&A via Rovo `AGENTIC` search mode with automatic fallback to `STANDARD` search when Agentic search is not enabled (`rovo_ask`)

## 🔑 Authentication Setup

Atlassian Rovo MCP supports headless authentication suitable for CLI workflows and CI/CD pipelines:

1. **Personal API Token (`Basic` auth, default)**:
   - Create an API token at [Atlassian Account Settings](https://id.atlassian.com/manage-profile/security/api-tokens).
   - Provide either `ROVO_EMAIL` + `ROVO_API_TOKEN` or a pre-encoded `ROVO_LOGIN_PASS_TOKEN` (`base64(email:api_token)`).
2. **Service-Account / Scoped Key (`Bearer` auth)**:
   - Set `ROVO_AUTH_TYPE=Bearer` and provide the key in `ROVO_LOGIN_PASS_TOKEN` (or `ROVO_API_TOKEN`).

> **Note:** Your Atlassian organization administrator must enable API-token authentication for the Atlassian Rovo MCP server.

### Configure `dmtools.env`

```bash
# Atlassian Rovo MCP Configuration
ROVO_MCP_URL=https://mcp.atlassian.com/v2/mcp
ROVO_LOGIN_PASS_TOKEN=eW91ci5lbWFpbEBjb21wYW55LmNvbTpBVEFUVDN4RmZHRjA...
ROVO_AUTH_TYPE=Basic
ROVO_CLOUD_ID=a1b2c3d4-e5f6-7890-abcd-ef1234567890
```

Or using separate email and API token:

```bash
ROVO_EMAIL=your.email@company.com
ROVO_API_TOKEN=ATATT3xFfGF0...
ROVO_AUTH_TYPE=Basic
ROVO_CLOUD_ID=a1b2c3d4-e5f6-7890-abcd-ef1234567890
```

## 🔧 Configuration Variables

### Required Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `ROVO_LOGIN_PASS_TOKEN` | Base64-encoded `email:api_token` (for `Basic`) or bearer token (for `Bearer`). Alternatively set `ROVO_EMAIL` + `ROVO_API_TOKEN`. | `base64(email:token)` |

### Optional Variables

| Variable | Description | Example | Default |
|----------|-------------|---------|---------|
| `ROVO_MCP_URL` | Remote Atlassian Rovo MCP endpoint URL | `https://mcp.atlassian.com/v2/mcp` | `https://mcp.atlassian.com/v2/mcp` |
| `ROVO_AUTH_TYPE` | Authentication scheme (`Basic` or `Bearer`) | `Basic` | `Basic` |
| `ROVO_CLOUD_ID` | Default Atlassian Cloud ID when `cloudId` is omitted from tool calls | `a1b2c3d4-e5f6-7890-abcd-ef1234567890` | None |
| `ROVO_EMAIL` | Atlassian account email (used with `ROVO_API_TOKEN` for `Basic` auth) | `user@company.com` | None |
| `ROVO_API_TOKEN` | Atlassian API token | `ATATT3xFfGF0...` | None |

## 📋 Available Rovo MCP Tools

See [../../mcp-tools/rovo-tools.md](../../mcp-tools/rovo-tools.md) for full parameter details.

| Tool | Description |
|------|-------------|
| `rovo_search` | Semantic natural-language search across Jira, Confluence, and third-party sources (`STANDARD` mode) |
| `rovo_summarize` | Pre-generated AI summary of a Confluence page (`getConfluenceContent` with `detail=ai_summary`) |
| `rovo_context` | Teamwork Graph context (linked issues, pages, PRs, repos, goals, people) via `getGraphContext` |
| `rovo_ask` | Ask a question via `AGENTIC` search mode with automatic fallback to `STANDARD` search on HTTP 403 |

### Quick Examples

```bash
# Semantic search across Confluence and Jira
dmtools rovo_search "deployment architecture for payment service"

# Summarize a Confluence page without downloading its full body
dmtools rovo_summarize "123456789"

# Inspect graph relationships for a Jira issue
dmtools rovo_context "jira_issue" "PROJ-123"

# Ask Rovo a natural-language question
dmtools rovo_ask "How does the authentication token refresh flow work?"
```

### JavaScript Agent Access

```javascript
function action(params) {
    const searchHits = rovo_search("payment service architecture", null, "confluence", 5);
    const summary = rovo_summarize("123456789");
    const graph = rovo_context("jira_issue", params.ticketKey);
    const answer = rovo_ask("What are the rollout prerequisites for payment service?");
    return { searchHits, summary, graph, answer };
}
```
