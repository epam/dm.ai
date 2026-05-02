from pathlib import Path

from testing.components.factories.installer_script_factory import create_installer_script
from testing.core.interfaces.installer_script import InstallerScript


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def test_dmc_960_skip_unknown_flag_downgrades_unknown_skill_to_warning() -> None:
    installer_script: InstallerScript = create_installer_script(REPOSITORY_ROOT)

    execution = installer_script.run_main(
        args=("--skills=jira,unknown", "--skip-unknown"),
        post_script="""
printf 'installer_env_path=%s\n' "$INSTALLER_ENV_PATH"
printf 'dmtools_executable=%s\n' "$(test -x "$SCRIPT_PATH" && printf yes || printf no)"
cat "$INSTALLER_ENV_PATH"
""",
    )
    stdout = installer_script.normalized_stdout(execution)
    stderr = installer_script.normalized_stderr(execution)
    combined_output = installer_script.normalized_combined_output(execution)

    assert execution.returncode == 0, (
        "The installer should succeed when --skip-unknown is provided alongside a valid skill.\n"
        f"stdout:\n{stdout}\n\nstderr:\n{stderr}"
    )
    assert "Installing DMTools CLI..." in stdout, (
        "A user should still see the normal installer startup banner.\n"
        f"stdout:\n{stdout}"
    )
    assert "Warning: Skipping unknown skills: unknown" in stdout, (
        "The installer should clearly warn which requested skill name was ignored.\n"
        f"stdout:\n{stdout}"
    )
    assert "Effective skills: jira (source: cli)" in stdout, (
        "The installer should visibly confirm that only the valid skill remains selected.\n"
        f"stdout:\n{stdout}"
    )
    assert "Unknown installer option: --skip-unknown" not in combined_output, (
        "The skip flag itself must be accepted instead of rejected as an unknown option.\n"
        f"output:\n{combined_output}"
    )
    assert "Unknown skills: unknown. Use --skip-unknown to continue." not in combined_output, (
        "The installer must downgrade the invalid skill to a warning instead of surfacing a fatal error.\n"
        f"output:\n{combined_output}"
    )
    assert "dmtools_executable=yes" in stdout, (
        "The valid jira installation should proceed far enough to produce an executable dmtools entrypoint.\n"
        f"stdout:\n{stdout}"
    )
    assert 'DMTOOLS_SKILLS="jira"' in stdout, (
        "The persisted installer environment should keep only the valid skill after filtering unknown entries.\n"
        f"stdout:\n{stdout}"
    )
    assert 'DMTOOLS_SKILLS="jira,unknown"' not in stdout, (
        "Unknown skill names must not remain in the installer-managed environment.\n"
        f"stdout:\n{stdout}"
    )
