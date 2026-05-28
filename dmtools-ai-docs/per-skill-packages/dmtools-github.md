# dmtools-github

## Overview

`dmtools-github` is the focused DMtools package for GitHub workflows, including PR inspection, comment management, labels, review-thread follow-up, binary file attachment storage via GitHub Releases assets, and **regex-based filtering of PRs and commits for large repositories**.

## Package / Artifact

- Java package: `com.github.istin.dmtools.github`
- Artifact alias: `com.github.istin:dmtools-github`
- Focused slash command: `/dmtools-github`

## Installer / CLI example

```bash
curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/skill-install.sh | bash -s -- --skills github
```

```bash
bash skill-install.sh --skills github
```

## Endpoints / Config keys

- Slash command entrypoint: `/dmtools-github`
- Core configuration keys: `SOURCE_GITHUB_TOKEN`, `SOURCE_GITHUB_BASE_PATH`
- Common optional keys: `SOURCE_GITHUB_WORKSPACE`, `SOURCE_GITHUB_REPOSITORY`, `SOURCE_GITHUB_BRANCH`

## Minimal usage example

```text
/dmtools-github review pull request 42 in epam/dm.ai and summarize unresolved comments
```

```bash
dmtools github_get_pr workspace=epam repository=dm.ai pullRequestId=42
```

```bash
# Store a file as a PR attachment via a draft release
dmtools github_get_or_create_draft_release workspace=epam repository=dm.ai tagName=mcp-assets-v1
dmtools github_upload_release_asset workspace=epam repository=dm.ai releaseId=<id> filePath=/tmp/screenshot.png
```

```bash
# Filter PRs by title regex (e.g. only feature PRs in a large repo)
dmtools github_list_prs_filtered workspace=epam repository=dm.ai state=merged titleRegex="^feat\("

# Collect commits from all feature/* branches (aggregated, deduplicated)
dmtools github_get_commits_from_branches workspace=epam repository=dm.ai branchNameRegex="^feature/" since=2024-01-01
```

## Key tools at a glance

| Tool | Category | Description |
|------|----------|-------------|
| `github_get_pr` | pull_requests | Get full PR details |
| `github_list_prs` | pull_requests | List PRs by state |
| `github_list_prs_filtered` | pull_requests | List PRs filtered by title regex |
| `github_get_commits_from_branches` | commits | Commits from branches matching regex |
| `github_get_or_create_draft_release` | releases | Get or create a draft release |
| `github_upload_release_asset` | releases | Upload file to release (supports overwrite) |
| `github_list_release_assets` | releases | List assets in a release |
| `github_delete_release_asset` | releases | Delete a release asset by ID |

Full list of 33 tools: [GitHub MCP tools reference](../references/mcp-tools/github-tools.md)

## Compatibility / Supported versions

- Compatible with Java 17+ and current DMtools focused skill releases
- Supports GitHub.com and GitHub Enterprise via configurable base paths

## Security & Permissions

- Store PATs in secure environment variables or CI secret storage only
- Grant the minimum GitHub scopes required for read-only review or comment-writing workflows
- Avoid pasting private repository contents into public channels when using AI-assisted review

## Linkbacks

- [Central installation guide](../references/installation/README.md)
- [Per-skill package index](index.md)
- [GitHub configuration guide](../references/configuration/integrations/github.md)
- [GitHub MCP tools reference](../references/mcp-tools/github-tools.md)

## Maintainer / Contact

- Maintainer: DMtools Team
- Support: [github.com/epam/dm.ai/issues](https://github.com/epam/dm.ai/issues)
