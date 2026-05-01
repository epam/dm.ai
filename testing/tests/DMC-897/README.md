# DMC-897

Automates the documentation sync scenario for skill docs after manual edits to `teammate-configs.md` and related files.

## Install dependencies

No extra project dependencies are required beyond Python 3, Java, and the repository's existing Gradle wrapper. `pytest` must be available in the execution environment.

## Run this test

```bash
PYTHONPATH=. python3 -m pytest testing/tests/DMC-897/test_dmc_897.py -q
```

## Required environment

- Linux or macOS shell with `bash`
- Java available for Gradle builds triggered by the documentation scripts
- Network access if Gradle needs to download dependencies on a fresh environment

## Expected passing output

```text
1 passed
```
