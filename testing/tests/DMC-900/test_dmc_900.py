from pathlib import Path

from testing.components.services.legacy_install_reference_service import (
    LegacyInstallReferenceService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def test_dmc_900_active_install_guidance_has_no_legacy_install_references() -> None:
    service = LegacyInstallReferenceService(REPOSITORY_ROOT)

    findings = service.all_findings()

    assert not findings, service.format_findings(findings)


def test_dmc_900_scans_install_guidance_anywhere_in_dmtools_docs(tmp_path: Path) -> None:
    repository_root = tmp_path / "repo"
    docs_root = repository_root / "dmtools-ai-docs"
    install_readme = docs_root / "README.md"
    changelog = docs_root / "CHANGELOG.md"

    docs_root.mkdir(parents=True)

    (repository_root / "README.md").write_text("# Overview\n", encoding="utf-8")
    install_readme.write_text(
        "# DMtools Skill\n"
        "## Quick Install\n"
        "curl -fsSL https://raw.githubusercontent.com/epam/dm.ai/main/dmtools-ai-docs/setup-dmtools.sh | bash\n",
        encoding="utf-8",
    )
    changelog.write_text(
        "# Changelog\n"
        "Supports raw URLs: https://raw.githubusercontent.com/owner/repo/branch/path/to/file.md\n",
        encoding="utf-8",
    )

    service = LegacyInstallReferenceService(repository_root)

    findings = service.forbidden_legacy_reference_findings()

    assert [(finding.file_path, finding.line_number) for finding in findings] == [
        ("dmtools-ai-docs/README.md", 3)
    ]


def test_dmc_900_allows_version_pinned_examples_in_deprecated_legacy_section(
    tmp_path: Path,
) -> None:
    repository_root = tmp_path / "repo"
    (repository_root / "dmtools-ai-docs").mkdir(parents=True)

    (repository_root / "README.md").write_text(
        "# Overview\n"
        "## Upgrading from legacy installs\n"
        "curl -fsSL https://github.com/owner/repo/releases/download/v1.2.3/install.sh | bash -s -- v1.2.3\n"
        "## Quick Install\n"
        "curl -fsSL https://github.com/owner/repo/releases/download/v2.0.0/install.sh | bash -s -- v2.0.0\n",
        encoding="utf-8",
    )

    service = LegacyInstallReferenceService(repository_root)

    findings = service.version_pinned_install_findings()

    assert [(finding.file_path, finding.line_number) for finding in findings] == [
        ("README.md", 5)
    ]
