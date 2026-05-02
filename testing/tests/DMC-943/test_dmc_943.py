from __future__ import annotations

import shlex
import site
from pathlib import Path

from testing.components.services.per_skill_page_audit_service import (
    PerSkillPageAuditService,
)
from testing.core.utils.repo_sandbox import RepoSandbox


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
CONFLUENCE_PAGE_RELATIVE_PATH = "dmtools-ai-docs/per-skill-packages/dmtools-confluence.md"
EXPECTED_JAVA_PACKAGE = "com.github.istin.dmtools.atlassian.confluence"
WRONG_JAVA_PACKAGE = "com.wrong.package"
EXPECTED_SLASH_COMMAND = "/dmtools-confluence"
WRONG_SLASH_COMMAND = "/dmtools-jira"
VALIDATION_COMMAND = (
    f"PYTHONPATH={shlex.quote(site.getusersitepackages())}${{PYTHONPATH:+:$PYTHONPATH}} "
    "python3 -m pytest testing/tests/DMC-916/test_dmc_916.py -q"
)


def test_dmc_943_validation_reports_wrong_confluence_identifiers() -> None:
    live_audit = PerSkillPageAuditService(REPOSITORY_ROOT)
    live_page = (REPOSITORY_ROOT / CONFLUENCE_PAGE_RELATIVE_PATH).read_text(encoding="utf-8")

    assert live_audit.audit() == []
    assert "## Package / Artifact" in live_page
    assert f"Java package: `{EXPECTED_JAVA_PACKAGE}`" in live_page, (
        "The live Confluence page should visibly show the canonical Java package marker "
        "to a documentation reader."
    )
    assert f"Focused slash command: `{EXPECTED_SLASH_COMMAND}`" in live_page, (
        "The live Confluence page should visibly show the canonical focused slash command "
        "to a documentation reader."
    )
    assert f"Slash command entrypoint: `{EXPECTED_SLASH_COMMAND}`" in live_page, (
        "The live Confluence page should repeat the same slash-command identifier in the "
        "Endpoints / Config keys section."
    )

    sandbox = RepoSandbox(REPOSITORY_ROOT)
    try:
        baseline_result = sandbox.run(VALIDATION_COMMAND)
        assert baseline_result.returncode == 0, baseline_result.combined_output
        assert "1 passed" in baseline_result.stdout, baseline_result.stdout

        sandbox.write_text(
            CONFLUENCE_PAGE_RELATIVE_PATH,
            _corrupt_confluence_page(sandbox.read_text(CONFLUENCE_PAGE_RELATIVE_PATH)),
        )

        corrupted_page = sandbox.read_text(CONFLUENCE_PAGE_RELATIVE_PATH)
        assert WRONG_JAVA_PACKAGE in corrupted_page
        assert WRONG_SLASH_COMMAND in corrupted_page
        assert EXPECTED_JAVA_PACKAGE not in corrupted_page
        assert EXPECTED_SLASH_COMMAND not in corrupted_page

        failure_result = sandbox.run(VALIDATION_COMMAND)
        combined_output = failure_result.combined_output

        assert failure_result.returncode != 0, (
            "The documentation validation suite should fail after the Confluence page "
            "technical identifiers are corrupted."
        )
        assert (
            "AssertionError: Per-skill documentation pages do not satisfy the required template."
            in combined_output
        )
        assert (
            "dmtools-ai-docs/per-skill-packages/dmtools-confluence.md does not include "
            f"expected Java package identifier {EXPECTED_JAVA_PACKAGE!r}."
        ) in combined_output
        assert (
            "dmtools-ai-docs/per-skill-packages/dmtools-confluence.md does not include "
            f"expected slash-command identifier {EXPECTED_SLASH_COMMAND!r}."
        ) in combined_output
    finally:
        sandbox.cleanup()


def _corrupt_confluence_page(page_markdown: str) -> str:
    corrupted = _replace_all(page_markdown, EXPECTED_JAVA_PACKAGE, WRONG_JAVA_PACKAGE)
    return _replace_all(corrupted, EXPECTED_SLASH_COMMAND, WRONG_SLASH_COMMAND)


def _replace_all(text: str, expected: str, replacement: str) -> str:
    if expected not in text:
        raise AssertionError(f"Expected text not found: {expected!r}")

    updated = text.replace(expected, replacement)
    if expected in updated:
        raise AssertionError(f"Expected replacement to remove all {expected!r} occurrences.")

    return updated
