from __future__ import annotations

from pathlib import Path

from testing.components.services.installer_version_resolution_service import (
    InstallerVersionResolutionService,
)
from testing.frameworks.api.rest.subprocess_process_runner import SubprocessProcessRunner


def create_installer_version_resolution_service(
    repository_root: Path,
    install_script_relative_path: str | Path = "install.sh",
) -> InstallerVersionResolutionService:
    return InstallerVersionResolutionService(
        repository_root=repository_root,
        runner=SubprocessProcessRunner(),
        install_script_relative_path=install_script_relative_path,
    )

