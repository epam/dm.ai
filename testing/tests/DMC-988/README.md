# DMC-988 automated test

This test validates that the GitHub issue templates are aligned with the
repository discoverability metadata and present DMTools as a CLI-first
orchestration toolkit instead of a generic project.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-988/test_dmc_988.py -q
```

## Environment

No environment variables are required. The test reads the checked-out
repository files directly.

## Expected passing output

```text
3 passed
```
