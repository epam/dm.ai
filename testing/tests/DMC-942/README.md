# DMC-942 automated test

This test validates that the per-skill documentation integrity validation suite fails with a precise user-visible error when a real child page is missing a mandatory section heading.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-942/test_dmc_942.py -q
```

## Environment

No environment variables are required. The test uses an isolated repository copy, confirms the current audit passes, then mutates `dmtools-ai-docs/per-skill-packages/dmtools-jira.md` inside that sandbox only.

## Expected passing output

```text
1 passed
```
