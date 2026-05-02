from pathlib import Path

from testing.components.services.codegenerator_migration_guidance_service import (
    CodeGeneratorMigrationGuidanceService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def test_dmc_910_codegenerator_migration_guidance_is_visible_and_actionable() -> None:
    service = CodeGeneratorMigrationGuidanceService(REPOSITORY_ROOT)

    audits = service.audit()

    assert {audit.label for audit in audits} == {
        "root README deprecated compatibility shims section",
        "jobs reference deprecation notice",
    }

    incomplete_audits = [audit for audit in audits if audit.missing_requirements]
    assert not incomplete_audits, service.format_missing_requirements(audits)
