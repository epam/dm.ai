from __future__ import annotations

import sys
from pathlib import Path
from typing import Any


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.services.workflow_dispatch_validation_service import (  # noqa: E402
    WorkflowDispatchValidationService,
)
from testing.core.interfaces.github_actions_release_client import (  # noqa: E402
    GitHubActionsReleaseClient,
)


class FakeGitHubActionsReleaseClient(GitHubActionsReleaseClient):
    def __init__(self) -> None:
        self.dispatch_calls: list[tuple[str, str, dict[str, object]]] = []
        self.head_sha = "abcdef1234567890"
        self.workflow_runs_responses: list[list[dict[str, Any]]] = []
        self.run_by_id: dict[int, dict[str, Any]] = {}
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
        return []

    def workflow_run(self, run_id: int) -> dict[str, Any]:
        return dict(self.run_by_id[run_id])

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
        return list(self.jobs_by_run_id[run_id])

    def workflow_job_logs(self, job_id: int) -> str:
        return self.logs_by_job_id[job_id]

    def list_releases(self, per_page: int = 20) -> list[dict[str, Any]]:
        del per_page
        return []

    def release_by_tag(self, tag: str) -> dict[str, Any]:
        raise AssertionError(f"Unexpected release lookup for {tag!r}")


def _build_service(client: GitHubActionsReleaseClient) -> WorkflowDispatchValidationService:
    return WorkflowDispatchValidationService(
        github_client=client,
        workflow_file="windows-git-bash-installer-check.yml",
        workflow_ref="main",
        workflow_name="Windows Git Bash Installer Check",
        workflow_job_name="validate-latest-installer",
        dispatch_timeout_seconds=1,
        completion_timeout_seconds=1,
        poll_interval_seconds=1,
        required_step_names=(
            "Set up Java 17",
            "Install DMTools CLI from latest release",
            "Validate installed wrapper and metadata",
        ),
        required_log_fragments=(
            "releases/latest/download/install.sh | bash",
            "DMTOOLS_INSTALL_DIR/dmtools.jar",
            "DMTOOLS_BIN_DIR/dmtools",
        ),
    )


def test_service_keeps_collecting_failed_run_step_and_log_evidence() -> None:
    client = FakeGitHubActionsReleaseClient()
    dispatched_run = {
        "id": 501,
        "html_url": "https://example.test/actions/runs/501",
        "event": "workflow_dispatch",
        "status": "completed",
        "conclusion": "failure",
        "head_branch": "main",
        "head_sha": client.head_sha,
        "created_at": "2099-05-15T12:00:00Z",
        "run_number": 44,
    }
    client.workflow_runs_responses = [
        [],
        [dispatched_run],
    ]
    client.run_by_id[501] = dict(dispatched_run)
    client.jobs_by_run_id[501] = [
        {
            "id": 601,
            "name": "validate-latest-installer",
            "html_url": "https://example.test/actions/runs/501/job/601",
            "status": "completed",
            "conclusion": "failure",
            "steps": [
                {"name": "Set up Java 17", "conclusion": "success"},
                {
                    "name": "Install DMTools CLI from latest release",
                    "conclusion": "success",
                },
                {
                    "name": "Validate installed wrapper and metadata",
                    "conclusion": "failure",
                },
            ],
        }
    ]
    client.logs_by_job_id[601] = (
        "Run curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install.sh | bash\n"
        'Run test -f "$DMTOOLS_INSTALL_DIR/dmtools.jar"\n'
        'Run "$DMTOOLS_BIN_DIR/dmtools" --help >/dev/null\n'
    )

    audit = _build_service(client).audit()

    assert client.dispatch_calls == [
        ("windows-git-bash-installer-check.yml", "main", {}),
    ]
    assert audit.workflow_run is not None
    assert audit.workflow_job is not None
    assert audit.workflow_job.step_conclusions == {
        "Set up Java 17": "success",
        "Install DMTools CLI from latest release": "success",
        "Validate installed wrapper and metadata": "failure",
    }

    failure_steps = [failure.step for failure in audit.failures]
    assert failure_steps == [2, 4, 6]
    assert all(failure.step != 5 for failure in audit.failures)
    assert all(failure.step != 7 for failure in audit.failures)
    assert "releases/latest/download/install.sh | bash" in audit.workflow_job.raw_log_text


def test_service_selects_the_earliest_new_run_after_dispatch_even_when_head_sha_changes() -> None:
    client = FakeGitHubActionsReleaseClient()
    client.head_sha = "before-dispatch-sha"

    dispatched_run = {
        "id": 701,
        "html_url": "https://example.test/actions/runs/701",
        "event": "workflow_dispatch",
        "status": "completed",
        "conclusion": "success",
        "head_branch": "main",
        "head_sha": "after-dispatch-sha",
        "created_at": "2099-05-15T12:00:00Z",
        "run_number": 70,
    }
    later_run = {
        "id": 702,
        "html_url": "https://example.test/actions/runs/702",
        "event": "workflow_dispatch",
        "status": "completed",
        "conclusion": "success",
        "head_branch": "main",
        "head_sha": "even-later-sha",
        "created_at": "2099-05-15T12:00:02Z",
        "run_number": 71,
    }
    client.workflow_runs_responses = [
        [],
        [later_run, dispatched_run],
    ]
    client.run_by_id[701] = dict(dispatched_run)
    client.jobs_by_run_id[701] = [
        {
            "id": 801,
            "name": "validate-latest-installer",
            "html_url": "https://example.test/actions/runs/701/job/801",
            "status": "completed",
            "conclusion": "success",
            "steps": [
                {"name": "Set up Java 17", "conclusion": "success"},
                {
                    "name": "Install DMTools CLI from latest release",
                    "conclusion": "success",
                },
                {
                    "name": "Validate installed wrapper and metadata",
                    "conclusion": "success",
                },
            ],
        }
    ]
    client.logs_by_job_id[801] = (
        "Run curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install.sh | bash\n"
        'Run test -f "$DMTOOLS_INSTALL_DIR/dmtools.jar"\n'
        'Run "$DMTOOLS_BIN_DIR/dmtools" --help >/dev/null\n'
    )

    audit = _build_service(client).audit()

    assert audit.failures == ()
    assert audit.workflow_run is not None
    assert audit.workflow_job is not None
    assert audit.workflow_run.run_id == 701
    assert audit.workflow_run.head_sha == "after-dispatch-sha"
    assert audit.workflow_job.conclusion == "success"
