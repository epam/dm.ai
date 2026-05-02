# DMC-953 automated test

This test validates that the installer gives `--skills` precedence over `DMTOOLS_SKILLS` and reports the winning source in the user-visible `Effective skills` log line.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-953/test_dmc_953.py -q
```

## Environment

The test runs against the checked-out `install.sh` script and enables `DMTOOLS_INSTALLER_TEST_MODE=true` through the shared installer skill-selection service so it can exercise the live argument parsing and precedence logic without performing a full installation.
