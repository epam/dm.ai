# DMC-1028 automated test

This test downloads the live latest-release `install.sh` asset from
`epam/dm.ai`, simulates GitHub API and git tag lookup failures, and verifies
that the installer shows the actionable user-facing error details required by
the ticket.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
PYTHONPATH=. python3 -m pytest testing/tests/DMC-1028/test_dmc_1028.py -q
```

## Environment

The test runs on Linux, downloads the deployed installer from the latest GitHub
release, and uses temporary `curl`/`git` stubs to force the GitHub API failure
path without performing a real installation on the host machine.
