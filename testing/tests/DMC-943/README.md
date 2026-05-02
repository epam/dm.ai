# DMC-943 automated test

This test validates that the per-skill documentation validation suite fails when the live `dmtools-confluence` page is corrupted with the wrong Java package identifier and slash-command endpoint.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-943/test_dmc_943.py -q
```

## Environment

No environment variables are required. The test verifies the checked-out repository content, then reruns the existing `DMC-916` validation in an isolated repository copy so the working tree is not modified.

## Expected passing output

```text
1 passed
```
