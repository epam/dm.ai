# DMC-903 automated test

This test validates that the root `README.md` installation section links to both detailed installation documents and that each detailed document links back to `README.md#installation`.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-903/test_dmc_903.py -q
```

## Environment

No environment variables are required. The test reads markdown files directly from this repository checkout.

## Expected passing output

```text
1 passed
```
