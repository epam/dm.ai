# DMC-928 automated test

This test validates that re-running `install.sh` with the same `--skills jira,github` selection behaves like a no-op from the install target's point of view: the rerun should succeed, report that the selected skills are already installed, avoid fresh download work, and leave the installed files unchanged.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-928/test_dmc_928.py -q
```

## Environment

No external credentials are required. The test runs the real installer in a temporary isolated HOME directory and observes the generated files under that sandbox.

## Expected passing output

```text
1 passed
```
