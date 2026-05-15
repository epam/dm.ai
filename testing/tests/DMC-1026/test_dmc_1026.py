from __future__ import annotations

import os
import re
import sys
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.factories.github_actions_release_client_factory import (  # noqa: E402
    create_github_actions_release_client,
)
from testing.core.interfaces.github_actions_release_client import (  # noqa: E402
    GitHubActionsReleaseClient,
)
from testing.core.utils.ticket_config_loader import load_ticket_config  # noqa: E402


TEST_DIRECTORY = Path(__file__).resolve().parent
CONFIG = load_ticket_config(TEST_DIRECTORY / "config.yaml")
VISIBLE_VERSION_PATTERN = re.compile(r"v\d+\.\d+\.\d+")
ANSI_PATTERN = re.compile(r"\x1b\[[0-9;]*m")
TIMESTAMP_PREFIX_PATTERN = re.compile(r"^\d{4}-\d{2}-\d{2}T[^ ]+\s+")


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


def build_github_client() -> GitHubActionsReleaseClient:
    return create_github_actions_release_client(
        owner=OWNER,
        repo=REPO,
        token=os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN"),
    )


def _parse_timestamp(value: str) -> datetime:
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def _normalize_log_text(raw_log: str) -> str:
    normalized_lines: list[str] = []
    for raw_line in raw_log.splitlines():
        line = raw_line.lstrip("\ufeff")
        parts = line.split("\t", 2)
        if len(parts) == 3:
            line = parts[2]
        line = ANSI_PATTERN.sub("", line)
        line = TIMESTAMP_PREFIX_PATTERN.sub("", line)
        normalized_lines.append(line)
    return "\n".join(normalized_lines)


def _log_excerpt(text: str, limit: int = 4000) -> str:
    stripped = text.strip()
    if len(stripped) <= limit:
        return stripped
    return f"...{stripped[-limit:]}"


def _format_run(run: dict[str, Any]) -> str:
    return (
        f"id={run.get('id')} status={run.get('status')} conclusion={run.get('conclusion')} "
        f"url={run.get('html_url')}"
    )


def _wait_for_dispatched_run(
    client: GitHubActionsReleaseClient,
    *,
    existing_run_ids: set[int],
    target_head_sha: str,
    dispatch_started_at: datetime,
) -> dict[str, Any]:
    deadline = time.monotonic() + DISPATCH_TIMEOUT_SECONDS
    earliest_created_at = dispatch_started_at - timedelta(seconds=POLL_INTERVAL_SECONDS)
    last_seen_runs: list[dict[str, Any]] = []

    while time.monotonic() < deadline:
        last_seen_runs = client.workflow_runs_for_workflow(
            WORKFLOW_FILE,
            branch=WORKFLOW_REF,
            event="workflow_dispatch",
            per_page=20,
        )
        matching_runs = [
            run
            for run in last_seen_runs
            if int(run.get("id", 0)) not in existing_run_ids
            and str(run.get("head_sha", "")) == target_head_sha
            and _parse_timestamp(str(run.get("created_at", ""))) >= earliest_created_at
        ]
        if matching_runs:
            return max(matching_runs, key=lambda run: int(run["id"]))
        time.sleep(POLL_INTERVAL_SECONDS)

    observed_runs = "\n".join(_format_run(run) for run in last_seen_runs) or "No runs returned."
    raise AssertionError(
        f"The {WORKFLOW_NAME!r} workflow did not appear after dispatch on {WORKFLOW_REF!r} within "
        f"{DISPATCH_TIMEOUT_SECONDS} seconds.\nObserved runs:\n{observed_runs}"
    )


def _wait_for_completion(
    client: GitHubActionsReleaseClient,
    *,
    run_id: int,
) -> dict[str, Any]:
    deadline = time.monotonic() + COMPLETION_TIMEOUT_SECONDS
    last_seen_run = client.workflow_run(run_id)

    while time.monotonic() < deadline:
        last_seen_run = client.workflow_run(run_id)
        if str(last_seen_run.get("status")) == "completed":
            return last_seen_run
        time.sleep(POLL_INTERVAL_SECONDS)

    raise AssertionError(
        f"Workflow run {run_id} did not complete within {COMPLETION_TIMEOUT_SECONDS} seconds.\n"
        f"Last observed run: {_format_run(last_seen_run)}"
    )


def _find_job(
    client: GitHubActionsReleaseClient,
    *,
    run_id: int,
) -> dict[str, Any]:
    jobs = client.workflow_jobs(run_id)
    for job in jobs:
        if str(job.get("name")) == WORKFLOW_JOB_NAME:
            return job

    observed_jobs = ", ".join(str(job.get("name")) for job in jobs) or "No jobs returned."
    raise AssertionError(
        f"Workflow run {run_id} did not expose the expected job {WORKFLOW_JOB_NAME!r}.\n"
        f"Observed jobs: {observed_jobs}"
    )


def _step_conclusions(job: dict[str, Any]) -> dict[str, str]:
    step_conclusions: dict[str, str] = {}
    for step in job.get("steps") or []:
        step_name = str(step.get("name", "")).strip()
        if step_name:
            step_conclusions[step_name] = str(step.get("conclusion", "")).strip()
    return step_conclusions


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
    client = build_github_client()

    dispatch_started_at = datetime.now(timezone.utc)
    target_head_sha = client.branch_head_sha(WORKFLOW_REF)
    existing_run_ids = {
        int(run["id"])
        for run in client.workflow_runs_for_workflow(
            WORKFLOW_FILE,
            branch=WORKFLOW_REF,
            per_page=20,
        )
        if run.get("id") is not None
    }

    client.dispatch_workflow(WORKFLOW_FILE, ref=WORKFLOW_REF)
    workflow_run = _wait_for_dispatched_run(
        client,
        existing_run_ids=existing_run_ids,
        target_head_sha=target_head_sha,
        dispatch_started_at=dispatch_started_at,
    )
    completed_run = _wait_for_completion(client, run_id=int(workflow_run["id"]))
    job = _find_job(client, run_id=int(completed_run["id"]))
    normalized_log = _normalize_log_text(client.workflow_job_logs(int(job["id"])))
    step_conclusions = _step_conclusions(job)

    assert str(completed_run.get("conclusion")) == "success", (
        f"The live {WORKFLOW_NAME!r} workflow should complete successfully after dispatch.\n"
        f"Observed run: {_format_run(completed_run)}\n"
        f"Observed job conclusion: {job.get('conclusion')}\n"
        f"Step conclusions: {step_conclusions}\n"
        f"Visible log excerpt:\n{_log_excerpt(normalized_log)}"
    )
    assert str(job.get("conclusion")) == "success", (
        f"The workflow job {WORKFLOW_JOB_NAME!r} should complete successfully.\n"
        f"Observed job id={job.get('id')} conclusion={job.get('conclusion')} url={job.get('html_url')}\n"
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
