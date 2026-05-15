# DMC-1030 automated test

This ticket harness runs the live `ReportGenerator` regression checks that cover
GitHub rate-limit recovery for pull-request data collection. It verifies two
observable outcomes from the maintainer flow:

1. the interrupted pull-request data-source request is retried and the same
   report run continues to completion; and
2. `X-RateLimit-Reset` is honored instead of falling back to the default retry
   cap.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
PYTHONPATH=. python3 -m pytest testing/tests/DMC-1030/test_dmc_1030.py -q
```

## Environment

No external credentials are required. The test executes targeted JUnit methods
from `com.github.istin.dmtools.reporting.ReportGeneratorTest` against the
current repository checkout.
