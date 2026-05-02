from __future__ import annotations

from pathlib import Path

from testing.components.services.installer_script_service import InstallerScriptService
from testing.frameworks.api.rest.subprocess_process_runner import SubprocessProcessRunner


def create_installer_script_service(repository_root: Path) -> InstallerScriptService:
    return InstallerScriptService(
        repository_root=repository_root,
        runner=SubprocessProcessRunner(),
    )
