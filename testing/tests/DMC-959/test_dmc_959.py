from pathlib import Path

from testing.components.factories.installer_script_factory import create_installer_script
from testing.core.interfaces.installer_script import InstallerScript
from testing.core.utils.ticket_config_loader import load_ticket_config


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
TEST_DIRECTORY = Path(__file__).resolve().parent
CONFIG = load_ticket_config(TEST_DIRECTORY / "config.yaml")
VALID_SKILL = str(CONFIG["valid_skill"])
INVALID_SKILL = str(CONFIG["invalid_skill"])
EXPECTED_INVALID_FRAGMENT = f"Unknown skills: {INVALID_SKILL}"
DOWNSTREAM_INSTALL_SIGNALS = (
    "Creating installation directory...",
    "Configured installer-managed skills at",
    "stubbed download_dmtools",
)


def test_dmc_959_mixed_valid_and_invalid_cli_skills_fail_with_invalid_names_listed() -> None:
    installer_script: InstallerScript = create_installer_script(REPOSITORY_ROOT)

    execution = installer_script.run_main(args=(f"--skills={VALID_SKILL},{INVALID_SKILL}",))
    stdout = installer_script.normalized_stdout(execution)
    stderr = installer_script.normalized_stderr(execution)
    combined_output = installer_script.normalized_combined_output(execution)

    assert execution.returncode != 0, (
        "The installer must exit non-zero when a CLI skill list mixes a valid skill "
        "with an unknown skill and --skip-unknown is not provided.\n"
        f"stdout:\n{stdout}\n\nstderr:\n{stderr}"
    )
    assert "Installing DMTools CLI..." in stdout, (
        "A user should still see the installer start banner before validation fails.\n"
        f"stdout:\n{stdout}"
    )
    assert EXPECTED_INVALID_FRAGMENT in combined_output, (
        "The user-visible output must explicitly identify the invalid skill name.\n"
        f"output:\n{combined_output}"
    )
    assert INVALID_SKILL in combined_output, (
        "The exact invalid skill requested by the user should appear in the failure output.\n"
        f"output:\n{combined_output}"
    )
    assert "--skip-unknown" in combined_output, (
        "The failure message should tell the user how to opt into warning-only behavior.\n"
        f"output:\n{combined_output}"
    )
    unexpected_downstream_signals = [
        signal for signal in DOWNSTREAM_INSTALL_SIGNALS if signal in combined_output
    ]
    assert not unexpected_downstream_signals, (
        "The installer should stop at skill validation before any repository installer "
        "signals for directory creation, persisted skill state, or download stubs appear, "
        "so a failed mixed-skill request cannot continue into a partial installation.\n"
        f"unexpected signals: {unexpected_downstream_signals}\n\n"
        f"output:\n{combined_output}"
    )
    assert "Effective skills:" not in stdout, (
        "The installer must not report a successful effective skill selection after "
        "rejecting the mixed CLI request.\n"
        f"stdout:\n{stdout}"
    )
