# DMC-998 automated test

This test verifies the AI Teammate GitHub Actions workflow still bootstraps
DMTools from the public GitHub Releases asset channel and that the workflow
contract stays aligned with the public installation guide.

It inspects:

1. `.github/workflows/ai-teammate.yml` for the DMTools install command
2. `dmtools-ai-docs/references/installation/README.md` for the public install command

The test passes only when both references use `github.com/epam/dm.ai/releases/.../install.sh`
and the workflow avoids legacy raw-source bootstrap URLs.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-998/test_dmc_998.py -q
```

## Environment

No external credentials are required. The test audits the checked-out repository
contents exactly as a maintainer or contributor would review them.

## Expected passing output

```text
1 passed
```
