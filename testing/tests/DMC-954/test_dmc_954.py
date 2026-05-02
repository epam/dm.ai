from __future__ import annotations

from testing.core.interfaces.installer_script import InstallerScript


def test_dmc_954_single_env_skill_renders_without_extra_separators(
    installer_script: InstallerScript,
) -> None:
    execution = installer_script.run_main(extra_env={"DMTOOLS_SKILLS": " jira "})
    stdout = installer_script.normalized_stdout(execution)
    stderr = installer_script.normalized_stderr(execution)

    assert execution.returncode == 0, (
        "The installer should succeed when DMTOOLS_SKILLS contains a single valid skill with "
        "leading and trailing whitespace.\n"
        f"stdout:\n{stdout}\n\nstderr:\n{stderr}"
    )
    assert "Installing DMTools CLI..." in stdout, (
        "A user should still see the standard installer banner during the run.\n"
        f"stdout:\n{stdout}"
    )

    effective_skill_lines = [
        line for line in stdout.splitlines() if line.startswith("Effective skills:")
    ]
    assert effective_skill_lines == ["Effective skills: jira (source: env)"], (
        "The installer should show exactly one normalized single-skill line with no leading "
        "or trailing separators.\n"
        f"Observed lines: {effective_skill_lines!r}\n\nstdout:\n{stdout}"
    )
    assert "Effective skills: jira," not in stdout, (
        "The visible installer output must not append a trailing comma to a single skill.\n"
        f"stdout:\n{stdout}"
    )
    assert "Effective skills: ,jira" not in stdout, (
        "The visible installer output must not prepend a leading comma to a single skill.\n"
        f"stdout:\n{stdout}"
    )
    assert "Warning: Skipping unknown skills:" not in stdout, (
        "A valid single-skill selection should not trigger unknown-skill warnings.\n"
        f"stdout:\n{stdout}"
    )
