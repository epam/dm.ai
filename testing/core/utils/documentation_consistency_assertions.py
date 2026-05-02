from __future__ import annotations

from typing import Iterable

from testing.core.interfaces.documentation_consistency_checker import (
    DocumentationConsistencyChecker,
)
from testing.core.models.job_reference import JobReference


def documentation_consistency_failure_message(
    checker: DocumentationConsistencyChecker,
) -> str | None:
    teammate_table, jobs_table = checker.canonical_reference_tables()
    if canonical_reference_signatures(teammate_table) != canonical_reference_signatures(
        jobs_table
    ):
        left_path, right_path = checker.canonical_paths
        return format_canonical_reference_mismatch(
            _display_label(left_path),
            teammate_table,
            _display_label(right_path),
            jobs_table,
        )

    return secondary_document_failure_message(checker)


def secondary_document_failure_message(
    checker: DocumentationConsistencyChecker,
) -> str | None:
    valid_names = checker.valid_job_names()
    references_by_name = checker.reference_by_name()

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
        failure_sections.append(checker.format_invalid_name_findings(invalid_job_name_findings))
    if summary_drift_findings:
        failure_sections.append(checker.format_summary_findings(summary_drift_findings))

    if not failure_sections:
        return None

    return "\n\n".join(failure_sections)


def canonical_reference_signatures(
    references: Iterable[JobReference],
) -> list[tuple[tuple[str, ...], str]]:
    return sorted(
        (
            tuple(sorted(reference.all_names)),
            reference.summary,
        )
        for reference in references
    )


def format_canonical_reference_mismatch(
    left_label: str,
    left_references: Iterable[JobReference],
    right_label: str,
    right_references: Iterable[JobReference],
) -> str:
    left_lines = [
        f"{index + 1}. names={sorted(reference.all_names)} | summary={reference.summary}"
        for index, reference in enumerate(left_references)
    ]
    right_lines = [
        f"{index + 1}. names={sorted(reference.all_names)} | summary={reference.summary}"
        for index, reference in enumerate(right_references)
    ]
    return (
        f"Canonical job names/summaries differ between {left_label} and {right_label}.\n"
        f"{left_label}:\n"
        + "\n".join(left_lines)
        + "\n\n"
        f"{right_label}:\n"
        + "\n".join(right_lines)
    )


def _display_label(path) -> str:
    if path.parent.name:
        return str(path.parent / path.name)
    return path.name
