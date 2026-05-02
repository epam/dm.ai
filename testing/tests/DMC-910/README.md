# DMC-910 automated test

This test validates the visible CodeGenerator migration guidance in the repository documentation.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-910/test_dmc_910.py -q
```

## Environment

No environment variables are required. The test reads the checked-out repository documentation directly.

## Expected passing output

```text
1 passed
```
