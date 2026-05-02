# DMC-949 automated test

This test exercises the repository's live `install.sh` main flow for a selective
`jira` install while the target installation directory is write-protected. It
stubs the unrelated download and shell-update steps so the run deterministically
reaches the real machine-readable metadata write and verifies that the installer
fails with a non-zero exit code instead of reporting a false success.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-949/test_dmc_949.py -q
```

## Environment

No external credentials are required. The test runs `install.sh` with
`DMTOOLS_INSTALLER_TEST_MODE=true`, `DMTOOLS_SKILLS=jira`, and a temporary
write-protected installation directory.

## Expected output when the test passes

- `pytest` reports `1 passed`
- The installer output includes `Installing DMTools CLI...`
- The installer output includes `Effective skills: jira (source: env)`
- The installer exits with a non-zero status after reporting a permission error
  for `installed-skills.json`
- The installer does not print the metadata success message or continue into
  later success-path steps
