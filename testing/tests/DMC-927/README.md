# DMC-927 automated test

This test exercises the real installer argument parsing and skill-selection flow for invalid
skill handling while stubbing download and system-mutation steps.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-927/test_dmc_927.py -q
```

## Environment

No credentials are required. The test executes the repository `install.sh` locally, isolates
installer output in a temporary directory, and stubs Java/download verification steps so the
assertions stay focused on user-visible invalid-skill behavior.
