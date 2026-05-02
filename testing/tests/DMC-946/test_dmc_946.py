from __future__ import annotations

from pathlib import Path

from testing.components.factories.installer_script_factory import create_installer_script
from testing.core.interfaces.installer_script import InstallerScript


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
EXPECTED_INTEGRATIONS = "ai,cli,file,kb,mermaid,jira"
_PROBE_PREFIX = "__DMC_946__:"
_INSTALLER_ENV_LINE_KEY = "installer_env_line"


def _probe_script() -> str:
    return f"""
printf '{_PROBE_PREFIX}strict_mode=%s\\n' "$STRICT_INSTALL_MODE"
if [ -f "$INSTALLER_ENV_PATH" ]; then
    while IFS= read -r line || [ -n "$line" ]; do
        printf '{_PROBE_PREFIX}{_INSTALLER_ENV_LINE_KEY}=%s\\n' "$line"
    done < "$INSTALLER_ENV_PATH"
fi
""".strip()


def _parse_probe_output(stdout: str) -> tuple[str, dict[str, str], dict[str, str]]:
    visible_lines: list[str] = []
    probe_values: dict[str, str] = {}
    installer_env: dict[str, str] = {}

    for line in stdout.splitlines():
        if not line.startswith(_PROBE_PREFIX):
            visible_lines.append(line)
            continue

        key, _, value = line[len(_PROBE_PREFIX) :].partition("=")
        if key == _INSTALLER_ENV_LINE_KEY:
            env_key, _, env_value = value.partition("=")
            if env_key:
                installer_env[env_key] = env_value.strip().strip('"')
            continue

        probe_values[key] = value.strip()

    return "\n".join(visible_lines).strip(), probe_values, installer_env


def test_dmc_946_strict_env_mode_allows_valid_skills_and_installs_successfully() -> None:
    installer_script: InstallerScript = create_installer_script(REPOSITORY_ROOT)

    execution = installer_script.run_main(
        args=("--skills=jira",),
        extra_env={"DMTOOLS_STRICT_INSTALL": "true"},
        post_script=_probe_script(),
    )
    stdout, probe_values, installer_env = _parse_probe_output(
        installer_script.normalized_stdout(execution)
    )
    stderr = installer_script.normalized_stderr(execution)

    assert execution.returncode == 0, (
        "Strict mode supplied through DMTOOLS_STRICT_INSTALL should not block a valid "
        "--skills=jira installation.\n"
        f"stdout:\n{stdout}\n\nstderr:\n{stderr}"
    )
    assert not stderr, f"Expected no stderr output for a valid strict-mode install.\nstderr:\n{stderr}"
    assert "Installing DMTools CLI..." in stdout, (
        "The installer should surface its normal startup banner before validating skills.\n"
        f"stdout:\n{stdout}"
    )
    assert "Effective skills: jira (source: cli)" in stdout, (
        "The installer should report the selected valid skill and preserve the CLI source.\n"
        f"stdout:\n{stdout}"
    )
    assert f"Effective integrations: {EXPECTED_INTEGRATIONS}" in stdout, (
        "The installer should surface the effective integrations for the selected jira skill.\n"
        f"stdout:\n{stdout}"
    )
    assert "Configured installer-managed skills at" in stdout, (
        "A successful install should report where the generated installer skill config was written.\n"
        f"stdout:\n{stdout}"
    )
    assert "Warning:" not in stdout, (
        "Strict mode with a valid skill selection should not show any warning banners.\n"
        f"stdout:\n{stdout}"
    )
    assert probe_values.get("strict_mode") == "true", (
        "DMTOOLS_STRICT_INSTALL=true should activate strict mode before skill validation.\n"
        f"probes: {probe_values}\nstdout:\n{stdout}"
    )
    assert installer_env.get("DMTOOLS_SKILLS") == "jira", (
        "The persisted installer-managed skill config should keep the selected jira skill.\n"
        f"installer_env: {installer_env}"
    )
    assert installer_env.get("DMTOOLS_INTEGRATIONS") == EXPECTED_INTEGRATIONS, (
        "The persisted installer-managed integration list should match the jira skill mapping.\n"
        f"installer_env: {installer_env}"
    )
