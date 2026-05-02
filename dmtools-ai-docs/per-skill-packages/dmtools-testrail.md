# dmtools-testrail

## Overview

`dmtools-testrail` is the focused DMtools package for TestRail test-management workflows, including case lookup, requirement linking, label handling, and generated test-case creation.

## Package / Artifact

- Java package: `com.github.istin.dmtools.testrail`
- Artifact alias: `com.github.istin:dmtools-testrail`
- Focused slash command: `/dmtools-testrail`

## Installer / CLI example

```bash
curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/skill-install.sh | bash -s -- --skills testrail
```

```bash
bash install.sh --skills testrail
```

## Endpoints / Config keys

- Slash command entrypoint: `/dmtools-testrail`
- Core configuration keys: `TESTRAIL_BASE_PATH`, `TESTRAIL_USERNAME`, `TESTRAIL_API_KEY`
- Common optional keys: `TESTRAIL_PROJECT`, `TESTRAIL_LOGGING_ENABLED`

## Minimal usage example

```text
/dmtools-testrail create or link test cases for PROJ-123 in the Regression project
```

```bash
dmtools testrail_get_cases_by_refs PROJ-123 "Regression"
```

## Compatibility / Supported versions

- Compatible with Java 17+ and current DMtools focused skill releases
- Uses the standard DMtools TestRail integration and job-based test-generation flows

## Security & Permissions

- Keep TestRail usernames and API keys in secure environment storage only
- Scope project access so automation can reach only the suites and cases it needs
- Enable request logging only when debugging, because logs can include sensitive metadata

## Linkbacks

- [Central installation guide](../references/installation/README.md)
- [Per-skill package index](index.md)
- [TestRail configuration guide](../references/configuration/integrations/testrail.md)
- [TestRail MCP tools reference](../references/mcp-tools/testrail-tools.md)

## Maintainer / Contact

- Maintainer: DMtools Team
- Support: [github.com/epam/dm.ai/issues](https://github.com/epam/dm.ai/issues)

