# dmtools-gitlab

## Overview

`dmtools-gitlab` is the focused DMtools package for GitLab merge-request review workflows, including MR inspection, comments, review activities, and inline feedback.

## Package / Artifact

- Java package: `com.github.istin.dmtools.gitlab`
- Artifact alias: `com.github.istin:dmtools-gitlab`
- Focused slash command: `/dmtools-gitlab`

## Installer / CLI example

```bash
curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/skill-install.sh | bash -s -- --skills gitlab
```

```bash
bash install.sh --skills gitlab
```

## Endpoints / Config keys

- Slash command entrypoint: `/dmtools-gitlab`
- Core configuration keys: `GITLAB_TOKEN`, `GITLAB_BASE_PATH`
- Common optional keys: `GITLAB_WORKSPACE`, `GITLAB_REPOSITORY`, `GITLAB_BRANCH`

## Minimal usage example

```text
/dmtools-gitlab inspect merge request 42 for mygroup/myrepo and list blocking review comments
```

```bash
dmtools gitlab_get_mr workspace=mygroup repository=myrepo pullRequestId=42
```

## Compatibility / Supported versions

- Compatible with Java 17+ and current DMtools focused skill releases
- Supports GitLab SaaS and self-hosted GitLab instances through `GITLAB_BASE_PATH`

## Security & Permissions

- Keep GitLab tokens in secret storage and rotate them on your normal credential schedule
- Use scoped tokens with the minimum repository access required for the workflow
- Treat merge-request discussions and diffs as potentially sensitive engineering data

## Linkbacks

- [Central installation guide](../references/installation/README.md)
- [Per-skill package index](index.md)
- [GitLab MCP tools reference](../references/mcp-tools/gitlab-tools.md)
- [Global configuration reference](../references/configuration/README.md)

## Maintainer / Contact

- Maintainer: DMtools Team
- Support: [github.com/epam/dm.ai/issues](https://github.com/epam/dm.ai/issues)

