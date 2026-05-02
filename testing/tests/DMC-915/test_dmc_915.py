from pathlib import Path

from testing.components.services.installer_cli_documentation_service import (
    InstallerCliDocumentationService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def test_dmc_915_installation_docs_cover_canonical_skill_selection_and_error_behavior() -> None:
    service = InstallerCliDocumentationService(REPOSITORY_ROOT)

    findings = service.audit_installation_readme()

    assert not findings, service.format_findings(findings)


def test_dmc_915_accepts_complete_installer_cli_documentation(tmp_path: Path) -> None:
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
                "`--skill <name>` is the primary selection form for one focused package.",
                "`--skills=<name,name>` remains an allowed alias when you want multiple packages.",
                "Use `--all-skills` to install every focused package.",
                "Use `--skip-unknown` to downgrade unknown skill names to warnings.",
                "Without `--skip-unknown`, unknown skill names cause a non-zero exit and list invalid names.",
                "",
                "```bash",
                "bash install.sh --skill jira",
                "bash install.sh --skills=jira,github",
                "bash install.sh --all-skills",
                "bash install.sh --skills=jira,unknown --skip-unknown",
                "```",
            )
        ),
        encoding="utf-8",
    )

    service = InstallerCliDocumentationService(repository_root)

    assert service.audit_installation_readme() == []


def test_dmc_915_flags_missing_skip_unknown_and_error_behavior(tmp_path: Path) -> None:
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
                "`--skill <name>` is the canonical way to select one package.",
                "`--skills=<name,name>` is an allowed alias for multi-skill installs.",
                "Use `--all-skills` to install everything.",
            )
        ),
        encoding="utf-8",
    )

    service = InstallerCliDocumentationService(repository_root)

    findings = service.audit_installation_readme()

    assert (
        "The installer usage section does not mention the `--skip-unknown` flag."
        in findings
    )
    assert any("invalid-skill behavior" in finding for finding in findings)
