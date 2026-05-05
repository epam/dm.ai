from __future__ import annotations

from typing import Any, Protocol

from testing.core.interfaces.publication_gate_client import PublicationGateClient


class GitHubActionsReleaseClient(PublicationGateClient, Protocol):
    def dispatch_workflow(self, workflow_id: str, *, ref: str) -> None:
        raise NotImplementedError

    def branch_head_sha(self, branch: str) -> str:
        raise NotImplementedError

    def workflow_runs_for_workflow(
        self,
        workflow_id: str,
        *,
        branch: str | None = None,
        event: str | None = None,
        per_page: int = 20,
    ) -> list[dict[str, Any]]:
        raise NotImplementedError

    def workflow_run(self, run_id: int) -> dict[str, Any]:
        raise NotImplementedError

    def list_releases(self, per_page: int = 20) -> list[dict[str, Any]]:
        raise NotImplementedError

    def release_by_tag(self, tag: str) -> dict[str, Any]:
        raise NotImplementedError
