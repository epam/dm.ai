from pathlib import Path

from testing.components.services.documentation_consistency_service import (
    DocumentationConsistencyService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def test_dmc_898_documentation_references_stay_consistent() -> None:
    service = DocumentationConsistencyService(REPOSITORY_ROOT)

    teammate_table, jobs_table = service.canonical_reference_tables()
    assert teammate_table == jobs_table, service.format_table_mismatch(
        "teammate-configs.md",
        teammate_table,
        "jobs/README.md",
        jobs_table,
    )

    valid_names = service.valid_job_names()

    invalid_json_name_findings = {
        path.name: invalid_names
        for path in service.secondary_paths
        if (invalid_names := service.invalid_json_name_values(path, valid_names))
    }
    assert not invalid_json_name_findings, service.format_invalid_name_findings(
        invalid_json_name_findings
    )

    suspicious_identifier_findings = {
        path.name: suspicious_tokens
        for path in service.secondary_paths
        if (suspicious_tokens := service.suspicious_job_identifiers(path, valid_names))
    }
    assert not suspicious_identifier_findings, service.format_invalid_name_findings(
        suspicious_identifier_findings
    )
