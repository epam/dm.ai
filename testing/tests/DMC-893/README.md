# DMC-893

Repository audit test for documented agent/job identifiers and active status.

## Covered checks

- audited jobs are registered in `JobRunner.createJobInstance()`
- audited jobs are listed in `JobRunner.getJobs()`
- `teammate-configs.md` rows and linked example configs use exact accepted identifiers
- markdown docs use exact accepted identifiers in JSON `name` fields
- active audited jobs are not marked as deprecated

## Run

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest -v testing.tests.DMC-893.test_agent_names_and_status
```

