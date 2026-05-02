# DMC-964 automated test

This test verifies the real selective skill installer flow in `dmtools-ai-docs/install.sh`
when a rerun mixes an already installed valid skill with an unknown skill name.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-964/test_dmc_964.py -q
```

## Environment

No credentials are required. The test runs the installer in an isolated repository
sandbox, seeds `.claude/skills/dmtools-jira` plus `installed-skills.json`, then reruns
`dmtools-ai-docs/install.sh` with `DMTOOLS_SKILLS='jira,unknown_skill_xyz'` and verifies:

- the installer exits with an error that names `unknown_skill_xyz`
- `.claude/skills/dmtools-jira` still exists with the same files
- `.claude/skills/installed-skills.json` still lists only `jira`
- no artifacts are created for `unknown_skill_xyz`
