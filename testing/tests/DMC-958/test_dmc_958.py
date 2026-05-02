from pathlib import Path

from testing.components.factories.installer_script_factory import create_installer_script
from testing.core.interfaces.installer_script import InstallerScript


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def test_dmc_958_installer_accepts_skills_equals_alias() -> None:
    installer_script: InstallerScript = create_installer_script(REPOSITORY_ROOT)

    execution = installer_script.run_main(args=("--skills=jira,github",))
    stdout = installer_script.normalized_stdout(execution)
    stderr = installer_script.normalized_stderr(execution)
    combined_output = installer_script.normalized_combined_output(execution)

    assert execution.returncode == 0, (
        "The installer should accept the equals-sign --skills alias without treating it "
        "as an unsupported option.\n"
        f"stdout:\n{stdout}\n\nstderr:\n{stderr}"
    )
    assert "Installing DMTools CLI..." in stdout, (
        "The installer should show the normal user-visible startup banner when the "
        "--skills=<name,name> alias is used.\n"
        f"stdout:\n{stdout}"
    )
    assert "Effective skills: jira, github (source: cli)" in stdout, (
        "The user-visible effective skills line should list both skills parsed from "
        "the equals-sign alias in the original order.\n"
        f"stdout:\n{stdout}"
    )
    assert "Unknown installer option" not in combined_output, (
        "The installer must not reject --skills=<name,name> as an unknown option.\n"
        f"output:\n{combined_output}"
    )
    assert "invalid option" not in combined_output.lower(), (
        "The installer must not surface an invalid-option error for the equals-sign "
        "--skills alias.\n"
        f"output:\n{combined_output}"
    )
