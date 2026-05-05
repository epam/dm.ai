# DMC-996 automated test

This test triggers the live `beta-release.yml` workflow on `main`, waits for the
publication flow to finish, then verifies two user-visible surfaces:

1. the published prerelease page exposes the maintained DMTools CLI and DMTools
   Agent Skill assets with GitHub Releases install guidance; and
2. the publication-job `GITHUB_STEP_SUMMARY` mirrors that supported packaging
   model while keeping beta/prerelease framing.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
PYTHONPATH=. python3 -m pytest testing/tests/DMC-996/test_dmc_996.py -q
```

## Environment

The live workflow dispatch requires repository access through `GH_TOKEN` or
`GITHUB_TOKEN`. In CI those credentials are injected automatically; when running
locally, use a token that can dispatch workflows and read Actions logs/releases
for `epam/dm.ai`.

## Expected passing output

```text
.                                                                        [100%]
1 passed
```
