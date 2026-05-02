# DMC-946 automated test

This test validates that `install.sh` accepts `DMTOOLS_STRICT_INSTALL=true` for a valid
`--skills=jira` install, keeps strict mode enabled, and still completes successfully.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-946/test_dmc_946.py -q
```

## Environment

No external credentials are required. The test runs the checked-out `install.sh` script with
`DMTOOLS_INSTALLER_TEST_MODE=true`, stubs download/system-mutation steps through the shared
installer harness, and inspects the generated installer-managed config in an isolated temp
directory.

## Expected passing output

```text
1 passed
```
