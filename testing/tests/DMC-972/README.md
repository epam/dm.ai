# DMC-972

Automates the MCP documentation generation regression for punctuation-heavy integration names by mutating the sandboxed `MCPToolRegistry.java` before regenerating docs.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
PYTHONPATH=. python3 -m pytest testing/tests/DMC-972/test_dmc_972.py -q -r a
```

## Required environment

- Linux or macOS shell with `bash`
- Java available for `./gradlew :dmtools-core:compileJava`
- Network access if Gradle must download dependencies in a fresh environment

## Expected passing output

```text
2 passed
```
