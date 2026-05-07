# DMC-1014 automated test

This test dispatches the live deprecated standalone workflows on `main` and
verifies that the `Upload deprecated compatibility JAR` step completes without
preventing publication of the release body, visible `GITHUB_STEP_SUMMARY`
content, and deprecated compatibility JAR asset.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
PYTHONPATH=. python3 -m pytest testing/tests/DMC-1014/test_dmc_1014.py -q -s
```

## Environment

GitHub credentials are required. The live test dispatches real workflow runs in
`epam/dm.ai`, waits for completion, and inspects the resulting job steps, logs,
release bodies, visible step summaries, and published release assets through
the GitHub API.
