# DMC-894

Repository-level automation for the `teammate-configs.md` "example usage" links.

## Install dependencies

No additional dependencies are required. The test uses the Node.js standard library only.

## Run this test

```bash
node --test testing/tests/DMC-894/test_example_usage_links.test.js
```

## Environment variables / config

No environment variables are required. The test reads repository files from the checked-out workspace.

## Expected passing output

The command exits with code `0` and prints a passing Node test result similar to:

```text
ok 1 - DMC-894 validates teammate example usage links
```
