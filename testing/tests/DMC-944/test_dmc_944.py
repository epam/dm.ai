from __future__ import annotations

import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.core.interfaces.installer_script import InstallerScript  # noqa: E402


def test_dmc_944_strict_flag_is_accepted_after_valid_skills_argument(
    installer_script: InstallerScript,
) -> None:
    execution = installer_script.run_main(
        args=("--skills=jira", "--strict"),
        post_script="""
printf 'installer_env_path=%s\\n' "$INSTALLER_ENV_PATH"
cat "$INSTALLER_ENV_PATH"
""",
    )
    stdout = installer_script.normalized_stdout(execution)
    stderr = installer_script.normalized_stderr(execution)
    combined_output = installer_script.normalized_combined_output(execution)

    assert execution.returncode == 0, (
        "The installer should accept --strict after a valid --skills option and finish successfully.\n"
        f"stdout:\n{stdout}\n\nstderr:\n{stderr}"
    )
    assert "Installing DMTools CLI..." in stdout, (
        "The standard installer banner should remain visible for a successful CLI run.\n"
        f"stdout:\n{stdout}"
    )
    assert "Effective skills: jira (source: cli)" in stdout, (
        "The installer should keep the requested jira skill selection from the CLI.\n"
        f"stdout:\n{stdout}"
    )
    assert "Effective integrations: ai,cli,file,kb,mermaid,jira" in stdout, (
        "The installer should resolve integrations for the accepted jira skill.\n"
        f"stdout:\n{stdout}"
    )
    assert 'DMTOOLS_SKILLS="jira"' in stdout, (
        "The generated installer environment should persist the jira skill selection.\n"
        f"stdout:\n{stdout}"
    )
    assert 'DMTOOLS_INTEGRATIONS="ai,cli,file,kb,mermaid,jira"' in stdout, (
        "The generated installer environment should persist the resolved jira integrations.\n"
        f"stdout:\n{stdout}"
    )
    assert "Unknown installer option: --strict" not in combined_output, (
        "The strict flag must be parsed as a supported option, not rejected as unknown.\n"
        f"output:\n{combined_output}"
    )
    assert "No valid skills selected" not in combined_output, (
        "A valid jira selection should not trigger strict-mode validation errors.\n"
        f"output:\n{combined_output}"
    )
