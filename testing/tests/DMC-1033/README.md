# DMC-1033 automated test

This test runs the real `ReportGenerator` job against a local GitHub API harness
that returns a temporary `429 Too Many Requests` response for the first commits
request and then succeeds on retry. It verifies the operator-visible logging
behavior around the wait/retry flow and confirms the report still completes after
the delayed retry.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
PYTHONPATH=. python3 -m pytest testing/tests/DMC-1033/test_dmc_1033.py -q
```

## Environment

The test builds the local DMTools CLI with `buildInstallLocal.sh`, runs it with a
temporary HOME directory, and points the GitHub source-code integration at a local
stub server. No external GitHub credentials are required.

## Expected passing output

```text
1 passed
```
