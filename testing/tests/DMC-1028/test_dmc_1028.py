from __future__ import annotations

import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.services.live_installer_github_api_failure_service import (  # noqa: E402
    LiveInstallerGitHubApiFailureService,
)
from testing.core.utils.ticket_config_loader import load_ticket_config  # noqa: E402
from testing.frameworks.api.rest.subprocess_process_runner import (  # noqa: E402
    SubprocessProcessRunner,
)


TEST_DIRECTORY = Path(__file__).resolve().parent
CONFIG = load_ticket_config(TEST_DIRECTORY / "config.yaml")
EXPECTED_OUTPUT_FRAGMENTS = tuple(str(fragment) for fragment in CONFIG["expected_output_fragments"])


def build_service() -> LiveInstallerGitHubApiFailureService:
    return LiveInstallerGitHubApiFailureService(
        repository_root=REPOSITORY_ROOT,
        runner=SubprocessProcessRunner(),
        release_installer_url=str(CONFIG["release_installer_url"]),
        repo=str(CONFIG["repo"]),
        api_failure_exit_code=int(str(CONFIG["api_failure_exit_code"])),
        api_failure_message=str(CONFIG["api_failure_message"]),
        git_failure_exit_code=int(str(CONFIG["git_failure_exit_code"])),
        git_failure_message=str(CONFIG["git_failure_message"]),
    )


def test_dmc_1028_installer_reports_actionable_error_when_github_api_lookup_fails() -> None:
    service = build_service()

    observation = service.simulate_github_api_failure()
    stdout = service.normalized_stdout(observation.execution)
    stderr = service.normalized_stderr(observation.execution)
    combined_output = service.normalized_combined_output(observation.execution)

    assert observation.resolved_installer_url.startswith("https://"), (
        "The test must exercise the live deployed installer downloaded from GitHub rather than "
        "a local repository copy.\n"
        f"Requested URL: {observation.release_installer_url}\n"
        f"Resolved URL: {observation.resolved_installer_url}"
    )
    assert str(CONFIG["expected_resolved_installer_url_fragment"]) in observation.resolved_installer_url, (
        "The latest release download should resolve to the installer shell script asset payload.\n"
        f"Resolved URL: {observation.resolved_installer_url}"
    )
    assert observation.execution.returncode != 0, (
        "The installer should stop with a non-zero exit when both GitHub API release lookups "
        "and the git tag fallback fail.\n"
        f"stdout:\n{stdout}\n\nstderr:\n{stderr}"
    )

    for fragment in EXPECTED_OUTPUT_FRAGMENTS:
        assert fragment in combined_output, (
            "The user-visible installer output must keep the actionable GitHub API failure details "
            "described in the ticket.\n"
            f"Missing fragment: {fragment!r}\n"
            f"output:\n{combined_output}"
        )

    assert str(CONFIG["api_failure_message"]) in combined_output, (
        "The failure output should surface the curl/API error text so a user can see the actual "
        "GitHub API failure without rerunning the installer in debug mode.\n"
        f"output:\n{combined_output}"
    )
