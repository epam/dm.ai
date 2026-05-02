# DMC-933 automated test

This test validates that a disposable summary change in the canonical Teammate references makes the audited `DMC-898` pytest rerun fail on the resulting `cli-integration.md` drift that a user would need to review.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-933/test_dmc_933.py -q
```

## Environment

No environment variables are required. The test uses an isolated repository copy and does not modify the working tree.

## Expected passing output

```text
1 passed
```
