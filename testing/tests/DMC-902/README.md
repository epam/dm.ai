# DMC-902 automated test

This test validates that every discovered `Upgrading from legacy installs` section in the tracked installation documentation includes the required migration safeguards: backup, EPAM release replacement, config preservation, path update guidance, verification commands, and rollback.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-902/test_dmc_902.py -q
```

## Environment

No environment variables are required. The test reads repository documentation files directly from this checkout.

## Expected passing output

```text
1 passed
```
