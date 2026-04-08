---
name: agent-doc-sync
description: Automatically keep Confluence documentation in sync with DMtools agent configuration changes. Use this skill whenever agent config files are created or modified — including Teammate/Expert/TestCasesGenerator JSON configs, JavaScript pre/post actions, prompt templates, instruction files, or CI/CD pipeline definitions. Also trigger when the user mentions updating agent docs, syncing documentation, or keeping a Confluence page up to date with agent changes. Even if the user doesn't explicitly say "documentation" — if they're changing agent configs and the project has a documentation page configured, remind them to sync it.
---

# Agent Documentation Sync

Keep your Confluence agent reference page automatically up to date whenever agent configuration files change. This skill detects which files in your project define agent behavior, reads them to understand the current agent setup, and updates a Confluence page with structured documentation — so your team always has an accurate, human-readable reference of what each agent does.

## Why this matters

DMtools agent repos accumulate configs, prompts, JS actions, and instructions over time. Without a documentation sync process, Confluence pages drift out of date — people add new agents or change prompts but forget to update the docs. This skill eliminates that gap by making documentation updates part of every code change, not a separate chore.

## How it works

When you create or modify agent-related files, this skill:

1. **Detects** which files changed and what kind of agent artifact they are
2. **Reads** the changed files plus their dependencies (e.g., a config references a prompt file)
3. **Fetches** the current Confluence page via DMtools
4. **Builds** an updated version of the relevant documentation sections
5. **Publishes** the update via DMtools with a descriptive history comment

The skill does not rewrite the entire page — it identifies which sections need updating and only modifies those, preserving everything else.

## Configuration

The skill needs to know two things: **what to watch** and **where to publish**. Both are configured in your project's `.github/copilot-instructions.md`, `CLAUDE.md`, or equivalent AI instructions file.

### Confluence target

Set these values to point at your documentation page. The skill reads Confluence credentials from `dmtools.env` automatically — you only configure the page identity here.

```markdown
## Agent Documentation Sync Configuration

<!-- Agent doc sync target -->
AGENT_DOC_PAGE_TITLE: "My Project — Agent Reference"
AGENT_DOC_SPACE: AS
AGENT_DOC_PAGE_ID: 15189573716
AGENT_DOC_PARENT_ID: 15175942544
```

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `AGENT_DOC_PAGE_TITLE` | Yes | — | Confluence page title (used for lookup and creation) |
| `AGENT_DOC_SPACE` | No | `CONFLUENCE_DEFAULT_SPACE` from dmtools.env | Confluence space key |
| `AGENT_DOC_PAGE_ID` | No | Auto-discovered from title | Page ID for faster lookup (avoids title search) |
| `AGENT_DOC_PARENT_ID` | No | — | Parent page ID (only needed if creating the page for the first time) |

### Watch paths

These are the directories and file patterns the skill monitors for changes. Customize them to match your project structure. The defaults below cover the standard DMtools Teammate pattern:

```markdown
<!-- Agent doc sync watch paths -->
Watch paths:
- agents/**/*.json          — Agent config files (Teammate, Expert, TestCasesGenerator)
- agents/**/*.js            — JavaScript pre/post actions
- agents/**/prompts/*.md    — Prompt templates (cliPrompt files)
- agents/**/instructions/**/*.md — Instruction files referenced in agent configs
- .github/workflows/*.yml   — CI/CD pipeline definitions (GitHub Actions)
- .gitlab/pipelines/*.yml   — CI/CD pipeline definitions (GitLab CI)
- .gitlab/ci/*.yml          — CI template definitions
```

Adapt these to your project. For example, if your project has product-specific overrides in a different folder:

```markdown
Watch paths:
- config/agents/*.json
- config/products/**/*.json
- scripts/agent-actions/*.js
- prompts/**/*.md
- .github/workflows/agent-*.yml
```

## What to do when watched files change

### Step 1: Identify what changed

Look at the files you just created or modified. Classify each one:

| File type | What it tells you | Documentation impact |
|-----------|-------------------|---------------------|
| **Agent JSON config** (`*.json` with `"name": "Teammate"` etc.) | A new or modified agent definition | Add/update the agent's row in the reference table |
| **JS action** (`preCliJSAction`, `postJSAction`) | How the agent preprocesses or postprocesses | Update the "Action" and "Output" columns |
| **Prompt template** (`cliPrompt` file) | What the AI agent is asked to do | Update the "Action" description |
| **Instruction file** (referenced in `instructions[]`) | Context and rules the agent follows | Update the "Context" column if it changes agent behavior |
| **CI/CD pipeline** (`.yml`) | How/when agents are triggered | Update routing/trigger info in the reference |
| **Product-specific override** | Per-project agent customization | Add/update the product row in the project table |

### Step 2: Fetch the current page

Use DMtools to get the current page content and version number:

```bash
dmtools confluence_content_by_title_and_space --data '{"space": "<AGENT_DOC_SPACE>", "title": "<AGENT_DOC_PAGE_TITLE>"}'
```

If `AGENT_DOC_PAGE_ID` is configured, you can fetch directly by ID for reliability. Note the `version.number` from the response — you need it for the update.

