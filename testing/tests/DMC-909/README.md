# DMC-909 automated test

This test verifies that the legacy `CodeGenerator` implementation files have been removed from the repository and that `dmtools-core` still builds successfully after the cleanup.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-909/test_dmc_909.py -q
```

## Environment

No environment variables are required. The test runs from the checked-out repository and executes the Gradle build in an isolated temporary copy of the workspace.

## Expected passing output

```text
2 passed
```
