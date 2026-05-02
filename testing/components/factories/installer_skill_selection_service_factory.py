from __future__ import annotations

from pathlib import Path

from testing.components.services.installer_skill_selection_service import (
    InstallerSkillSelectionService,
)
from testing.core.interfaces.installer_skill_selection import InstallerSkillSelection
from testing.frameworks.api.rest.subprocess_process_runner import SubprocessProcessRunner


def create_installer_skill_selection_service(
    repository_root: Path,
) -> InstallerSkillSelection:
    return InstallerSkillSelectionService(
        repository_root=repository_root,
        runner=SubprocessProcessRunner(),
    )
