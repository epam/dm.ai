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
    assert teammate_table == jobs_table, checker.format_table_mismatch(
        "teammate-configs.md",
        teammate_table,
        "jobs/README.md",
        jobs_table,
    )

    valid_names = checker.valid_job_names()
    references_by_name = checker.reference_by_name()

    invalid_job_name_findings = {
        path.name: invalid_names
        for path in checker.secondary_paths
        if (invalid_names := checker.invalid_job_names(path, valid_names))
    }
    assert not invalid_job_name_findings, checker.format_invalid_name_findings(
        invalid_job_name_findings
    )

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
    assert not summary_drift_findings, checker.format_summary_findings(
        summary_drift_findings
    )
