# DMC-922 automated test

This test validates that the root `README.md` exposes the GitHub-compatible `#installation` anchor through its visible `### Installation` heading, so deep links from the installation guides resolve to the correct section.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-922/test_dmc_922.py -q
```

## Environment

No environment variables are required. The test reads markdown files directly from this repository checkout.

