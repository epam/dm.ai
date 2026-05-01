from pathlib import Path

from testing.components.services.legacy_install_reference_service import (
    LegacyInstallReferenceService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def test_dmc_900_active_install_guidance_has_no_legacy_install_references() -> None:
    service = LegacyInstallReferenceService(REPOSITORY_ROOT)

    findings = service.all_findings()

    assert not findings, service.format_findings(findings)
