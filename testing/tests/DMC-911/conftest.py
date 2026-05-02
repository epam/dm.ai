from __future__ import annotations

from pathlib import Path

import pytest

from testing.components.factories.dmtools_cli_factory import create_dmtools_cli
from testing.core.interfaces.dmtools_cli import DmtoolsCli


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


@pytest.fixture
def dmtools_cli_service() -> DmtoolsCli:
    return create_dmtools_cli(REPOSITORY_ROOT)
