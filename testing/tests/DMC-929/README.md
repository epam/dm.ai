# DMC-929 automated test

This test verifies that re-running the root installer with a narrower skill selection rewrites the
persisted installer-managed skill metadata so stale GitHub selection state is removed and Jira
remains active.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-929/test_dmc_929.py -q
```

## Environment

No credentials are required. The test runs the repository root `install.sh` in an isolated sandbox,
stubs network and system-install side effects, persists the generated `dmtools-installer.env`
between two installer executions, and verifies the second run removes the stale GitHub selection
from the installer-managed config.
