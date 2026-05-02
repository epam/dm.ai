from pathlib import Path

from testing.components.factories.installer_script_factory import create_installer_script
from testing.core.interfaces.installer_script import InstallerScript


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def test_dmc_947_runtime_supports_required_skill_selection_contract() -> None:
    installer_script: InstallerScript = create_installer_script(REPOSITORY_ROOT)

    single_skill = installer_script.run_main(args=("--skill", "jira"))
    single_skill_output = installer_script.normalized_combined_output(single_skill)
    assert single_skill.returncode == 0, single_skill_output
    assert "Effective skills: jira (source: cli)" in single_skill_output

    multi_skill = installer_script.run_main(args=("--skills=jira,github",))
    multi_skill_output = installer_script.normalized_combined_output(multi_skill)
    assert multi_skill.returncode == 0, multi_skill_output
    assert "Effective skills: jira,github (source: cli)" in multi_skill_output

    all_skills = installer_script.run_main(args=("--all-skills",))
    all_skills_output = installer_script.normalized_combined_output(all_skills)
    assert all_skills.returncode == 0, all_skills_output
    assert "Installing all skills (source: cli)" in all_skills_output

    mixed_invalid = installer_script.run_main(args=("--skills=jira,unknown",))
    mixed_invalid_output = installer_script.normalized_combined_output(mixed_invalid)
    assert mixed_invalid.returncode != 0, mixed_invalid_output
    assert "Unknown skills: unknown" in mixed_invalid_output
    assert installer_script.side_effect_marker not in mixed_invalid_output

    skip_unknown = installer_script.run_main(
        args=("--skills=jira,unknown", "--skip-unknown")
    )
    skip_unknown_output = installer_script.normalized_combined_output(skip_unknown)
    assert skip_unknown.returncode == 0, skip_unknown_output
    assert "Warning: Skipping unknown skills: unknown" in skip_unknown_output
    assert "Effective skills: jira (source: cli)" in skip_unknown_output

    invalid_only = installer_script.run_main(args=("--skill", "unknown"))
    invalid_only_output = installer_script.normalized_combined_output(invalid_only)
    assert invalid_only.returncode != 0, invalid_only_output
    assert "Unknown skills: unknown" in invalid_only_output
    assert installer_script.side_effect_marker not in invalid_only_output
