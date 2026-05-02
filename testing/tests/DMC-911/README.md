# DMC-911 Automation

## What this test covers

This test executes the real `CodeGenerator` compatibility shim through the local
JobRunner CLI entrypoint and verifies that:

- the command returns a non-null compatibility response
- the response matches the documented no-op message exactly
- the traced process does not make outbound `AF_INET` / `AF_INET6` connect or send calls

## Ticket scope

The ticket validates the deprecated `CodeGenerator` shim remains side-effect free
and does not trigger Jira or AI provider traffic.

## Install dependencies

The Python test depends on:

- `pytest`
- Java 17+
- `strace`

Project Python dependencies can be installed with:

```bash
python -m pip install -r testing/requirements.txt
```

## Run this test

```bash
pytest testing/tests/DMC-911/test_dmc_911.py
```

## Environment variables / config

No credentials or external environment variables are required. The test builds
the local fat JAR and runs the CLI entrypoint directly from the repository.

## Expected passing output

The command exits with status `0` and reports:

```text
1 passed
```
