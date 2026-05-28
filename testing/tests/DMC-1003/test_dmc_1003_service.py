from __future__ import annotations

import sys
from pathlib import Path
from typing import Any


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
        self.head_sha = "abcdef1234567890"
        self.runs_by_workflow: list[dict[str, Any]] = []
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
        return dict(self.run_by_id[run_id])

    def list_releases(self, per_page: int = 20) -> list[dict[str, Any]]:
        del per_page
        return list(self.list_release_payload)

    def release_by_tag(self, tag: str) -> dict[str, Any]:
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
    require_step_summary: bool,
    reuse_existing_release: bool = False,
) -> DeprecatedWorkflowRunAuditService:
    return DeprecatedWorkflowRunAuditService(
        github_client=client,
        workflow_file="standalone-release-auto.yml",
        workflow_ref="main",
        workflow_name="Auto Standalone Release",
        release_job_name="auto-standalone-release",
        release_tag=release_tag,
        dispatch_timeout_seconds=1,
        completion_timeout_seconds=1,
        poll_interval_seconds=1,
        required_notice_markers=("deprecated", "internal-only"),
        forbidden_strings=("install.sh", "install.ps1", "skill-install.sh"),
        require_step_summary=require_step_summary,
        dispatch_inputs=None,
        reuse_existing_release=reuse_existing_release,
    )


def test_service_reuses_existing_release_outputs_without_dispatch() -> None:
    client = FakeGitHubActionsReleaseClient()
    client.runs_by_workflow = [
        {
            "id": 17,
            "html_url": "https://example.test/runs/17",
            "event": "workflow_dispatch",
            "status": "completed",
            "conclusion": "success",
            "head_branch": "main",
            "head_sha": client.head_sha,
            "created_at": "2026-05-05T20:00:00Z",
            "run_number": 17,
        }
    ]
    client.run_by_id[17] = dict(client.runs_by_workflow[0])
    client.jobs_by_run_id[17] = [
        {
            "id": 101,
            "name": "auto-standalone-release",
            "html_url": "https://example.test/jobs/101",
            "status": "completed",
            "conclusion": "success",
        }
    ]
    client.logs_by_job_id[101] = "job log without summary output"
    client.release_by_tag_payload["v2026.05.05-standalone-abcdef1"] = {
        "tag_name": "v2026.05.05-standalone-abcdef1",
        "html_url": "https://example.test/releases/v2026.05.05-standalone-abcdef1",
        "body": (
            "> **Deprecated / internal-only workflow.**\n"
            "Do not treat this release body as installation guidance."
        ),
        "created_at": "2026-05-05T20:00:00Z",
    }

    audit = _build_service(
        client,
        release_tag="v2026.05.05-standalone-abcdef1",
        require_step_summary=False,
        reuse_existing_release=True,
    ).audit()

    assert client.dispatch_calls == []
    assert audit.workflow_run is not None
    assert audit.release is not None
    assert audit.release_job is not None
    assert audit.failures == ()


