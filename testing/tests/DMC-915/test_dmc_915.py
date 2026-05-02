from pathlib import Path

from testing.components.services.installer_cli_documentation_service import (
    InstallerCliDocumentationService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def test_dmc_915_installation_docs_match_supported_skill_selection_syntax() -> None:
    service = InstallerCliDocumentationService(REPOSITORY_ROOT)

    findings = service.audit_installation_readme()

    assert not findings, service.format_findings(findings)


def test_dmc_915_accepts_ticket_required_installer_docs(tmp_path: Path) -> None:
    repository_root = tmp_path / "repo"
    installation_path = (
        repository_root / "dmtools-ai-docs/references/installation/README.md"
    )
    installation_path.parent.mkdir(parents=True)
    installation_path.write_text(
        "\n".join(
            (
                "# DMtools Installation Guide",
                "",
                "## Install Only the Skills You Need",
                "",
                "```bash",
                "curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/skill-install.sh | bash -s -- --skill jira",
                "curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/skill-install.sh | bash -s -- --skills=jira,github",
                "curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/skill-install.sh | bash -s -- --all-skills",
                "curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/skill-install.sh | bash -s -- --skills=jira,unknown --skip-unknown",
                "```",
                "",
                "Unknown skill names cause a non-zero exit and list the invalid names.",
                "When `--skip-unknown` is provided, invalid skill names are downgraded to warnings.",
            )
        ),
        encoding="utf-8",
    )

    service = InstallerCliDocumentationService(repository_root)

    assert service.audit_installation_readme() == []


def test_dmc_915_flags_missing_ticket_required_installer_docs(
    tmp_path: Path,
) -> None:
    repository_root = tmp_path / "repo"
    installation_path = (
        repository_root / "dmtools-ai-docs/references/installation/README.md"
    )
    installation_path.parent.mkdir(parents=True)
    installation_path.write_text(
        "\n".join(
            (
                "# DMtools Installation Guide",
                "",
                "## Install Only the Skills You Need",
                "",
                "```bash",
                "bash install.sh --skills jira",
                "bash install.sh --skills jira,github",
                "```",
                "",
                "Set `DMTOOLS_SKILLS=jira,github` for non-interactive installs.",
            )
        ),
        encoding="utf-8",
    )

    service = InstallerCliDocumentationService(repository_root)

    findings = service.audit_installation_readme()

    assert any("`--skill <name>`" in finding for finding in findings)
    assert any("`--skills=<name,name>`" in finding for finding in findings)
    assert any("`--all-skills`" in finding for finding in findings)
    assert any("`--skip-unknown`" in finding for finding in findings)
    assert any(
        "non-zero exit and list the invalid names" in finding for finding in findings
    )
    assert any(
        "`--skip-unknown` downgrades invalid skill names to warnings"
        in finding
        for finding in findings
    )


def test_dmc_915_format_findings_handles_missing_section(tmp_path: Path) -> None:
    repository_root = tmp_path / "repo"
    installation_path = (
        repository_root / "dmtools-ai-docs/references/installation/README.md"
    )
    installation_path.parent.mkdir(parents=True)
    installation_path.write_text(
        "# DMtools Installation Guide\n\n## Quick Installation\n\nUse the automatic installer.\n",
        encoding="utf-8",
    )

    service = InstallerCliDocumentationService(repository_root)

    findings = service.audit_installation_readme()
    formatted = service.format_findings(findings)

    assert findings == [
        "Missing 'Install Only the Skills You Need' section in "
        "dmtools-ai-docs/references/installation/README.md."
    ]
    assert "Observed 'Install Only the Skills You Need' section:" in formatted
    assert "  (section missing)" in formatted
    assert "Expected the installation guide to document the DMC-915 installer" in formatted


def test_dmc_915_keeps_code_block_comments_inside_extracted_section(
    tmp_path: Path,
) -> None:
    repository_root = tmp_path / "repo"
    installation_path = (
        repository_root / "dmtools-ai-docs/references/installation/README.md"
    )
    installation_path.parent.mkdir(parents=True)
    installation_path.write_text(
        "\n".join(
            (
                "# DMtools Installation Guide",
                "",
                "## Install Only the Skills You Need",
                "",
                "```bash",
                "# Install only Jira",
                "bash install.sh --skills jira",
                "```",
                "",
                "```powershell",
                "$env:DMTOOLS_SKILLS = \"jira,github\"",
                "```",
                "",
                "### Manual Focused Package Installation",
                "",
                "Keep reading here.",
            )
        ),
        encoding="utf-8",
    )

    service = InstallerCliDocumentationService(repository_root)

    section = service._extract_named_sections(
        installation_path,
        service.SECTION_HEADING,
    )[0]

    assert "# Install only Jira" in section
    assert "$env:DMTOOLS_SKILLS = \"jira,github\"" in section
