from __future__ import annotations

from pathlib import Path

from testing.components.services.installer_skill_selection_service import (
    InstallerSkillSelectionService,
)
from testing.frameworks.api.rest.subprocess_process_runner import SubprocessProcessRunner


def create_installer_skill_selection_service(
    repository_root: Path,
) -> InstallerSkillSelectionService:
    return InstallerSkillSelectionService(
        repository_root=repository_root,
        runner=SubprocessProcessRunner(),
    )
