from pathlib import Path

from testing.components.services.product_identity_audit_service import (
    ProductIdentityAuditService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def test_dmc_981_core_docs_use_consistent_orchestrator_identity() -> None:
    service = ProductIdentityAuditService(REPOSITORY_ROOT)

    audits = service.audit()

    assert {audit.label for audit in audits} == {
        "root README lead paragraph",
        "documentation hub lead paragraph",
        "skill guide lead paragraph",
    }

    failing_audits = [audit for audit in audits if audit.issues]
    assert not failing_audits, service.format_findings(failing_audits)


def test_dmc_981_accepts_frontmatter_docs_when_lead_identity_is_consistent(
    tmp_path: Path,
) -> None:
    repository_root = tmp_path / "repo"
    (repository_root / "dmtools-ai-docs").mkdir(parents=True)

    (repository_root / "README.md").write_text(
        "# DMTools\n\n"
        "Enterprise dark-factory orchestrator for automating delivery workflows.\n",
        encoding="utf-8",
    )
    (repository_root / "dmtools-ai-docs/README.md").write_text(
        "---\n"
        "name: dmtools\n"
        "description: Example metadata that should not affect the visible lead paragraph.\n"
        "---\n\n"
        "# DMtools Development Assistant\n\n"
        "DMTools is an enterprise dark-factory orchestrator for reusable AI-assisted delivery workflows.\n",
        encoding="utf-8",
    )
    (repository_root / "dmtools-ai-docs/SKILL.md").write_text(
        "# DMtools Agent Skill\n\n"
        "DMTools is an enterprise dark-factory orchestrator for project-level agent workflows.\n",
        encoding="utf-8",
    )

    service = ProductIdentityAuditService(repository_root)

    audits = service.audit()

    assert [audit.issues for audit in audits] == [(), (), ()]


def test_dmc_981_reports_conflicting_toolkit_identity_language(tmp_path: Path) -> None:
    repository_root = tmp_path / "repo"
    (repository_root / "dmtools-ai-docs").mkdir(parents=True)

    (repository_root / "README.md").write_text(
        "# DMTools\n\n"
        "Enterprise dark-factory orchestrator for automating delivery workflows.\n",
        encoding="utf-8",
    )
    (repository_root / "dmtools-ai-docs/README.md").write_text(
        "# DMtools Development Assistant\n\n"
        "Comprehensive knowledge base for DMtools - an AI-powered development toolkit.\n",
        encoding="utf-8",
    )
    (repository_root / "dmtools-ai-docs/SKILL.md").write_text(
        "# DMtools Agent Skill\n\n"
        "Universal AI assistant skill for DMtools.\n",
        encoding="utf-8",
    )

    service = ProductIdentityAuditService(repository_root)

    failing_audits = [audit for audit in service.audit() if audit.issues]

    assert [audit.label for audit in failing_audits] == [
        "documentation hub lead paragraph",
        "skill guide lead paragraph",
    ]
    assert failing_audits[0].issues == (
        "missing orchestrator wording",
        "missing enterprise dark-factory positioning",
        "contains conflicting toolkit wording",
    )
    assert failing_audits[1].issues == (
        "missing orchestrator wording",
        "missing enterprise dark-factory positioning",
    )
