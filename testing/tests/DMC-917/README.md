# DMC-917 automated test

This test validates that the installation guide uses reusable release placeholders, still includes at least one `latest` alias example, and explicitly tells readers that `latest` is mutable while pinned release-tags are the authoritative reference.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-917/test_dmc_917.py -q
```

## Environment

No environment variables are required. The test reads `dmtools-ai-docs/references/installation/README.md` from this repository checkout.
