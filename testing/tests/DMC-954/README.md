# DMC-954 automated test

This test exercises the repository `install.sh` main flow with `DMTOOLS_SKILLS=' jira '`
and verifies that the user-visible `Effective skills` log line is normalized to a single
skill with no leading or trailing separators.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-954/test_dmc_954.py -q
```

## Environment

No external credentials are required. The test runs the repository `install.sh` locally,
isolates installation side effects in a temporary directory, and stubs download and shell
mutation steps so the assertions stay focused on the real user-visible installer output.

## Expected output when the test passes

- `pytest` reports `1 passed`
- The installer output includes `Installing DMTools CLI...`
- The installer output includes exactly `Effective skills: jira (source: env)`
- The installer output does not include `Effective skills: jira,` or `Effective skills: ,jira`
