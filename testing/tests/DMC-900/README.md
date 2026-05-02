# DMC-900 automated test

This test verifies that active installation guidance in `README.md` and `dmtools-ai-docs/**/*.md` no longer contains legacy repository references (`IstiN/dmtools`, `IstiN/dmtools-cli`, raw GitHub bootstrap URLs) and does not ship version-pinned installer examples where the latest-release flow should be used.

The deprecated `Upgrading from legacy installs` section is the only allowed place for legacy-reference matches.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-900/test_dmc_900.py -q
```

## Environment

No environment variables are required. The test reads documentation files directly from this repository checkout.

## Expected passing output

```text
3 passed
```
