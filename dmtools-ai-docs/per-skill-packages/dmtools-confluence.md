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
bash skill-install.sh --skills confluence
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

## Markdown round-trip with attachments

You can edit Confluence pages locally as Markdown and push them back, including attachments:

1. Download a page as Markdown:
   ```bash
   dmtools confluence_content_by_id "123456" "md"
   ```

2. Save the Markdown body to a file and place referenced files next to it (or in a subdirectory):
   ```markdown
   # Design Doc

   ![architecture](assets/architecture.png)

   See [spec](assets/spec.pdf).
   ```

3. Create or update the page and upload referenced attachments automatically:
   ```bash
   # Create a new page
   dmtools confluence_create_page_from_markdown_file_with_attachments \
       "Design Doc" "654321" "/tmp/design.md" "TEAM" "/tmp/assets"

   # Update an existing page
   dmtools confluence_update_page_from_markdown_file_with_attachments \
       "123456" "Design Doc" "654321" "/tmp/design.md" "TEAM" "/tmp/assets"
   ```

Existing attachments are skipped, so the same command can be run repeatedly without re-uploading files.

For manual attachment management, use:

```bash
# Upload a single file
dmtools confluence_upload_attachment "123456" "/tmp/diagram.png"

# Upload an entire directory
dmtools confluence_upload_attachments "123456" "/tmp/attachments"
```

## Sync a Markdown documentation tree

To keep an entire directory of Markdown documentation in sync with Confluence, use `confluence_sync_markdown_directory`:

```
docs/
├── index.md
├── getting-started.md
├── assets/
│   └── screenshot.png
└── api/
    ├── index.md
    └── authentication.md
```

```bash
dmtools confluence_sync_markdown_directory "/tmp/docs" "123456" "TEAM" "false" "/tmp/assets"
```

Behavior:

- `123456` is the existing Confluence page ID that represents `/tmp/docs`.
- `index.md` or `README.md` inside a directory supplies the body for the matching folder page.
- Subdirectories become child pages named after the directory.
- Regular `.md` files become child pages under their parent directory page.
- Cross-links between `.md` files are rewritten to Confluence `ri:page` references.
- Local images and attachments are uploaded idempotently (existing attachments are skipped).
- Non-Markdown files in each directory (for example `api/schema.json` or `assets/summary.pdf`) are uploaded as attachments to the corresponding Confluence page. If such a file is not already referenced in the page body, an **Attachments** section is appended to the page with explicit links so the files remain visible and downloadable.
- Passing `true` for `deleteOrphans` removes Confluence child pages whose titles no longer match any file or directory in the tree.

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
