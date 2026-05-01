from pathlib import Path

from testing.components.services.upgrade_guidance_service import UpgradeGuidanceService


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def test_dmc_902_upgrade_guidance_is_complete() -> None:
    service = UpgradeGuidanceService(REPOSITORY_ROOT)

    audits = service.audit_upgrade_guidance_sections()

    assert audits, (
        "Expected at least one 'Upgrading from legacy installs' section in README.md, "
        "dmtools-ai-docs/references/installation/README.md, or "
        "dmtools-ai-docs/references/installation/troubleshooting.md."
    )

    incomplete_audits = [audit for audit in audits if audit.missing_requirements]
    assert not incomplete_audits, service.format_missing_requirements(audits)
