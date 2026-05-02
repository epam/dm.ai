from __future__ import annotations

from pathlib import Path

from testing.components.services.dmtools_cli_service import DmtoolsCliService
from testing.core.interfaces.dmtools_cli import DmtoolsCli
from testing.frameworks.api.rest.subprocess_process_runner import SubprocessProcessRunner


def create_dmtools_cli(repository_root: Path) -> DmtoolsCli:
    return DmtoolsCliService(
        repository_root=repository_root,
        runner=SubprocessProcessRunner(),
    )