def test_service_reports_removed_installer_strings_in_step_summary() -> None:
    client = FakeGitHubActionsReleaseClient()
    dispatched_run = [
        {
            "id": 18,
            "html_url": "https://example.test/runs/18",
            "event": "workflow_dispatch",
            "status": "in_progress",
            "conclusion": "",
            "head_branch": "main",
            "head_sha": client.head_sha,
            "created_at": "2099-05-05T20:01:00Z",
            "run_number": 18,
        }
    ]
    client.workflow_runs_responses = [
        [],
        dispatched_run,
    ]
    client.run_by_id[18] = {
        "id": 18,
        "html_url": "https://example.test/runs/18",
        "event": "workflow_dispatch",
        "status": "completed",
        "conclusion": "success",
        "head_branch": "main",
        "head_sha": client.head_sha,
        "created_at": "2099-05-05T20:01:00Z",
        "run_number": 18,
    }
    client.jobs_by_run_id[18] = [
        {
            "id": 102,
            "name": "auto-standalone-release",
            "html_url": "https://example.test/jobs/102",
            "status": "completed",
            "conclusion": "success",
        }
    ]
    client.logs_by_job_id[102] = (
        '2026-05-05T20:02:00Z echo "Deprecated/internal-only packaging workflow" >> $GITHUB_STEP_SUMMARY\n'
        '2026-05-05T20:02:01Z echo "curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install.sh | bash" >> $GITHUB_STEP_SUMMARY'
    )
    client.release_by_tag_payload["v2026.05.05-standalone-abcdef1"] = {
        "tag_name": "v2026.05.05-standalone-abcdef1",
        "html_url": "https://example.test/releases/v2026.05.05-standalone-abcdef1",
        "body": (
            "> **Deprecated / internal-only workflow.**\n"
            "Do not treat this release body as installation guidance."
        ),
        "created_at": "2099-05-05T20:02:00Z",
    }

    audit = _build_service(
        client,
        release_tag="v2026.05.05-standalone-abcdef1",
        require_step_summary=True,
    ).audit()

    assert client.dispatch_calls == [("standalone-release-auto.yml", "main", {})]
    assert audit.release_job is not None
    assert audit.failures
    assert any("install.sh" in failure.actual for failure in audit.failures)


def test_service_discovers_release_tag_from_logs_when_not_predicted() -> None:
    client = FakeGitHubActionsReleaseClient()
    dispatched_run = [
        {
            "id": 19,
            "html_url": "https://example.test/runs/19",
            "event": "workflow_dispatch",
            "status": "in_progress",
            "conclusion": "",
            "head_branch": "main",
            "head_sha": client.head_sha,
            "created_at": "2099-05-05T23:59:58Z",
            "run_number": 19,
        }
    ]
    client.workflow_runs_responses = [
        [],
        dispatched_run,
    ]
    client.run_by_id[19] = {
        "id": 19,
        "html_url": "https://example.test/runs/19",
        "event": "workflow_dispatch",
        "status": "completed",
        "conclusion": "success",
        "head_branch": "main",
        "head_sha": client.head_sha,
        "created_at": "2099-05-05T23:59:58Z",
        "run_number": 19,
    }
    client.jobs_by_run_id[19] = [
        {
            "id": 103,
            "name": "auto-standalone-release",
            "html_url": "https://example.test/jobs/103",
            "status": "completed",
            "conclusion": "success",
        }
    ]
    client.logs_by_job_id[103] = (
        "2099-05-06T00:00:01Z tag_name=v2099.05.06-standalone-abcdef1\n"
        '2099-05-06T00:00:02Z echo "Deprecated/internal-only packaging workflow" >> $GITHUB_STEP_SUMMARY\n'
    )
    client.release_by_tag_payload["v2099.05.06-standalone-abcdef1"] = {
        "tag_name": "v2099.05.06-standalone-abcdef1",
        "html_url": "https://example.test/releases/v2099.05.06-standalone-abcdef1",
        "body": (
            "> **Deprecated / internal-only workflow.**\n"
            "Do not treat this release body as installation guidance."
        ),
        "created_at": "2099-05-06T00:00:03Z",
    }

    audit = _build_service(
        client,
        release_tag="",
        require_step_summary=True,
    ).audit()

    assert client.dispatch_calls == [("standalone-release-auto.yml", "main", {})]
    assert audit.release is not None
    assert audit.release.tag_name == "v2099.05.06-standalone-abcdef1"
    assert audit.failures == ()

