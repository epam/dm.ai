# DMC-968 automated test

This test validates that re-running `install.sh --skills jira,github` after manually
deleting only the installed `dmtools` launcher restores the missing shell script
without redownloading the existing `dmtools.jar`.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-968/test_dmc_968.py -q
```

## Environment

No external credentials are required. The test runs the real installer in an isolated
temporary HOME directory, removes only the installed launcher between runs, and then
verifies the user-visible rerun output plus the final installer-managed artifacts.
