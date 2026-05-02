# DMC-908

Automated CLI compatibility test for the deprecated `codegenerator` runtime name.

## What this test covers

This test executes the user-facing command from the ticket:

```bash
./dmtools.sh run codegenerator --param1=test
```

It verifies that the command:

- exits successfully;
- shows a deprecation warning containing `deprecated` and `1.8.0`;
- returns the compatibility shim success response; and
- does not fail by treating `codegenerator` as a missing configuration file.

## Install dependencies

No additional dependencies are required. Use the repository-provided Node.js runtime.

## Required environment

- Run from the repository root: `/home/runner/work/dm.ai/dm.ai`
- Java available for `./dmtools.sh`
- A built DMTools JAR available via the repository build or local install

## Run this test

```bash
node testing/tests/DMC-908/deprecated-codegenerator-run-compatibility.test.js
```
