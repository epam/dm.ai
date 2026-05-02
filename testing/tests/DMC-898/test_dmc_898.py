from pathlib import Path

from testing.components.factories.documentation_consistency_checker_factory import (
    create_documentation_consistency_checker,
)
from testing.core.interfaces.documentation_consistency_checker import (
    DocumentationConsistencyChecker,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def test_dmc_898_documentation_references_stay_consistent() -> None:
    checker: DocumentationConsistencyChecker = create_documentation_consistency_checker(
        REPOSITORY_ROOT
    )

    teammate_table, jobs_table = checker.canonical_reference_tables()
    assert _canonical_reference_signatures(
        teammate_table
    ) == _canonical_reference_signatures(jobs_table), _format_canonical_reference_mismatch(
        "teammate-configs.md",
        teammate_table,
        "jobs/README.md",
        jobs_table,
    )

    secondary_document_failure_message = _secondary_document_failure_message(checker)
    assert secondary_document_failure_message is None, secondary_document_failure_message


def test_secondary_document_failure_message_combines_all_findings() -> None:
    checker = _StubDocumentationConsistencyChecker()

    failure_message = _secondary_document_failure_message(checker)

    assert failure_message == "invalid names\n\nsummary drift"


def _secondary_document_failure_message(
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
        failure_sections.append(
            checker.format_invalid_name_findings(invalid_job_name_findings)
        )
    if summary_drift_findings:
        failure_sections.append(checker.format_summary_findings(summary_drift_findings))

    if not failure_sections:
        return None

    return "\n\n".join(failure_sections)


def _canonical_reference_signatures(
    references,
) -> list[tuple[tuple[str, ...], str]]:
    return sorted(
        (
            tuple(sorted(reference.all_names)),
            reference.summary,
        )
        for reference in references
    )


def _format_canonical_reference_mismatch(
    left_label: str,
    left_references,
    right_label: str,
    right_references,
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
        f"{left_label}:\n" + "\n".join(left_lines) + "\n\n"
        f"{right_label}:\n" + "\n".join(right_lines)
    )


class _StubDocumentationConsistencyChecker(DocumentationConsistencyChecker):
    @property
    def canonical_paths(self) -> tuple[Path, Path]:
        return Path("teammate-configs.md"), Path("jobs/README.md")

    @property
    def secondary_paths(self) -> tuple[Path, Path]:
        return Path("javascript-agents.md"), Path("cli-integration.md")

    def canonical_reference_tables(self):
        raise NotImplementedError

    def reference_by_name(self) -> dict[str, object]:
        return {}

    def valid_job_names(self) -> set[str]:
        return {"Teammate"}

    def invalid_job_names(self, path: Path, valid_names: set[str]) -> list[str]:
        if path.name == "javascript-agents.md":
            return ["StoryProcessor"]
        return []

    def inconsistent_secondary_summaries(
        self,
        path: Path,
        references_by_name: dict[str, object],
    ) -> list[str]:
        if path.name == "cli-integration.md":
            return ["Teammate summary drift"]
        return []

    @staticmethod
    def format_table_mismatch(*args, **kwargs) -> str:
        raise NotImplementedError

    @staticmethod
    def format_invalid_name_findings(findings: dict[str, list[str]]) -> str:
        assert findings == {"javascript-agents.md": ["StoryProcessor"]}
        return "invalid names"

    @staticmethod
    def format_summary_findings(findings: dict[str, list[str]]) -> str:
        assert findings == {"cli-integration.md": ["Teammate summary drift"]}
        return "summary drift"
