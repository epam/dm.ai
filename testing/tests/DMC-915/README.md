# DMC-915 automated test

This test validates both sides of the DMC-915 contract:
1. `dmtools-ai-docs/references/installation/README.md` documents `--skill <name>` as the primary form, `--skills=<name,name>` as an alias, `--all-skills`, `--skip-unknown`, and explicit invalid-skill behavior.
2. `dmtools-ai-docs/install.sh` actually supports the same commands and failure/warning behavior so the documentation cannot pass while describing unsupported syntax.

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
6 passed
```
