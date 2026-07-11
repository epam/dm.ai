# DMTools

Enterprise dark-factory orchestrator for automating delivery workflows across trackers, source control, documentation, design systems, AI providers, and CI/CD.

> Quick install
>
> ```bash
> curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install.sh | bash
> ```
>
> PowerShell: https://github.com/epam/dm.ai/releases/latest/download/install.ps1

[![Latest Release](https://img.shields.io/github/v/release/epam/dm.ai?label=latest%20version)](https://github.com/epam/dm.ai/releases/latest) [![codecov](https://codecov.io/gh/epam/dm.ai/branch/main/graph/badge.svg)](https://codecov.io/gh/epam/dm.ai) [![](https://jitpack.io/v/epam/dm.ai.svg)](https://jitpack.io/#epam/dm.ai)

> DMTools is orchestration layer for enterprise dark factories. It is designed for self-hosted and enterprise environments where teams need repeatable AI-assisted workflows instead of one-off scripts or server-first demos.

## What DMTools is for

DMTools helps platform, delivery, QA, and engineering teams orchestrate end-to-end work such as:

- running MCP tools against Jira, Azure DevOps, GitHub, Confluence, Figma, Teams, SharePoint, and AI providers
- executing reusable jobs and agents for analysis, test generation, reporting, and teammate workflows
- wiring GitHub Actions and other CI/CD pipelines into delivery automation
- packaging focused AI assistant skills for project-level usage in Cursor, Claude, and Codex

## Primary usage paths

| Usage path | What you do with it | Start here |
|---|---|---|
| CLI + MCP tools | Execute direct tool calls and integration operations from the terminal | [MCP tools reference](dmtools-ai-docs/references/mcp-tools/README.md) |
| Jobs + agents | Run orchestrated workflows such as Teammate, reporting, and test generation | [Jobs reference](dmtools-ai-docs/references/jobs/README.md) |
| GitHub workflow automation | Run DMTools in CI/CD for ticket processing and teammate flows | [GitHub Actions teammate workflow](dmtools-ai-docs/references/workflows/github-actions-teammate.md) |
| AI assistant skills | Install project-level DMTools skills for agent-driven usage | [Skill guide](dmtools-ai-docs/SKILL.md) |

## Documentation map

All maintained documentation lives under [dmtools-ai-docs/](dmtools-ai-docs/).

| Topic | Link |
|---|---|
| Installation and upgrade | [references/installation/README.md](dmtools-ai-docs/references/installation/README.md) |
| Configuration overview | [references/configuration/README.md](dmtools-ai-docs/references/configuration/README.md) |
| Integration setup guides | [references/configuration/integrations/](dmtools-ai-docs/references/configuration/integrations/) |
| MCP tools reference | [references/mcp-tools/README.md](dmtools-ai-docs/references/mcp-tools/README.md) |
| Jobs and workflow orchestration | [references/jobs/README.md](dmtools-ai-docs/references/jobs/README.md) |
| GitHub workflow guidance | [references/workflows/github-actions-teammate.md](dmtools-ai-docs/references/workflows/github-actions-teammate.md) |
| GitHub repository discoverability | [references/workflows/github-repository-discoverability-playbook.md](dmtools-ai-docs/references/workflows/github-repository-discoverability-playbook.md) |
| Examples | [references/examples/](dmtools-ai-docs/references/examples/) |
| Documentation index | [dmtools-ai-docs/README.md](dmtools-ai-docs/README.md) |

## Quick start

### Installation

**Latest Version:** [![Latest Release](https://img.shields.io/github/v/release/epam/dm.ai?label=)](https://github.com/epam/dm.ai/releases/latest) — browse the [latest releases page](https://github.com/epam/dm.ai/releases/latest) for installer assets.

**macOS / Linux / Git Bash:**

```bash
curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install.sh | bash
```

**Windows PowerShell:**

```powershell
irm https://github.com/epam/dm.ai/releases/latest/download/install.ps1 | iex
```

**Java baseline:** Java 17+ for the DMTools CLI and Agent Skill installer flow.

### Agent Skill

Run the Agent Skill installer from your project root so it can detect project-level skill directories:

```bash
curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/skill-install.sh | bash
curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/skill-install.sh | bash -s -- --skills jira,github
```

For shared base configs and child-agent overrides, use the existing [`parent` config inheritance pattern](dmtools-ai-docs/references/configuration/json-config-rules.md#config-inheritance-via-parent).

**Verify installation:**

```bash
dmtools --version
dmtools list
```

### Minimal configuration

Create `dmtools.env` in your project root (or export the same variables in your shell). You only need the variables for the integrations you actually use — there is no single required set, and the AI provider key is optional (required only for AI-powered jobs and agents).

**Jira** — [full guide](dmtools-ai-docs/references/configuration/integrations/jira.md)

```bash
JIRA_BASE_PATH=https://your-company.atlassian.net
JIRA_EMAIL=your-email@company.com
JIRA_API_TOKEN=your-jira-api-token
```

**Azure DevOps** — [full guide](dmtools-ai-docs/references/configuration/integrations/ado.md)

```bash
ADO_BASE_PATH=https://dev.azure.com/your-organization
ADO_PAT=your-personal-access-token
ADO_PROJECT=YourProject
```

**GitHub** — [full guide](dmtools-ai-docs/references/configuration/integrations/github.md)

```bash
SOURCE_GITHUB_TOKEN=ghp_your_token
```

**Confluence**

```bash
CONFLUENCE_BASE_PATH=https://your-company.atlassian.net/wiki
CONFLUENCE_EMAIL=your-email@company.com
CONFLUENCE_API_TOKEN=your-confluence-api-token
```

**TestRail** — [full guide](dmtools-ai-docs/references/configuration/integrations/testrail.md)

```bash
TESTRAIL_BASE_PATH=https://your-company.testrail.io
TESTRAIL_USERNAME=your-email@company.com
TESTRAIL_API_KEY=your-testrail-api-key
```

**Figma**

```bash
FIGMA_TOKEN=your-figma-personal-access-token
```

**AI provider** (optional — only needed for AI-powered jobs and agents). Configure one:

```bash
# Gemini (free tier available)
GEMINI_API_KEY=your-gemini-api-key
# OpenAI
# OPEN_AI_API_KEY=sk-...
# Enterprise gateway / AWS Bedrock / local models
# DIAL_API_KEY=...   # or AWS_ACCESS_KEY_ID + AWS_SECRET_ACCESS_KEY, or OLLAMA_BASE_URL
```

Example combined `dmtools.env` (Jira + Gemini):

```bash
cat > dmtools.env << EOF
JIRA_BASE_PATH=https://your-company.atlassian.net
JIRA_EMAIL=your-email@company.com
JIRA_API_TOKEN=your-jira-api-token
GEMINI_API_KEY=your-gemini-api-key
EOF
chmod 600 dmtools.env
```

For the full variable list, auth setup (token generation, PAT scopes, base64 credentials), and per-integration options, use the maintained docs instead of copying old snippets from the repository root:

- [Configuration overview](dmtools-ai-docs/references/configuration/README.md)
- [Integration setup guides](dmtools-ai-docs/references/configuration/integrations/)

### First commands

```bash
dmtools list
dmtools jira_get_ticket YOUR-123
dmtools run agents/story_development.json
```

## Upgrading from legacy installs

Keep the latest-release installer flow introduced in DMC-858 when refreshing existing environments.

1. Back up `~/.dmtools` before changing anything so you can restore the current wrapper, JAR, and local state if migration fails.
2. Re-run the installer from `https://github.com/epam/dm.ai/releases/latest/download/install.sh` (or `install.bat` / `install.ps1` on Windows) to replace any legacy bootstrap URLs.
3. Preserve or merge your existing `dmtools.env`, `dmtools-local.env`, and local configuration instead of overwriting them.
4. Remove stale aliases, wrapper scripts, and PATH entries that resolve outside `~/.dmtools/bin`.
5. Verify the migrated install with `dmtools --version` and `dmtools list`.
6. If anything fails, roll back by restoring the backup copy of `~/.dmtools` and reactivating the previous wrapper or PATH configuration.

Tagged reinstall example for rollback or migration validation:

**macOS / Linux / Git Bash:**

```bash
curl -fsSL https://github.com/epam/dm.ai/releases/download/v1.7.179/install.sh | bash -s -- v1.7.179
```

**Windows:**

```cmd
set DMTOOLS_VERSION=v1.7.179 && curl -fsSL https://github.com/epam/dm.ai/releases/download/v1.7.179/install.bat -o "%TEMP%\dmtools-install.bat" && "%TEMP%\dmtools-install.bat"
```

## Build from source

```bash
./gradlew :dmtools-core:shadowJar
./buildInstallLocal.sh
```

See the [installation guide](dmtools-ai-docs/references/installation/README.md) for local development installation details.
