# DMC-925 automated test

This test validates the installer skill-selection logs for the ticket scenario in `input/DMC-925/request.md`.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-925/test_dmc_925.py -q
```

## Environment

The test runs against the checked-out `install.sh` script and enables `DMTOOLS_INSTALLER_TEST_MODE=true` so it can exercise the real argument parsing and logging flow without performing a full installation.

## Expected output

```text
.
1 passed
```
