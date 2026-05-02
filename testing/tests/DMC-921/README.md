# DMC-921 automated test

This test validates that `dmtools-ai-docs/references/installation/README.md` exposes a visible backlink to the root `README.md#installation` section and that following it returns the user to the installation guidance in the root README.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-921/test_dmc_921.py -q
```

## Environment

No environment variables are required. The test reads markdown files directly from this repository checkout.

## Expected passing output

```text
1 passed
```

