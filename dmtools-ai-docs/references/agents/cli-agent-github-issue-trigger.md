# Triggering CliAgent from a GitHub Issue

This guide explains how to run a `CliAgent` job that is triggered by a GitHub issue instead of a Jira/ADO/Rally ticket. The agent reads the issue, prepares a local input context, runs an external CLI agent (Cursor, Claude Code, Copilot, etc.), and can push the result as a new branch based on `main`.

## Use Case

- A user files a feature request or bug in GitHub Issues.
- A GitHub Actions workflow (or a local script) picks up the event.
- `CliAgent` fetches the issue body, labels, and metadata.
- The CLI agent receives the issue as a Markdown prompt in `input/github_issue_development/context.md`.
- The CLI agent implements the change in a fresh branch cut from `main`.
- A post-action pushes the branch and optionally opens a pull request.

## Required Configuration

Set these environment variables or add them to `dmtools.env`:

```bash
# GitHub
SOURCE_GITHUB_TOKEN=ghp_...
SOURCE_GITHUB_WORKSPACE=your-org
SOURCE_GITHUB_REPOSITORY=your-repo
SOURCE_GITHUB_BRANCH=main

# AI provider (whichever your CLI agent does NOT need)
DEFAULT_LLM=gemini
GEMINI_API_KEY=...
```

> `CliAgent` only needs a tracker config if you use the optional `input` ticket feature. For a pure GitHub-issue flow, the issue is fetched in `preJSAction` with the `github_get_issue` MCP tool.

## Job Configuration

Save as `agents/cli_agent_github_issue.json`:

```json
{
  "name": "CliAgent",
  "params": {
    "metadata": {
      "contextId": "github_issue_development"
    },
    "preJSAction": "agents/js/prepareGitHubIssueContext.js",
    "cliCommands": [
      "./agents/scripts/run-cursor-agent.sh \"Read input/github_issue_development/context.md, implement the request, and write your changes to the working tree.\""
    ],
    "postJSAction": "agents/js/commitAndPushBranch.js",
    "outputType": "none",
    "cleanupInputFolder": true,
    "cleanupOutputsFolder": false,
    "customParams": {
      "baseBranch": "main"
    }
  }
}
```

### How parameters work

| Param | Purpose |
|-------|---------|
| `metadata.contextId` | Name of the input folder created under `input/`. Used to avoid collisions with other `CliAgent` runs. |
| `preJSAction` | GraalJS script that calls `github_get_issue` and writes `input/{contextId}/context.md`. |
| `cliCommands` | One or more shell commands that run the external CLI agent. Any shell syntax is allowed. |
| `postJSAction` | GraalJS script that creates the branch, commits, and pushes. |
| `customParams.baseBranch` | Branch that the new feature branch is cut from. Passed to the post-action. |

## JavaScript Actions

### `agents/js/prepareGitHubIssueContext.js`

Reads the GitHub issue from environment variables set by the calling workflow and writes a Markdown context file.

```javascript
function action(params) {
  const customParams = params.customParams || {};
  const issueNumber = process.env.GITHUB_ISSUE_NUMBER || customParams.issueNumber;
  const workspace = process.env.GITHUB_REPOSITORY_OWNER || customParams.workspace || "your-org";
  const repository = process.env.GITHUB_REPOSITORY
    ? process.env.GITHUB_REPOSITORY.split("/")[1]
    : customParams.repository || "your-repo";

  if (!issueNumber) {
    throw new Error("GITHUB_ISSUE_NUMBER or customParams.issueNumber is required");
  }

  const issue = github_get_issue(workspace, repository, String(issueNumber));

  const context = `# GitHub Issue #${issue.number}: ${issue.title || ""}

- **URL:** ${issue.html_url || ""}
- **State:** ${issue.state || ""}
- **Labels:** ${(issue.labels || []).join(", ")}

${issue.body || ""}
`;

  const inputFolder = params.inputFolderPath;
  file_write(`${inputFolder}/context.md`, context);

  return {
    success: true,
    issueNumber: issue.number,
    issueTitle: issue.title
  };
}
```

### `agents/js/commitAndPushBranch.js`

Creates a new branch from `main`, commits the CLI agent changes, and pushes it.

```javascript
function action(params) {
  const customParams = params.customParams || {};
  const issueNumber = process.env.GITHUB_ISSUE_NUMBER || customParams.issueNumber || "unknown";
  const baseBranch = customParams.baseBranch || "main";
  const branchName = `feature/github-issue-${issueNumber}`;

  // Create a fresh branch from the configured base branch
  cli_execute_command(`git fetch origin ${baseBranch}`);
  cli_execute_command(`git checkout -b ${branchName} origin/${baseBranch}`);

  // Stage and commit any changes produced by the CLI agent
  cli_execute_command('git add -A');
  cli_execute_command(`git commit -m "Implement changes for GitHub issue #${issueNumber}"`);

  // Push the branch
  cli_execute_command(`git push -u origin ${branchName}`);

  return {
    success: true,
    branch: branchName
  };
}
```

## GitHub Actions Workflow

Add `.github/workflows/cli-agent-issue.yml`:

```yaml
name: CliAgent from Issue

on:
  issues:
    types: [opened, reopened, labeled]
  workflow_dispatch:
    inputs:
      issue_number:
        description: "GitHub issue number"
        required: true

jobs:
  run-agent:
    runs-on: ubuntu-latest
    permissions:
      contents: write
      issues: read
    steps:
      - uses: actions/checkout@v4
        with:
          ref: main
          fetch-depth: 0

      - name: Install DMtools
        run: |
          curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install.sh | bash

      - name: Run CliAgent
        env:
          SOURCE_GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          SOURCE_GITHUB_WORKSPACE: ${{ github.repository_owner }}
          SOURCE_GITHUB_REPOSITORY: ${{ github.event.repository.name }}
          GITHUB_ISSUE_NUMBER: ${{ github.event.issue.number || github.event.inputs.issue_number }}
          GEMINI_API_KEY: ${{ secrets.GEMINI_API_KEY }}
          DEFAULT_LLM: gemini
        run: |
          dmtools run agents/cli_agent_github_issue.json
```

## Running Locally

Export the issue number and run the config directly:

```bash
export GITHUB_ISSUE_NUMBER=42
export SOURCE_GITHUB_TOKEN=ghp_...
export SOURCE_GITHUB_WORKSPACE=your-org
export SOURCE_GITHUB_REPOSITORY=your-repo

dmtools run agents/cli_agent_github_issue.json
```

## Security Notes

- Never commit `dmtools.env` or workflow secrets.
- Use `excludedEnvVariables` or `excludeEnvVariablesByRegex` in `CliAgent` params to hide sensitive keys from the CLI subprocess if your agent does not need them:

```json
{
  "params": {
    "excludedEnvVariables": ["GEMINI_API_KEY", "OPENAI_API_KEY"]
  }
}
```

## See Also

- [CliAgent job reference](../jobs/README.md#cliagent)
- [CLI Integration Guide](cli-integration.md)
- [GitHub MCP Tools](../mcp-tools/github-tools.md)
