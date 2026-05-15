# DMC-1026 automated test

This test dispatches the live `windows-git-bash-installer-check.yml` workflow on
`main` and verifies the documented Git Bash installer path on a real Windows
runner. It checks the visible installer output for latest-release resolution,
ensures the historical GitHub API lookup failure does not appear, and requires
the post-install wrapper validation step to succeed for a user-observable
"installation successful" outcome.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
PYTHONPATH=. python3 -m pytest testing/tests/DMC-1026/test_dmc_1026.py -q
```

## Environment

GitHub credentials are required. The test dispatches a real workflow run in
`epam/dm.ai`, waits for completion through the GitHub Actions API, and inspects
the resulting Windows Git Bash job log.
