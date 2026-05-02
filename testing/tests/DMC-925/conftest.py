from __future__ import annotations

from pathlib import Path

import pytest

from testing.components.factories.installer_skill_selection_service_factory import (
    create_installer_skill_selection_service,
)
from testing.core.interfaces.installer_skill_selection import InstallerSkillSelection


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


@pytest.fixture
def installer_skill_selection_service() -> InstallerSkillSelection:
    return create_installer_skill_selection_service(REPOSITORY_ROOT)
