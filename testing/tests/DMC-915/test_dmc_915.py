from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Mapping, Sequence

from testing.components.services.installer_cli_documentation_service import (
    InstallerCliDocumentationService,
)
from testing.core.interfaces.installer_script import InstallerScript
from testing.core.models.process_execution_result import ProcessExecutionResult


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


@dataclass
class FakeInstallerScript(InstallerScript):
    responses: Mapping[tuple[str, ...], ProcessExecutionResult]

    def run_main(
        self,
        args: Sequence[str] = (),
        extra_env: Mapping[str, str] | None = None,
        post_script: str = "",
    ) -> ProcessExecutionResult:
        del extra_env, post_script
        key = tuple(args)
        response = self.responses.get(key)
        if response is not None:
            return response
        return ProcessExecutionResult(
            args=tuple(args),
            cwd=Path("/tmp/fake-installer"),
            returncode=1,
            stdout="",
            stderr=f"Unhandled fake installer command: {' '.join(args)}",
        )


def _result(
    *args: str,
    returncode: int,
    stdout: str = "",
    stderr: str = "",
) -> ProcessExecutionResult:
    return ProcessExecutionResult(
        args=args,
        cwd=Path("/tmp/fake-installer"),
        returncode=returncode,
        stdout=stdout,
        stderr=stderr,
    )


def _ticket_compliant_installer() -> FakeInstallerScript:
    return FakeInstallerScript(
        responses={
            ("--skill", "jira"): _result(
                "--skill",
                "jira",
                returncode=0,
                stdout="Effective skills: jira (source: cli)\n",
            ),
            ("--skills=jira,github",): _result(
                "--skills=jira,github",
                returncode=0,
                stdout="Effective skills: jira,github (source: cli)\n",
            ),
            ("--all-skills",): _result(
                "--all-skills",
                returncode=0,
                stdout="Installing all skills (source: cli)\n",
            ),
            ("--skills=jira,unknown",): _result(
                "--skills=jira,unknown",
                returncode=1,
                stderr="Error: Unknown skills: unknown. Use --skip-unknown to continue.\n",
            ),
            ("--skills=jira,unknown", "--skip-unknown"): _result(
                "--skills=jira,unknown",
                "--skip-unknown",
                returncode=0,
                stdout=(
                    "Warning: Skipping unknown skills: unknown\n"
                    "Effective skills: jira (source: cli)\n"
                ),
            ),
            ("--skill", "unknown"): _result(
                "--skill",
                "unknown",
                returncode=1,
                stderr=(
                    "Error: No valid skills selected. Unknown skills: unknown. "
                    "Allowed skills: jira,github\n"
                ),
            ),
        }
    )


def _runtime_without_ticket_flags() -> FakeInstallerScript:
    return FakeInstallerScript(
        responses={
            ("--skill", "jira"): _result(
                "--skill",
                "jira",
                returncode=1,
                stderr="Error: Unknown installer option: --skill\n",
            ),
            ("--skills=jira,github",): _result(
                "--skills=jira,github",
                returncode=0,
                stdout="Effective skills: jira,github (source: cli)\n",
            ),
            ("--all-skills",): _result(
                "--all-skills",
                returncode=1,
                stderr="Error: Unknown installer option: --all-skills\n",
            ),
            ("--skills=jira,unknown",): _result(
                "--skills=jira,unknown",
                returncode=0,
                stdout=(
                    "Warning: Skipping unknown skills: unknown\n"
                    "Effective skills: jira (source: cli)\n"
                ),
            ),
            ("--skills=jira,unknown", "--skip-unknown"): _result(
                "--skills=jira,unknown",
                "--skip-unknown",
                returncode=1,
                stderr="Error: Unknown installer option: --skip-unknown\n",
            ),
            ("--skill", "unknown"): _result(
                "--skill",
                "unknown",
                returncode=1,
                stderr="Error: Unknown installer option: --skill\n",
            ),
        }
    )


def test_dmc_915_installation_docs_and_runtime_match_ticket_contract() -> None:
    service = InstallerCliDocumentationService(REPOSITORY_ROOT)

    findings = service.audit_installation_readme()

    assert not findings, service.format_findings(findings)


def test_dmc_915_accepts_ticket_required_docs_when_runtime_supports_same_contract(
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

    service = InstallerCliDocumentationService(
        repository_root,
        installer_script=_ticket_compliant_installer(),
    )

    assert service.audit_installation_readme() == []


def test_dmc_915_flags_docs_that_describe_commands_the_runtime_rejects(
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

    service = InstallerCliDocumentationService(
        repository_root,
        installer_script=_runtime_without_ticket_flags(),
    )

    findings = service.audit_installation_readme()

    assert (
        "The installer runtime does not accept `--skill <name>` as a working "
        "skill-selection command."
    ) in findings
    assert (
        "The installer runtime does not accept the `--all-skills` flag."
    ) in findings
    assert (
        "The installer runtime does not fail for mixed valid+invalid skill "
        "selections without `--skip-unknown` while listing invalid names."
    ) in findings
    assert (
        "The installer runtime does not prove that `--skip-unknown` changes a "
        "mixed valid+invalid selection from a non-zero failure into a "
        "warning-backed success."
    ) in findings


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

    service = InstallerCliDocumentationService(
        repository_root,
        installer_script=_ticket_compliant_installer(),
    )

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

    service = InstallerCliDocumentationService(
        repository_root,
        installer_script=_ticket_compliant_installer(),
    )

    findings = service.audit_installation_readme()
    formatted = service.format_findings(findings)

    assert findings == [
        "Missing 'Install Only the Skills You Need' section in "
        "dmtools-ai-docs/references/installation/README.md."
    ]
    assert "Observed 'Install Only the Skills You Need' section:" in formatted
    assert "  (section missing)" in formatted
    assert "Observed installer behavior:" in formatted
    assert "bash dmtools-ai-docs/install.sh --skill jira -> exit 0;" in formatted


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

    service = InstallerCliDocumentationService(
        repository_root,
        installer_script=_ticket_compliant_installer(),
    )

    section = service._extract_named_sections(
        installation_path,
        service.SECTION_HEADING,
    )[0]

    assert "# Install only Jira" in section
    assert "$env:DMTOOLS_SKILLS = \"jira,github\"" in section
