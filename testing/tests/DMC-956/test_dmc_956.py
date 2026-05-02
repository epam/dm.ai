from pathlib import Path

from testing.components.factories.installer_script_factory import create_installer_script
from testing.core.interfaces.installer_script import InstallerScript


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def test_dmc_956_skill_flag_is_accepted_and_processed_for_single_skill_install() -> None:
    installer_script: InstallerScript = create_installer_script(
        REPOSITORY_ROOT,
        "dmtools-ai-docs/install.sh",
    )

    execution = installer_script.run_main(args=("--skill", "jira"))
    combined_output = installer_script.normalized_combined_output(execution)

    assert execution.returncode == 0, (
        "Expected `bash install.sh --skill jira` to succeed for the skill installer.\n"
        f"Observed output:\n{combined_output}"
    )
    assert "Effective skills: jira (source: cli)" in combined_output, (
        "The installer should tell the user that `jira` became the effective skill "
        "selection from the CLI.\n"
        f"Observed output:\n{combined_output}"
    )
    assert "Unknown installer option" not in combined_output, (
        "The user-visible logs must not report `--skill` as an unknown option.\n"
        f"Observed output:\n{combined_output}"
    )
    assert installer_script.side_effect_marker in combined_output, (
        "The installer should continue into the installation flow after accepting "
        "`--skill jira`, which proves the flag was processed rather than ignored.\n"
        f"Observed output:\n{combined_output}"
    )
