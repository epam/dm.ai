from pathlib import Path

from testing.components.services.skill_installer_service import SkillInstallerService


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def test_dmc_929_selective_skill_uninstall_removes_deselected_skill_and_updates_metadata() -> None:
    service = SkillInstallerService(REPOSITORY_ROOT)

    result = service.run_selective_skill_uninstall(
        retained_skill="jira",
        removed_skill="github",
    )
    failures = service.validate_selective_uninstall(result)

    assert not failures, service.format_failures(result, failures)
