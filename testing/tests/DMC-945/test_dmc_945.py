from __future__ import annotations

import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.core.interfaces.installer_script import InstallerScript  # noqa: E402


def test_dmc_945_strict_flag_is_accepted_when_it_is_the_first_cli_argument(
    installer_script: InstallerScript,
) -> None:
    execution = installer_script.run_main(
        args=("--strict", "--skills=jira"),
        post_script="""
printf 'installer_env_path=%s\\n' "$INSTALLER_ENV_PATH"
cat "$INSTALLER_ENV_PATH"
""",
    )
    stdout = installer_script.normalized_stdout(execution)
    stderr = installer_script.normalized_stderr(execution)

    assert execution.returncode == 0, (
        "The installer should accept --strict when it is the first CLI argument and "
        "continue with the requested valid skill.\n"
        f"stdout:\n{stdout}\n\nstderr:\n{stderr}"
    )
    assert "Installing DMTools CLI..." in stdout, (
        "A user should see the standard installer banner before argument parsing completes.\n"
        f"stdout:\n{stdout}"
    )
    assert "Effective skills: jira (source: cli)" in stdout, (
        "The installer should treat the following --skills option as the effective selection.\n"
        f"stdout:\n{stdout}"
    )
    assert "Effective integrations: ai,cli,file,kb,mermaid,jira" in stdout, (
        "The installer should resolve integrations for the accepted jira skill.\n"
        f"stdout:\n{stdout}"
    )
    assert 'DMTOOLS_SKILLS="jira"' in stdout, (
        "The generated installer environment should persist the selected jira skill.\n"
        f"stdout:\n{stdout}"
    )
    assert 'DMTOOLS_INTEGRATIONS="ai,cli,file,kb,mermaid,jira"' in stdout, (
        "The generated installer environment should persist the resolved jira integrations.\n"
        f"stdout:\n{stdout}"
    )
    assert "Unknown installer option: --strict" not in stderr, (
        "The strict flag must be parsed as a supported option, not rejected by the CLI parser.\n"
        f"stderr:\n{stderr}"
    )
    assert "No valid skills selected" not in stderr, (
        "A valid jira selection should not trigger strict-mode validation errors.\n"
        f"stderr:\n{stderr}"
    )
