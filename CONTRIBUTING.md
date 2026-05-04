# Contributing to DMTools

Thank you for your interest in contributing! This document explains how to build, test, and submit changes.

## Getting Started

```bash
# Clone the repository
git clone https://github.com/IstiN/dmtools-cli.git
cd dmtools-cli

# Copy the example env file and fill in your credentials
cp dmtools.env.example dmtools.env
```

## Building

```bash
# Build all modules
./gradlew buildAll

# Build the core CLI fat JAR only
./gradlew :dmtools-core:shadowJar

# Install locally for development testing
./buildInstallLocal.sh
```

## Running Tests

```bash
# Unit tests (fast, no API calls — run these for every change)
./gradlew :dmtools-core:test

# Run a specific test class
./gradlew :dmtools-core:test --tests "ClassName.methodName"
```

> **Note:** Integration tests (`integrationTest` source set) make real API calls and require valid credentials. Do not run them in CI without credentials; they are excluded from the default build.

## Submitting a Pull Request

1. Fork the repository and create a feature branch from `main`.
2. Make your changes following the code style rules in `CLAUDE.md`.
3. Add or update unit tests for any new logic.
4. Verify all tests pass: `./gradlew :dmtools-core:test`
5. Open a pull request with a clear description of what you changed and why.

## Maintainer discoverability checklist

For repository positioning, GitHub About metadata, release-facing keyword alignment, and the split between repo-backed updates and manual GitHub settings, use the canonical playbook in `dmtools-ai-docs/references/workflows/github-repository-discoverability-playbook.md`.

## Adding a New MCP Tool

See the step-by-step guide in `CLAUDE.md` under **"Adding a New MCP Tool"** and the full tool reference in `docs/README-MCP.md`.

## Code Style

- Use package imports — never fully-qualified class names (except for conflict resolution).
- No comments in Russian.
- No documentation comments unless explicitly requested.
- All new code must have unit tests using JUnit 5 + Mockito.

## Reporting Bugs

Please open a [GitHub Issue](https://github.com/IstiN/dmtools-cli/issues) with:
- A minimal reproduction case
- Your DMTools version (`./dmtools.sh --version`)
- Operating system and Java version

## Questions

Use [GitHub Discussions](https://github.com/IstiN/dmtools-cli/discussions) for questions and ideas.
