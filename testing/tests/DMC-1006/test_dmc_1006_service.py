from __future__ import annotations

import sys
from pathlib import Path
from typing import Any
from urllib.error import HTTPError


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.services.deprecated_workflow_run_audit_service import (  # noqa: E402
    DeprecatedWorkflowRunAuditService,
)
from testing.core.interfaces.github_actions_release_client import (  # noqa: E402
    GitHubActionsReleaseClient,
)


class FakeGitHubActionsReleaseClient(GitHubActionsReleaseClient):
    def __init__(self) -> None:
        self.dispatch_calls: list[tuple[str, str, dict[str, object]]] = []
        self.head_sha = "f625f3df36fb0f632e12d9231da356ab719462b3"
        self.workflow_runs_responses: list[list[dict[str, Any]]] = []
        self.run_by_id: dict[int, dict[str, Any]] = {}
        self.jobs_by_run_id: dict[int, list[dict[str, Any]]] = {}
        self.logs_by_job_id: dict[int, str] = {}
        self.release_by_tag_payload: dict[str, dict[str, Any]] = {}
        self.list_release_payload: list[dict[str, Any]] = []

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

    def list_releases(self, per_page: int = 20) -> list[dict[str, Any]]:
        del per_page
        return list(self.list_release_payload)

    def release_by_tag(self, tag: str) -> dict[str, Any]:
        if tag not in self.release_by_tag_payload:
            raise HTTPError(
                url=f"https://example.test/releases/tags/{tag}",
                code=404,
                msg="Not Found",
                hdrs=None,
                fp=None,
            )
        return dict(self.release_by_tag_payload[tag])

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


def _build_service(
    client: GitHubActionsReleaseClient,
    *,
    release_tag: str,
) -> DeprecatedWorkflowRunAuditService:
    return DeprecatedWorkflowRunAuditService(
        github_client=client,
        workflow_file="standalone-release.yml",
        workflow_ref="main",
        workflow_name="Create Standalone Release with Flutter SPA",
        release_job_name="create-standalone-release",
        release_tag=release_tag,
        dispatch_timeout_seconds=1,
        completion_timeout_seconds=1,
        poll_interval_seconds=1,
        required_notice_markers=("deprecated", "internal-only"),
        forbidden_strings=(),
        require_step_summary=True,
        dispatch_inputs={"release_tag": release_tag, "flutter_release_tag": "latest"},
        reuse_existing_release=False,
    )


def test_service_accepts_release_published_after_dispatch_when_draft_was_created_earlier() -> None:
    client = FakeGitHubActionsReleaseClient()
    release_tag = "v2026.0507.182304-standalone"
    dispatched_run = [
        {
            "id": 31,
            "html_url": "https://example.test/runs/31",
            "event": "workflow_dispatch",
            "status": "in_progress",
            "conclusion": "",
            "head_branch": "main",
            "head_sha": client.head_sha,
            "created_at": "2099-05-07T18:23:39Z",
            "run_number": 31,
        }
    ]
    client.workflow_runs_responses = [
        [],
        dispatched_run,
    ]
    client.run_by_id[31] = {
        "id": 31,
        "html_url": "https://example.test/runs/31",
        "event": "workflow_dispatch",
        "status": "completed",
        "conclusion": "success",
        "head_branch": "main",
        "head_sha": client.head_sha,
        "created_at": "2099-05-07T18:23:39Z",
        "run_number": 31,
    }
    client.jobs_by_run_id[31] = [
        {
            "id": 131,
            "name": "create-standalone-release",
            "html_url": "https://example.test/jobs/131",
            "status": "completed",
            "conclusion": "success",
        }
    ]
    client.logs_by_job_id[131] = (
        f"2099-05-07T18:25:37Z tag_name={release_tag}\n"
        '2099-05-07T18:26:07Z echo "Deprecated / internal-only workflow." >> $GITHUB_STEP_SUMMARY\n'
    )
    client.release_by_tag_payload[release_tag] = {
        "tag_name": release_tag,
        "html_url": f"https://example.test/releases/{release_tag}",
        "body": (
            "> **Deprecated / internal-only workflow.**\n"
            "Do not treat this release body as installation guidance."
        ),
        "created_at": "2099-05-07T17:05:10Z",
        "published_at": "2099-05-07T18:25:37Z",
    }

    audit = _build_service(client, release_tag=release_tag).audit()

    assert client.dispatch_calls == [
        (
            "standalone-release.yml",
            "main",
            {"release_tag": release_tag, "flutter_release_tag": "latest"},
        )
    ]
    assert audit.release is not None
    assert audit.release.tag_name == release_tag
    assert audit.failures == ()


def test_service_accepts_release_from_list_fallback_when_draft_was_created_earlier() -> None:
    client = FakeGitHubActionsReleaseClient()
    release_tag = "v2026.0507.182304-standalone"
    dispatched_run = [
        {
            "id": 32,
            "html_url": "https://example.test/runs/32",
            "event": "workflow_dispatch",
            "status": "in_progress",
            "conclusion": "",
            "head_branch": "main",
            "head_sha": client.head_sha,
            "created_at": "2099-05-07T18:23:39Z",
            "run_number": 32,
        }
    ]
    client.workflow_runs_responses = [
        [],
        dispatched_run,
    ]
    client.run_by_id[32] = {
        "id": 32,
        "html_url": "https://example.test/runs/32",
        "event": "workflow_dispatch",
        "status": "completed",
        "conclusion": "success",
        "head_branch": "main",
        "head_sha": client.head_sha,
        "created_at": "2099-05-07T18:23:39Z",
        "run_number": 32,
    }
    client.jobs_by_run_id[32] = [
        {
            "id": 132,
            "name": "create-standalone-release",
            "html_url": "https://example.test/jobs/132",
            "status": "completed",
            "conclusion": "success",
        }
    ]
    client.logs_by_job_id[132] = (
        f"2099-05-07T18:25:37Z tag_name={release_tag}\n"
        '2099-05-07T18:26:07Z echo "Deprecated / internal-only workflow." >> $GITHUB_STEP_SUMMARY\n'
    )
    client.list_release_payload = [
        {
            "tag_name": release_tag,
            "html_url": f"https://example.test/releases/{release_tag}",
            "body": (
                "> **Deprecated / internal-only workflow.**\n"
                "Do not treat this release body as installation guidance."
            ),
            "created_at": "2099-05-07T17:05:10Z",
            "published_at": "2099-05-07T18:25:37Z",
        }
    ]

    audit = _build_service(client, release_tag=release_tag).audit()

    assert client.dispatch_calls == [
        (
            "standalone-release.yml",
            "main",
            {"release_tag": release_tag, "flutter_release_tag": "latest"},
        )
    ]
    assert audit.release is not None
    assert audit.release.tag_name == release_tag
    assert audit.failures == ()
