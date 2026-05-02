# dmtools-report

## Overview

`dmtools-report` is the focused DMtools package for report-generation workflows, including configurable productivity reports, data-source aggregation, computed metrics, and HTML/JSON outputs.

## Package / Artifact

- Java package: `com.github.istin.dmtools.report`
- Artifact alias: `com.github.istin:dmtools-report`
- Focused slash command: `/dmtools-report`

## Installer / CLI example

```bash
curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/skill-install.sh | bash -s -- --skills report
```

```bash
bash install.sh --skills report
```

## Endpoints / Config keys

- Slash command entrypoint: `/dmtools-report`
- Primary runtime entrypoint: `dmtools run dmtools-ai-docs/references/examples/report-generator-job.json`
- Common dependent configuration keys: `JIRA_BASE_PATH`, `ADO_BASE_PATH`, `SOURCE_GITHUB_TOKEN`, `GITLAB_TOKEN`, `FIGMA_TOKEN`, and one configured AI provider such as `GEMINI_API_KEY` or `OPENAI_API_KEY`

## Minimal usage example

```text
/dmtools-report generate a contribution report for the backend team using Jira and GitHub data from the last sprint
```

```bash
dmtools run dmtools-ai-docs/references/examples/report-generator-job.json
```

## Compatibility / Supported versions

- Compatible with Java 17+ and current DMtools focused skill releases
- Supports the data sources documented in the DMtools reporting guides and example job configs

## Security & Permissions

- Reports inherit access from their underlying data sources, so secure every referenced tracker, VCS, or design-system credential
- Review report outputs before sharing because HTML and JSON exports can expose internal metrics and identities
- Keep generated files out of source control unless they are intentionally published artifacts

## Linkbacks

- [Central installation guide](../references/installation/README.md)
- [Per-skill package index](index.md)
- [Report generation guide](../references/reporting/report-generation.md)
- [Job system reference](../references/jobs/README.md)

## Maintainer / Contact

- Maintainer: DMtools Team
- Support: [github.com/epam/dm.ai/issues](https://github.com/epam/dm.ai/issues)

