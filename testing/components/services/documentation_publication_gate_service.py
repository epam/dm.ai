from __future__ import annotations

import io
import json
import os
import re
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import HTTPRedirectHandler, Request, build_opener, urlopen

from testing.core.models.publication_gate_audit import (
    CheckRunRecord,
    PublicationGateAudit,
    PublicationGateFailure,
    PullRequestCandidate,
    SignOffRecord,
)


@dataclass(frozen=True)
class GitHubRestClient:
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

    def commit_check_runs(self, commit_sha: str) -> list[dict[str, Any]]:
        response = self._get_json(
            f"/repos/{self.owner}/{self.repo}/commits/{commit_sha}/check-runs",
            {"per_page": 100},
        )
        if not isinstance(response, dict):
            raise AssertionError(f"Expected a check-runs payload dict, got: {type(response)!r}")
        check_runs = response.get("check_runs", [])
        if not isinstance(check_runs, list):
            raise AssertionError(f"Expected a list of check runs, got: {type(check_runs)!r}")
        return check_runs

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


class DocumentationPublicationGateService:
    REQUIRED_PR_LINE = "Duplicate-check: completed — see ticket comment"
    MAINTAINER_ASSOCIATIONS = {"OWNER", "MEMBER", "COLLABORATOR"}
    LINK_VALIDATION_MARKERS = ("link validation", "link check", "markdown link")
    SMOKE_CHECK_MARKERS = ("documentation smoke", "docs smoke", "smoke")
    TECHNICAL_WRITER_MARKERS = (
        "technical writer",
        "tech writer",
        "documentation writer",
        "docs writer",
    )
    SIGNOFF_MARKERS = ("approved", "approve", "lgtm", "sign-off", "signed off")
    DUPLICATE_CHECK_PATTERN = re.compile(r"\bduplicate[- ]check\b", re.IGNORECASE)
    JQL_PATTERN = re.compile(
        r"(\bjql\b|project\s*=|issuekey\s*=|labels\s+in\s*\(|summary\s+~)",
        re.IGNORECASE,
    )
    REPO_SEARCH_PATTERN = re.compile(
        r"(\brepo:\S+|\brepo search\b|\bgithub search\b|\brg\s+\S+|\bripgrep\b)",
        re.IGNORECASE,
    )

    def __init__(
        self,
        repository_root: Path,
        ticket_key: str,
        github_client: GitHubRestClient | None = None,
        target_pull_request_number: int | None = None,
        technical_writer_logins: list[str] | tuple[str, ...] | set[str] | None = None,
    ) -> None:
        self.repository_root = repository_root
        self.ticket_key = ticket_key
        self.comments_path = repository_root / "input" / ticket_key / "comments.md"
        self.github_client = github_client or GitHubRestClient(
            owner="epam",
            repo="dm.ai",
            token=os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN"),
        )
        self.target_pull_request_number = target_pull_request_number
        self.technical_writer_logins = {
            self._normalize_text(str(login))
            for login in (technical_writer_logins or ())
            if str(login).strip()
        }

    def audit(self) -> PublicationGateAudit:
        failures: list[PublicationGateFailure] = []

        comments_text = (
            self.comments_path.read_text(encoding="utf-8") if self.comments_path.exists() else ""
        )
        duplicate_check_preview = self._find_duplicate_check_comment_preview(comments_text)
        if not duplicate_check_preview:
            failures.append(
                PublicationGateFailure(
                    step=1,
                    summary=(
                        "The ticket comments do not contain a recorded duplicate-check entry "
                        "with both JQL and repository search evidence."
                    ),
                    expected=(
                        "A ticket comment that explicitly records the duplicate check and includes "
                        "both the JQL used and the repository search string(s)."
                    ),
                    actual=self._comment_observation(comments_text),
                )
            )

        pull_request = self._target_documentation_pull_request()
        successful_checks: tuple[CheckRunRecord, ...] = ()
        maintainer_signoffs: tuple[SignOffRecord, ...] = ()
        technical_writer_signoffs: tuple[SignOffRecord, ...] = ()
        observed_signoff_records: tuple[SignOffRecord, ...] = ()

        if pull_request is None:
            failures.append(
                PublicationGateFailure(
                    step=2,
                    summary="No merged documentation pull request could be identified for verification.",
                    expected=(
                        "The configured target pull request must be merged and change README.md or "
                        "dmtools-ai-docs/** so the publication-gate evidence can be audited."
                        if self.target_pull_request_number is not None
                        else "At least one merged pull request that changes README.md or "
                        "dmtools-ai-docs/** so the publication-gate evidence can be audited."
                    ),
                    actual=(
                        f"Pull request #{self.target_pull_request_number} was not a merged "
                        "documentation PR."
                        if self.target_pull_request_number is not None
                        else "The recent merged pull requests did not include any documentation changes."
                    ),
                )
            )
        else:
            if self._normalize_text(self.REQUIRED_PR_LINE) not in self._normalize_text(
                pull_request.body
            ):
                failures.append(
                    PublicationGateFailure(
                        step=2,
                        summary="The documentation PR description is missing the required duplicate-check line.",
                        expected=self.REQUIRED_PR_LINE,
                        actual=self._preview_text(pull_request.body),
                    )
                )

            successful_checks = self._successful_checks_for_pull_request(pull_request)
            if not self._has_required_check(successful_checks, self.LINK_VALIDATION_MARKERS) or not self._has_required_check(
                successful_checks,
                self.SMOKE_CHECK_MARKERS,
            ):
                failures.append(
                    PublicationGateFailure(
                        step=3,
                        summary=(
                            "The documentation PR does not expose successful GitHub Actions job logs "
                            "for both link validation and the documentation smoke check."
                        ),
                        expected=(
                            "Successful GitHub Actions jobs whose logs explicitly show the link "
                            "validation result and the documentation smoke-check result."
                        ),
                        actual=self._checks_observation(successful_checks),
                    )
                )

            observed_signoff_records = self._collect_signoff_records(pull_request)
            maintainer_signoffs = tuple(
                record
                for record in observed_signoff_records
                if self._is_maintainer_signoff(record, pull_request.author_login)
            )
            technical_writer_signoffs = tuple(
                record
                for record in observed_signoff_records
                if self._is_technical_writer_signoff(record, pull_request.author_login)
            )
            if not maintainer_signoffs or not technical_writer_signoffs:
                failures.append(
                    PublicationGateFailure(
                        step=4,
                        summary=(
                            "The documentation PR is missing the required core-maintainer and "
                            "technical-writer sign-offs."
                        ),
                        expected=(
                            "At least one maintainer approval plus a distinct technical-writer "
                            "sign-off recorded on the PR."
                        ),
                        actual=self._signoff_observation(
                            observed_signoff_records,
                            maintainer_signoffs,
                            technical_writer_signoffs,
                        ),
                    )
                )

        return PublicationGateAudit(
            ticket_comment_preview=duplicate_check_preview,
            pull_request=pull_request,
            validation_failures=tuple(failures),
            successful_checks=successful_checks,
            maintainer_signoffs=maintainer_signoffs,
            technical_writer_signoffs=technical_writer_signoffs,
            observed_signoff_records=observed_signoff_records,
        )

    @staticmethod
    def format_failures(failures: tuple[PublicationGateFailure, ...] | list[PublicationGateFailure]) -> str:
        return "\n\n".join(failure.format() for failure in failures)

    def human_observations(self, audit: PublicationGateAudit) -> list[str]:
        observations = [
            (
                "Ticket comment snapshot shows: "
                + (
                    audit.ticket_comment_preview
                    if audit.ticket_comment_preview
                    else self._comment_observation(
                        self.comments_path.read_text(encoding="utf-8")
                        if self.comments_path.exists()
                        else ""
                    )
                )
            )
        ]

        if audit.pull_request is None:
            observations.append("No merged documentation pull request was found in the recent repository history.")
            return observations

        observations.append(
            f"Target documentation PR visible to a reviewer: #{audit.pull_request.number} "
            f"{audit.pull_request.title} ({audit.pull_request.html_url})."
        )
        observations.append(f"PR description preview: {self._preview_text(audit.pull_request.body)}")
        observations.append(
            "Visible successful Actions job evidence: "
            + (
                ", ".join(check.describe() for check in audit.successful_checks)
                if audit.successful_checks
                else "none"
            )
        )
        observations.append(
            "Visible sign-off activity: "
            + (
                "; ".join(record.describe() for record in audit.observed_signoff_records)
                if audit.observed_signoff_records
                else "none"
            )
        )
        return observations

    def _latest_documentation_pull_request(self) -> PullRequestCandidate | None:
        merged_candidates: list[PullRequestCandidate] = []
        for pull_request in self.github_client.list_recent_pull_requests():
            candidate = self._pull_request_candidate_from_payload(pull_request)
            if candidate is None:
                continue
            merged_candidates.append(candidate)

        if not merged_candidates:
            return None

        return max(merged_candidates, key=lambda candidate: candidate.merged_at)

    def _target_documentation_pull_request(self) -> PullRequestCandidate | None:
        if self.target_pull_request_number is not None:
            payload = self.github_client.pull_request(self.target_pull_request_number)
            return self._pull_request_candidate_from_payload(payload)
        return self._latest_documentation_pull_request()

    def _pull_request_candidate_from_payload(
        self,
        pull_request: dict[str, Any],
    ) -> PullRequestCandidate | None:
        merged_at = pull_request.get("merged_at")
        if not merged_at:
            return None

        number = int(pull_request["number"])
        changed_files = tuple(
            str(file_info.get("filename", ""))
            for file_info in self.github_client.pull_request_files(number)
        )
        if not any(self._is_documentation_file(path) for path in changed_files):
            return None

        return PullRequestCandidate(
            number=number,
            title=str(pull_request.get("title", "")),
            html_url=str(pull_request.get("html_url", "")),
            body=str(pull_request.get("body", "")),
            head_sha=str((pull_request.get("head") or {}).get("sha", "")),
            author_login=str((pull_request.get("user") or {}).get("login", "")),
            merged_at=str(merged_at),
            changed_files=changed_files,
        )

    @staticmethod
    def _is_documentation_file(path: str) -> bool:
        return path == "README.md" or path.startswith("dmtools-ai-docs/")

    def _find_duplicate_check_comment_preview(self, comments_text: str) -> str:
        normalized_blocks = [
            block.strip()
            for block in re.split(r"\n\s*-\s*\n", comments_text)
            if block.strip()
        ]
        for block in normalized_blocks:
            if not self.DUPLICATE_CHECK_PATTERN.search(block):
                continue
            if not self.JQL_PATTERN.search(block):
                continue
            if not self.REPO_SEARCH_PATTERN.search(block):
                continue
            return self._preview_text(block)
        return ""

    def _successful_checks_for_pull_request(
        self,
        pull_request: PullRequestCandidate,
    ) -> tuple[CheckRunRecord, ...]:
        checks = []
        for workflow_run in self.github_client.workflow_runs_for_head_sha(pull_request.head_sha):
            workflow_status = str(workflow_run.get("status", ""))
            workflow_conclusion = str(workflow_run.get("conclusion", ""))
            if workflow_status != "completed" or workflow_conclusion != "success":
                continue
            workflow_name = str(workflow_run.get("name", ""))
            run_id = workflow_run.get("id")
            if run_id is None:
                continue

            for job in self.github_client.workflow_jobs(int(run_id)):
                status = str(job.get("status", ""))
                conclusion = str(job.get("conclusion", ""))
                if status != "completed" or conclusion != "success":
                    continue

                job_id = job.get("id")
                log_text = (
                    self.github_client.workflow_job_logs(int(job_id))
                    if job_id is not None
                    else ""
                )
                checks.append(
                    CheckRunRecord(
                        name=str(job.get("name", "")) or workflow_name,
                        status=status,
                        conclusion=conclusion,
                        html_url=str(job.get("html_url", "")),
                        workflow_name=workflow_name,
                        log_excerpt=self._log_excerpt(log_text),
                        log_text=self._normalize_text(log_text),
                    )
                )
        return tuple(checks)

    @staticmethod
    def _has_required_check(
        checks: tuple[CheckRunRecord, ...],
        markers: tuple[str, ...],
    ) -> bool:
        return any(
            any(marker in check.log_text for marker in markers)
            for check in checks
        )

    def _collect_signoff_records(
        self,
        pull_request: PullRequestCandidate,
    ) -> tuple[SignOffRecord, ...]:
        records: list[SignOffRecord] = []

        for review in self.github_client.pull_request_reviews(pull_request.number):
            records.append(
                SignOffRecord(
                    login=str((review.get("user") or {}).get("login", "")),
                    source="review",
                    state=str(review.get("state", "")),
                    author_association=str(review.get("author_association", "")),
                    body=str(review.get("body", "")),
                )
            )

        for comment in self.github_client.pull_request_issue_comments(pull_request.number):
            records.append(
                SignOffRecord(
                    login=str((comment.get("user") or {}).get("login", "")),
                    source="comment",
                    state="COMMENTED",
                    author_association=str(comment.get("author_association", "")),
                    body=str(comment.get("body", "")),
                )
            )

        return tuple(records)

    def _is_maintainer_signoff(
        self,
        record: SignOffRecord,
        pull_request_author: str,
    ) -> bool:
        return (
            record.login
            and record.login != pull_request_author
            and record.source == "review"
            and record.state.upper() == "APPROVED"
            and record.author_association.upper() in self.MAINTAINER_ASSOCIATIONS
        )

    def _is_technical_writer_signoff(
        self,
        record: SignOffRecord,
        pull_request_author: str,
    ) -> bool:
        body = self._normalize_text(record.body)
        normalized_login = self._normalize_text(record.login)
        return (
            record.login
            and record.login != pull_request_author
            and (
                normalized_login in self.technical_writer_logins
                or (
                    any(marker in body for marker in self.TECHNICAL_WRITER_MARKERS)
                    and any(marker in body for marker in self.SIGNOFF_MARKERS)
                )
            )
        )

    @staticmethod
    def _normalize_text(value: str) -> str:
        translation_table = str.maketrans(
            {
                "\u2010": "-",
                "\u2011": "-",
                "\u2012": "-",
                "\u2013": "-",
                "\u2014": "-",
                "\u2015": "-",
            }
        )
        return " ".join(value.translate(translation_table).lower().split())

    @staticmethod
    def _preview_text(value: str, limit: int = 240) -> str:
        compact = " ".join(value.split())
        if not compact:
            return "no visible text"
        if len(compact) <= limit:
            return compact
        return f"{compact[: limit - 3]}..."

    def _comment_observation(self, comments_text: str) -> str:
        preview = self._preview_text(comments_text)
        if preview == "no visible text":
            return "comments.md is empty or missing."
        return f"Observed comment history: {preview}"

    @staticmethod
    def _checks_observation(checks: tuple[CheckRunRecord, ...]) -> str:
        if not checks:
            return "No successful GitHub Actions jobs were visible for the pull request head commit."
        return "Visible successful Actions jobs: " + ", ".join(check.describe() for check in checks)

    def _log_excerpt(self, log_text: str, limit: int = 240) -> str:
        compact = " ".join(log_text.split())
        if not compact:
            return "no visible log output"

        normalized = self._normalize_text(compact)
        markers = self.LINK_VALIDATION_MARKERS + self.SMOKE_CHECK_MARKERS
        for marker in markers:
            index = normalized.find(marker)
            if index == -1:
                continue
            end = min(len(compact), index + limit)
            return compact[index:end]

        return self._preview_text(compact, limit=limit)

    @staticmethod
    def _signoff_observation(
        observed_records: tuple[SignOffRecord, ...],
        maintainer_signoffs: tuple[SignOffRecord, ...],
        technical_writer_signoffs: tuple[SignOffRecord, ...],
    ) -> str:
        details = []
        details.append(
            "Maintainer sign-offs: "
            + (
                "; ".join(record.describe() for record in maintainer_signoffs)
                if maintainer_signoffs
                else "none"
            )
        )
        details.append(
            "Technical-writer sign-offs: "
            + (
                "; ".join(record.describe() for record in technical_writer_signoffs)
                if technical_writer_signoffs
                else "none"
            )
        )
        details.append(
            "Observed review/comment activity: "
            + (
                "; ".join(record.describe() for record in observed_records)
                if observed_records
                else "none"
            )
        )
        return " ".join(details)
