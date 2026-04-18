---
name: agent-doc-sync
description: Automatically keep Confluence documentation in sync with DMtools agent configuration changes. Use this skill whenever agent config files are created or modified — including Teammate/Expert/TestCasesGenerator JSON configs, JavaScript pre/post actions, prompt templates, or instruction files. Also trigger when the user mentions updating agent docs, syncing documentation, or keeping a Confluence page up to date with agent changes.
---

# Agent Documentation Sync

Keep your Confluence agent reference page automatically up to date whenever agent configuration files change. This skill runs the `agent_doc_sync.json` JSRunner job, which reads `sm.json` to discover all registered agents, reads each config and its referenced prompt files, and publishes a fully-generated reference table to Confluence.

## Why this matters

DMtools agent repos accumulate configs, prompts, JS actions, and instructions over time. Without a documentation sync process, Confluence pages drift out of date. This skill eliminates that gap by running a single command that re-reads the source of truth (`sm.json`) and regenerates the full documentation page.

## How it works

The skill uses a JSRunner job (`agents/agent_doc_sync.json`) backed by `agents/js/agentDocSync.js`:

1. **Reads `sm.json`** — the authoritative list of agent rules (JQL triggers, configFile references)
2. **Deduplicates config references** — each agent is documented once even if referenced by multiple rules
3. **Reads each agent config** — extracts name, job type, AI role, prompt, post-actions, output type
4. **Reads each prompt file** — uses the first paragraph as the "Action" description
5. **Builds a full HTML page** — generates the complete Agent Reference table
6. **Publishes to Confluence** — updates an existing page or creates a new one

The generated page is fully managed by the job — do not edit it manually; run the job to refresh.

## Running the job

```bash
./dmtools.sh run agents/agent_doc_sync.json
```

Before running, fill in the `customParams` in `agents/agent_doc_sync.json`:

```json
{
  "name": "JSRunner",
  "params": {
    "jsPath": "agents/js/agentDocSync.js",
    "jobParams": {
      "customParams": {
        "smJsonPath": "agents/sm.json",
        "confluenceSpace": "AS",
        "confluencePageTitle": "AI Teammate Agents Reference",
        "confluencePageId": "15189573716",
        "confluenceParentId": "15175942544"
      }
    }
  }
}
```

### Parameters

| Parameter | Required | Description |
|-----------|----------|-------------|
| `smJsonPath` | No | Path to `sm.json` (default: `agents/sm.json`) |
| `confluenceSpace` | **Yes** | Confluence space key (e.g. `AS`) |
| `confluencePageTitle` | **Yes** | Page title — used for lookup and as the page's title |
| `confluencePageId` | No | Confluence page ID for direct lookup (faster, avoids title search) |
| `confluenceParentId` | No | Parent page ID — required only when creating a new page |

## Prerequisites

1. **DMtools is installed** — `dmtools.sh` available and `dmtools.env` configured
2. **Confluence credentials** in `dmtools.env`:
   ```
   CONFLUENCE_BASE_PATH=https://your-company.atlassian.net/wiki
   CONFLUENCE_EMAIL=your-email@company.com
   CONFLUENCE_API_TOKEN=your-token
   CONFLUENCE_AUTH_TYPE=Basic
   CONFLUENCE_DEFAULT_SPACE=YOUR_SPACE
   ```
3. **File read access** — `agent_doc_sync.json` is run from the repo root so that `agents/sm.json` and config files resolve correctly
4. `agents/agent_doc_sync.json` and `agents/js/agentDocSync.js` are present (from the `IstiN/dmtools-agents` submodule)

## Quick setup guide

To add this to your project:

1. Ensure the `agents` submodule is initialized:
   ```bash
   git submodule update --init --recursive
   ```
2. Copy and fill in the config file:
   ```bash
   cp agents/agent_doc_sync.json agents/my_agent_doc_sync.json
   # Edit my_agent_doc_sync.json — set confluenceSpace, confluencePageTitle, etc.
   ```
3. Run it:
   ```bash
   ./dmtools.sh run agents/my_agent_doc_sync.json
   ```

## When to run

Run `agent_doc_sync.json` whenever:

- A new agent JSON config is added to `agents/`
- An existing agent's prompt, JS action, or role changes
- `sm.json` gains a new rule or a rule's description/JQL changes

To automate this, add it as a step in your CI/CD pipeline or as a post-commit hook.

## Edge cases

- **0 agents discovered from sm.json** — the job fails closed and refuses to publish an empty page
- **Config file referenced in sm.json but missing on disk** — the agent is skipped with a warning
- **Page doesn't exist yet** — set `confluenceParentId` so the job can create it; without it the job reports an error
- **No Confluence credentials** — configure `dmtools.env` before running
