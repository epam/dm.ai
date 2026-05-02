from __future__ import annotations

from testing.core.interfaces.installer_script import InstallerScript


_PROBE_PREFIX = "__DMC_957__:"
_INSTALLER_ENV_LINE_KEY = "installer_env_line"


def _probe_script() -> str:
    return f"""
if [ -f "$INSTALLER_ENV_PATH" ]; then
    while IFS= read -r line || [ -n "$line" ]; do
        printf '{_PROBE_PREFIX}{_INSTALLER_ENV_LINE_KEY}=%s\\n' "$line"
    done < "$INSTALLER_ENV_PATH"
fi
""".strip()


def _parse_probe_output(stdout: str) -> tuple[str, dict[str, str]]:
    visible_lines: list[str] = []
    installer_env: dict[str, str] = {}

    for line in stdout.splitlines():
        if not line.startswith(_PROBE_PREFIX):
            visible_lines.append(line)
            continue

        key, _, value = line[len(_PROBE_PREFIX) :].partition("=")
        if key != _INSTALLER_ENV_LINE_KEY:
            continue

        env_key, _, env_value = value.partition("=")
        if env_key:
            installer_env[env_key] = env_value.strip().strip('"')

    return "\n".join(visible_lines).strip(), installer_env


def test_dmc_957_all_skills_flag_installs_all_canonical_skills(
    installer_script: InstallerScript,
) -> None:
    execution = installer_script.run_main(
        args=("--all-skills",),
        post_script=_probe_script(),
    )
    stdout, installer_env = _parse_probe_output(
        installer_script.normalized_stdout(execution)
    )
    stderr = installer_script.normalized_stderr(execution)
    expected_skills_csv = installer_script.available_skills_csv
    expected_effective_skills_line = (
        f"Effective skills: {expected_skills_csv} (source: cli)"
    )
    effective_skill_lines = [
        line for line in stdout.splitlines() if line.startswith("Effective skills:")
    ]

    assert execution.returncode == 0, (
        "The installer should accept --all-skills and complete successfully.\n"
        f"stdout:\n{stdout}\n\nstderr:\n{stderr}"
    )
    assert not stderr, (
        "A successful --all-skills run should not emit user-visible stderr output.\n"
        f"stderr:\n{stderr}"
    )
    assert "Installing DMTools CLI..." in stdout, (
        "A user should see the standard installer banner before the all-skills selection "
        "is processed.\n"
        f"stdout:\n{stdout}"
    )
    assert "Installing all skills (source: cli)" in stdout, (
        "The installer should visibly confirm that the --all-skills toggle was accepted.\n"
        f"stdout:\n{stdout}"
    )
    assert effective_skill_lines == [expected_effective_skills_line], (
        "The user-visible 'Effective skills' output should contain exactly one line with "
        "the full canonical skill list in installer order.\n"
        f"Observed lines: {effective_skill_lines!r}\n\nstdout:\n{stdout}"
    )
    assert "Warning:" not in stdout, (
        "A valid all-skills selection should not surface warning banners.\n"
        f"stdout:\n{stdout}"
    )
    assert "Error:" not in stdout, (
        "A passing all-skills flow should not surface error banners in the visible output.\n"
        f"stdout:\n{stdout}"
    )
    assert "Configured installer-managed skills at" in stdout, (
        "A successful all-skills run should report where the generated installer skill "
        "configuration was written.\n"
        f"stdout:\n{stdout}"
    )
    assert installer_env.get("DMTOOLS_SKILLS") == expected_skills_csv, (
        "The generated installer configuration should persist the full canonical skill list "
        "after an --all-skills run.\n"
        f"installer_env: {installer_env}"
    )
