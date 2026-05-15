from __future__ import annotations

import os
import re
import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.factories.windows_installer_workflow_audit_service_factory import (  # noqa: E402
    create_windows_installer_workflow_audit_service,
)
from testing.core.interfaces.windows_installer_workflow_audit_service import (  # noqa: E402
    WindowsInstallerWorkflowAuditService,
)
from testing.core.utils.ticket_config_loader import load_ticket_config  # noqa: E402


TEST_DIRECTORY = Path(__file__).resolve().parent
CONFIG = load_ticket_config(TEST_DIRECTORY / "config.yaml")
VISIBLE_VERSION_PATTERN = re.compile(r"v\d+\.\d+\.\d+")


def _config_int(key: str) -> int:
    return int(str(CONFIG[key]))


OWNER = str(CONFIG["owner"])
REPO = str(CONFIG["repo"])
WORKFLOW_REF = str(CONFIG["workflow_ref"])
WORKFLOW_FILE = str(CONFIG["workflow_file"])
WORKFLOW_NAME = str(CONFIG["workflow_name"])
WORKFLOW_JOB_NAME = str(CONFIG["workflow_job_name"])
DISPATCH_TIMEOUT_SECONDS = _config_int("dispatch_timeout_seconds")
COMPLETION_TIMEOUT_SECONDS = _config_int("completion_timeout_seconds")
POLL_INTERVAL_SECONDS = _config_int("poll_interval_seconds")


def _log_excerpt(text: str, limit: int = 4000) -> str:
    stripped = text.strip()
    if len(stripped) <= limit:
        return stripped
    return f"...{stripped[-limit:]}"


def build_workflow_audit_service() -> WindowsInstallerWorkflowAuditService:
    return create_windows_installer_workflow_audit_service(
        owner=OWNER,
        repo=REPO,
        workflow_file=WORKFLOW_FILE,
        workflow_ref=WORKFLOW_REF,
        workflow_name=WORKFLOW_NAME,
        workflow_job_name=WORKFLOW_JOB_NAME,
        dispatch_timeout_seconds=DISPATCH_TIMEOUT_SECONDS,
        completion_timeout_seconds=COMPLETION_TIMEOUT_SECONDS,
        poll_interval_seconds=POLL_INTERVAL_SECONDS,
        token=os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN"),
    )


def _assert_visible_installer_behavior(normalized_log: str) -> None:
    assert (
        "curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install.sh | bash"
        in normalized_log
    ), (
        "The Windows workflow must exercise the documented Git Bash installer command.\n"
        f"Visible log excerpt:\n{_log_excerpt(normalized_log)}"
    )
    assert (
        "shell: C:\\Program Files\\Git\\bin\\bash.EXE" in normalized_log
    ), (
        "The installer run must execute under Git Bash on a Windows runner.\n"
        f"Visible log excerpt:\n{_log_excerpt(normalized_log)}"
    )
    assert "Fetching latest CLI release information..." in normalized_log, (
        "A user should see the latest-release lookup stage in the Git Bash installer output.\n"
        f"Visible log excerpt:\n{_log_excerpt(normalized_log)}"
    )

    found_release_match = re.search(
        r"Found latest CLI release:\s*(?P<version>v\d+\.\d+\.\d+)",
        normalized_log,
    )
    latest_version_match = re.search(
        r"Latest version:\s*(?P<version>v\d+\.\d+\.\d+)",
        normalized_log,
    )
    assert found_release_match is not None, (
        "The installer output should show the resolved stable CLI release tag.\n"
        f"Visible log excerpt:\n{_log_excerpt(normalized_log)}"
    )
    assert latest_version_match is not None, (
        "The installer output should print the selected latest version after lookup.\n"
        f"Visible log excerpt:\n{_log_excerpt(normalized_log)}"
    )
    assert found_release_match.group("version") == latest_version_match.group("version"), (
        "The visible release resolution and selected latest version must match.\n"
        f"Resolved: {found_release_match.group('version')}\n"
        f"Selected: {latest_version_match.group('version')}\n"
        f"Visible log excerpt:\n{_log_excerpt(normalized_log)}"
    )
    assert VISIBLE_VERSION_PATTERN.fullmatch(found_release_match.group("version")), (
        "The resolved version must remain on a stable vX.Y.Z tag.\n"
        f"Resolved version: {found_release_match.group('version')}"
    )

    forbidden_fragments = (
        "GitHub API failed (exit code: 22)",
        "Failed to find latest CLI release from GitHub API.",
        "Warning: Installation completed but dmtools command test failed.",
        "Error: DMTools JAR file not found. Please install DMTools first:",
    )
    for forbidden_fragment in forbidden_fragments:
        assert forbidden_fragment not in normalized_log, (
            "The Windows Git Bash install path must finish without the historical release-lookup "
            f"failure or a broken wrapper validation message {forbidden_fragment!r}.\n"
            f"Visible log excerpt:\n{_log_excerpt(normalized_log)}"
        )

    assert "DMTools CLI installation completed!" in normalized_log, (
        "The installer output should end with the success completion message.\n"
        f"Visible log excerpt:\n{_log_excerpt(normalized_log)}"
    )


def test_dmc_1026_windows_git_bash_latest_installer_path_resolves_and_installs_successfully() -> None:
    service = build_workflow_audit_service()
    audit = service.audit()

    if audit.failures:
        raise AssertionError(service.format_failures(audit.failures))

    assert audit.workflow_run is not None, "Expected a workflow run observation."
    assert audit.workflow_job is not None, "Expected a workflow job observation."

    normalized_log = audit.workflow_job.normalized_log_text
    step_conclusions = audit.workflow_job.step_conclusions

    assert audit.workflow_run.conclusion == "success", (
        f"The live {WORKFLOW_NAME!r} workflow should complete successfully after dispatch.\n"
        f"Observed run: id={audit.workflow_run.run_id} status={audit.workflow_run.status} "
        f"conclusion={audit.workflow_run.conclusion} url={audit.workflow_run.html_url}\n"
        f"Observed job conclusion: {audit.workflow_job.conclusion}\n"
        f"Step conclusions: {step_conclusions}\n"
        f"Visible log excerpt:\n{_log_excerpt(normalized_log)}"
    )
    assert audit.workflow_job.conclusion == "success", (
        f"The workflow job {WORKFLOW_JOB_NAME!r} should complete successfully.\n"
        f"Observed job id={audit.workflow_job.job_id} conclusion={audit.workflow_job.conclusion} "
        f"url={audit.workflow_job.html_url}\n"
        f"Step conclusions: {step_conclusions}\n"
        f"Visible log excerpt:\n{_log_excerpt(normalized_log)}"
    )
    assert step_conclusions.get("Install DMTools CLI from latest release") == "success", (
        "The installer step itself should succeed on Windows Git Bash.\n"
        f"Step conclusions: {step_conclusions}\n"
        f"Visible log excerpt:\n{_log_excerpt(normalized_log)}"
    )
    assert step_conclusions.get("Validate installed wrapper and metadata") == "success", (
        "The installed wrapper and metadata validation must succeed so the user can actually use "
        "the installed CLI after the documented Git Bash flow.\n"
        f"Step conclusions: {step_conclusions}\n"
        f"Visible log excerpt:\n{_log_excerpt(normalized_log)}"
    )

    _assert_visible_installer_behavior(normalized_log)
