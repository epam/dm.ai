# DMC-948 automated test

This test runs the live DMTools installer from the raw `main` branch in an isolated
temporary home directory, performs a selective `jira,confluence` install in the
default `~/.dmtools` location, and verifies that the installer's internal
post-install `dmtools` command validation succeeds without surfacing metadata
assertion warnings.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-948/test_dmc_948.py -q
```

## Environment

The test requires outbound network access to:

- `raw.githubusercontent.com` for the live installer script
- `github.com` for the downloaded release assets

It uses a temp directory for `HOME` so the installer writes to an isolated
`~/.dmtools` tree without modifying the user environment.

## Expected output when the test passes

- `pytest` reports `1 passed`
- The installer output includes `Installing DMTools CLI...`
- The installer output includes `Effective skills: jira, confluence (source: env)`
- The installer output includes `DMTools CLI installed successfully!`
- The installer output does not include `Installation completed but dmtools command test failed`
- The isolated `~/.dmtools` directory contains `installed-skills.json` and `endpoints.json`
