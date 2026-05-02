# DMC-934 automated test

This test validates that the documentation audit treats JSRunner summary formatting-only differences consistently by ignoring trailing whitespace and newline-only layout changes while keeping the user-visible summary aligned with the canonical reference.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-934/test_dmc_934.py -q
```

## Environment

No environment variables are required. The test reads the checked-out repository documentation and a temporary synthetic documentation fixture created during the test run.

## Expected passing output

```text
1 passed
```
