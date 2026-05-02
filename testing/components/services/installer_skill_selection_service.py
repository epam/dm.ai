from __future__ import annotations

import re
import shlex
from pathlib import Path
from typing import Mapping

from testing.core.interfaces.installer_skill_selection import InstallerSkillSelection
from testing.core.interfaces.process_runner import ProcessRunner
from testing.core.models.installer_skill_selection_observation import (
    InstallerSkillSelectionObservation,
)


class InstallerSkillSelectionService(InstallerSkillSelection):
    _ANSI_ESCAPE_PATTERN = re.compile(r"\x1B\[[0-?]*[ -/]*[@-~]")
    _EFFECTIVE_SKILLS_PATTERN = re.compile(
        r"(?m)^(?P<line>Effective skills:\s*(?P<skills>.+?)\s+\(source:\s*(?P<source>[^)]+)\))$"
    )
    _INVALID_SKILLS_PATTERN = re.compile(
        r"(?m)^Warning:\s*Skipping unknown skills:\s*(?P<skills>[^\n\r]+)$"
    )

    def __init__(
        self,
        repository_root: Path,
        runner: ProcessRunner,
    ) -> None:
        self.repository_root = repository_root
        self.install_script_path = repository_root / "install.sh"
        self.runner = runner

    def resolve_with_env(self, skills_csv: str) -> InstallerSkillSelectionObservation:
        return self._run(
            command_label="env",
            raw_skills_input=skills_csv,
            commands=(
                "parse_installer_args",
                "resolve_skill_selection",
            ),
            extra_env={"DMTOOLS_SKILLS": skills_csv},
        )

    def resolve_with_env_and_cli(
        self,
        env_skills_csv: str,
        cli_skills_csv: str,
    ) -> InstallerSkillSelectionObservation:
        return self._run(
            command_label="env+cli",
            raw_skills_input=f"env={env_skills_csv!r}; cli={cli_skills_csv!r}",
            commands=(
                f"parse_installer_args --skills={shlex.quote(cli_skills_csv)}",
                "resolve_skill_selection",
            ),
            extra_env={"DMTOOLS_SKILLS": env_skills_csv},
        )

    def resolve_with_cli(self, skills_csv: str) -> InstallerSkillSelectionObservation:
        return self._run(
            command_label="cli",
            raw_skills_input=skills_csv,
            commands=(
                f"parse_installer_args --skills={shlex.quote(skills_csv)}",
                "resolve_skill_selection",
            ),
        )

    def format_execution_failure(
        self,
        observation: InstallerSkillSelectionObservation,
    ) -> str:
        return (
            f"Installer {observation.command_label} skill selection failed with exit code "
            f"{observation.returncode}.\n"
            f"raw input: {observation.raw_skills_input!r}\n"
            f"stdout:\n{observation.stdout}\n\nstderr:\n{observation.stderr}"
        )

    def _run(
        self,
        *,
        command_label: str,
        raw_skills_input: str,
        commands: tuple[str, ...],
        extra_env: Mapping[str, str] | None = None,
    ) -> InstallerSkillSelectionObservation:
        env: dict[str, str] = {
            "DMTOOLS_INSTALLER_TEST_MODE": "true",
        }
        if extra_env is not None:
            env.update(extra_env)

        script = "\n".join(
            (
                "set -e",
                f"source {shlex.quote(str(self.install_script_path))}",
                *commands,
            )
        )
        completed = self.runner.run(
            ["bash", "-lc", script],
            cwd=self.repository_root,
            env=env,
        )
        visible_output = self._strip_ansi(completed.stdout)
        effective_skills_match = self._EFFECTIVE_SKILLS_PATTERN.search(visible_output)
        if not effective_skills_match:
            raise AssertionError(
                "Installer output did not include the expected 'Effective skills' log line.\n"
                f"stdout:\n{visible_output}\n\nstderr:\n{completed.stderr}"
            )

        invalid_match = self._INVALID_SKILLS_PATTERN.search(visible_output)
        invalid_skills = ()
        if invalid_match:
            invalid_skills = tuple(
                skill.strip()
                for skill in invalid_match.group("skills").split(",")
                if skill.strip()
            )

        return InstallerSkillSelectionObservation(
            command_label=command_label,
            raw_skills_input=raw_skills_input,
            returncode=completed.returncode,
            stdout=completed.stdout,
            stderr=completed.stderr,
            visible_output=visible_output,
            effective_skills_line=effective_skills_match.group("line"),
            effective_skills=tuple(
                skill.strip()
                for skill in effective_skills_match.group("skills").split(",")
                if skill.strip()
            ),
            skills_source=effective_skills_match.group("source").strip(),
            invalid_skills=invalid_skills,
        )

    @classmethod
    def _strip_ansi(cls, text: str) -> str:
        return cls._ANSI_ESCAPE_PATTERN.sub("", text)
