# DMC-1040 automated test

This test reproduces the ticket flow exactly: it runs
`./gradlew :dmtools-core:shadowJar` and then inspects the ticket's expected
artifact directory, `dmtools-core/build/libs/`, for a `dmtools-v*-all.jar`
shadow JAR.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
PYTHONPATH=. python3 -m pytest testing/tests/DMC-1040/test_dmc_1040.py -q
```

## Human-style verification

The automated scenario mirrors a user verifying the build output manually after
running the documented Gradle command:

1. Run `./gradlew :dmtools-core:shadowJar`.
2. Open `dmtools-core/build/libs/`.
3. Confirm whether a `dmtools-v*-all.jar` file is visibly present there.
