from __future__ import annotations

from typing import Any, Protocol


class StableReleaseAuditClient(Protocol):
    def list_releases(self, limit: int = 20) -> list[dict[str, Any]]:
        raise NotImplementedError

    def workflow_runs(
        self,
        workflow_file: str,
        *,
        branch: str | None = None,
        event: str | None = None,
        status: str | None = None,
        limit: int = 20,
    ) -> list[dict[str, Any]]:
        raise NotImplementedError

    def workflow_jobs(self, run_id: int) -> list[dict[str, Any]]:
        raise NotImplementedError

    def workflow_job_logs(self, job_id: int) -> str:
        raise NotImplementedError
