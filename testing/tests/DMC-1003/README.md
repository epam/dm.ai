# DMC-1003 automated test

This test audits the live `main` branch standalone workflow output surfaces in
`epam/dm.ai` and verifies that the deprecated/internal-only release body and
step summary copy no longer exposes installer script commands or the old public
installation-path header.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
PYTHONPATH=. python3 -m pytest testing/tests/DMC-1003/test_dmc_1003.py -q
```

## Environment

No credentials are required. The live test fetches the public workflow files
from `https://raw.githubusercontent.com/epam/dm.ai/main/...`.
