# DMC-918 automated test

This test audits the publication-gate evidence for the ticket-configured documentation pull request in `epam/dm.ai` (currently PR #71). It verifies that the ticket comments record the duplicate-check query details, the PR body contains the required duplicate-check line, the PR exposes successful GitHub Actions job logs for link-validation and documentation-smoke results, and the review trail includes both maintainer and technical-writer sign-off.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-918/test_dmc_918.py -q
```

## Environment

No required environment variables. The test reads `input/DMC-918/comments.md`, `testing/tests/DMC-918/config.yaml`, and the public GitHub metadata for `epam/dm.ai`.

If available, `GH_TOKEN` or `GITHUB_TOKEN` can be set to increase GitHub API rate limits.

## Expected passing output

```text
3 passed
```
