# DMC-1032 automated test

This test runs the real `ReportGenerator` on `main` against a deterministic
GitHub-like API harness and verifies that already collected report data is
preserved when a later GitHub metric is rate limited, waits, and retries.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
PYTHONPATH=. python3 -m pytest testing/tests/DMC-1032/test_dmc_1032.py -q -s
```

## Environment

The test builds or reuses the local `dmtools` shadow JAR, starts an in-process
mock GitHub API server, and copies the preserved report artifacts into a
temporary system directory so the observation paths remain inspectable after the
pytest run completes without leaving generated files under `testing/`.
