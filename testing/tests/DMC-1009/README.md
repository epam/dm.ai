# DMC-1009 automated test

This test dispatches the live deprecated standalone workflows on `main` and
verifies that the `Build deprecated compatibility artifact` step completes
without blocking publication of the release body and visible
`GITHUB_STEP_SUMMARY` outputs.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
PYTHONPATH=. python3 -m pytest testing/tests/DMC-1009/test_dmc_1009.py -q -s
```

## Environment

GitHub credentials are required. The live test dispatches real workflow runs in
`epam/dm.ai`, waits for completion, and inspects the resulting job steps, logs,
release bodies, and visible step summaries through the GitHub API.
