from __future__ import annotations

import sys
from pathlib import Path
from typing import Any


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.services.windows_installer_workflow_audit_service import (  # noqa: E402
    WindowsInstallerWorkflowAuditService,
)
from testing.core.interfaces.github_actions_release_client import (  # noqa: E402
    GitHubActionsReleaseClient,
)


class FakeGitHubActionsReleaseClient(GitHubActionsReleaseClient):
    def __init__(self) -> None:
        self.dispatch_calls: list[tuple[str, str, dict[str, object]]] = []
        self.head_sha = "abcdef1234567890"
        self.runs_by_workflow: list[dict[str, Any]] = []
        self.workflow_runs_responses: list[list[dict[str, Any]]] = []
        self.run_responses_by_id: dict[int, list[dict[str, Any]]] = {}
        self.jobs_by_run_id: dict[int, list[dict[str, Any]]] = {}
        self.logs_by_job_id: dict[int, str] = {}

    def dispatch_workflow(
        self,
        workflow_id: str,
        *,
        ref: str,
        inputs: dict[str, object] | None = None,
    ) -> None:
        self.dispatch_calls.append((workflow_id, ref, dict(inputs or {})))

    def branch_head_sha(self, branch: str) -> str:
        del branch
        return self.head_sha

    def workflow_runs_for_workflow(
        self,
        workflow_id: str,
        *,
        branch: str | None = None,
        event: str | None = None,
        per_page: int = 20,
    ) -> list[dict[str, Any]]:
        del workflow_id, branch, event, per_page
        if self.workflow_runs_responses:
            return list(self.workflow_runs_responses.pop(0))
        return list(self.runs_by_workflow)

    def workflow_run(self, run_id: int) -> dict[str, Any]:
        responses = self.run_responses_by_id.get(run_id)
        if not responses:
            raise AssertionError(f"No workflow_run response configured for run_id={run_id}.")
        if len(responses) > 1:
            return dict(responses.pop(0))
        return dict(responses[0])

    def list_releases(self, per_page: int = 20) -> list[dict[str, Any]]:
        del per_page
        return []

    def release_by_tag(self, tag: str) -> dict[str, Any]:
        raise AssertionError(f"Unexpected release_by_tag call for {tag!r}.")

    def list_recent_pull_requests(self, limit: int = 20) -> list[dict[str, Any]]:
        del limit
        return []

    def pull_request_files(self, number: int) -> list[dict[str, Any]]:
        del number
        return []

    def pull_request_reviews(self, number: int) -> list[dict[str, Any]]:
        del number
        return []

    def pull_request_issue_comments(self, number: int) -> list[dict[str, Any]]:
        del number
        return []

    def pull_request(self, number: int) -> dict[str, Any]:
        del number
        return {}

    def workflow_runs_for_head_sha(self, head_sha: str) -> list[dict[str, Any]]:
        del head_sha
        return []

    def workflow_jobs(self, run_id: int) -> list[dict[str, Any]]:
        return list(self.jobs_by_run_id.get(run_id, []))

    def workflow_job_logs(self, job_id: int) -> str:
        return self.logs_by_job_id[job_id]


def _build_service(
    client: GitHubActionsReleaseClient,
) -> WindowsInstallerWorkflowAuditService:
    return WindowsInstallerWorkflowAuditService(
        github_client=client,
        workflow_file="windows-git-bash-installer-check.yml",
        workflow_ref="main",
        workflow_name="Windows Git Bash Installer Check",
        workflow_job_name="validate-latest-installer",
        dispatch_timeout_seconds=1,
        completion_timeout_seconds=1,
        poll_interval_seconds=1,
    )


def test_service_dispatches_workflow_waits_for_completion_and_normalizes_logs() -> None:
    client = FakeGitHubActionsReleaseClient()
    workflow_run = {
        "id": 42,
        "html_url": "https://example.test/runs/42",
        "event": "workflow_dispatch",
        "status": "completed",
        "conclusion": "success",
        "head_branch": "main",
        "head_sha": client.head_sha,
        "created_at": "2099-05-15T13:00:05Z",
        "run_number": 42,
    }
    client.workflow_runs_responses = [
        [],
        [workflow_run],
    ]
    client.run_responses_by_id[42] = [workflow_run]
    client.jobs_by_run_id[42] = [
        {
            "id": 501,
            "name": "validate-latest-installer",
            "html_url": "https://example.test/jobs/501",
            "status": "completed",
            "conclusion": "success",
            "steps": [
                {"name": "Install DMTools CLI from latest release", "conclusion": "success"},
                {"name": "Validate installed wrapper and metadata", "conclusion": "success"},
            ],
        }
    ]
    client.logs_by_job_id[501] = (
        "\ufeff2026-05-15T13:00:10Z\tvalidate\t"
        "\x1b[32mFound latest CLI release: v1.7.184\x1b[0m\n"
        "2026-05-15T13:00:11Z\tvalidate\tDMTools CLI installation completed!"
    )

    audit = _build_service(client).audit()

    assert client.dispatch_calls == [("windows-git-bash-installer-check.yml", "main", {})]
    assert audit.failures == ()
    assert audit.workflow_run is not None
    assert audit.workflow_run.run_id == 42
    assert audit.workflow_job is not None
    assert audit.workflow_job.step_conclusions == {
        "Install DMTools CLI from latest release": "success",
        "Validate installed wrapper and metadata": "success",
    }
    assert "Found latest CLI release: v1.7.184" in audit.workflow_job.normalized_log_text
    assert "\x1b[32m" not in audit.workflow_job.normalized_log_text
    assert "2026-05-15T13:00:10Z" not in audit.workflow_job.normalized_log_text


def test_service_reports_failed_job_with_step_conclusions_and_excerpt() -> None:
    client = FakeGitHubActionsReleaseClient()
    discovered_run = {
        "id": 43,
        "html_url": "https://example.test/runs/43",
        "event": "workflow_dispatch",
        "status": "in_progress",
        "conclusion": "",
        "head_branch": "main",
        "head_sha": client.head_sha,
        "created_at": "2099-05-15T13:01:00Z",
        "run_number": 43,
    }
    completed_run = dict(discovered_run)
    completed_run["status"] = "completed"
    completed_run["conclusion"] = "failure"
    client.workflow_runs_responses = [
        [],
        [discovered_run],
    ]
    client.run_responses_by_id[43] = [completed_run]
    client.jobs_by_run_id[43] = [
        {
            "id": 502,
            "name": "validate-latest-installer",
            "html_url": "https://example.test/jobs/502",
            "status": "completed",
            "conclusion": "failure",
            "steps": [
                {"name": "Install DMTools CLI from latest release", "conclusion": "success"},
                {"name": "Validate installed wrapper and metadata", "conclusion": "failure"},
            ],
        }
    ]
    client.logs_by_job_id[502] = (
        "2026-05-15T13:01:05Z\tvalidate\tError: DMTools JAR file not found. Please install DMTools first:"
    )

    audit = _build_service(client).audit()

    assert len(audit.failures) == 1
    failure = audit.failures[0]
    assert failure.step == 2
    assert "conclusion='failure'" in failure.actual
    assert "Error: DMTools JAR file not found" in failure.actual
    assert audit.workflow_job is not None
    assert audit.workflow_job.conclusion == "failure"
