# DMC-986 automated test

This test validates the canonical GitHub repository discoverability metadata
source and the maintainer-facing playbook that points to it.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
PYTHONPATH=. python3 -m pytest testing/tests/DMC-986/test_dmc_986.py -q
```

## Environment

No environment variables are required. The test reads the checked-out repository
metadata JSON and the discoverability playbook directly.

## Expected passing output

```text
3 passed
```
