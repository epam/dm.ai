# dm.ai Product Domain Knowledge — PO Context

## What is dm.ai / DMtools?

dm.ai is a **CLI-first AI orchestration toolkit** that connects enterprise systems (Jira, Azure DevOps, Confluence, Figma, Teams, TestRail, and more) and prepares rich context for AI coding agents (GitHub Copilot CLI, Cursor, Claude Code, etc.).

The core product is a **single Java CLI binary** (`dmtools`) distributed as a fat JAR with install scripts for all platforms.

---

## The Three-Layer Mental Model

Every feature in dmtools fits into one of these layers:

### 1. 🔌 Connectors (MCP Tools)
Human-friendly CLI methods to communicate with enterprise systems.

```bash
dmtools jira_get_ticket DMC-123          # get a ticket
dmtools jira_search_by_jql "..."         # search tickets
dmtools github_get_pr_comments ...       # read PR comments
dmtools figma_get_layers ...             # extract Figma design data
dmtools ado_get_work_item ...            # Azure DevOps work item
```

Each connector is a `@MCPTool`-annotated Java method exposed as a CLI command.
Output format is controlled by flags: `--mini` (compact), `--json` (raw JSON), `--toon` (token-optimised human-readable).

### 2. 🤖 preCLI Context Preparation → AI Execution → postCLI Automation
The primary job of **most integrations** is:
1. **preCLI**: Gather context from multiple systems (Jira ticket + GitHub PR + Figma designs + existing code) and write it to `input/` files
2. **AI execution**: Pass context to an AI CLI agent (Copilot, Cursor, Claude Code) which reads `input/`, does the work, writes `output/`
3. **postCLI**: Read `output/`, then automate follow-up actions (create PR, post Jira comment, update ticket status, attach files)

This is the **Teammate pattern** — the backbone of all agent workflows.

### 3. 📊 Ready-made Jobs
Pre-built automation pipelines that don't need a CLI agent:
- `ReportGeneratorJob` — configurable analytics reports from tracker, SCM, CSV, or Figma data
- `ReportVisualizerJob` — interactive HTML visualization for generated report JSON
- `TestCasesGenerator` — auto-generate test cases from stories
- `Expert` — domain Q&A from knowledge base
- `JSRunner` — custom JavaScript automation (SM agent, intake, etc.)
- `KBProcessingJob` — process source content into structured knowledge-base output

---

## Key Architecture Facts

| Fact | Detail |
|---|---|
| **Implementation scope** | `dmtools-core/src/main/java` only |
| **New tools pattern** | `@MCPTool` / `@MCPParam` annotations — no REST endpoints, no Spring Boot |
| **Config** | `.dmtools/config.js` or `dmtools.env` |
| **Agent configs** | `agents/*.json` — Teammate/Expert/JSRunner job configs |
| **Output formats** | `--mini` saves ~40% tokens, `--toon` is human-readable, `--json` is raw |
| **Submodule** | `agents/` is a git submodule — dm.ai project config is in `.dmtools/config.js` |

---

## What a Good Story Looks Like in This Project

- **Scope**: `dmtools-core` only. No API server, no UI, no Spring Boot endpoints.
- **New connector/tool**: Add `@MCPTool` method → it automatically becomes a CLI command + MCP tool.
- **New workflow**: New `agents/*.json` config + optional JavaScript in `agents/js/`.
- **Max 5 SPs per story**. If bigger — split into Core subtask + separate follow-up stories.
- **Only [CORE] subtasks** when decomposing. Never [API], [UI], [SD API].

---

## How AI Agents Use DMtools (Think From the Agent's Perspective)

When designing features, always ask: *"How will Copilot / Cursor / Claude use this tool?"*

- Tools should return **structured, token-efficient output** by default.
- The `--mini` flag should strip noise (stack traces, verbose metadata) while keeping actionable data.
- Context files written to `input/` should be **self-contained** — the AI agent must be able to do its job reading only `input/` without needing to call dmtools again.
- preCLI scripts should anticipate what context the AI will need and fetch it proactively.
- postCLI scripts should be **idempotent** — safe to re-run if the AI produces the same output.

---

## Current Integration Surface (as of latest release)

Check live with:
```bash
dmtools list
grep -rn "@MCPTool" dmtools-core/src/main/java --include="*.java" | wc -l
```

Key integration packages:
- `com.github.istin.dmtools.jira` — Jira (52+ tools)
- `com.github.istin.dmtools.github` — GitHub
- `com.github.istin.dmtools.microsoft.ado` — Azure DevOps
- `com.github.istin.dmtools.atlassian.confluence` — Confluence
- `com.github.istin.dmtools.figma` — Figma
- `com.github.istin.dmtools.report` — Report generation
- `com.github.istin.dmtools.job` — Job framework (Teammate, Expert, JSRunner, etc.)
