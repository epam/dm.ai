# DMC-979 automated test

This test validates that the root `README.md` no longer frames DMTools as a legacy OAuth web app or Swagger-driven hosted service, and that the visible entry-point narrative stays CLI-first.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-979/test_dmc_979.py -q
```

## Environment

No environment variables are required. The test reads the checked-out repository documentation directly.

## Expected passing output

```text
2 passed
```
