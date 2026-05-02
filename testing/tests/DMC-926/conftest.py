from __future__ import annotations

from pathlib import Path

import pytest

from testing.components.factories.installer_script_factory import create_installer_script
from testing.components.services.installer_full_install_audit_service import (
    InstallerFullInstallAuditService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


@pytest.fixture
def installer_full_install_audit_service() -> InstallerFullInstallAuditService:
    return InstallerFullInstallAuditService(create_installer_script(REPOSITORY_ROOT))
