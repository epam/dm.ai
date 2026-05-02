# DMC-957 automated test

This test exercises the repository `install.sh` runtime with `--all-skills` and verifies
that the live installer accepts the flag, surfaces the all-skills banner, and reports the
full canonical skill list in the user-visible terminal output.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-957/test_dmc_957.py -q
```

## Environment

No external credentials are required. The test runs the repository `install.sh` locally in
installer test mode, stubs external download/shell-mutation operations, and inspects the
visible CLI output plus the generated installer skill config.
