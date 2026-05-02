# DMC-909 automated test

This test verifies that the legacy `CodeGenerator` implementation has been replaced by the documented compatibility shim, that the compatibility source/tests remain in place, and that `dmtools-core` still builds successfully.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-909/test_dmc_909.py -q
```

## Environment

No environment variables are required. The test creates an isolated `RepoSandbox` copy of the checked-out repository under a temporary `.repo-sandboxes/` directory, bootstraps local git metadata inside that sandbox so repo-aware unit tests still behave normally, and runs the Gradle build there.

## Expected passing output

```text
2 passed
```
