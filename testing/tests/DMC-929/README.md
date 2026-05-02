# DMC-929 automated test

This test verifies the real Claude skill installer flow in `dmtools-ai-docs/install.sh`.
It starts from the ticket precondition where `.claude/skills` already contains both
`dmtools-jira` and `dmtools-github` plus `installed-skills.json`, then reruns the
installer with `DMTOOLS_SKILLS='jira'` and checks the user-visible result.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-929/test_dmc_929.py -q
```

## Environment

No credentials are required. The test runs `dmtools-ai-docs/install.sh` in an isolated
repository sandbox, stubs the release download with local zip assets, and verifies:

- `dmtools-jira` remains installed
- `dmtools-github` is removed
- `.claude/skills/installed-skills.json` is valid JSON and lists only Jira
