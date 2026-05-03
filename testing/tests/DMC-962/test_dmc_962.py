from __future__ import annotations

import re
import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.services.skill_installer_service import SkillInstallerService  # noqa: E402
from testing.core.utils.repo_sandbox import CommandResult  # noqa: E402
from testing.core.utils.ticket_config_loader import load_ticket_config  # noqa: E402


ANSI_ESCAPE_PATTERN = re.compile(r"\x1b\[[0-9;]*m")
TEST_DIRECTORY = Path(__file__).resolve().parent
CONFIG = load_ticket_config(TEST_DIRECTORY / "config.yaml")
RETAINED_SKILL = str(CONFIG["retained_skill"])
REMOVED_SKILL = str(CONFIG["removed_skill"])
ADDED_SKILL = str(CONFIG["added_skill"])
EXPECTED_SELECTED_SKILLS = (RETAINED_SKILL, ADDED_SKILL)


def test_dmc_962_mixed_selective_reinstall_syncs_artifacts_and_metadata() -> None:
    service = SkillInstallerService(REPOSITORY_ROOT)

    result = service.run_selective_skill_transition(
        retained_skill=RETAINED_SKILL,
        removed_skill=REMOVED_SKILL,
        added_skill=ADDED_SKILL,
    )
    failures = service.validate_selective_transition(result)
    normalized_output = _normalize_output(result.installer_command_result.combined_output)

    assert result.installer_command_result.returncode == 0, service.format_failures(
        result,
        failures,
    )
    assert (
        f"Effective skills: {','.join(EXPECTED_SELECTED_SKILLS)} (source: env)"
        in normalized_output
    ), (
        "The installer should tell the user exactly which mixed selective skill set "
        "it will apply before changing artifacts and metadata.\n"
        f"combined output:\n{normalized_output}"
    )
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
        added_skill_dir = skills_root / f"dmtools-{ADDED_SKILL}"
        added_skill_dir.mkdir(parents=True, exist_ok=True)
        (added_skill_dir / "SKILL.md").write_text(f"# {ADDED_SKILL}\n", encoding="utf-8")
        (added_skill_dir / "artifact.txt").write_text(
            f"{ADDED_SKILL} artifact\n",
            encoding="utf-8",
        )
        (skills_root / "installed-skills.json").write_text(
            '{\n'
            '  "version": "test-seed",\n'
            f'  "installed_skills": ["{RETAINED_SKILL}", "{ADDED_SKILL}"],\n'
            f'  "active_commands": ["/dmtools-{RETAINED_SKILL}", "/dmtools-{ADDED_SKILL}"]\n'
            '}\n',
            encoding="utf-8",
        )
        (skills_root / "endpoints.json").write_text(
            '{\n'
            '  "version": "test-seed",\n'
            '  "endpoints": [\n'
            f'    {{"name": "{RETAINED_SKILL}", "path": "/dmtools/{RETAINED_SKILL}"}},\n'
            f'    {{"name": "{ADDED_SKILL}", "path": "/dmtools/{ADDED_SKILL}"}}\n'
            '  ]\n'
            '}\n',
            encoding="utf-8",
        )
        return CommandResult(
            command=command,
            returncode=0,
            stdout=f"Effective skills: {RETAINED_SKILL}, {ADDED_SKILL} (source: env)\n",
            stderr="",
        )


def test_dmc_962_service_uses_injected_sandbox_and_tracks_added_skill_metadata(
    tmp_path: Path,
) -> None:
    sandbox = FakeSandbox(tmp_path)
    service = SkillInstallerService(
        REPOSITORY_ROOT,
        sandbox_factory=lambda _: sandbox,
    )

    result = service.run_selective_skill_transition(
        retained_skill=RETAINED_SKILL,
        removed_skill=REMOVED_SKILL,
        added_skill=ADDED_SKILL,
    )
    failures = service.validate_selective_transition(result)

    assert len(sandbox.commands) == 1
    assert "dmtools-ai-docs/install.sh" in sandbox.commands[0][0]
    assert f"DMTOOLS_SKILLS={RETAINED_SKILL},{ADDED_SKILL}" in sandbox.commands[0][0]
    assert f"--skills {RETAINED_SKILL},{ADDED_SKILL}" not in sandbox.commands[0][0]
    assert sandbox.cleaned_up is True
    assert result.initial_metadata is not None
    assert result.initial_metadata.installed_skills == (RETAINED_SKILL, REMOVED_SKILL)
    assert result.initial_metadata.active_commands == (
        f"/dmtools-{RETAINED_SKILL}",
        f"/dmtools-{REMOVED_SKILL}",
    )
    assert result.initial_added_state is not None
    assert result.initial_added_state.exists is False
    assert result.final_metadata is not None
    assert result.final_metadata.installed_skills == (RETAINED_SKILL, ADDED_SKILL)
    assert result.final_metadata.active_commands == (
        f"/dmtools-{RETAINED_SKILL}",
        f"/dmtools-{ADDED_SKILL}",
    )
    assert result.final_removed_state.exists is False
    assert result.final_added_state is not None
    assert result.final_added_state.exists is True
    assert not failures, service.format_failures(result, failures)


def _normalize_output(output: str) -> str:
    return ANSI_ESCAPE_PATTERN.sub("", output)
