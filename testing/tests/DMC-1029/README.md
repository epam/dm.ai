# DMC-1029 automated test

This test validates the live `windows-git-bash-installer-check.yml` workflow on
`main`. It confirms that the manually dispatchable Windows Git Bash validation
workflow exists, runs on a Windows runner with Bash, installs the CLI from the
latest GitHub release, and finishes successfully.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
PYTHONPATH=. python3 -m pytest testing/tests/DMC-1029/test_dmc_1029.py -q
```

## Environment

The live workflow dispatch requires repository access through `GH_TOKEN` or
`GITHUB_TOKEN`. In CI those credentials are injected automatically; when
running locally, use a token that can dispatch workflows and read Actions logs
for `epam/dm.ai`.

## Expected passing output

```text
1 passed
```
