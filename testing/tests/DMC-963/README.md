# DMC-963 automated test

This test verifies the Claude skill installer flow in `dmtools-ai-docs/install.sh`
when `DMTOOLS_SKILLS` is explicitly set to an empty string.

It starts from the ticket precondition where `.claude/skills` already contains an
installed skill plus `installed-skills.json` and `endpoints.json`, reruns the
installer with an empty skill selection, and checks that:

- no `dmtools*` skill folders remain under `.claude/skills`
- `installed-skills.json` is present and contains no installed skills or commands
- `endpoints.json` is present and contains no active `/dmtools*` command entries

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
python3 -m pytest testing/tests/DMC-963/test_dmc_963.py -q
```

## Environment

No credentials are required. The test runs `dmtools-ai-docs/install.sh` in an
isolated repository sandbox, seeds the `.claude/skills` precondition, and stubs
release downloads with local zip assets so the ticket scenario executes against
the checked-out installer implementation.
