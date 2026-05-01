# DMC-896 Automation

## What this test covers

This test audits all discoverable `SKILL.md` frontmatter descriptions under the configured ticket scope and verifies that each summary:

- stays within 2 sentences
- stays within 160 characters
- avoids filler wording
- includes a concise `Use ...` guidance sentence when a second sentence is present

## Assumption

The ticket input does not include the explicit list of the "10 audited agents", so the automation audits the discoverable `SKILL.md` descriptions under `dmtools-ai-docs`, which is the only repo area named in the preconditions.

## Install dependencies

No project-specific dependencies are required. The test uses Python's standard library.

## Run this test

```bash
python testing/tests/DMC-896/test_dmc_896.py
```

## Environment variables / config

None.

## Expected passing output

The command exits with status `0` and reports:

```text
OK
```
