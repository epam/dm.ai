# DMC-950 automated test

This test runs the live DMTools installer from the raw `main` branch in an isolated
temporary home directory, requests a selective install with one valid skill and one
unknown skill, and verifies the installer preserves metadata for the valid skill
without leaking the invalid one into generated artifacts.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-950/test_dmc_950.py -q
```

## Environment

The test requires outbound network access to:

- `raw.githubusercontent.com` for the live installer script
- `github.com` for the downloaded release assets

It uses a temp directory for `HOME`, `DMTOOLS_INSTALL_DIR`, and `DMTOOLS_BIN_DIR`
so it does not modify the user environment.

## Expected output when the test passes

- `pytest` reports `1 passed`
- The installer output warns about `non_existent_skill_xyz`
- The installer output still reports `Effective skills: jira (source: env)`
- `installed-skills.json` contains only `jira` plus version metadata
- `endpoints.json` contains only `/dmtools/jira`
