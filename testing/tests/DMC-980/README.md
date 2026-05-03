# DMC-980 automated test

This test validates that the README navigation surfaces point users to maintained repository documentation hubs for installation, configuration, integrations, MCP tools, teammate workflows, and AI skill packages.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-980/test_dmc_980.py -q
```

## Environment

No environment variables are required. The test reads `README.md` and repository documentation paths from this checkout.

## Expected passing output

```text
3 passed
```
