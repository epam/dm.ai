# DMC-1003 automated test

This test validates the live GitHub Actions outputs for the deprecated
standalone release workflows in `epam/dm.ai`. It inspects the generated release
body and the emitted `GITHUB_STEP_SUMMARY` content from executed workflow runs
instead of parsing workflow source YAML.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
PYTHONPATH=. python3 -m pytest testing/tests/DMC-1003/test_dmc_1003.py -q
```

## Environment

GitHub credentials are required. The live test dispatches or inspects real
workflow runs in `epam/dm.ai` and reads release/job output surfaces through the
GitHub API.
