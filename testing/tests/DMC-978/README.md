# DMC-978 automated test

This test validates the root `README.md` opening as a user-facing product contract.
It verifies that the hero and opening sections present DMTools as an enterprise
dark-factory orchestrator, keep the CLI install path visible, explain who the
product is for, and surface the primary usage paths for MCP tooling and jobs.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-978/test_dmc_978.py -q
```

## Environment

No environment variables are required. The test reads `README.md` directly from
this repository checkout, and the ticket metadata is defined in `config.yaml`.

## Expected passing output

```text
1 passed
```
