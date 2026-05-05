from __future__ import annotations

import io
import json
import zipfile
from dataclasses import dataclass
from typing import Any
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import HTTPRedirectHandler, Request, build_opener, urlopen

from testing.core.interfaces.publication_gate_client import PublicationGateClient


@dataclass(frozen=True)
class GitHubPublicationGateRestClient(PublicationGateClient):
    owner: str
    repo: str
    token: str | None = None

    API_ROOT = "https://api.github.com"

    def _get_json(
        self,
        path: str,
        query: dict[str, str | int] | None = None,
    ) -> Any:
        url = f"{self.API_ROOT}{path}"
        if query:
            url = f"{url}?{urlencode(query)}"

        headers = {
            "Accept": "application/vnd.github+json",
            "User-Agent": "dm-ai-testing-publication-gates",
            "X-GitHub-Api-Version": "2022-11-28",
        }
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"

        request = Request(url, headers=headers)
        with urlopen(request, timeout=30) as response:
            return json.load(response)

    def list_recent_pull_requests(self, limit: int = 20) -> list[dict[str, Any]]:
        response = self._get_json(
            f"/repos/{self.owner}/{self.repo}/pulls",
            {"state": "closed", "sort": "updated", "direction": "desc", "per_page": limit},
        )
        if not isinstance(response, list):
            raise AssertionError(f"Expected a list of pull requests, got: {type(response)!r}")
        return response

    def list_releases(self, limit: int = 20) -> list[dict[str, Any]]:
        response = self._get_json(
            f"/repos/{self.owner}/{self.repo}/releases",
            {"per_page": limit},
        )
        if not isinstance(response, list):
            raise AssertionError(f"Expected a list of releases, got: {type(response)!r}")
        return response

    def pull_request_files(self, number: int) -> list[dict[str, Any]]:
        response = self._get_json(
            f"/repos/{self.owner}/{self.repo}/pulls/{number}/files",
            {"per_page": 100},
        )
        if not isinstance(response, list):
            raise AssertionError(f"Expected a list of pull request files, got: {type(response)!r}")
        return response

    def pull_request_reviews(self, number: int) -> list[dict[str, Any]]:
        response = self._get_json(
            f"/repos/{self.owner}/{self.repo}/pulls/{number}/reviews",
            {"per_page": 100},
        )
        if not isinstance(response, list):
            raise AssertionError(f"Expected a list of pull request reviews, got: {type(response)!r}")
        return response

    def pull_request_issue_comments(self, number: int) -> list[dict[str, Any]]:
        response = self._get_json(
            f"/repos/{self.owner}/{self.repo}/issues/{number}/comments",
            {"per_page": 100},
        )
        if not isinstance(response, list):
            raise AssertionError(f"Expected a list of issue comments, got: {type(response)!r}")
        return response

    def pull_request(self, number: int) -> dict[str, Any]:
        response = self._get_json(f"/repos/{self.owner}/{self.repo}/pulls/{number}")
        if not isinstance(response, dict):
            raise AssertionError(f"Expected a pull request payload dict, got: {type(response)!r}")
        return response

    def workflow_runs_for_head_sha(self, head_sha: str) -> list[dict[str, Any]]:
        response = self._get_json(
            f"/repos/{self.owner}/{self.repo}/actions/runs",
            {"head_sha": head_sha, "status": "completed", "per_page": 100},
        )
        if not isinstance(response, dict):
            raise AssertionError(f"Expected a workflow-runs payload dict, got: {type(response)!r}")
        workflow_runs = response.get("workflow_runs", [])
        if not isinstance(workflow_runs, list):
            raise AssertionError(f"Expected a list of workflow runs, got: {type(workflow_runs)!r}")
        return workflow_runs

    def workflow_runs(
        self,
        workflow_file: str,
        *,
        branch: str | None = None,
        event: str | None = None,
        status: str | None = None,
        limit: int = 20,
    ) -> list[dict[str, Any]]:
        query: dict[str, str | int] = {"per_page": limit}
        if branch:
            query["branch"] = branch
        if event:
            query["event"] = event
        if status:
            query["status"] = status

        response = self._get_json(
            f"/repos/{self.owner}/{self.repo}/actions/workflows/{workflow_file}/runs",
            query,
        )
        if not isinstance(response, dict):
            raise AssertionError(f"Expected a workflow-runs payload dict, got: {type(response)!r}")
        workflow_runs = response.get("workflow_runs", [])
        if not isinstance(workflow_runs, list):
            raise AssertionError(f"Expected a list of workflow runs, got: {type(workflow_runs)!r}")
        return workflow_runs

    def workflow_jobs(self, run_id: int) -> list[dict[str, Any]]:
        response = self._get_json(
            f"/repos/{self.owner}/{self.repo}/actions/runs/{run_id}/jobs",
            {"per_page": 100},
        )
        if not isinstance(response, dict):
            raise AssertionError(f"Expected a workflow-jobs payload dict, got: {type(response)!r}")
        jobs = response.get("jobs", [])
        if not isinstance(jobs, list):
            raise AssertionError(f"Expected a list of workflow jobs, got: {type(jobs)!r}")
        return jobs

    def workflow_job_logs(self, job_id: int) -> str:
        headers = {
            "Accept": "application/vnd.github+json",
            "User-Agent": "dm-ai-testing-publication-gates",
            "X-GitHub-Api-Version": "2022-11-28",
        }
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"

        class _NoRedirect(HTTPRedirectHandler):
            def redirect_request(self, req, fp, code, msg, response_headers, newurl):  # type: ignore[override]
                return None

        request = Request(
            f"{self.API_ROOT}/repos/{self.owner}/{self.repo}/actions/jobs/{job_id}/logs",
            headers=headers,
        )

        try:
            with build_opener(_NoRedirect).open(request, timeout=30) as response:
                payload = response.read()
        except HTTPError as error:
            redirect_url = error.headers.get("location")
            if not redirect_url:
                return f"log download unavailable: HTTP {error.code}"
            redirected_request = Request(
                redirect_url,
                headers={"User-Agent": "dm-ai-testing-publication-gates"},
            )
            try:
                with urlopen(redirected_request, timeout=30) as response:
                    payload = response.read()
            except HTTPError as redirected_error:
                return f"log download unavailable: HTTP {redirected_error.code}"

        if payload.startswith(b"PK\x03\x04"):
            with zipfile.ZipFile(io.BytesIO(payload)) as archive:
                contents: list[str] = []
                for name in sorted(archive.namelist()):
                    with archive.open(name) as log_file:
                        contents.append(log_file.read().decode("utf-8", errors="replace"))
                return "\n".join(contents)

        return payload.decode("utf-8", errors="replace")
