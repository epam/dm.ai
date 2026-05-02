from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class InstallerSkillSelectionObservation:
    command_label: str
    raw_skills_input: str
    returncode: int
    stdout: str
    stderr: str
    visible_output: str
    effective_skills_line: str
    effective_skills: tuple[str, ...]
    skills_source: str
    invalid_skills: tuple[str, ...] = ()

    @property
    def combined_output(self) -> str:
        return "\n".join(part for part in (self.stdout.strip(), self.stderr.strip()) if part)
