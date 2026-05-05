# DMC-997 automated test

This test audits the live `main`-branch workflow output definitions for the
deprecated standalone/server packaging flows in `epam/dm.ai`. It inspects only
published output surfaces (release bodies and GitHub step summaries), then
verifies that they include a deprecated/internal-only notice and do not include
customer-facing install guidance or legacy Flutter/Swagger/localhost references.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
PYTHONPATH=. python3 -m pytest testing/tests/DMC-997/test_dmc_997.py -q
```

## Environment

No credentials are required. The live test fetches the public workflow files
from `https://raw.githubusercontent.com/epam/dm.ai/main/...`.

## Expected passing output

```text
4 passed
```
