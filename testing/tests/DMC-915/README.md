# DMC-915 automated test

This test validates that `dmtools-ai-docs/references/installation/README.md` matches the installer syntax that is actually supported today for focused skill installation and does not promise unsupported flags or warning-based error handling.

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
