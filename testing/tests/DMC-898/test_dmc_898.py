from pathlib import Path

from testing.components.factories.documentation_consistency_checker_factory import (
    create_documentation_consistency_checker,
)
from testing.core.interfaces.documentation_consistency_checker import (
    DocumentationConsistencyChecker,
)
from testing.core.models.job_reference import JobReference
from testing.core.utils.documentation_consistency_assertions import (
    documentation_consistency_failure_message,
    secondary_document_failure_message,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def test_dmc_898_documentation_references_stay_consistent() -> None:
    checker: DocumentationConsistencyChecker = create_documentation_consistency_checker(
        REPOSITORY_ROOT
    )

    failure_message = documentation_consistency_failure_message(checker)
    assert failure_message is None, failure_message


def test_secondary_document_failure_message_combines_all_findings() -> None:
    checker = _StubDocumentationConsistencyChecker()

    failure_message = secondary_document_failure_message(checker)

    assert failure_message == "invalid names\n\nsummary drift"


def test_documentation_consistency_failure_message_reports_canonical_table_mismatch() -> None:
    checker = _CanonicalMismatchDocumentationConsistencyChecker()

    failure_message = documentation_consistency_failure_message(checker)

    assert failure_message == (
        "Canonical job names/summaries differ between teammate-configs.md and "
        "jobs/README.md.\n"
        "teammate-configs.md:\n"
        "1. names=['Teammate'] | summary=Canonical teammate summary\n\n"
        "jobs/README.md:\n"
        "1. names=['Teammate'] | summary=Drifted teammate summary"
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


class _CanonicalMismatchDocumentationConsistencyChecker(DocumentationConsistencyChecker):
    @property
    def canonical_paths(self) -> tuple[Path, Path]:
        return Path("teammate-configs.md"), Path("jobs/README.md")

    @property
    def secondary_paths(self) -> tuple[Path, Path]:
        return Path("javascript-agents.md"), Path("cli-integration.md")

    def canonical_reference_tables(
        self,
    ) -> tuple[list[JobReference], list[JobReference]]:
        teammate_reference = JobReference(
            job="Teammate",
            summary="Canonical teammate summary",
            accepted_names=(),
            example="example.json",
        )
        drifted_reference = JobReference(
            job="Teammate",
            summary="Drifted teammate summary",
            accepted_names=(),
            example="example.json",
        )
        return [teammate_reference], [drifted_reference]

    def reference_by_name(self) -> dict[str, JobReference]:
        return {}

    def valid_job_names(self) -> set[str]:
        raise AssertionError("secondary findings should not be evaluated after table mismatch")

    def invalid_job_names(self, path: Path, valid_names: set[str]) -> list[str]:
        raise AssertionError("secondary findings should not be evaluated after table mismatch")

    def inconsistent_secondary_summaries(
        self,
        path: Path,
        references_by_name: dict[str, JobReference],
    ) -> list[str]:
        raise AssertionError("secondary findings should not be evaluated after table mismatch")

    @staticmethod
    def format_table_mismatch(*args, **kwargs) -> str:
        raise NotImplementedError

    @staticmethod
    def format_invalid_name_findings(findings: dict[str, list[str]]) -> str:
        raise NotImplementedError

    @staticmethod
    def format_summary_findings(findings: dict[str, list[str]]) -> str:
        raise NotImplementedError
