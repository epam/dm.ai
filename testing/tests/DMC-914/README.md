# DMC-914 automated test

This test validates that `dmtools-ai-docs/per-skill-packages/index.md` exists and acts as the canonical per-skill catalogue for the initial 9 focused DMtools packages. It checks the visible markdown catalogue table for the exact skill name, slash command path, Java package identifier, and artifact alias for each approved skill.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-914/test_dmc_914.py -q
```

## Environment

No environment variables are required. The test reads documentation files directly from this repository checkout.

## Expected passing output

```text
5 passed
```
