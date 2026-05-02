# dmtools-github

## Overview

`dmtools-github` is the focused DMtools package for GitHub pull-request review workflows, including PR inspection, comment management, labels, and review-thread follow-up.

## Package / Artifact

- Java package: `com.github.istin.dmtools.github`
- Artifact alias: `com.github.istin:dmtools-github`
- Focused slash command: `/dmtools-github`

## Installer / CLI example

```bash
curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/skill-install.sh | bash -s -- --skills github
```

```bash
bash install.sh --skills github
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

