# dmtools-ado

## Overview

`dmtools-ado` is the focused DMtools package for Azure DevOps work-item and pull-request workflows, including planning, ticket updates, comments, reviewers, and code-review thread management.

## Package / Artifact

- Java package: `com.github.istin.dmtools.microsoft.ado`
- Artifact alias: `com.github.istin:dmtools-ado`
- Focused slash command: `/dmtools-ado`

## Installer / CLI example

```bash
curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/skill-install.sh | bash -s -- --skills ado
```

```bash
bash install.sh --skills ado
```

## Endpoints / Config keys

- Slash command entrypoint: `/dmtools-ado`
- Core configuration keys: `ADO_BASE_PATH`, `ADO_PAT`, `ADO_PROJECT`
- Common optional keys: `ADO_AREA_PATH`, `ADO_ITERATION_PATH`, `ADO_TEAM`

## Minimal usage example

```text
/dmtools-ado get work item 12345, review its pull requests, and draft an update comment
```

```bash
dmtools ado_get_work_item 12345
```

## Compatibility / Supported versions

- Compatible with Java 17+ and current DMtools focused skill releases
- Supports Azure DevOps organizations configured through the standard DMtools PAT flow

## Security & Permissions

- Keep PAT values in secret storage and prefer scoped tokens over all-access credentials
- Review repository and work-item permissions before enabling write operations from automation
- Do not commit exported work-item payloads or PR data with sensitive customer details

## Linkbacks

- [Central installation guide](../references/installation/README.md)
- [Per-skill package index](index.md)
- [Azure DevOps configuration guide](../references/configuration/integrations/ado.md)
- [ADO MCP tools reference](../references/mcp-tools/ado-tools.md)

## Maintainer / Contact

- Maintainer: DMtools Team
- Support: [github.com/epam/dm.ai/issues](https://github.com/epam/dm.ai/issues)

