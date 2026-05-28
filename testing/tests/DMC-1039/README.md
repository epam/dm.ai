# DMC-1039 automated test

This test dispatches the live `standalone-release-auto.yml` workflow on `main`
and verifies that `Capture compatibility artifact metadata` completes without
the missing-shadow-JAR failure while downstream release steps continue.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
PYTHONPATH=. python3 -m pytest testing/tests/DMC-1039/test_dmc_1039.py -q
```

## Environment

GitHub credentials are required. The live test dispatches a real workflow run in
`epam/dm.ai`, waits for completion, and inspects the resulting Actions job,
visible steps, logs, published release, and step summary through the GitHub API.
