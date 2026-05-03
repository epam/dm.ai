# DMC-967 automated test

This regression test validates the live installer flow for an existing `jira`
installation upgraded to `jira,github`. It checks the user-visible second-run
output and the installed artifact timestamps to ensure the new skill can be
added without redownloading or rewriting the shared core CLI artifacts.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-967/test_dmc_967.py -q
```

## Environment

No external credentials are required. The test runs the repository `install.sh`
in an isolated temporary HOME directory, starting with `jira` installed and then
re-running the live installer with `jira,github`.

## Expected passing output

```text
1 passed
```

