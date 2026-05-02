from pathlib import Path

from testing.components.services.documentation_consistency_service import (
    DocumentationConsistencyService,
)
from testing.core.models.job_reference import JobReference


def test_summary_drift_is_reported_for_paraphrased_overview_text(tmp_path: Path) -> None:
    document_path = tmp_path / "secondary.md"
    document_path.write_text(
        "# Secondary Doc\n\n"
        "## Overview\n\n"
        "Teammate supports integration with external CLI agents for advanced code generation tasks.\n",
        encoding="utf-8",
    )

    checker = DocumentationConsistencyService(tmp_path)
    reference = JobReference(
        job="Teammate",
        summary="Orchestrates ticket context, AI instructions, and optional CLI or JS hooks for end-to-end workflow automation.",
        accepted_names=(),
        example="example.json",
    )

    findings = checker.inconsistent_secondary_summaries(
        document_path,
        {"Teammate": reference},
    )

    assert len(findings) == 1
    assert "Teammate summary drift" in findings[0]
    assert "under 'Overview'" in findings[0]
    assert "Teammate supports integration with external CLI agents" in findings[0]


def test_exact_summary_is_accepted_in_summary_context(tmp_path: Path) -> None:
    document_path = tmp_path / "secondary.md"
    document_path.write_text(
        "# Secondary Doc\n\n"
        "## Overview\n\n"
        "Teammate orchestrates ticket context, AI instructions, and optional CLI or JS hooks for end-to-end workflow automation.\n",
        encoding="utf-8",
    )

    checker = DocumentationConsistencyService(tmp_path)
    reference = JobReference(
        job="Teammate",
        summary="Orchestrates ticket context, AI instructions, and optional CLI or JS hooks for end-to-end workflow automation.",
        accepted_names=(),
        example="example.json",
    )

    findings = checker.inconsistent_secondary_summaries(
        document_path,
        {"Teammate": reference},
    )

    assert findings == []


def test_runtime_notes_do_not_count_as_summary_prose(tmp_path: Path) -> None:
    document_path = tmp_path / "secondary.md"
    document_path.write_text(
        "# Secondary Doc\n\n"
        "## Runtime Behavior\n\n"
        "Teammate processes cliPrompt and appends it to each CLI command.\n",
        encoding="utf-8",
    )

    checker = DocumentationConsistencyService(tmp_path)
    reference = JobReference(
        job="Teammate",
        summary="Orchestrates ticket context, AI instructions, and optional CLI or JS hooks for end-to-end workflow automation.",
        accepted_names=(),
        example="example.json",
    )

    findings = checker.inconsistent_secondary_summaries(
        document_path,
        {"Teammate": reference},
    )

    assert findings == []