def test_service_uses_published_at_when_release_created_at_matches_older_target_commit() -> None:
    client = FakeGitHubActionsReleaseClient()
    dispatched_run = [
        {
            "id": 20,
            "html_url": "https://example.test/runs/20",
            "event": "workflow_dispatch",
            "status": "in_progress",
            "conclusion": "",
            "head_branch": "main",
            "head_sha": client.head_sha,
            "created_at": "2099-05-06T12:00:00Z",
            "run_number": 20,
        }
    ]
    client.workflow_runs_responses = [
        [],
        dispatched_run,
    ]
    client.run_by_id[20] = {
        "id": 20,
        "html_url": "https://example.test/runs/20",
        "event": "workflow_dispatch",
        "status": "completed",
        "conclusion": "success",
        "head_branch": "main",
        "head_sha": client.head_sha,
        "created_at": "2099-05-06T12:00:00Z",
        "run_number": 20,
    }
    client.jobs_by_run_id[20] = [
        {
            "id": 104,
            "name": "auto-standalone-release",
            "html_url": "https://example.test/jobs/104",
            "status": "completed",
            "conclusion": "success",
        }
    ]
    client.logs_by_job_id[104] = (
        '2099-05-06T12:03:00Z echo "**Release:** [v2099.05.06-standalone-abcdef2]'
        '(https://github.com/example/releases/tag/v2099.05.06-standalone-abcdef2)" >> $GITHUB_STEP_SUMMARY\n'
        '2099-05-06T12:03:01Z echo "**Positioning:** Deprecated/internal-only packaging workflow" '
        '>> $GITHUB_STEP_SUMMARY\n'
    )
    client.release_by_tag_payload["v2099.05.06-standalone-abcdef2"] = {
        "tag_name": "v2099.05.06-standalone-abcdef2",
        "html_url": "https://example.test/releases/v2099.05.06-standalone-abcdef2",
        "body": (
            "> **Deprecated / internal-only workflow.**\n"
            "Do not treat this release body as installation guidance."
        ),
        "created_at": "2099-05-06T10:00:00Z",
        "published_at": "2099-05-06T12:03:02Z",
    }

    audit = _build_service(
        client,
        release_tag="v2099.05.06-standalone-abcdef2",
        require_step_summary=True,
    ).audit()

    assert client.dispatch_calls == [("standalone-release-auto.yml", "main", {})]
    assert audit.release is not None
    assert audit.release.tag_name == "v2099.05.06-standalone-abcdef2"
    assert audit.failures == ()


def test_service_prefers_quoted_step_summary_tag_over_untagged_release_url() -> None:
    client = FakeGitHubActionsReleaseClient()
    dispatched_run = [
        {
            "id": 21,
            "html_url": "https://example.test/runs/21",
            "event": "workflow_dispatch",
            "status": "in_progress",
            "conclusion": "",
            "head_branch": "main",
            "head_sha": client.head_sha,
            "created_at": "2099-05-06T13:00:00Z",
            "run_number": 21,
        }
    ]
    client.workflow_runs_responses = [
        [],
        dispatched_run,
    ]
    client.run_by_id[21] = {
        "id": 21,
        "html_url": "https://example.test/runs/21",
        "event": "workflow_dispatch",
        "status": "completed",
        "conclusion": "success",
        "head_branch": "main",
        "head_sha": client.head_sha,
        "created_at": "2099-05-06T13:00:00Z",
        "run_number": 21,
    }
    client.jobs_by_run_id[21] = [
        {
            "id": 105,
            "name": "auto-standalone-release",
            "html_url": "https://example.test/jobs/105",
            "status": "completed",
            "conclusion": "success",
        }
    ]
    client.logs_by_job_id[105] = (
        "2099-05-06T13:03:00Z https://github.com/example/releases/tag/untagged-deadbeef\n"
        '2099-05-06T13:03:01Z echo "**Release:** [v2099.05.06-standalone-abcdef3]'
        '(https://github.com/example/releases/tag/v2099.05.06-standalone-abcdef3)" '
        '>> "$GITHUB_STEP_SUMMARY"\n'
        '2099-05-06T13:03:02Z echo "**Positioning:** Deprecated/internal-only packaging workflow" '
        '>> "$GITHUB_STEP_SUMMARY"\n'
    )
    client.release_by_tag_payload["v2099.05.06-standalone-abcdef3"] = {
        "tag_name": "v2099.05.06-standalone-abcdef3",
        "html_url": "https://example.test/releases/v2099.05.06-standalone-abcdef3",
        "body": (
            "> **Deprecated / internal-only workflow.**\n"
            "Do not treat this release body as installation guidance."
        ),
        "created_at": "2099-05-06T11:00:00Z",
        "published_at": "2099-05-06T13:03:03Z",
    }

    audit = _build_service(
        client,
        release_tag="",
        require_step_summary=True,
    ).audit()

    assert client.dispatch_calls == [("standalone-release-auto.yml", "main", {})]
    assert audit.release is not None
    assert audit.release.tag_name == "v2099.05.06-standalone-abcdef3"
    assert audit.release_job is not None
    assert "v2099.05.06-standalone-abcdef3" in audit.release_job.step_summary_markdown
    assert audit.failures == ()


