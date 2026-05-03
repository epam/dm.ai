# DMC-981 automated test

This test validates that the repository front door and the core DMTools documentation
entry points use the same product identity language.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
PYTHONPATH=. python3 -m pytest testing/tests/DMC-981/test_dmc_981.py -q
```

## Environment

No environment variables are required. The test reads the checked-out repository
documentation directly.

## Expected passing output

```text
3 passed
```
