from __future__ import annotations

import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.factories.installer_skill_selection_service_factory import (  # noqa: E402
    create_installer_skill_selection_service,
)
from testing.core.interfaces.installer_skill_selection import InstallerSkillSelection  # noqa: E402
from testing.core.utils.ticket_config_loader import load_ticket_config  # noqa: E402


TEST_DIRECTORY = Path(__file__).resolve().parent
CONFIG = load_ticket_config(TEST_DIRECTORY / "config.yaml")
ENV_SKILLS = tuple(str(skill) for skill in CONFIG["env_skills"])
CLI_SKILLS = tuple(str(skill) for skill in CONFIG["cli_skills"])
EXPECTED_EFFECTIVE_SKILLS = tuple(str(skill) for skill in CONFIG["expected_effective_skills"])
EXPECTED_SKILLS_SOURCE = str(CONFIG["expected_skills_source"])
EXPECTED_EFFECTIVE_SKILLS_LINE = (
    f"Effective skills: {', '.join(EXPECTED_EFFECTIVE_SKILLS)} "
    f"(source: {EXPECTED_SKILLS_SOURCE})"
)


def build_service() -> InstallerSkillSelection:
    return create_installer_skill_selection_service(REPOSITORY_ROOT)


def test_dmc_953_cli_skills_override_dmtools_skills_env_var() -> None:
    service = build_service()
    observation = service.resolve_with_env_and_cli(
        env_skills_csv=",".join(ENV_SKILLS),
        cli_skills_csv=",".join(CLI_SKILLS),
    )

    assert observation.returncode == 0, service.format_execution_failure(observation)
    assert observation.skills_source == EXPECTED_SKILLS_SOURCE, (
        "When both DMTOOLS_SKILLS and --skills are provided, the installer must report "
        "that the CLI argument won.\n"
        f"Observed line: {observation.effective_skills_line!r}"
    )
    assert observation.effective_skills == EXPECTED_EFFECTIVE_SKILLS, (
        "The installer must use only the CLI-provided skill list and ignore the "
        "environment variable values.\n"
        f"Observed line: {observation.effective_skills_line!r}\n"
        f"Visible output:\n{observation.visible_output}"
    )
    assert observation.effective_skills_line == EXPECTED_EFFECTIVE_SKILLS_LINE, (
        "The user-visible 'Effective skills' line should show the CLI skills in the "
        "expected order with the CLI source label.\n"
        f"Observed output:\n{observation.visible_output}"
    )
    for env_skill in ENV_SKILLS:
        assert env_skill not in observation.effective_skills, (
            "The final effective skill list must not retain any value that came only "
            "from DMTOOLS_SKILLS after --skills overrides it.\n"
            f"Observed line: {observation.effective_skills_line!r}"
        )