def test_service_exposes_release_job_steps_and_full_log_text() -> None:
    client = FakeGitHubActionsReleaseClient()
    dispatched_run = [
        {
            "id": 22,
            "html_url": "https://example.test/runs/22",
            "event": "workflow_dispatch",
            "status": "in_progress",
            "conclusion": "",
            "head_branch": "main",
            "head_sha": client.head_sha,
            "created_at": "2099-05-06T10:00:00Z",
            "run_number": 22,
        }
    ]
    client.workflow_runs_responses = [
        [],
        dispatched_run,
    ]
    client.run_by_id[22] = {
        "id": 22,
        "html_url": "https://example.test/runs/22",
        "event": "workflow_dispatch",
        "status": "completed",
        "conclusion": "success",
        "head_branch": "main",
        "head_sha": client.head_sha,
        "created_at": "2099-05-06T10:00:00Z",
        "run_number": 22,
    }
    client.jobs_by_run_id[22] = [
        {
            "id": 106,
            "name": "auto-standalone-release",
            "html_url": "https://example.test/jobs/106",
            "status": "completed",
            "conclusion": "success",
            "steps": [
                {"name": "Build deprecated compatibility artifact", "conclusion": "success"},
                {"name": "Summary", "conclusion": "success"},
            ],
        }
    ]
    client.logs_by_job_id[106] = (
        "2099-05-06T10:00:01Z Task :dmtools-core:test FAILED\n"
        '2099-05-06T10:00:02Z echo "Deprecated/internal-only packaging workflow" >> $GITHUB_STEP_SUMMARY\n'
        "2099-05-06T10:00:03Z tag_name=v2099.05.06-standalone-abcdef4\n"
    )
    client.release_by_tag_payload["v2099.05.06-standalone-abcdef4"] = {
        "tag_name": "v2099.05.06-standalone-abcdef4",
        "html_url": "https://example.test/releases/v2099.05.06-standalone-abcdef4",
        "body": (
            "> **Deprecated / internal-only workflow.**\n"
            "Do not treat this release body as installation guidance."
        ),
        "created_at": "2099-05-06T10:00:04Z",
    }

    audit = _build_service(
        client,
        release_tag="",
        require_step_summary=True,
    ).audit()

    assert audit.release_job is not None
    assert audit.release_job.raw_log_text == client.logs_by_job_id[106]
    assert audit.release_job.step_conclusions == {
        "Build deprecated compatibility artifact": "success",
        "Summary": "success",
    }


