# DMC-960 automated test

This test validates that `install.sh --skills=jira,unknown --skip-unknown` downgrades
the invalid `unknown` skill to a warning, keeps the valid `jira` skill selected, and
still completes successfully.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-960/test_dmc_960.py -q
```

## Environment

No external credentials are required. The test runs the checked-out `install.sh` script
with `DMTOOLS_INSTALLER_TEST_MODE=true`, stubs download/system-mutation steps through the
shared installer harness, and inspects the generated installer-managed config in an
isolated temp directory.

## Expected passing output

```text
1 passed
```
