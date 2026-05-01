# DMC-896 Automation

## What this test covers

This test audits the 10 agent blurbs documented in the common job reference table at
`dmtools-ai-docs/references/agents/teammate-configs.md` and verifies that each summary:

- stays within 2 sentences
- stays within 160 characters
- avoids filler wording
- uses concise, technical, active voice

## Ticket scope

The ticket scope is pinned in `config.yaml` to the 10 jobs listed in the "Common job reference" table:

- `Teammate`
- `JSRunner`
- `TestCasesGenerator`
- `InstructionsGenerator`
- `DevProductivityReport`
- `BAProductivityReport`
- `QAProductivityReport`
- `ReportGenerator`
- `ReportVisualizer`
- `KBProcessingJob`

The test fails if any configured agent is missing from the reference table or if any configured summary falls outside the length/tone rules.

## Install dependencies

No project-specific dependencies are required. The test uses Python's standard library.

## Run this test

```bash
python testing/tests/DMC-896/test_dmc_896.py
```

## Environment variables / config

No environment variables are required. The audited table path and 10-agent scope are defined in `config.yaml`.

## Expected passing output

The command exits with status `0` and reports:

```text
OK
```
