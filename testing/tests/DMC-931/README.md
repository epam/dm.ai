# DMC-931 automated test

This test validates the installer's user-visible failure path when `DMTOOLS_SKILLS`
contains only unknown values. It executes the real `main` flow from `install.sh`
with downstream installation steps stubbed, so the test exercises the live skill
validation logic without performing downloads or filesystem changes.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-931/test_dmc_931.py -q
```

## Environment

No external credentials are required. The test runs the repository's `install.sh`
script in `DMTOOLS_INSTALLER_TEST_MODE=true` and injects `DMTOOLS_SKILLS=invalid1,invalid2`.

## Expected passing output

```text
1 passed
```
