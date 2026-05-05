from __future__ import annotations

import json
from dataclasses import dataclass
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from testing.frameworks.api.rest.github_publication_gate_client import (
    GitHubPublicationGateRestClient,
)


@dataclass(frozen=True)
class GitHubActionsReleaseRestClient(GitHubPublicationGateRestClient):
    def _request(
        self,
        method: str,
        path: str,
        *,
        query: dict[str, str | int] | None = None,
        payload: dict[str, object] | None = None,
    ) -> tuple[int, str]:
        url = f"{self.API_ROOT}{path}"
        if query:
            url = f"{url}?{urlencode(query)}"

        headers = {
            "Accept": "application/vnd.github+json",
            "Content-Type": "application/json",
            "User-Agent": "dm-ai-testing-beta-release",
            "X-GitHub-Api-Version": "2022-11-28",
        }
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"

        data = None
        if payload is not None:
            data = json.dumps(payload).encode("utf-8")

        request = Request(url, headers=headers, data=data, method=method)
        with urlopen(request, timeout=30) as response:
            return response.getcode(), response.read().decode("utf-8", errors="replace")

    def dispatch_workflow(self, workflow_id: str, *, ref: str) -> None:
        status_code, _ = self._request(
            "POST",
            f"/repos/{self.owner}/{self.repo}/actions/workflows/{workflow_id}/dispatches",
            payload={"ref": ref},
        )
        if status_code != 204:
            raise AssertionError(f"Expected workflow dispatch to return 204, got {status_code}.")

    def branch_head_sha(self, branch: str) -> str:
        response = self._get_json(f"/repos/{self.owner}/{self.repo}/branches/{branch}")
        if not isinstance(response, dict):
            raise AssertionError(f"Expected a branch payload dict, got: {type(response)!r}")
        sha = str((response.get("commit") or {}).get("sha", ""))
        if not sha:
            raise AssertionError(f"Branch {branch!r} did not include a commit SHA.")
        return sha

    def workflow_runs_for_workflow(
        self,
        workflow_id: str,
        *,
        branch: str | None = None,
        event: str | None = None,
        per_page: int = 20,
    ) -> list[dict[str, object]]:
        query: dict[str, str | int] = {"per_page": per_page}
        if branch:
            query["branch"] = branch
        if event:
            query["event"] = event

        response = self._get_json(
            f"/repos/{self.owner}/{self.repo}/actions/workflows/{workflow_id}/runs",
            query,
        )
        if not isinstance(response, dict):
            raise AssertionError(f"Expected a workflow-runs payload dict, got: {type(response)!r}")
        workflow_runs = response.get("workflow_runs", [])
        if not isinstance(workflow_runs, list):
            raise AssertionError(f"Expected a list of workflow runs, got: {type(workflow_runs)!r}")
        return workflow_runs

    def workflow_run(self, run_id: int) -> dict[str, object]:
        response = self._get_json(f"/repos/{self.owner}/{self.repo}/actions/runs/{run_id}")
        if not isinstance(response, dict):
            raise AssertionError(f"Expected a workflow-run payload dict, got: {type(response)!r}")
        return response

    def list_releases(self, per_page: int = 20) -> list[dict[str, object]]:
        response = self._get_json(
            f"/repos/{self.owner}/{self.repo}/releases",
            {"per_page": per_page},
        )
        if not isinstance(response, list):
            raise AssertionError(f"Expected a list of releases, got: {type(response)!r}")
        return response

    def release_by_tag(self, tag: str) -> dict[str, object]:
        response = self._get_json(f"/repos/{self.owner}/{self.repo}/releases/tags/{tag}")
        if not isinstance(response, dict):
            raise AssertionError(f"Expected a release payload dict, got: {type(response)!r}")
        return response
