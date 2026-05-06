# DMC-1006 automated test

This test validates that the live `standalone-release.yml` workflow on `main`
finishes successfully, publishes its release body and `GITHUB_STEP_SUMMARY`
content, and does not fail because of the historical `dorny/test-reporter` /
`No test report files were found` regression.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
PYTHONPATH=. python3 -m pytest testing/tests/DMC-1006/test_dmc_1006.py -q
```

## Environment

GitHub credentials are required. The live test dispatches the real deprecated
workflow in `epam/dm.ai`, waits for completion, and reads workflow/job/release
output through the GitHub API.

## Expected passing output

```text
1 passed
```
