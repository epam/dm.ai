# DMC-1007 automated test

This test dispatches the live `standalone-release-auto.yml` workflow on `main`
and verifies the run completes successfully without the removed-project Gradle
resolution error for `:dmtools-automation:test`. It also confirms the workflow
publishes the expected release body, visible `GITHUB_STEP_SUMMARY` text, and
the deprecated compatibility JAR asset on the resulting GitHub Release.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
PYTHONPATH=. python3 -m pytest testing/tests/DMC-1007/test_dmc_1007.py -q
```

## Environment

GitHub credentials are required. The test dispatches a real workflow run in
`epam/dm.ai`, waits for completion through the GitHub API, and inspects the
resulting job log plus published release surfaces.
