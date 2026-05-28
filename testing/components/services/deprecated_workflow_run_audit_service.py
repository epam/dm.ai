from __future__ import annotations

import re
from datetime import datetime, timedelta, timezone
from threading import Event
from typing import Any
from urllib.error import HTTPError

from testing.core.interfaces.deprecated_workflow_run_audit_service import (
    DeprecatedWorkflowRunAuditService as DeprecatedWorkflowRunAuditServiceContract,
)
from testing.core.interfaces.github_actions_release_client import GitHubActionsReleaseClient
from testing.core.models.deprecated_workflow_run_audit import (
    DeprecatedWorkflowAuditFailure,
    DeprecatedWorkflowJobObservation,
    DeprecatedWorkflowReleaseObservation,
    DeprecatedWorkflowRunAudit,
    DeprecatedWorkflowRunObservation,
)


class DeprecatedWorkflowRunAuditService(DeprecatedWorkflowRunAuditServiceContract):
    SUMMARY_ECHO_PATTERN = re.compile(
        r'echo (?P<argument>.+?) >> ["\']?\$GITHUB_STEP_SUMMARY["\']?$'
    )
    ANSI_PATTERN = re.compile(r"\x1b\[[0-9;]*m")
    TIMESTAMP_PREFIX_PATTERN = re.compile(r"^\d{4}-\d{2}-\d{2}T[^ ]+\s+")
    RELEASE_URL_PATTERN = re.compile(r"/releases/tag/(?P<tag>[^)\s]+)")
    RELEASE_TAG_OUTPUT_PATTERN = re.compile(r"(?:^|\b)tag_name=(?P<tag>v[0-9A-Za-z.\-]+)")
    USING_TAG_PATTERN = re.compile(r"Using tag:\s*(?P<tag>v[0-9A-Za-z.\-]+)")

    def __init__(
        self,
        github_client: GitHubActionsReleaseClient,
        *,
        workflow_file: str,
        workflow_ref: str,
        workflow_name: str,
        release_job_name: str,
        release_tag: str,
        dispatch_timeout_seconds: int,
        completion_timeout_seconds: int,
        poll_interval_seconds: int,
        required_notice_markers: tuple[str, ...],
        forbidden_strings: tuple[str, ...],
        require_step_summary: bool,
        dispatch_inputs: dict[str, object] | None = None,
        reuse_existing_release: bool = False,
    ) -> None:
        self.github_client = github_client
        self.workflow_file = workflow_file
        self.workflow_ref = workflow_ref
        self.workflow_name = workflow_name
        self.release_job_name = release_job_name
        self.release_tag = release_tag
        self.dispatch_timeout_seconds = dispatch_timeout_seconds
        self.completion_timeout_seconds = completion_timeout_seconds
        self.poll_interval_seconds = poll_interval_seconds
        self.required_notice_markers = required_notice_markers
        self.forbidden_strings = forbidden_strings
        self.require_step_summary = require_step_summary
        self.dispatch_inputs = dict(dispatch_inputs or {})
        self.reuse_existing_release = reuse_existing_release
        self._waiter = Event()

    def audit(self) -> DeprecatedWorkflowRunAudit:
        failures: list[DeprecatedWorkflowAuditFailure] = []
        target_head_sha = self.github_client.branch_head_sha(self.workflow_ref)

        if self.reuse_existing_release:
            existing_audit = self._audit_existing_successful_run(target_head_sha)
            if existing_audit is not None:
                return existing_audit

        dispatch_started_at = datetime.now(timezone.utc)
        existing_run_ids = {
            int(run["id"])
            for run in self.github_client.workflow_runs_for_workflow(
                self.workflow_file,
                branch=self.workflow_ref,
                per_page=20,
            )
            if run.get("id") is not None
        }

        self.github_client.dispatch_workflow(
            self.workflow_file,
            ref=self.workflow_ref,
            inputs=self.dispatch_inputs,
        )
        workflow_run = self._wait_for_dispatched_run(
            existing_run_ids=existing_run_ids,
            target_head_sha=target_head_sha,
            dispatch_started_at=dispatch_started_at,
        )
        if workflow_run is None:
            failures.append(
                DeprecatedWorkflowAuditFailure(
                    step=1,
                    summary=f"The {self.workflow_name} workflow did not appear after dispatch.",
                    expected=(
                        f"A new {self.workflow_name!r} run triggered by workflow_dispatch on "
                        f"{self.workflow_ref!r}."
                    ),
                    actual=(
                        f"No new workflow_dispatch run was listed for {self.workflow_file!r} "
                        f"within {self.dispatch_timeout_seconds} seconds."
                    ),
                )
            )
            return DeprecatedWorkflowRunAudit(
                workflow_run=None,
                release_job=None,
                release=None,
                failures=tuple(failures),
            )

        return self._complete_audit(workflow_run=workflow_run, dispatch_started_at=dispatch_started_at)

    @staticmethod
    def format_failures(
        failures: tuple[DeprecatedWorkflowAuditFailure, ...] | list[DeprecatedWorkflowAuditFailure],
    ) -> str:
        lines = ["Deprecated workflow run outputs did not match the expected live behavior:"]
        for failure in failures:
            lines.append(failure.format())
        return "\n".join(lines)

    def _audit_existing_successful_run(
        self,
        target_head_sha: str,
    ) -> DeprecatedWorkflowRunAudit | None:
        if not self.release_tag:
            return None
        try:
            release_payload = self.github_client.release_by_tag(self.release_tag)
        except HTTPError as error:
            if error.code != 404:
                raise
            return None

        workflow_run: DeprecatedWorkflowRunObservation | None = None
        for run in self.github_client.workflow_runs_for_workflow(
            self.workflow_file,
            branch=self.workflow_ref,
            per_page=20,
        ):
            if str(run.get("head_sha", "")) != target_head_sha:
                continue
            if str(run.get("status", "")) != "completed":
                continue
            if str(run.get("conclusion", "")) != "success":
                continue
            workflow_run = self._build_run_observation(run)
            break

        if workflow_run is None:
            return None

        return self._complete_audit(
            workflow_run=workflow_run,
            dispatch_started_at=self._parse_github_datetime(workflow_run.created_at)
            or datetime.now(timezone.utc),
            release_payload=release_payload,
        )

    def _complete_audit(
        self,
        *,
        workflow_run: DeprecatedWorkflowRunObservation,
        dispatch_started_at: datetime,
        release_payload: dict[str, Any] | None = None,
    ) -> DeprecatedWorkflowRunAudit:
        failures: list[DeprecatedWorkflowAuditFailure] = []
        release_job_payload = None
        release_job = None

        workflow_run = self._wait_for_completion(workflow_run.run_id)
        if workflow_run.status != "completed" or workflow_run.conclusion != "success":
            release_job_payload = self._find_release_job_payload(workflow_run.run_id)
            if release_job_payload is not None:
                release_job = self._build_release_job_observation(release_job_payload)
            failures.append(
                DeprecatedWorkflowAuditFailure(
                    step=2,
                    summary=f"The {self.workflow_name} workflow did not finish successfully.",
                    expected="A completed workflow run with conclusion 'success'.",
                    actual=(
                        f"Run {workflow_run.html_url} finished with status={workflow_run.status!r} "
                        f"and conclusion={workflow_run.conclusion!r}. "
                        + (
                            f"Job excerpt: {self._preview_text(release_job.log_excerpt, limit=360)}"
                            if release_job is not None
                            else "No publication job log was available."
                        )
                    ),
                )
            )
            return DeprecatedWorkflowRunAudit(
                workflow_run=workflow_run,
                release_job=release_job,
                release=None,
                failures=tuple(failures),
            )

        release_job_payload = self._find_release_job_payload(workflow_run.run_id)
        if release_job_payload is None:
            failures.append(
                DeprecatedWorkflowAuditFailure(
                    step=3,
                    summary="The completed workflow run does not expose the publication job.",
                    expected=f"A job named {self.release_job_name!r} in the completed workflow run.",
                    actual=f"No job matched {self.release_job_name!r} in run {workflow_run.html_url}.",
                )
            )
            return DeprecatedWorkflowRunAudit(
                workflow_run=workflow_run,
                release_job=None,
                release=None,
                failures=tuple(failures),
            )

        release_job = self._build_release_job_observation(release_job_payload)
        if release_job.status != "completed" or release_job.conclusion != "success":
            failures.append(
                DeprecatedWorkflowAuditFailure(
                    step=3,
                    summary="The publication job did not finish successfully.",
                    expected=f"A completed {self.release_job_name} job with conclusion 'success'.",
                    actual=(
                        f"Job {release_job.html_url} finished with status={release_job.status!r} "
                        f"and conclusion={release_job.conclusion!r}. "
                        f"Job excerpt: {self._preview_text(release_job.log_excerpt, limit=360)}"
                    ),
                )
            )
            return DeprecatedWorkflowRunAudit(
                workflow_run=workflow_run,
                release_job=release_job,
                release=None,
                failures=tuple(failures),
            )

        release = self._find_release_observation(
            release_job=release_job,
            release_payload=release_payload,
            dispatch_started_at=dispatch_started_at,
        )
        if release is None:
            expected_release = self.release_tag or "the release emitted by the completed workflow run"
            failures.append(
                DeprecatedWorkflowAuditFailure(
                    step=4,
                    summary="The workflow release page was not discoverable after the workflow succeeded.",
                    expected=(
                        f"A GitHub release for {expected_release} created by the completed workflow run."
                    ),
                    actual=(
                        f"Job {release_job.html_url} completed successfully, but no matching release "
                        "was discoverable from the emitted run outputs."
                    ),
                )
            )
            return DeprecatedWorkflowRunAudit(
                workflow_run=workflow_run,
                release_job=release_job,
                release=None,
                failures=tuple(failures),
            )

        failures.extend(self._audit_surface("release body", release.body))
        if self.require_step_summary:
            if not release_job.step_summary_markdown.strip():
                failures.append(
                    DeprecatedWorkflowAuditFailure(
                        step=5,
                        summary="The publication job did not publish any Step Summary content.",
                        expected="Visible GITHUB_STEP_SUMMARY content for the completed run.",
                        actual=f"Job {release_job.html_url} produced no captured summary lines.",
                    )
                )
            else:
                failures.extend(self._audit_surface("step summary", release_job.step_summary_markdown))
        elif release_job.step_summary_markdown.strip():
            failures.extend(self._audit_surface("step summary", release_job.step_summary_markdown))

        return DeprecatedWorkflowRunAudit(
            workflow_run=workflow_run,
            release_job=release_job,
            release=release,
            failures=tuple(failures),
        )

    def _audit_surface(
        self,
        surface_name: str,
        content: str,
    ) -> list[DeprecatedWorkflowAuditFailure]:
        normalized_content = self._normalize_visible_text(content)
        failures: list[DeprecatedWorkflowAuditFailure] = []

        missing_markers = [
            marker for marker in self.required_notice_markers if marker not in normalized_content
        ]
        if missing_markers:
            failures.append(
                DeprecatedWorkflowAuditFailure(
                    step=5,
                    summary=f"The published {surface_name} is missing the deprecated/internal-only notice.",
                    expected=(
                        "Live workflow output should clearly position the workflow as deprecated "
                        "and internal-only."
                    ),
                    actual=(
                        f"Missing markers {missing_markers}. Visible content: "
                        f"{self._preview_text(content, limit=360)}"
                    ),
                )
            )

        for forbidden in self.forbidden_strings:
            if forbidden in normalized_content:
                failures.append(
                    DeprecatedWorkflowAuditFailure(
                        step=5,
                        summary=f"The published {surface_name} still exposes removed install guidance.",
                        expected=(
                            "Live workflow output must not mention installer scripts or the removed "
                            "public installation header."
                        ),
                        actual=(
                            f"Found {forbidden!r}. Visible content: "
                            f"{self._preview_text(content, limit=360)}"
                        ),
                    )
                )
        return failures

    def _wait_for_dispatched_run(
        self,
        *,
        existing_run_ids: set[int],
        target_head_sha: str,
        dispatch_started_at: datetime,
    ) -> DeprecatedWorkflowRunObservation | None:
        deadline = self._deadline(self.dispatch_timeout_seconds)
        while datetime.now(timezone.utc) < deadline:
            runs = self.github_client.workflow_runs_for_workflow(
                self.workflow_file,
                branch=self.workflow_ref,
                event="workflow_dispatch",
                per_page=20,
            )
            for run in runs:
                run_id = run.get("id")
                if run_id is None or int(run_id) in existing_run_ids:
                    continue
                if str(run.get("head_sha", "")) != target_head_sha:
                    continue
                created_at = self._parse_github_datetime(str(run.get("created_at", "")))
                if created_at is None or created_at < dispatch_started_at - timedelta(minutes=1):
                    continue
                return self._build_run_observation(run)
            self._waiter.wait(self.poll_interval_seconds)
        return None

    def _wait_for_completion(self, run_id: int) -> DeprecatedWorkflowRunObservation:
        deadline = self._deadline(self.completion_timeout_seconds)
        last_seen = self._build_run_observation(self.github_client.workflow_run(run_id))
        while datetime.now(timezone.utc) < deadline:
            current = self._build_run_observation(self.github_client.workflow_run(run_id))
            last_seen = current
            if current.status == "completed":
                return current
            self._waiter.wait(self.poll_interval_seconds)
        return last_seen

    def _find_release_job_payload(self, run_id: int) -> dict[str, Any] | None:
        for job in self.github_client.workflow_jobs(run_id):
            if str(job.get("name", "")) == self.release_job_name:
                return job
        return None

    def _build_release_job_observation(self, payload: dict[str, Any]) -> DeprecatedWorkflowJobObservation:
        job_id = int(payload["id"])
        log_text = self.github_client.workflow_job_logs(job_id)
        return DeprecatedWorkflowJobObservation(
            job_id=job_id,
            name=str(payload.get("name", "")),
            html_url=str(payload.get("html_url", "")),
            status=str(payload.get("status", "")),
            conclusion=str(payload.get("conclusion", "")),
            step_summary_markdown=self._extract_step_summary_markdown(log_text),
            log_excerpt=self._log_excerpt(log_text),
            step_conclusions=self._extract_step_conclusions(payload),
            raw_log_text=log_text,
        )

    def _find_release_observation(
        self,
        *,
        release_job: DeprecatedWorkflowJobObservation,
        release_payload: dict[str, Any] | None,
        dispatch_started_at: datetime,
    ) -> DeprecatedWorkflowReleaseObservation | None:
        payload = release_payload
        candidate_tags = self._candidate_release_tags(release_job)
        lookup_deadline = self._deadline(max(self.poll_interval_seconds * 6, 120))
        while datetime.now(timezone.utc) < lookup_deadline and payload is None:
            for tag in candidate_tags:
                try:
                    payload = self.github_client.release_by_tag(tag)
                except HTTPError as error:
                    if error.code != 404:
                        raise
                if payload is not None:
                    break
            if payload is None:
                payload = self._find_recent_release_payload(
                    candidate_tags=candidate_tags,
                    dispatch_started_at=dispatch_started_at,
                )
            if payload is None:
                self._waiter.wait(self.poll_interval_seconds)

        if payload is None:
            return None

        visible_at = self._release_visibility_datetime(payload)
        if visible_at is not None and visible_at < dispatch_started_at - timedelta(minutes=5):
            return None

        return DeprecatedWorkflowReleaseObservation(
            tag_name=str(payload.get("tag_name", "")),
            html_url=str(payload.get("html_url", "")),
            body=str(payload.get("body", "")),
        )

    def _candidate_release_tags(self, release_job: DeprecatedWorkflowJobObservation) -> tuple[str, ...]:
        candidates: list[str] = []
        for value in (
            self.release_tag,
            self._release_tag_from_text(release_job.step_summary_markdown),
            self._release_tag_from_text(release_job.raw_log_text or release_job.log_excerpt),
        ):
            normalized = value.strip()
            if normalized and normalized not in candidates:
                candidates.append(normalized)
        return tuple(candidates)

    def _find_recent_release_payload(
        self,
        *,
        candidate_tags: tuple[str, ...],
        dispatch_started_at: datetime,
    ) -> dict[str, Any] | None:
        for release in self.github_client.list_releases(per_page=100):
            tag_name = str(release.get("tag_name", "")).strip()
            if candidate_tags and tag_name not in candidate_tags:
                continue
            visible_at = self._release_visibility_datetime(release)
            if visible_at is None or visible_at < dispatch_started_at - timedelta(minutes=5):
                continue
            if candidate_tags or self._release_looks_like_deprecated_workflow_output(release):
                return release
        return None

    def _release_timestamp(self, payload: dict[str, Any]) -> datetime | None:
        for key in ("published_at", "created_at"):
            value = self._parse_github_datetime(str(payload.get(key, "")))
            if value is not None:
                return value
        return None

    def _extract_step_summary_markdown(self, raw_log_text: str) -> str:
        lines: list[str] = []
        for raw_line in raw_log_text.splitlines():
            line = self._normalize_log_line(raw_line)
            if line.startswith("##[group]Run "):
                continue
            if "$GITHUB_STEP_SUMMARY" not in line:
                continue
            match = self.SUMMARY_ECHO_PATTERN.search(line)
            if not match:
                continue
            lines.append(self._decode_echo_argument(match.group("argument")))
        return "\n".join(lines).strip()

    @staticmethod
    def _extract_step_conclusions(job_payload: dict[str, Any]) -> dict[str, str]:
        steps = job_payload.get("steps", [])
        if not isinstance(steps, list):
            return {}
        return {
            str(step.get("name", "")): str(step.get("conclusion", ""))
            for step in steps
            if isinstance(step, dict) and str(step.get("name", ""))
        }

    def _log_excerpt(self, raw_log_text: str, limit: int = 6000) -> str:
        cleaned = "\n".join(self._normalize_log_line(line) for line in raw_log_text.splitlines())
        if len(cleaned) <= limit:
            return cleaned
        return "..." + cleaned[-(limit - 3) :]

    @classmethod
    def _normalize_log_line(cls, line: str) -> str:
        without_ansi = cls.ANSI_PATTERN.sub("", line)
        return cls.TIMESTAMP_PREFIX_PATTERN.sub("", without_ansi).strip()

    @staticmethod
    def _decode_echo_argument(argument: str) -> str:
        value = argument.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in {'"', "'"}:
            value = value[1:-1]
        return (
            value.replace(r"\\", "\\")
            .replace(r"\"", '"')
            .replace(r"\`", "`")
            .replace(r"\$", "$")
        )

    @staticmethod
    def _normalize_visible_text(value: str) -> str:
        return " ".join(value.lower().split())

    @classmethod
    def _release_tag_from_text(cls, text: str) -> str:
        for pattern in (cls.RELEASE_TAG_OUTPUT_PATTERN, cls.USING_TAG_PATTERN):
            match = pattern.search(text)
            if match:
                return match.group("tag")
        release_url_matches = list(cls.RELEASE_URL_PATTERN.finditer(text))
        if release_url_matches:
            return release_url_matches[-1].group("tag")
        return ""

    def _release_looks_like_deprecated_workflow_output(self, payload: dict[str, Any]) -> bool:
        body = self._normalize_visible_text(str(payload.get("body", "")))
        if not body:
            return False
        return all(marker in body for marker in self.required_notice_markers)

    def _release_visibility_datetime(self, payload: dict[str, Any]) -> datetime | None:
        for field in ("published_at", "created_at", "updated_at"):
            parsed = self._parse_github_datetime(str(payload.get(field, "")))
            if parsed is not None:
                return parsed
        return None

    @staticmethod
    def _preview_text(value: str, *, limit: int) -> str:
        compact = " ".join(value.split())
        if not compact:
            return "no visible text"
        if len(compact) <= limit:
            return compact
        return compact[: limit - 3] + "..."

    @staticmethod
    def _build_run_observation(payload: dict[str, Any]) -> DeprecatedWorkflowRunObservation:
        return DeprecatedWorkflowRunObservation(
            run_id=int(payload["id"]),
            html_url=str(payload.get("html_url", "")),
            event=str(payload.get("event", "")),
            status=str(payload.get("status", "")),
            conclusion=str(payload.get("conclusion", "")),
            head_branch=str(payload.get("head_branch", "")),
            head_sha=str(payload.get("head_sha", "")),
            created_at=str(payload.get("created_at", "")),
            run_number=int(payload.get("run_number", 0)),
        )

    @staticmethod
    def _parse_github_datetime(value: str) -> datetime | None:
        if not value:
            return None
        return datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)

    @staticmethod
    def _deadline(timeout_seconds: int) -> datetime:
        return datetime.now(timezone.utc).replace(microsecond=0) + timedelta(seconds=timeout_seconds)
