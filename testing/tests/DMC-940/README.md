# DMC-940 automated test

This test verifies that the per-skill documentation validation suite fails with clear, user-visible messages when the entire `dmtools-ai-docs/per-skill-packages/` directory is missing or when a single mandatory child page such as `dmtools-github.md` is missing.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-940/test_dmc_940.py -q
```

## Environment

No environment variables are required. The test copies the current repository into temporary sandboxes, mutates the documentation files there, and runs the existing DMC-916 pytest validation as a user would from the command line.

## Expected passing output

```text
3 passed
```
