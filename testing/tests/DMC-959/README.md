# DMC-959 automated test

This test runs the repository's live `install.sh` entrypoint with a mixed
`--skills=jira,unknown_skill_abc` CLI selection and verifies the user-visible
failure behavior required by the ticket:

1. the installer exits non-zero by default;
2. the console output explicitly identifies `unknown_skill_abc` as invalid;
3. the installer stops before any downstream installation side effects can start.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-959/test_dmc_959.py -q
```

## Environment

No external credentials are required. The test exercises the checked-out
`install.sh` script in `DMTOOLS_INSTALLER_TEST_MODE=true` and stubs the
downstream installation steps so it can validate the live CLI parsing and
error-reporting behavior without performing a real install.
