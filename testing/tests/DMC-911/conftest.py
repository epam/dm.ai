from __future__ import annotations

from pathlib import Path

import pytest

from testing.components.services.dmtools_cli_service import DmtoolsCliService
from testing.frameworks.api.rest.subprocess_process_runner import SubprocessProcessRunner


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


@pytest.fixture
def dmtools_cli_service() -> DmtoolsCliService:
    return DmtoolsCliService(
        repository_root=REPOSITORY_ROOT,
        runner=SubprocessProcessRunner(),
    )
