from __future__ import annotations

from pathlib import Path

import pytest

from testing.components.factories.installer_skill_selection_service_factory import (
    create_installer_skill_selection_service,
)
from testing.components.services.installer_skill_selection_service import (
    InstallerSkillSelectionService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


@pytest.fixture
def installer_skill_selection_service() -> InstallerSkillSelectionService:
    return create_installer_skill_selection_service(REPOSITORY_ROOT)
