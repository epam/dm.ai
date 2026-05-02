# DMC-899 automated test

This test validates that the repository README exposes the required latest-release installation entry points for the EPAM `dm.ai` project.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-899/test_dmc_899.py -q
```

## Environment

No environment variables are required. The test reads `README.md` directly from this repository checkout.

## Expected passing output

```text
1 passed
```
