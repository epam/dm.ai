# DMC-918 automated test

This test audits the publication-gate evidence for the ticket-configured documentation pull request in `epam/dm.ai` (currently PR #71). The strict audit still checks duplicate-check evidence, the PR body line, documentation link-validation/smoke logs, and maintainer plus technical-writer sign-off, but the live acceptance is that the LLM records the human-style verification result in the ticket instead of requiring an external human to update historical PR/process artifacts.

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
8 passed
```
