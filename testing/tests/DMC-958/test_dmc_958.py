from __future__ import annotations

import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.factories.installer_script_factory import create_installer_script  # noqa: E402
from testing.core.interfaces.installer_script import InstallerScript  # noqa: E402
from testing.core.utils.ticket_config_loader import load_ticket_config  # noqa: E402


TEST_DIRECTORY = Path(__file__).resolve().parent
CONFIG = load_ticket_config(TEST_DIRECTORY / "config.yaml")
INSTALLER_ARGS = tuple(str(argument) for argument in CONFIG["installer_args"])
EXPECTED_EFFECTIVE_SKILLS = tuple(str(skill) for skill in CONFIG["expected_effective_skills"])
EXPECTED_SKILLS_SOURCE = str(CONFIG["expected_skills_source"])
EXPECTED_BANNER_FRAGMENT = str(CONFIG["expected_banner_fragment"])
EXPECTED_CONTINUATION_FRAGMENT = str(CONFIG["expected_continuation_fragment"])
FORBIDDEN_OUTPUT_FRAGMENTS = tuple(str(fragment) for fragment in CONFIG["forbidden_output_fragments"])
EXPECTED_EFFECTIVE_SKILLS_LINE = (
    f"Effective skills: {', '.join(EXPECTED_EFFECTIVE_SKILLS)} "
    f"(source: {EXPECTED_SKILLS_SOURCE})"
)


def test_dmc_958_installer_accepts_skills_equals_alias() -> None:
    installer_script: InstallerScript = create_installer_script(REPOSITORY_ROOT)

    execution = installer_script.run_main(args=INSTALLER_ARGS)
    stdout = installer_script.normalized_stdout(execution)
    stderr = installer_script.normalized_stderr(execution)
    combined_output = installer_script.normalized_combined_output(execution)

    assert execution.returncode == 0, (
        "The installer should accept the equals-sign --skills alias without treating it "
        "as an unsupported option.\n"
        f"stdout:\n{stdout}\n\nstderr:\n{stderr}"
    )
    assert EXPECTED_BANNER_FRAGMENT in stdout, (
        "The installer should show the normal user-visible startup banner when the "
        "--skills=<name,name> alias is used.\n"
        f"stdout:\n{stdout}"
    )
    assert EXPECTED_EFFECTIVE_SKILLS_LINE in stdout, (
        "The user-visible effective skills line should list both skills parsed from "
        "the equals-sign alias in the original order.\n"
        f"stdout:\n{stdout}"
    )
    assert EXPECTED_CONTINUATION_FRAGMENT in stdout, (
        "The installer should continue into the real installation flow after "
        "accepting --skills=<name,name>, which proves the alias was processed "
        "instead of printing the banner and exiting early.\n"
        f"stdout:\n{stdout}"
    )
    normalized_output = combined_output.lower()
    for forbidden_fragment in FORBIDDEN_OUTPUT_FRAGMENTS:
        candidate_output = normalized_output if forbidden_fragment.islower() else combined_output
        assert forbidden_fragment not in candidate_output, (
            "The installer must not reject --skills=<name,name> while parsing the "
            "equals-sign alias.\n"
            f"Forbidden fragment: {forbidden_fragment!r}\n"
            f"output:\n{combined_output}"
        )
