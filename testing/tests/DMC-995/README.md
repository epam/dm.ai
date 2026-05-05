# DMC-995 automated test

This test audits the latest live stable GitHub Release in `epam/dm.ai` together
with the nearest successful `release.yml` workflow run on `main`. It verifies the
user-visible release copy exposes only supported GitHub Releases install paths for
the DMTools CLI and Agent Skill, and that the matching unified-release workflow
evidence does not reference raw bootstrap URLs or unsupported `dmtools-server`
distribution paths.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
PYTHONPATH=. python3 -m pytest testing/tests/DMC-995/test_dmc_995.py -q
```

## Environment

The live test reads public GitHub release and GitHub Actions metadata for
`epam/dm.ai`. If available, `GH_TOKEN` or `GITHUB_TOKEN` can be set to raise API
rate limits.

## Expected passing output

```text
3 passed
```
