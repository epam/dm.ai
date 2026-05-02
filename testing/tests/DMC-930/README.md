# DMC-930 automated test

This test runs the live DMTools installer from the raw `main` branch in an isolated
temporary home directory, performs a selective `jira,confluence` install, and
verifies that the install produces machine-readable metadata files for skill state
and endpoint discovery.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-930/test_dmc_930.py -q
```

## Environment

The test requires outbound network access to:

- `raw.githubusercontent.com` for the live installer script
- `github.com` for the downloaded release assets

It uses a temp directory for `HOME`, `DMTOOLS_INSTALL_DIR`, and `DMTOOLS_BIN_DIR`
so it does not modify the user environment.
