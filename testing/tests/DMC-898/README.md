# DMC-898 automated test

This test validates that the canonical job-reference entries stay aligned across the mirrored documentation files and that the secondary agent docs only use valid job names in their examples.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-898/test_dmc_898.py -q
```

## Environment

No environment variables are required. The test reads documentation files directly from this repository checkout.

## Expected passing output

```text
1 passed
```
