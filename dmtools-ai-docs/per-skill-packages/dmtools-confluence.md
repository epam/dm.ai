# dmtools-confluence

## Overview

`dmtools-confluence` is the focused DMtools package for Confluence content discovery and page-management workflows, including page lookup, search, hierarchy traversal, and update automation.

## Package / Artifact

- Java package: `com.github.istin.dmtools.atlassian.confluence`
- Artifact alias: `com.github.istin:dmtools-confluence`
- Focused slash command: `/dmtools-confluence`

## Installer / CLI example

```bash
curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/skill-install.sh | bash -s -- --skills confluence
```

```bash
bash install.sh --skills confluence
```

## Endpoints / Config keys

- Slash command entrypoint: `/dmtools-confluence`
- Core configuration keys: `CONFLUENCE_BASE_PATH`, `CONFLUENCE_LOGIN_PASS_TOKEN`
- Common optional keys: `CONFLUENCE_GRAPHQL_PATH`, `CONFLUENCE_DEFAULT_SPACE`

## Minimal usage example

```text
/dmtools-confluence find the onboarding page in the TEAM space and summarize the latest updates
```

```bash
dmtools confluence_content_by_title_and_space "Onboarding" "TEAM"
```

## Compatibility / Supported versions

- Compatible with Java 17+ and current DMtools focused skill releases
- Works with the DMtools configuration flow documented for Confluence-backed content access

## Security & Permissions

- Keep Confluence credentials in secret storage and out of source control
- Use least-privilege API access for spaces and pages that the automation actually needs
- Review generated page updates before writing into shared documentation spaces

## Linkbacks

- [Central installation guide](../references/installation/README.md)
- [Per-skill package index](index.md)
- [Confluence MCP tools reference](../references/mcp-tools/confluence-tools.md)
- [Global configuration reference](../references/configuration/README.md)

## Maintainer / Contact

- Maintainer: DMtools Team
- Support: [github.com/epam/dm.ai/issues](https://github.com/epam/dm.ai/issues)

