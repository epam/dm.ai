from __future__ import annotations

import os
import shutil
import subprocess
import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.services.skill_installer_service import SkillInstallerService  # noqa: E402
from testing.core.utils.repo_sandbox import CommandResult  # noqa: E402


def test_dmc_929_rerunning_root_installer_with_only_jira_rewrites_installer_skill_config() -> None:
    service = SkillInstallerService(REPOSITORY_ROOT)

    result = service.run_selective_skill_uninstall(
        retained_skill="jira",
        removed_skill="github",
    )
    failures = service.validate_selective_uninstall(result)

    assert not failures, service.format_failures(result, failures)


class FakeSandbox:
    def __init__(self, repository_root: Path, sandbox_root: Path) -> None:
        self.workspace = sandbox_root / "workspace"
        self.home = sandbox_root / "home"
        self.workspace.mkdir(parents=True, exist_ok=True)
        self.home.mkdir(parents=True, exist_ok=True)
        shutil.copy2(repository_root / "install.sh", self.workspace / "install.sh")
        self.commands: list[tuple[str, int]] = []
        self.cleaned_up = False

    def cleanup(self) -> None:
        self.cleaned_up = True

    def run(self, command: str, timeout: int = 1800) -> CommandResult:
        self.commands.append((command, timeout))
        completed = subprocess.run(
            ["bash", "-lc", command],
            cwd=self.workspace,
            env={
                **os.environ,
                "HOME": str(self.home),
                "PYTHONUNBUFFERED": "1",
            },
            capture_output=True,
            text=True,
            timeout=timeout,
            check=False,
        )
        return CommandResult(
            command=command,
            returncode=completed.returncode,
            stdout=completed.stdout,
            stderr=completed.stderr,
        )


def test_dmc_929_service_uses_injected_sandbox_and_parses_exact_skill_config(tmp_path: Path) -> None:
    sandbox = FakeSandbox(REPOSITORY_ROOT, tmp_path)
    service = SkillInstallerService(
        REPOSITORY_ROOT,
        sandbox_factory=lambda _: sandbox,
    )

    result = service.run_selective_skill_uninstall(
        retained_skill="jira",
        removed_skill="github",
    )
    failures = service.validate_selective_uninstall(result)

    assert len(sandbox.commands) == 2
    assert sandbox.cleaned_up is True
    assert result.initial_config is not None
    assert result.initial_config.skills == ("jira", "github")
    assert result.initial_config.integrations == (
        "ai",
        "cli",
        "file",
        "kb",
        "mermaid",
        "jira",
        "github",
    )
    assert result.updated_config is not None
    assert result.updated_config.skills == ("jira",)
    assert result.updated_config.integrations == ("ai", "cli", "file", "kb", "mermaid", "jira")
    assert "github" not in result.updated_config.raw
    assert not failures, service.format_failures(result, failures)
