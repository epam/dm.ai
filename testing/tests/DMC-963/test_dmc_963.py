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
SEEDED_SKILLS = tuple(str(skill) for skill in CONFIG["seeded_skills"])


def test_dmc_963_empty_skill_selection_removes_all_artifacts_and_clears_metadata() -> None:
    service = SkillInstallerService(REPOSITORY_ROOT)

    result = service.run_deselect_all_skills(seeded_skills=SEEDED_SKILLS)
    failures = service.validate_deselect_all_skills(result)

    assert not failures, service.format_deselect_all_failures(result, failures)
