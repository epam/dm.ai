from __future__ import annotations

import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.services.skill_installer_service import SkillInstallerService  # noqa: E402
from testing.core.utils.repo_sandbox import CommandResult  # noqa: E402


def test_dmc_929_rerunning_skill_installer_with_only_jira_removes_github_and_updates_metadata() -> None:
    service = SkillInstallerService(REPOSITORY_ROOT)

    result = service.run_selective_skill_uninstall(
        retained_skill="jira",
        removed_skill="github",
    )
    failures = service.validate_selective_uninstall(result)

    assert not failures, service.format_failures(result, failures)


class FakeSandbox:
    def __init__(self, sandbox_root: Path) -> None:
        self.workspace = sandbox_root / "workspace"
        self.home = sandbox_root / "home"
        self.workspace.mkdir(parents=True, exist_ok=True)
        self.home.mkdir(parents=True, exist_ok=True)
        self.commands: list[tuple[str, int]] = []
        self.cleaned_up = False

    def cleanup(self) -> None:
        self.cleaned_up = True

    def run(self, command: str, timeout: int = 1800) -> CommandResult:
        self.commands.append((command, timeout))
        skills_root = self.workspace / ".claude" / "skills"
        github_dir = skills_root / "dmtools-github"
        if github_dir.exists():
            for child in github_dir.iterdir():
                child.unlink()
            github_dir.rmdir()
        (skills_root / "installed-skills.json").write_text(
            '{\n'
            '  "version": "test-seed",\n'
            '  "installed_skills": ["jira"],\n'
            '  "active_commands": ["/dmtools-jira"]\n'
            '}\n',
            encoding="utf-8",
        )
        (skills_root / "endpoints.json").write_text(
            '{\n'
            '  "version": "test-seed",\n'
            '  "endpoints": [\n'
            '    {"name": "jira", "path": "/dmtools/jira"}\n'
            '  ]\n'
            '}\n',
            encoding="utf-8",
        )
        return CommandResult(
            command=command,
            returncode=0,
            stdout="stubbed rerun\n",
            stderr="",
        )


def test_dmc_929_service_uses_injected_sandbox_and_parses_exact_metadata(tmp_path: Path) -> None:
    sandbox = FakeSandbox(tmp_path)
    service = SkillInstallerService(
        REPOSITORY_ROOT,
        sandbox_factory=lambda _: sandbox,
    )

    result = service.run_selective_skill_uninstall(
        retained_skill="jira",
        removed_skill="github",
    )
    failures = service.validate_selective_uninstall(result)

    assert len(sandbox.commands) == 1
    assert "dmtools-ai-docs/install.sh" in sandbox.commands[0][0]
    assert "DMTOOLS_SKILLS=jira" in sandbox.commands[0][0]
    assert "--skills jira" not in sandbox.commands[0][0]
    assert sandbox.cleaned_up is True
    assert result.initial_metadata is not None
    assert result.initial_metadata.installed_skills == ("jira", "github")
    assert result.initial_metadata.active_commands == ("/dmtools-jira", "/dmtools-github")
    assert result.final_metadata is not None
    assert result.final_metadata.installed_skills == ("jira",)
    assert result.final_metadata.active_commands == ("/dmtools-jira",)
    assert result.final_removed_state.exists is False
    assert not failures, service.format_failures(result, failures)
