# DMC-901 automated test

This test validates that the installation documentation uses the canonical EPAM version-pinned release artifact URL and that specific-version installer examples keep their version flag aligned with the release path.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-901/test_dmc_901.py -q
```

## Environment

No environment variables are required. The test reads repository documentation files directly from this checkout.

## Expected passing output

```text
1 passed
```
