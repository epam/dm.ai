# DMC-958 automated test

This test verifies that the root `install.sh` installer accepts the `--skills=<name,name>` alias, exits successfully, and shows the expected user-visible `Effective skills` line.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-958/test_dmc_958.py -q
```

## Environment

The test runs against the checked-out root `install.sh` script and enables `DMTOOLS_INSTALLER_TEST_MODE=true` through the shared installer script service so it can exercise the live installer flow without performing a real installation.
