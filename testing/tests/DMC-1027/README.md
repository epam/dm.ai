# DMC-1027 automated test

This regression test executes the real installer `main()` flow for both
`install.sh` and `install` while stubbing external side effects. It verifies the
installer resolves the newest stable CLI release from the paginated GitHub
releases API, reports that version in the installer logs, and never falls back
to `/releases/latest` or git tag lookup when a valid `vX.Y.Z` release is already
present.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-1027/test_dmc_1027.py -q
```

## Environment

No credentials are required. The test runs the repository installers locally in
`DMTOOLS_INSTALLER_TEST_MODE=true`, injects mocked GitHub API responses through a
shared testing service, and validates the user-visible stdout/stderr emitted by
the installer flow.

