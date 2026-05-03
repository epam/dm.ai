# DMC-973

Automates the regression where `scripts/generate-mcp-docs.sh` must recreate
`dmtools-ai-docs/references/mcp-tools/` after the directory is deleted, while
keeping the documentation sync outputs user-visible and current.

## Install dependencies

```bash
python3 -m pip install --user -r testing/requirements.txt
```

## Run this test

```bash
PYTHONPATH=. python3 -m pytest testing/tests/DMC-973/test_dmc_973.py -q
```

## Required environment

- Linux or macOS shell with `bash`
- Java available for Gradle builds triggered by the documentation scripts
- Network access if Gradle needs to download dependencies on a fresh environment

## Expected passing output

```text
2 passed
```
