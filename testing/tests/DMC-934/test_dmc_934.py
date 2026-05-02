from __future__ import annotations

import re
from pathlib import Path

from testing.components.services.documentation_consistency_service import (
    DocumentationConsistencyService,
)
from testing.core.interfaces.documentation_consistency_checker import (
    DocumentationConsistencyChecker,
)
from testing.core.models.job_reference import JobReference
from testing.core.utils.markdown_job_reference_parser import extract_markdown_paragraphs


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
TEXT_TOKEN_PATTERN = re.compile(r"[A-Za-z0-9]+")


def test_dmc_934_documentation_audit_handles_whitespace_only_jsrunner_variations(
    tmp_path: Path,
) -> None:
    live_checker = DocumentationConsistencyService(REPOSITORY_ROOT)
    jsrunner_reference = live_checker.reference_by_name()["JSRunner"]

    live_jsrunner_summary = _summary_paragraph(
        live_checker.javascript_agents_path,
        "The Recommended Approach: JSRunner",
    )
    assert _normalize_text(jsrunner_reference.summary) in _normalize_text(
        live_jsrunner_summary.text
    ), (
        "Expected the live JSRunner documentation to display the canonical summary "
        f"for users. Found: {live_jsrunner_summary.text!r}"
    )
    assert _consistency_failure_message(live_checker) is None

    whitespace_fixture_root = _create_whitespace_variation_fixture(
        tmp_path,
        live_checker.reference_by_name(),
    )
    whitespace_checker = DocumentationConsistencyService(whitespace_fixture_root)

    assert _consistency_failure_message(whitespace_checker) is None

    whitespace_jsrunner_summary = _summary_paragraph(
        whitespace_checker.javascript_agents_path,
        "The Recommended Approach: JSRunner",
    )
    assert _normalize_text(jsrunner_reference.summary) in _normalize_text(
        whitespace_jsrunner_summary.text
    ), (
        "Expected newline-only formatting changes in the JSRunner guidance paragraph "
        "to preserve the user-visible summary."
    )


def _create_whitespace_variation_fixture(
    tmp_path: Path,
    references_by_name: dict[str, JobReference],
) -> Path:
    repository_root = tmp_path / "repo"
    agents_dir = repository_root / "dmtools-ai-docs/references/agents"
    jobs_dir = repository_root / "dmtools-ai-docs/references/jobs"
    agents_dir.mkdir(parents=True)
    jobs_dir.mkdir(parents=True)

    teammate_summary = references_by_name["Teammate"].summary
    jsrunner_summary = references_by_name["JSRunner"].summary

    (agents_dir / "teammate-configs.md").write_text(
        "\n".join(
            [
                "# AI Teammate Configuration Guide",
                "",
                "## Common job reference",
                "",
                "| Job | Summary | Accepted `name` | Example |",
                "|-----|---------|-----------------|---------|",
                f"| `Teammate` | {teammate_summary} | `Teammate` | [story_development.json](../../../agents/story_development.json) |",
                f"| `JSRunner` | {jsrunner_summary}   | `JSRunner` | [run_all.json](../../../agents/js/unit-tests/run_all.json) |",
            ]
        ),
        encoding="utf-8",
    )
    (jobs_dir / "README.md").write_text(
        "\n".join(
            [
                "# Jobs Reference",
                "",
                "| Job | Summary | Accepted `name` | Example |",
                "|-----|---------|-----------------|---------|",
                f"| `Teammate` | {teammate_summary} | `Teammate` | [story_development.json](../../../agents/story_development.json) |",
                f"| `JSRunner` | {jsrunner_summary} | `JSRunner` | [run_all.json](../../../agents/js/unit-tests/run_all.json) |",
            ]
        ),
        encoding="utf-8",
    )
    (agents_dir / "javascript-agents.md").write_text(
        "\n".join(
            [
                "# JavaScript Agents",
                "",
                "## Testing and Debugging Agents",
                "",
                "### The Recommended Approach: JSRunner",
                "",
                "JSRunner executes one GraalJS script with DMtools context for isolated automation,   ",
                "debugging, and JS agent testing.",
            ]
        ),
        encoding="utf-8",
    )
    (agents_dir / "cli-integration.md").write_text(
        "\n".join(
            [
                "# CLI Agent Integration with Teammate",
                "",
                "## Overview",
                "",
                f"Teammate {teammate_summary[0].lower() + teammate_summary[1:]}",
            ]
        ),
        encoding="utf-8",
    )

    return repository_root


def _consistency_failure_message(
    checker: DocumentationConsistencyChecker,
) -> str | None:
    teammate_table, jobs_table = checker.canonical_reference_tables()
    if _canonical_reference_signatures(teammate_table) != _canonical_reference_signatures(
        jobs_table
    ):
        return checker.format_table_mismatch(
            checker.canonical_paths[0].name,
            teammate_table,
            checker.canonical_paths[1].name,
            jobs_table,
        )

    references_by_name = checker.reference_by_name()
    valid_names = checker.valid_job_names()

    invalid_job_name_findings = {
        path.name: invalid_names
        for path in checker.secondary_paths
        if (invalid_names := checker.invalid_job_names(path, valid_names))
    }
    summary_drift_findings = {
        path.name: summary_mismatches
        for path in checker.secondary_paths
        if (
            summary_mismatches := checker.inconsistent_secondary_summaries(
                path,
                references_by_name,
            )
        )
    }

    failure_sections: list[str] = []
    if invalid_job_name_findings:
        failure_sections.append(
            checker.format_invalid_name_findings(invalid_job_name_findings)
        )
    if summary_drift_findings:
        failure_sections.append(checker.format_summary_findings(summary_drift_findings))

    return "\n\n".join(failure_sections) if failure_sections else None


def _canonical_reference_signatures(
    references: list[JobReference],
) -> list[tuple[tuple[str, ...], str]]:
    return sorted(
        (tuple(sorted(reference.all_names)), reference.summary) for reference in references
    )


def _summary_paragraph(path: Path, heading: str):
    for paragraph in extract_markdown_paragraphs(path):
        if paragraph.heading == heading:
            return paragraph
    raise AssertionError(f"Could not find summary paragraph under heading {heading!r}.")


def _normalize_text(text: str) -> str:
    return " ".join(TEXT_TOKEN_PATTERN.findall(text.lower()))
