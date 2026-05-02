# dmtools-jira

## Overview

`dmtools-jira` is the focused DMtools package for Jira workflows, including ticket lookup, JQL search, comments, assignments, and Jira/Xray-driven delivery automation.

## Package / Artifact

- Java package: `com.github.istin.dmtools.atlassian.jira`
- Artifact alias: `com.github.istin:dmtools-jira`
- Focused slash command: `/dmtools-jira`

## Installer / CLI example

```bash
curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/skill-install.sh | bash -s -- --skills jira
```

```bash
bash install.sh --skills jira
```

## Endpoints / Config keys

- Slash command entrypoint: `/dmtools-jira`
- Core configuration keys: `JIRA_BASE_PATH`, `JIRA_LOGIN_PASS_TOKEN`, `JIRA_AUTH_TYPE`
- Optional test-management keys: `XRAY_BASE_PATH`, `XRAY_CLIENT_ID`, `XRAY_CLIENT_SECRET`

## Minimal usage example

```text
/dmtools-jira help me search Jira issues assigned to me and prepare a test-case plan for PROJ-123
```

```bash
dmtools jira_get_ticket PROJ-123
```

## Compatibility / Supported versions

- Compatible with Java 17+ and current DMtools focused skill releases
- Intended for repositories that use the DMtools installer and `dmtools.env` configuration model

## Security & Permissions

- Store Jira credentials only in `dmtools.env`, `dmtools-local.env`, or CI secret storage
- Use the minimum Jira and Xray permissions needed for the project you automate
- Do not commit base64 tokens, API keys, or exported ticket data containing sensitive information

## Linkbacks

- [Central installation guide](../references/installation/README.md)
- [Per-skill package index](index.md)
- [Jira configuration guide](../references/configuration/integrations/jira.md)
- [Jira MCP tools reference](../references/mcp-tools/jira-tools.md)

## Maintainer / Contact

- Maintainer: DMtools Team
- Support: [github.com/epam/dm.ai/issues](https://github.com/epam/dm.ai/issues)

