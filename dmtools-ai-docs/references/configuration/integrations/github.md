# GitHub Configuration Guide

## Overview

DMtools provides 8 MCP tools for GitHub integration, focused on pull request management: listing PRs, getting details, reading comments and review conversations, adding comments/labels, and getting diffs.

## Authentication

### Generate a Personal Access Token

1. Go to **GitHub Settings → Developer settings → Personal access tokens → Tokens (classic)**
2. Click **Generate new token**
3. Select scopes:
   - `repo` — full repository access (read PRs, comments, reviews)
   - `write:discussion` — if you need to post comments
4. Copy the token immediately (shown only once)

### Configure DMtools

Add to your `dmtools.env`:

```bash
SOURCE_GITHUB_TOKEN=ghp_YourPersonalAccessTokenHere
SOURCE_GITHUB_WORKSPACE=YourOrgOrUsername    # optional default workspace
SOURCE_GITHUB_REPOSITORY=your-repo          # optional default repository
SOURCE_GITHUB_BASE_PATH=https://api.github.com
SOURCE_GITHUB_BRANCH=main                   # optional default branch
```

### DMTOOLS_INTEGRATIONS

If your `dmtools.env` contains a `DMTOOLS_INTEGRATIONS` list, make sure `github` is included:

```bash
# Without this, `dmtools list github` returns {"tools": []}
DMTOOLS_INTEGRATIONS=jira,cli,file,teams,figma,jira_xray,testrail,github,ado,confluence
```

## Verify Setup

```bash
# List all GitHub tools (should return 8 tools)
dmtools list github

# Get a specific pull request
dmtools github_get_pr workspace=MyOrg repository=my-repo pullRequestId=42
```

## Tools Available

| Tool | Description |
|------|-------------|
| `github_list_prs` | List PRs by state (`open`, `closed`, `merged`) |
| `github_get_pr` | Get PR details (title, state, author, branches, merge info) |
| `github_get_pr_comments` | Get all comments (inline review + discussion) |
| `github_get_pr_conversations` | Get inline review threads grouped by root/replies |
| `github_get_pr_activities` | Get all activity (reviews + comments) |
| `github_add_pr_comment` | Post a comment to the PR discussion |
| `github_add_pr_label` | Add a label to a PR |
| `github_get_pr_diff` | Get diff statistics (files changed, additions, deletions) |

Full reference: [github-tools.md](../../mcp-tools/github-tools.md)

## GitHub Enterprise

For GitHub Enterprise, set a custom base path:

```bash
SOURCE_GITHUB_BASE_PATH=https://github.yourcompany.com/api/v3
```
