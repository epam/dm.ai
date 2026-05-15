from __future__ import annotations

import re
from datetime import datetime, timedelta, timezone
from threading import Event
from typing import Any

from testing.core.interfaces.github_actions_release_client import GitHubActionsReleaseClient
from testing.core.interfaces.windows_installer_workflow_audit_service import (
    WindowsInstallerWorkflowAuditService as WindowsInstallerWorkflowAuditServiceContract,
)
from testing.core.models.windows_installer_workflow_audit import (
    WindowsInstallerWorkflowAudit,
    WindowsInstallerWorkflowAuditFailure,
    WindowsInstallerWorkflowJobObservation,
    WindowsInstallerWorkflowRunObservation,
)


class WindowsInstallerWorkflowAuditService(WindowsInstallerWorkflowAuditServiceContract):
    ANSI_PATTERN = re.compile(r"\x1b\[[0-9;]*m")
    TIMESTAMP_PREFIX_PATTERN = re.compile(r"^\d{4}-\d{2}-\d{2}T[^ ]+\s+")

    def __init__(
        self,
        github_client: GitHubActionsReleaseClient,
        *,
        workflow_file: str,
        workflow_ref: str,
        workflow_name: str,
        workflow_job_name: str,
        dispatch_timeout_seconds: int,
        completion_timeout_seconds: int,
        poll_interval_seconds: int,
    ) -> None:
        self.github_client = github_client
        self.workflow_file = workflow_file
        self.workflow_ref = workflow_ref
        self.workflow_name = workflow_name
        self.workflow_job_name = workflow_job_name
        self.dispatch_timeout_seconds = dispatch_timeout_seconds
        self.completion_timeout_seconds = completion_timeout_seconds
        self.poll_interval_seconds = poll_interval_seconds
        self._waiter = Event()

    def audit(self) -> WindowsInstallerWorkflowAudit:
        failures: list[WindowsInstallerWorkflowAuditFailure] = []
        dispatch_started_at = datetime.now(timezone.utc)
        target_head_sha = self.github_client.branch_head_sha(self.workflow_ref)
        existing_run_ids = {
            int(run["id"])
            for run in self.github_client.workflow_runs_for_workflow(
                self.workflow_file,
                branch=self.workflow_ref,
                per_page=20,
            )
            if run.get("id") is not None
        }

        self.github_client.dispatch_workflow(self.workflow_file, ref=self.workflow_ref)
        workflow_run = self._wait_for_dispatched_run(
            existing_run_ids=existing_run_ids,
            target_head_sha=target_head_sha,
            dispatch_started_at=dispatch_started_at,
        )
        if workflow_run is None:
            failures.append(
                WindowsInstallerWorkflowAuditFailure(
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
            return WindowsInstallerWorkflowAudit(
                workflow_run=None,
                workflow_job=None,
                failures=tuple(failures),
            )

        completed_run = self._wait_for_completion(workflow_run.run_id)
        workflow_job_payload = self._find_workflow_job_payload(completed_run.run_id)
        workflow_job = (
            self._build_workflow_job_observation(workflow_job_payload)
            if workflow_job_payload is not None
            else None
        )

        if completed_run.status != "completed" or completed_run.conclusion != "success":
            failures.append(
                WindowsInstallerWorkflowAuditFailure(
                    step=2,
                    summary=f"The {self.workflow_name} workflow did not finish successfully.",
                    expected="A completed workflow run with conclusion 'success'.",
                    actual=(
                        f"Run {completed_run.html_url} finished with status={completed_run.status!r} "
                        f"and conclusion={completed_run.conclusion!r}. "
                        + (
                            f"Job excerpt: {workflow_job.log_excerpt}"
                            if workflow_job is not None and workflow_job.log_excerpt
                            else "No workflow job log was available."
                        )
                    ),
                )
            )
            return WindowsInstallerWorkflowAudit(
                workflow_run=completed_run,
                workflow_job=workflow_job,
                failures=tuple(failures),
            )

        if workflow_job is None:
            failures.append(
                WindowsInstallerWorkflowAuditFailure(
                    step=3,
                    summary="The completed workflow run does not expose the expected job.",
                    expected=f"A job named {self.workflow_job_name!r} in the completed workflow run.",
                    actual=(
                        f"No job matched {self.workflow_job_name!r} in run {completed_run.html_url}."
                    ),
                )
            )
            return WindowsInstallerWorkflowAudit(
                workflow_run=completed_run,
                workflow_job=None,
                failures=tuple(failures),
            )

        if workflow_job.status != "completed" or workflow_job.conclusion != "success":
            failures.append(
                WindowsInstallerWorkflowAuditFailure(
                    step=4,
                    summary="The expected workflow job did not finish successfully.",
                    expected=f"A completed {self.workflow_job_name!r} job with conclusion 'success'.",
                    actual=(
                        f"Job {workflow_job.html_url} finished with status={workflow_job.status!r} "
                        f"and conclusion={workflow_job.conclusion!r}. "
                        f"Step conclusions: {workflow_job.step_conclusions}. "
                        f"Job excerpt: {workflow_job.log_excerpt}"
                    ),
                )
            )

        return WindowsInstallerWorkflowAudit(
            workflow_run=completed_run,
            workflow_job=workflow_job,
            failures=tuple(failures),
        )

    @staticmethod
    def format_failures(
        failures: tuple[WindowsInstallerWorkflowAuditFailure, ...]
        | list[WindowsInstallerWorkflowAuditFailure],
    ) -> str:
        lines = ["Windows installer workflow outputs did not match the expected live behavior:"]
        for failure in failures:
            lines.append(failure.format())
        return "\n".join(lines)

    def _wait_for_dispatched_run(
        self,
        *,
        existing_run_ids: set[int],
        target_head_sha: str,
        dispatch_started_at: datetime,
    ) -> WindowsInstallerWorkflowRunObservation | None:
        deadline = self._deadline(self.dispatch_timeout_seconds)
        earliest_created_at = dispatch_started_at - timedelta(seconds=self.poll_interval_seconds)

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
                if created_at is None or created_at < earliest_created_at:
                    continue
                return self._build_run_observation(run)
            self._waiter.wait(self.poll_interval_seconds)
        return None

    def _wait_for_completion(self, run_id: int) -> WindowsInstallerWorkflowRunObservation:
        deadline = self._deadline(self.completion_timeout_seconds)
        last_seen = self._build_run_observation(self.github_client.workflow_run(run_id))

        while datetime.now(timezone.utc) < deadline:
            current = self._build_run_observation(self.github_client.workflow_run(run_id))
            last_seen = current
            if current.status == "completed":
                return current
            self._waiter.wait(self.poll_interval_seconds)
        return last_seen

    def _find_workflow_job_payload(self, run_id: int) -> dict[str, Any] | None:
        for job in self.github_client.workflow_jobs(run_id):
            if str(job.get("name", "")) == self.workflow_job_name:
                return job
        return None

    def _build_workflow_job_observation(
        self,
        payload: dict[str, Any],
    ) -> WindowsInstallerWorkflowJobObservation:
        job_id = int(payload["id"])
        raw_log_text = self.github_client.workflow_job_logs(job_id)
        normalized_log_text = self._normalize_log_text(raw_log_text)
        return WindowsInstallerWorkflowJobObservation(
            job_id=job_id,
            name=str(payload.get("name", "")),
            html_url=str(payload.get("html_url", "")),
            status=str(payload.get("status", "")),
            conclusion=str(payload.get("conclusion", "")),
            step_conclusions=self._extract_step_conclusions(payload),
            normalized_log_text=normalized_log_text,
            log_excerpt=self._log_excerpt(normalized_log_text),
            raw_log_text=raw_log_text,
        )

    @staticmethod
    def _extract_step_conclusions(job_payload: dict[str, Any]) -> dict[str, str]:
        steps = job_payload.get("steps", [])
        if not isinstance(steps, list):
            return {}
        return {
            str(step.get("name", "")).strip(): str(step.get("conclusion", "")).strip()
            for step in steps
            if isinstance(step, dict) and str(step.get("name", "")).strip()
        }

    @classmethod
    def _normalize_log_text(cls, raw_log_text: str) -> str:
        normalized_lines: list[str] = []
        for raw_line in raw_log_text.splitlines():
            line = raw_line.lstrip("\ufeff")
            parts = line.split("\t", 2)
            if len(parts) == 3:
                line = parts[2]
            line = cls.ANSI_PATTERN.sub("", line)
            line = cls.TIMESTAMP_PREFIX_PATTERN.sub("", line)
            normalized_lines.append(line)
        return "\n".join(normalized_lines)

    @staticmethod
    def _log_excerpt(normalized_log_text: str, limit: int = 4000) -> str:
        stripped = normalized_log_text.strip()
        if len(stripped) <= limit:
            return stripped
        return f"...{stripped[-limit:]}"

    @staticmethod
    def _build_run_observation(payload: dict[str, Any]) -> WindowsInstallerWorkflowRunObservation:
        return WindowsInstallerWorkflowRunObservation(
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
        if not value.strip():
            return None
        return datetime.fromisoformat(value.replace("Z", "+00:00"))

    @staticmethod
    def _deadline(timeout_seconds: int) -> datetime:
        return datetime.now(timezone.utc) + timedelta(seconds=timeout_seconds)
