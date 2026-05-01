from pathlib import Path

from testing.components.services.legacy_install_reference_service import (
    LegacyInstallReferenceService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def test_dmc_900_active_install_guidance_has_no_legacy_install_references() -> None:
    service = LegacyInstallReferenceService(REPOSITORY_ROOT)

    findings = service.all_findings()

    assert not findings, service.format_findings(findings)


def test_dmc_900_scans_only_primary_install_docs(tmp_path: Path) -> None:
    repository_root = tmp_path / "repo"
    unrelated_docs = repository_root / "dmtools-ai-docs" / "guides"
    installation_docs = repository_root / "dmtools-ai-docs" / "references" / "installation"

    unrelated_docs.mkdir(parents=True)
    installation_docs.mkdir(parents=True)

    (repository_root / "README.md").write_text("# Overview\n", encoding="utf-8")
    (unrelated_docs / "README.md").write_text(
        "# Setup\n"
        "curl -fsSL https://raw.githubusercontent.com/epam/dm.ai/main/dmtools-ai-docs/setup-dmtools.sh | bash\n",
        encoding="utf-8",
    )
    (installation_docs / "README.md").write_text(
        "# Installation\n"
        "curl -fsSL https://raw.githubusercontent.com/epam/dm.ai/main/dmtools-ai-docs/setup-dmtools.sh | bash\n",
        encoding="utf-8",
    )

    service = LegacyInstallReferenceService(repository_root)

    findings = service.forbidden_legacy_reference_findings()

    assert [(finding.file_path, finding.line_number) for finding in findings] == [
        ("dmtools-ai-docs/references/installation/README.md", 2)
    ]
