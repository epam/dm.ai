from __future__ import annotations

from pathlib import Path

from testing.components.services.installer_metadata_service import InstallerMetadataService
from testing.frameworks.api.rest.subprocess_process_runner import SubprocessProcessRunner


def create_installer_metadata_service(
    repository_root: Path,
    installer_url: str = InstallerMetadataService.DEFAULT_INSTALLER_URL,
) -> InstallerMetadataService:
    return InstallerMetadataService(
        repository_root=repository_root,
        runner=SubprocessProcessRunner(),
        installer_url=installer_url,
    )
