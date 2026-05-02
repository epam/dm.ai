# DMC-962 automated test

This test verifies the real Claude skill installer flow in `dmtools-ai-docs/install.sh`
for a mixed selective reinstall. It starts from the ticket precondition where
`.claude/skills` already contains `dmtools-jira` and `dmtools-github` plus
`installed-skills.json`, then reruns the installer with `DMTOOLS_SKILLS='jira,confluence'`
and checks the user-visible output plus the resulting artifacts and metadata.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-962/test_dmc_962.py -q
```

## Environment

No credentials are required. The test runs `dmtools-ai-docs/install.sh` in an isolated
repository sandbox, stubs the release downloads with local zip assets, and verifies:

- `dmtools-jira` remains installed
- `dmtools-github` is removed
- `dmtools-confluence` is added
- `.claude/skills/installed-skills.json` lists only Jira and Confluence
