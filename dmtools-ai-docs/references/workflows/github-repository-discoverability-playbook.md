# GitHub Repository Discoverability Playbook

Use this page as the maintainer source of truth for repository discoverability updates. The canonical metadata values are versioned in `dmtools-core/src/main/resources/github-repository-discoverability.json` and covered by `GitHubRepositoryDiscoverabilityTest`.

## Canonical repository metadata

| Surface | Canonical value |
|---|---|
| Short description | `Enterprise dark-factory orchestrator for automating delivery workflows across trackers, source control, documentation, design systems, AI providers, and CI/CD.` |
| About text | `DMTools is the orchestration layer for enterprise dark factories: a self-hosted CLI with MCP tools, jobs, and AI agents for delivery workflows across Jira, GitHub, Azure DevOps, documentation, design systems, and CI/CD.` |
| GitHub topics | `dark-factory`, `delivery-automation`, `workflow-orchestration`, `platform-engineering`, `self-hosted`, `enterprise-ai`, `mcp`, `ai-agents`, `generative-ai`, `workflow-automation` |
| Supporting release/package keywords | `dmtools`, `dark-factory`, `delivery-automation`, `workflow-orchestration`, `mcp`, `ai-agents`, `generative-ai`, `self-hosted`, then vendor or connector terms such as `cursor`, `claude`, `codex`, `jira`, `azure-devops`, `github` |

### Topic strategy

- Keep the GitHub topic list anchored on orchestration and enterprise positioning first.
- Use only a small number of AI discovery terms in canonical topics: `mcp`, `ai-agents`, and `generative-ai`.
- Do not spend canonical topic slots on vendor-specific names. Use those only in supporting release or package copy when they add search value.

## Manual GitHub settings

These settings are not stored in the repository and must be applied in the GitHub UI:

1. **About description and topics**: update the repository About panel in GitHub using the canonical values above.
2. **Homepage**: keep the repo homepage pointed at the canonical project entry point you want users and GitHub searchers to land on, usually the repository README or a future product site if one becomes authoritative.
3. **Social preview**: upload a dark hero card with the DMTools wordmark and one short value line only.

### Social preview asset guidance

- Use a dark, high-contrast base with light text.
- Keep the text to the product name plus one short positioning line only.
- Preferred value line: `Enterprise dark-factory orchestrator`.
- Use at most one restrained accent colour.
- Any text rendered into the image must meet WCAG AA contrast guidance.
- Do not use screenshots, integration collages, or feature-list text in the image.

## Repo-backed files that must stay aligned

These files are versioned and should move together when positioning changes:

- `README.md` for the primary public entry point and documentation map.
- `CONTRIBUTING.md` for the maintainer-facing link to this playbook.
- `.github/ISSUE_TEMPLATE/*` for GitHub entry-surface language.
- `.github/workflows/release.yml` and `.github/workflows/release-ai-skill.yml` for release and package-facing keyword copy.
- `dmtools-core/src/main/resources/github-repository-discoverability.json` for canonical metadata values.

## Maintainer checklist

Run this checklist before a release or any positioning refresh:

1. Update `github-repository-discoverability.json` if the approved product positioning changes.
2. Keep README and contributor links pointing to this playbook.
3. Confirm issue templates still describe DMTools surfaces accurately.
4. Sync release-note or package copy so orchestration-first language stays ahead of vendor-specific keywords.
5. Apply any required About, topics, homepage, and social-preview changes in the GitHub UI.
6. If new vendor or connector keywords are needed, add them only after the canonical orchestration and AI-discovery terms.
