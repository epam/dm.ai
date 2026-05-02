# DMC-926 automated test

This test covers the installer's backward-compatible full-install flow when the skill selection is
missing, empty, or explicitly set to `all`.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-926/test_dmc_926.py -q
```

## Environment

No credentials are required. The test runs the repository `install.sh` locally, stubs download and
system-mutation steps through the shared installer test service, and inspects the persisted
installer-managed skill configuration from the isolated test run.

## Expected passing output

```text
3 passed
```
