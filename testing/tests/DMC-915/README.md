# DMC-915 automated test

This test validates that `dmtools-ai-docs/references/installation/README.md` documents the canonical installer skill-selection syntax and the observable behavior for invalid skill names.

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