### Step 3: Build the updated content

Read the changed files and their related artifacts. For an agent JSON config, also read:
- The prompt file it references (`cliPrompt`)
- The JS actions it references (`preCliJSAction`, `postJSAction`)
- The instruction files in its `instructions[]` array

Then update the relevant sections of the Confluence page HTML body. The page should follow the structure described in the "Page structure" section below. Only modify sections affected by the changes — keep everything else intact.

### Step 4: Publish the update

```bash
dmtools confluence_update_page_with_history --data '{
  "contentId": "<AGENT_DOC_PAGE_ID>",
  "parentId": "<AGENT_DOC_PARENT_ID>",
  "space": "<AGENT_DOC_SPACE>",
  "title": "<AGENT_DOC_PAGE_TITLE>",
  "historyComment": "Updated agent docs: <brief description of what changed>",
  "body": "<updated HTML body>"
}'
```

The `historyComment` should be specific — not just "updated docs" but something like "Added new story_solution agent, updated PAYM pipeline routing."

## Page structure

The Confluence documentation page uses a two-part structure that separates shared behavior from project-specific customizations. This mirrors how DMtools agent repos typically work: shared configs in one place, per-product overrides in another.

### Part 1: Common Configuration

This section documents what all agents share:

- **Shared agents** — list each agent config with a summary table:

  | Agent Name | Context | Action | Output |
  |---|---|---|---|
  | `story_description` | Triggered Jira ticket + connected repos | Reads ticket, generates structured description | Updates Jira Description field |
  | `story_questions` | Jira ticket + existing subtasks | Analyzes requirements gaps, generates clarifying questions | Creates question subtasks in Jira |

  For each agent, the columns mean:
  - **Context**: What input the agent receives (ticket data, attachments, repo code)
  - **Action**: What the agent does (summarized from the prompt + instructions)
  - **Output**: What is created or modified in Jira/Confluence

- **Shared JavaScript actions** — brief descriptions of each JS file and when it runs
- **Shared instructions** — list instruction files and what rules they enforce
- **Output conventions** — file naming, format requirements

### Part 2: Project-Specific Configuration

A table with one row per project/product, showing how each customizes the shared agents:

| Project Key | Pipeline File | Agent Config | Confluence Space | AI Role / Domain | Repositories |
|---|---|---|---|---|---|
| PAYM | `.gitlab/pipelines/paym.yml` | `ai_teammate/paym/` | AS | Payments domain expert | `repositories.json` → 2 repos |
| PDCT | `.gitlab/pipelines/pdct.yml` | `ai_teammate/pdct/` | AS | Product catalog specialist | `repositories.json` → 3 repos |

This table should not repeat information from the Common section — only note what differs per project (overridden `knownInfo`, `aiRole`, additional instructions, specific repos).

### Creating the page from scratch

If the page doesn't exist yet, create it with the full structure above, populated from all agent configs in the project. Use:

```bash
dmtools confluence_create_page --data '{
  "parentId": "<AGENT_DOC_PARENT_ID>",
  "space": "<AGENT_DOC_SPACE>",
  "title": "<AGENT_DOC_PAGE_TITLE>",
  "body": "<full HTML body>"
}'
```

## Quick setup guide

To add this skill to your project:

1. **Ensure DMtools is configured** — you need `dmtools.env` with Confluence credentials:
   ```
   CONFLUENCE_BASE_PATH=https://your-company.atlassian.net/wiki
   CONFLUENCE_EMAIL=your-email@company.com
   CONFLUENCE_API_TOKEN=your-token
   CONFLUENCE_AUTH_TYPE=Basic
   CONFLUENCE_DEFAULT_SPACE=YOUR_SPACE
   ```

2. **Add configuration** to your `.github/copilot-instructions.md` or `CLAUDE.md`:
   ```markdown
   ## Agent Documentation Sync

   When any agent configuration file is created or modified, update the Confluence
   reference page using the agent-doc-sync skill.

   AGENT_DOC_PAGE_TITLE: "Your Project — Agent Reference"
   AGENT_DOC_SPACE: YOUR_SPACE
   AGENT_DOC_PAGE_ID: your-page-id
   AGENT_DOC_PARENT_ID: your-parent-id

   Watch paths:
   - agents/**/*.json
   - agents/**/*.js
   - agents/**/prompts/*.md
   - agents/**/instructions/**/*.md
   - .github/workflows/*.yml
   ```

3. **Create the initial page** — the first time you run an agent change with this skill active, it will offer to create the Confluence page from scratch by scanning all existing agent configs.

## Edge cases

- **Page doesn't exist yet**: Create it from scratch using all current agent configs. Ask the user to confirm before creating.
- **Config references a file that doesn't exist**: Note it in the documentation as "referenced but not found" — don't fail silently.
- **Multiple agents share the same JS action**: Document the JS action once in Common Configuration, reference it from each agent row.
- **No Confluence credentials**: Warn the user that dmtools.env needs Confluence integration configured, and point them to the DMtools setup guide.
- **Page was manually edited**: The skill preserves manual edits in sections it doesn't own. Only update sections that correspond to changed files.
