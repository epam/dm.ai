# DMC-987 automated test

This test validates that the maintainer-facing GitHub repository discoverability
playbook is reachable from both `README.md` and `CONTRIBUTING.md`, and that the
playbook separates repo-backed updates from manual GitHub UI settings.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
PYTHONPATH=. python3 -m pytest testing/tests/DMC-987/test_dmc_987.py -q
```

## Environment

No environment variables are required. The test reads the checked-out repository
documentation directly.

## Expected passing output

```text
5 passed
```
