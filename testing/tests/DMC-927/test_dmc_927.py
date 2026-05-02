from __future__ import annotations

import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.core.interfaces.installer_script import InstallerScript  # noqa: E402


def test_dmc_927_normal_mode_skips_unknown_skills_and_keeps_valid_selection(
    installer_script: InstallerScript,
) -> None:
    execution = installer_script.run_main(
        extra_env={"DMTOOLS_SKILLS": "jira,unknown_skill"},
        post_script="""
printf 'installer_env_path=%s\\n' "$INSTALLER_ENV_PATH"
cat "$INSTALLER_ENV_PATH"
""",
    )

    assert execution.returncode == 0, (
        "Normal installer mode should continue when at least one known skill remains.\n"
        f"stdout:\n{execution.stdout}\n\nstderr:\n{execution.stderr}"
    )
    assert "Warning: Skipping unknown skills: unknown_skill" in execution.stdout
    assert "Effective skills: jira (source: env)" in execution.stdout
    assert "Effective integrations: ai,cli,file,kb,mermaid,jira" in execution.stdout
    assert 'DMTOOLS_SKILLS="jira"' in execution.stdout
    assert 'DMTOOLS_INTEGRATIONS="ai,cli,file,kb,mermaid,jira"' in execution.stdout


def test_dmc_927_strict_mode_rejects_invalid_skills_with_invalid_skill_error(
    installer_script: InstallerScript,
) -> None:
    execution = installer_script.run_main(args=("--skills=invalid", "--strict"))

    assert execution.returncode != 0, "Strict installer mode must fail for invalid skills."
    assert "No valid skills selected. Unknown skills: invalid." in execution.stderr
    assert "Allowed skills:" in execution.stderr
    assert "Unknown installer option: --strict" not in execution.stderr
