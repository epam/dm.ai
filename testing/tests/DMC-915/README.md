# DMC-915 automated test

This test validates that `dmtools-ai-docs/references/installation/README.md` documents the installer contract required by DMC-915: `--skill <name>` as the primary form, `--skills=<name,name>` as an alias, `--all-skills`, `--skip-unknown`, and explicit invalid-skill behavior.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-915/test_dmc_915.py -q
```

## Environment

No environment variables are required. The test reads the installation guide from this repository checkout.

## Expected passing output

```text
5 passed
```
