# DMC-920 automated test

This test validates that `dmtools-ai-docs/references/installation/troubleshooting.md` exposes a visible backlink to the root `README.md#installation` anchor and that the link resolves to the installation section users expect to return to.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-920/test_dmc_920.py -q
```

## Environment

No environment variables are required. The test reads markdown files directly from this repository checkout.

