from __future__ import annotations

from typing import Any, Protocol


class PublicationGateClient(Protocol):
    def list_recent_pull_requests(self, limit: int = 20) -> list[dict[str, Any]]:
        raise NotImplementedError

    def pull_request_files(self, number: int) -> list[dict[str, Any]]:
        raise NotImplementedError

    def pull_request_reviews(self, number: int) -> list[dict[str, Any]]:
        raise NotImplementedError

    def pull_request_issue_comments(self, number: int) -> list[dict[str, Any]]:
        raise NotImplementedError

    def pull_request(self, number: int) -> dict[str, Any]:
        raise NotImplementedError

    def workflow_runs_for_head_sha(self, head_sha: str) -> list[dict[str, Any]]:
        raise NotImplementedError

    def workflow_jobs(self, run_id: int) -> list[dict[str, Any]]:
        raise NotImplementedError

    def workflow_job_logs(self, job_id: int) -> str:
        raise NotImplementedError
