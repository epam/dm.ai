# DMC-989 automated test

This test validates the repository-backed social preview contract. It checks that
the repository contains a committed SVG social preview source, that the SVG
expresses the approved DMTools composition with accessible contrast, and that the
discoverability playbook documents the required PNG export sizes.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-989/test_dmc_989.py -q
```

## Environment

No environment variables are required. The test inspects files directly from this
repository checkout.

## Expected passing output

```text
6 passed
```
