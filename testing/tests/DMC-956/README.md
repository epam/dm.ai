# DMC-956 automated test

This test validates that the skill installer accepts `--skill jira`, reports the
selected skill in the user-visible output, and continues into the installation
flow without surfacing an unknown-option error.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-956/test_dmc_956.py -q
```

## Environment

No external credentials are required. The test runs the checked-out
`dmtools-ai-docs/install.sh` entry point with the live argument parsing logic and
stubs downstream installation side effects so it can safely verify the deployed
CLI contract.

## Expected passing output

```text
1 passed
```
