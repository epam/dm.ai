from __future__ import annotations

import os
import re
import stat
import subprocess
import sys
from pathlib import Path

import pytest


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

INSTALL_SCRIPT = REPOSITORY_ROOT / "install.sh"
ALL_SKILLS = (
    "dmtools,jira,confluence,github,gitlab,figma,teams,"
    "sharepoint,ado,testrail,xray"
)
ALL_INTEGRATIONS = (
    "ai,cli,file,kb,mermaid,jira,confluence,github,gitlab,figma,"
    "teams,teams_auth,sharepoint,ado,testrail,jira_xray"
)
ANSI_ESCAPE_PATTERN = re.compile(r"\x1b\[[0-9;]*m")


def strip_ansi(value: str) -> str:
    return ANSI_ESCAPE_PATTERN.sub("", value)


def parse_installer_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        key, _, raw_value = line.partition("=")
        values[key] = raw_value.strip().strip('"')
    return values


def run_installer_with_stubbed_runtime(
    temp_dir: Path,
    *,
    args: tuple[str, ...] = (),
    env_overrides: dict[str, str | None] | None = None,
) -> subprocess.CompletedProcess[str]:
    install_dir = temp_dir / "install-root"
    bin_dir = install_dir / "bin"
    installer_env_path = bin_dir / "dmtools-installer.env"

    env = os.environ.copy()
    env["DMTOOLS_INSTALLER_TEST_MODE"] = "true"
    env["DMTOOLS_INSTALL_DIR"] = str(install_dir)
    env["DMTOOLS_BIN_DIR"] = str(bin_dir)
    env["DMTOOLS_INSTALLER_ENV_PATH"] = str(installer_env_path)
    env.pop("DMTOOLS_SKILLS", None)
    if env_overrides:
        for key, value in env_overrides.items():
            if value is None:
                env.pop(key, None)
            else:
                env[key] = value

    script = f"""
set -e
source "{INSTALL_SCRIPT}"

check_java() {{
    info "check_java stub"
}}

get_latest_version() {{
    printf '%s' 'v9.9.9'
}}

download_dmtools() {{
    local version="$1"
    progress "Downloading DMTools $version (stub)"
    mkdir -p "$INSTALL_DIR" "$BIN_DIR"
    printf 'stub jar for %s\\n' "$version" > "$JAR_PATH"
    cat > "$SCRIPT_PATH" <<'EOF'
#!/bin/bash
echo "dmtools stub"
EOF
    chmod +x "$SCRIPT_PATH"
}}

update_shell_config() {{
    info "update_shell_config stub"
}}

verify_installation() {{
    [ -s "$JAR_PATH" ] || error "Expected installer to create $JAR_PATH"
    [ -x "$SCRIPT_PATH" ] || error "Expected installer to create executable $SCRIPT_PATH"
    info "verify_installation stub"
}}

print_instructions() {{
    info "print_instructions stub"
}}

main "$@"
"""

    return subprocess.run(
        ["bash", "-lc", script, "installer-test", *args],
        cwd=REPOSITORY_ROOT,
        env=env,
        capture_output=True,
        text=True,
    )


@pytest.mark.parametrize(
    ("scenario_name", "env_overrides", "expected_source"),
    [
        ("missing-selection", {}, "default"),
        ("empty-selection", {"DMTOOLS_SKILLS": ""}, "env"),
        ("all-selection", {"DMTOOLS_SKILLS": "all"}, "env"),
    ],
)
def test_dmc_926_missing_or_empty_skill_selection_installs_all_skills_by_default(
    tmp_path: Path,
    scenario_name: str,
    env_overrides: dict[str, str],
    expected_source: str,
) -> None:
    scenario_dir = tmp_path / scenario_name
    scenario_dir.mkdir()
    install_root = scenario_dir / "install-root"
    bin_dir = install_root / "bin"
    installer_env_path = bin_dir / "dmtools-installer.env"
    jar_path = install_root / "dmtools.jar"
    script_path = bin_dir / "dmtools"

    result = run_installer_with_stubbed_runtime(
        scenario_dir,
        env_overrides=env_overrides,
    )
    stdout = strip_ansi(result.stdout)
    stderr = strip_ansi(result.stderr)

    assert result.returncode == 0, (
        f"Installer run failed for {scenario_name}.\nstdout:\n{stdout}\n\nstderr:\n{stderr}"
    )
    assert not stderr.strip(), f"Expected no stderr output for {scenario_name}, got:\n{stderr}"
    assert f"Installing all skills (source: {expected_source})" in stdout, (
        "Installer did not announce the default full-install selection for "
        f"{scenario_name}.\nstdout:\n{stdout}"
    )
    assert f"Effective skills: {ALL_SKILLS} (source: {expected_source})" in stdout, (
        "Installer did not show the canonical all-skills selection for "
        f"{scenario_name}.\nstdout:\n{stdout}"
    )
    assert "Configured installer-managed skills at" in stdout, (
        "Installer did not report the persisted skill configuration path.\n"
        f"stdout:\n{stdout}"
    )

    assert install_root.is_dir(), f"Expected install root to exist: {install_root}"
    assert bin_dir.is_dir(), f"Expected bin directory to exist: {bin_dir}"
    assert jar_path.is_file(), f"Expected installer to create JAR artifact: {jar_path}"
    assert script_path.is_file(), f"Expected installer to create CLI script: {script_path}"
    assert installer_env_path.is_file(), (
        "Expected installer to persist the selected skill set at "
        f"{installer_env_path}"
    )
    assert os.stat(script_path).st_mode & stat.S_IXUSR, (
        f"Expected installer-created CLI script to be executable: {script_path}"
    )

    installer_env = parse_installer_env(installer_env_path)
    assert installer_env.get("DMTOOLS_SKILLS") == ALL_SKILLS, (
        "Persisted DMTOOLS_SKILLS did not contain the canonical full install set.\n"
        f"expected: {ALL_SKILLS}\nactual: {installer_env.get('DMTOOLS_SKILLS')}"
    )
    assert installer_env.get("DMTOOLS_INTEGRATIONS") == ALL_INTEGRATIONS, (
        "Persisted DMTOOLS_INTEGRATIONS did not contain the canonical full install set.\n"
        f"expected: {ALL_INTEGRATIONS}\nactual: {installer_env.get('DMTOOLS_INTEGRATIONS')}"
    )
