from __future__ import annotations

import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.services.skill_installer_service import SkillInstallerService  # noqa: E402
from testing.core.utils.ticket_config_loader import load_ticket_config  # noqa: E402


TEST_DIRECTORY = Path(__file__).resolve().parent
CONFIG = load_ticket_config(TEST_DIRECTORY / "config.yaml")
RETAINED_SKILL = str(CONFIG["retained_skill"])
INVALID_SKILL = str(CONFIG["invalid_skill"])


def test_dmc_964_invalid_skill_reinstall_reports_error_and_preserves_existing_state() -> None:
    service = SkillInstallerService(REPOSITORY_ROOT)

    result = service.run_invalid_skill_reinstall(
        retained_skill=RETAINED_SKILL,
        invalid_skill=INVALID_SKILL,
    )
    failures = service.validate_invalid_skill_reinstall(result)

    assert not failures, service.format_invalid_skill_reinstall_failures(result, failures)
