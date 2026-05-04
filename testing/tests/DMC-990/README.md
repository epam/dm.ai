# DMC-990 automated test

This test runs the repository discoverability validation flow that maintainers use,
then audits the repo-backed discoverability metadata for the extra consistency
checks required by the ticket: canonical metadata presence, social-preview
guidance, GitHub topic-count limits, and playbook file-reference integrity.

## Install dependencies

```bash
python3 -m pip install -r testing/requirements.txt
```

## Run this test

```bash
PYTHONPATH=. python3 -m pytest testing/tests/DMC-990/test_dmc_990.py -q
```

## Environment

No external credentials are required. The live test executes the existing
`GitHubRepositoryDiscoverabilityTest` Gradle flow against the checked-out
repository, then inspects the versioned metadata and playbook files directly.

## Expected passing output

```text
4 passed
```