def test_service_extracts_step_summary_lines_when_github_summary_path_is_quoted() -> None:
    client = FakeGitHubActionsReleaseClient()
    dispatched_run = [
        {
            "id": 23,
            "html_url": "https://example.test/runs/23",
            "event": "workflow_dispatch",
            "status": "in_progress",
            "conclusion": "",
            "head_branch": "main",
            "head_sha": client.head_sha,
            "created_at": "2099-05-06T11:00:00Z",
            "run_number": 23,
        }
    ]
    client.workflow_runs_responses = [
        [],
        dispatched_run,
    ]
    client.run_by_id[23] = {
        "id": 23,
        "html_url": "https://example.test/runs/23",
        "event": "workflow_dispatch",
        "status": "completed",
        "conclusion": "success",
        "head_branch": "main",
        "head_sha": client.head_sha,
        "created_at": "2099-05-06T11:00:00Z",
        "run_number": 23,
    }
    client.jobs_by_run_id[23] = [
        {
            "id": 107,
            "name": "auto-standalone-release",
            "html_url": "https://example.test/jobs/107",
            "status": "completed",
            "conclusion": "success",
        }
    ]
    client.logs_by_job_id[107] = (
        '2099-05-06T11:00:01Z echo "Deprecated/internal-only packaging workflow" >> "$GITHUB_STEP_SUMMARY"\n'
        '2099-05-06T11:00:02Z echo "> Do not reuse this workflow summary as customer-facing installation guidance." >> "$GITHUB_STEP_SUMMARY"\n'
    )
    client.release_by_tag_payload["v2099.05.06-standalone-abcdef5"] = {
        "tag_name": "v2099.05.06-standalone-abcdef5",
        "html_url": "https://example.test/releases/v2099.05.06-standalone-abcdef5",
        "body": (
            "> **Deprecated / internal-only workflow.**\n"
            "Do not treat this release body as installation guidance."
        ),
        "created_at": "2099-05-06T11:00:03Z",
    }

    audit = _build_service(
        client,
        release_tag="v2099.05.06-standalone-abcdef5",
        require_step_summary=True,
    ).audit()

    assert audit.release_job is not None
    assert audit.release_job.step_summary_markdown == (
        "Deprecated/internal-only packaging workflow\n"
        "> Do not reuse this workflow summary as customer-facing installation guidance."
    )


def test_service_accepts_release_published_after_dispatch_even_when_created_at_is_older() -> None:
    client = FakeGitHubActionsReleaseClient()
    dispatched_run = [
        {
            "id": 24,
            "html_url": "https://example.test/runs/24",
            "event": "workflow_dispatch",
            "status": "in_progress",
            "conclusion": "",
            "head_branch": "main",
            "head_sha": client.head_sha,
            "created_at": "2099-05-06T12:00:00Z",
            "run_number": 24,
        }
    ]
    client.workflow_runs_responses = [
        [],
        dispatched_run,
    ]
    client.run_by_id[24] = {
        "id": 24,
        "html_url": "https://example.test/runs/24",
        "event": "workflow_dispatch",
        "status": "completed",
        "conclusion": "success",
        "head_branch": "main",
        "head_sha": client.head_sha,
        "created_at": "2099-05-06T12:00:00Z",
        "run_number": 24,
    }
    client.jobs_by_run_id[24] = [
        {
            "id": 108,
            "name": "auto-standalone-release",
            "html_url": "https://example.test/jobs/108",
            "status": "completed",
            "conclusion": "success",
        }
    ]
    client.logs_by_job_id[108] = (
        '2099-05-06T12:00:01Z echo "Deprecated/internal-only packaging workflow" >> $GITHUB_STEP_SUMMARY\n'
        "2099-05-06T12:00:02Z tag_name=v2099.05.06-standalone-abcdef6\n"
    )
    client.release_by_tag_payload["v2099.05.06-standalone-abcdef6"] = {
        "tag_name": "v2099.05.06-standalone-abcdef6",
        "html_url": "https://example.test/releases/v2099.05.06-standalone-abcdef6",
        "body": (
            "> **Deprecated / internal-only workflow.**\n"
            "Do not treat this release body as installation guidance."
        ),
        "created_at": "2099-05-06T11:00:00Z",
        "published_at": "2099-05-06T12:00:03Z",
    }

    audit = _build_service(
        client,
        release_tag="v2099.05.06-standalone-abcdef6",
        require_step_summary=True,
    ).audit()

    assert audit.release is not None
    assert audit.release.tag_name == "v2099.05.06-standalone-abcdef6"
    assert audit.failures == ()
