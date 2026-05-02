# DMC-908

Automated CLI compatibility test for the deprecated `codegenerator` runtime name.

## What this test covers

This test executes the user-facing command from the ticket:

```bash
./dmtools.sh run codegenerator --param1=test
```

It verifies that the user-facing wrapper:

- exits successfully;
- shows a deprecation warning containing `deprecated` and `1.8.0`;
- returns the compatibility shim success response, including `No action was taken and no code artifacts were produced.`; and
- does not fail by treating `codegenerator` as a missing configuration file.

It also executes the underlying `JobRunner` entry point through the testing framework abstraction so the test can prove the compatibility shim remains a visible no-op even when wrapper behavior regresses.

## Install dependencies

No additional dependencies are required. Use the repository-provided Node.js runtime.

## Required environment

- Run from the repository root: `/home/runner/work/dm.ai/dm.ai`
- Java available for `./dmtools.sh`
- A built DMTools fat JAR from the current checkout (`./gradlew :dmtools-core:shadowJar`)
- The test intentionally rejects `~/.dmtools/dmtools.jar`; it only uses an artifact built from this checkout

## Run this test

```bash
node testing/tests/DMC-908/deprecated-codegenerator-run-compatibility.test.js
```
