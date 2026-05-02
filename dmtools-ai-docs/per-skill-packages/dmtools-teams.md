# dmtools-teams

## Overview

`dmtools-teams` is the focused DMtools package for Microsoft Teams communication workflows, including chat lookup, message retrieval, transcript discovery, file download, and sending messages.

## Package / Artifact

- Java package: `com.github.istin.dmtools.microsoft.teams`
- Artifact alias: `com.github.istin:dmtools-teams`
- Focused slash command: `/dmtools-teams`

## Installer / CLI example

```bash
curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/skill-install.sh | bash -s -- --skills teams
```

```bash
bash install.sh --skills teams
```

## Endpoints / Config keys

- Slash command entrypoint: `/dmtools-teams`
- Core configuration keys: `TEAMS_TENANT_ID`, `TEAMS_CLIENT_ID`, `TEAMS_CLIENT_SECRET`
- Common optional keys: `TEAMS_CHANNEL_ID` and Teams-specific chat or team identifiers passed to CLI commands

## Minimal usage example

```text
/dmtools-teams summarize new messages from the Release Coordination chat since yesterday
```

```bash
dmtools teams_messages_since "Release Coordination" "2026-05-01T00:00:00Z"
```

## Compatibility / Supported versions

- Compatible with Java 17+ and current DMtools focused skill releases
- Uses Microsoft Graph-backed Teams access through the standard DMtools authentication flow

## Security & Permissions

- Keep Microsoft application credentials and refresh tokens in secure secret storage
- Grant the minimum Graph permissions required for chats, channels, files, or transcripts
- Treat downloaded chats, recordings, and transcripts as sensitive collaboration data

## Linkbacks

- [Central installation guide](../references/installation/README.md)
- [Per-skill package index](index.md)
- [Teams MCP tools reference](../references/mcp-tools/teams-tools.md)
- [Global configuration reference](../references/configuration/README.md)

## Maintainer / Contact

- Maintainer: DMtools Team
- Support: [github.com/epam/dm.ai/issues](https://github.com/epam/dm.ai/issues)

