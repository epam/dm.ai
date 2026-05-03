# DMC-966 automated test

This test validates that the installer treats a repeated selective install as a
no-op even when the second run provides the same skills in a different order.
It performs a real install of `jira,github` inside an isolated HOME directory,
reruns `install.sh` with `--skills github,jira`, and verifies the user-visible
logs plus the installed core artifact timestamps.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-966/test_dmc_966.py -q
```

## Environment

No external credentials are required. The test runs the live installer in a
temporary sandboxed HOME directory and observes the generated files under that
isolated installation target.

## Expected passing output

```text
1 passed
```

