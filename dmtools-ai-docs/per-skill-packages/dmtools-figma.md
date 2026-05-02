# dmtools-figma

## Overview

`dmtools-figma` is the focused DMtools package for Figma design-analysis workflows, including file structure inspection, node exports, image extraction, icon discovery, and design-token access.

## Package / Artifact

- Java package: `com.github.istin.dmtools.figma`
- Artifact alias: `com.github.istin:dmtools-figma`
- Focused slash command: `/dmtools-figma`

## Installer / CLI example

```bash
curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/skill-install.sh | bash -s -- --skills figma
```

```bash
bash install.sh --skills figma
```

## Endpoints / Config keys

- Slash command entrypoint: `/dmtools-figma`
- Core configuration keys: `FIGMA_TOKEN`, `FIGMA_BASE_PATH`
- Common optional keys: `FIGMA_FILE_KEY`, node-specific `href` and `nodeId` parameters in CLI usage

## Minimal usage example

```text
/dmtools-figma inspect a Figma screen, list top-level layers, and extract the exportable icons
```

```bash
dmtools figma_get_layers "https://www.figma.com/file/abc123/Product?node-id=1%3A2"
```

## Compatibility / Supported versions

- Compatible with Java 17+ and current DMtools focused skill releases
- Uses the Figma API through the standard DMtools token-based configuration pattern

## Security & Permissions

- Keep Figma personal access tokens in secret storage only
- Limit shared design links and downloaded artifacts to the teams that need them
- Review whether design files contain confidential roadmap, brand, or customer information before exporting them

## Linkbacks

- [Central installation guide](../references/installation/README.md)
- [Per-skill package index](index.md)
- [Figma MCP tools reference](../references/mcp-tools/figma-tools.md)
- [Global configuration reference](../references/configuration/README.md)

## Maintainer / Contact

- Maintainer: DMtools Team
- Support: [github.com/epam/dm.ai/issues](https://github.com/epam/dm.ai/issues)

