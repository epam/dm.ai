# DMC-1034 automated test

This test verifies that the ReportGenerator regression for malformed
`X-RateLimit-Reset` metadata remains wired to the deployed fallback retry path
instead of crashing with `NumberFormatException`.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
PYTHONPATH=. python3 -m pytest testing/tests/DMC-1034/test_dmc_1034.py -q
```

## Environment

No extra environment variables are required beyond the repository checkout and
the standard Python test dependencies from `testing/requirements.txt`.

## Expected passing output

```text
2 passed
```
