from pathlib import Path

from testing.components.factories.installer_script_factory import create_installer_script
from testing.core.interfaces.installer_script import InstallerScript


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def test_dmc_931_all_invalid_skills_fail_fast_with_user_visible_error() -> None:
    installer_script: InstallerScript = create_installer_script(REPOSITORY_ROOT)

    execution = installer_script.run_main_with_env_skills("invalid1,invalid2")
    stdout = installer_script.normalized_stdout(execution)
    stderr = installer_script.normalized_stderr(execution)
    combined_output = installer_script.normalized_combined_output(execution)

    assert execution.returncode != 0, (
        "Expected the installer to exit with a non-zero code when DMTOOLS_SKILLS "
        "contains only unknown skills.\n"
        f"stdout:\n{stdout}\n\nstderr:\n{stderr}"
    )
    assert "Installing DMTools CLI..." in stdout, (
        "The installer should surface its normal startup banner before validating "
        f"the requested skills.\nstdout:\n{stdout}"
    )
    assert "Warning: Skipping unknown skills: invalid1,invalid2" in stdout, (
        "A user should see exactly which requested skills were rejected.\n"
        f"stdout:\n{stdout}"
    )
    assert (
        "Error: No valid skills selected. Unknown skills: invalid1,invalid2. "
        f"Allowed skills: {installer_script.available_skills_csv}"
    ) in stderr, (
        "The user-facing error must explain that nothing installable remains and "
        "show the supported skill list.\n"
        f"stderr:\n{stderr}"
    )
    assert installer_script.side_effect_marker not in combined_output, (
        "The installer should stop before directory creation, configuration writes, "
        "downloads, or verification when skill validation fails.\n"
        f"output:\n{combined_output}"
    )
    assert "Effective skills:" not in stdout, (
        "The installer must not report a successful skill selection when every "
        f"requested skill is invalid.\nstdout:\n{stdout}"
    )
