# DMC-916 automated test

This test validates that each per-skill child page under `dmtools-ai-docs/per-skill-packages/` follows the mandatory structure from the ticket, includes the expected technical identifiers, and links back to the central installation guide and per-skill index.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-916/test_dmc_916.py -q
```

## Environment

No environment variables are required. The test reads markdown files directly from this repository checkout.

## Expected passing output

```text
1 passed
```
