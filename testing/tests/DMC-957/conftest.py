from __future__ import annotations

from pathlib import Path

import pytest

from testing.components.factories.installer_script_factory import create_installer_script
from testing.core.interfaces.installer_script import InstallerScript


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


@pytest.fixture
def installer_script() -> InstallerScript:
    return create_installer_script(REPOSITORY_ROOT)
