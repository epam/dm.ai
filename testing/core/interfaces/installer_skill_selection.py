from __future__ import annotations

from typing import Protocol

from testing.core.models.installer_skill_selection_observation import (
    InstallerSkillSelectionObservation,
)


class InstallerSkillSelection(Protocol):
    def resolve_with_env(
        self,
        skills_csv: str,
    ) -> InstallerSkillSelectionObservation:
        raise NotImplementedError

    def resolve_with_env_and_cli(
        self,
        env_skills_csv: str,
        cli_skills_csv: str,
    ) -> InstallerSkillSelectionObservation:
        raise NotImplementedError

    def resolve_with_cli(
        self,
        skills_csv: str,
    ) -> InstallerSkillSelectionObservation:
        raise NotImplementedError

    def format_execution_failure(
        self,
        observation: InstallerSkillSelectionObservation,
    ) -> str:
        raise NotImplementedError
