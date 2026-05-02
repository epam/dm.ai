# DMC-944 automated test

This regression test exercises the real installer argument parser for the
`./install.sh --skills='jira' --strict` flow while stubbing download and system
mutation steps. It verifies the user-visible CLI output, successful exit status,
and the generated installer skill configuration.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-944/test_dmc_944.py -q
```

## Environment

No credentials are required. The test runs the repository `install.sh` locally
in `DMTOOLS_INSTALLER_TEST_MODE=true`, captures stdout/stderr, and inspects the
installer-managed environment file that a user installation would generate.

## Expected passing output

```text
1 passed
```
