from __future__ import annotations

from pathlib import Path

from testing.components.services.installer_script_service import InstallerScriptService
from testing.core.interfaces.installer_script import InstallerScript
from testing.frameworks.api.rest.subprocess_process_runner import SubprocessProcessRunner


def create_installer_script(
    repository_root: Path,
    install_script_relative_path: str | Path = "install.sh",
) -> InstallerScript:
    return InstallerScriptService(
        repository_root=repository_root,
        runner=SubprocessProcessRunner(),
        install_script_relative_path=install_script_relative_path,
    )
