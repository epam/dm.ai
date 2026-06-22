# dmtools-mermaid

## Overview

`dmtools-mermaid` is the DMtools skill for rendering Mermaid diagrams to SVG and PNG files entirely server-side — no Playwright, no browser, no network required. It uses an embedded GraalJS Mermaid engine for SVG generation and Apache Batik for SVG→PNG rasterisation.

Supports all major Mermaid diagram types: flowchart, sequence, class, state, ER, user-journey, gantt, pie, git, mindmap, kanban, quadrant, requirement, timeline, venn, block, architecture, C4, sankey, radar, xy, treemap, treeview, ishikawa, packet, wardley.

## Package / Artifact

- Java package: `com.github.istin.dmtools.mermaid`
- Renderer module: `com.github.istin:dmtools-mermaid-renderer` (submodule `dmtools-mermaid-renderer`)
- Focused slash command: `/dmtools-mermaid`

## Installer / CLI example

```bash
curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/skill-install.sh | bash -s -- --skills mermaid
```

```bash
bash install.sh --skills mermaid
```

## CLI commands

```bash
# Render to SVG (vector, lossless)
dmtools mermaid_to_svg "flowchart TD; A[Start] --> B[Done]" --output diagram.svg

# Render to PNG (raster, suitable for embedding in docs/PRs)
dmtools mermaid_to_png "flowchart TD; A[Start] --> B[Done]" --output diagram.png

# Generate diagrams from Confluence / Jira pages
dmtools mermaid_index_generate confluence '["SPACE/pages/**"]' --storage_path ./diagrams
```

## Usage in JavaScript agents

```javascript
// Render a diagram to SVG or PNG from within a JS agent
const svgPath = mermaid_to_svg("classDiagram\n  class User { +String name }",  "--output user.svg");
const pngPath = mermaid_to_png("pie title Breakdown\n  \"A\":40\n  \"B\":60", "--output pie.png");
```

## Supported diagram types

| Category | Types |
|----------|-------|
| Flow / Logic | `flowchart`, `sequenceDiagram`, `stateDiagram` |
| Structure | `classDiagram`, `erDiagram`, `requirementDiagram`, `block`, `architecture`, `C4Context` |
| Planning | `gantt`, `timeline`, `kanban`, `journey` (user journey) |
| Data | `pie`, `xychart`, `sankey`, `quadrantChart`, `radar`, `treemap` |
| Knowledge | `mindmap`, `gitGraph`, `venn`, `ishikawa`, `treeview`, `packet`, `wardley` |

## Configuration

No external API credentials required — the renderer runs entirely in-process using GraalJS. Only the dmtools JAR and the `dmtools-mermaid-renderer` submodule are needed.

Ensure the submodule is initialised when building from source:
```bash
git submodule update --init dmtools-mermaid-renderer
```

## Compatibility / Supported versions

- Requires Java 17+
- GraalJS polyglot engine (bundled via `org.graalvm.polyglot`)
- Apache Batik 1.17+ for PNG rasterisation
- Compatible with current DMtools focused skill releases

## Security & Permissions

- No external network calls are made during rendering
- Mermaid source text is processed in-memory — do not pass sensitive data as diagram text if logs are forwarded externally
- Output files are written to the path you specify; ensure the target directory has correct permissions

## MCP Tools reference

See [mermaid-tools.md](../references/mcp-tools/mermaid-tools.md) for full parameter documentation of all 5 tools:
- `mermaid_to_svg`
- `mermaid_to_png`
- `mermaid_index_generate`
- `mermaid_index_read`
- `mermaid_index_read_list`

## Linkbacks

- [Central installation guide](../references/installation/README.md)
- [Per-skill package index](index.md)
- [Mermaid MCP tools reference](../references/mcp-tools/mermaid-tools.md)

## Maintainer / Contact

- Maintainer: DMtools Team
- Support: [github.com/epam/dm.ai/issues](https://github.com/epam/dm.ai/issues)
