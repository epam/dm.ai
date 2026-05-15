from __future__ import annotations

from datetime import datetime, timedelta, timezone
from threading import Event
from typing import Any

from testing.core.interfaces.github_actions_release_client import GitHubActionsReleaseClient
from testing.core.interfaces.workflow_dispatch_validation_service import (
    WorkflowDispatchValidationService as WorkflowDispatchValidationServiceContract,
)
from testing.core.models.workflow_dispatch_validation import (
    WorkflowDispatchAuditFailure,
    WorkflowDispatchJobObservation,
    WorkflowDispatchRunObservation,
    WorkflowDispatchValidationAudit,
)


class WorkflowDispatchValidationService(WorkflowDispatchValidationServiceContract):
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
        required_step_names: tuple[str, ...],
        required_log_fragments: tuple[str, ...],
    ) -> None:
        self.github_client = github_client
        self.workflow_file = workflow_file
        self.workflow_ref = workflow_ref
        self.workflow_name = workflow_name
        self.workflow_job_name = workflow_job_name
        self.dispatch_timeout_seconds = dispatch_timeout_seconds
        self.completion_timeout_seconds = completion_timeout_seconds
        self.poll_interval_seconds = poll_interval_seconds
        self.required_step_names = required_step_names
        self.required_log_fragments = required_log_fragments
        self._waiter = Event()

    def audit(self) -> WorkflowDispatchValidationAudit:
        failures: list[WorkflowDispatchAuditFailure] = []
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

        self.github_client.dispatch_workflow(self.workflow_file, ref=self.workflow_ref)
        workflow_run = self._wait_for_dispatched_run(
            existing_run_ids=existing_run_ids,
            dispatch_started_at=dispatch_started_at,
        )
        if workflow_run is None:
            failures.append(
                WorkflowDispatchAuditFailure(
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
            return WorkflowDispatchValidationAudit(
                workflow_run=None,
                workflow_job=None,
                failures=tuple(failures),
            )

        workflow_run = self._wait_for_completion(workflow_run.run_id)
        workflow_job = self._job_observation_or_none(workflow_run.run_id)
        if workflow_run.status != "completed" or workflow_run.conclusion != "success":
            failures.append(
                WorkflowDispatchAuditFailure(
                    step=2,
                    summary=f"The {self.workflow_name} workflow did not finish successfully.",
                    expected="A completed workflow run with conclusion 'success'.",
                    actual=(
                        f"Run {workflow_run.html_url} finished with status={workflow_run.status!r} "
                        f"and conclusion={workflow_run.conclusion!r}. "
                        + (
                            f"Job excerpt: {workflow_job.log_excerpt}"
                            if workflow_job is not None and workflow_job.log_excerpt
                            else "No workflow job log excerpt was available."
                        )
                    ),
                )
            )
        if workflow_job is None:
            failures.append(
                WorkflowDispatchAuditFailure(
                    step=3,
                    summary="The completed workflow run did not expose the validation job.",
                    expected=(
                        f"A job named {self.workflow_job_name!r} in the completed workflow run."
                    ),
                    actual=(
                        f"No job matched {self.workflow_job_name!r} in run {workflow_run.html_url}."
                    ),
                )
            )
            return WorkflowDispatchValidationAudit(
                workflow_run=workflow_run,
                workflow_job=None,
                failures=tuple(failures),
            )

        if workflow_job.status != "completed" or workflow_job.conclusion != "success":
            failures.append(
                WorkflowDispatchAuditFailure(
                    step=4,
                    summary="The validation job did not finish successfully.",
                    expected=(
                        f"A completed {self.workflow_job_name!r} job with conclusion 'success'."
                    ),
                    actual=(
                        f"Job {workflow_job.html_url} finished with status={workflow_job.status!r} "
                        f"and conclusion={workflow_job.conclusion!r}. "
                        f"Job excerpt: {workflow_job.log_excerpt or 'no log excerpt available'}"
                    ),
                )
            )

        missing_steps = [
            step_name
            for step_name in self.required_step_names
            if step_name not in workflow_job.step_conclusions
        ]
        if missing_steps:
            failures.append(
                WorkflowDispatchAuditFailure(
                    step=5,
                    summary="The validation job did not expose all expected user-visible steps.",
                    expected=(
                        "Step names visible in the run should include "
                        + ", ".join(repr(step_name) for step_name in self.required_step_names)
                        + "."
                    ),
                    actual=(
                        f"Missing steps: {missing_steps}. Visible steps: "
                        f"{list(workflow_job.step_conclusions.keys()) or ['none']}."
                    ),
                )
            )

        failed_steps = [
            step_name
            for step_name in self.required_step_names
            if workflow_job.step_conclusions.get(step_name) != "success"
        ]
        if failed_steps:
            failures.append(
                WorkflowDispatchAuditFailure(
                    step=6,
                    summary="One or more expected validation steps did not complete successfully.",
                    expected="Each user-visible validation step should conclude with 'success'.",
                    actual=(
                        "Observed step conclusions: "
                        + ", ".join(
                            f"{step_name}={workflow_job.step_conclusions.get(step_name, 'missing')!r}"
                            for step_name in self.required_step_names
                        )
                    ),
                )
            )

        normalized_log_text = self._normalize_visible_text(workflow_job.raw_log_text)
        missing_log_fragments = [
            fragment
            for fragment in self.required_log_fragments
            if self._normalize_visible_text(fragment) not in normalized_log_text
        ]
        if missing_log_fragments:
            failures.append(
                WorkflowDispatchAuditFailure(
                    step=7,
                    summary="The validation job log did not show the expected installer verification flow.",
                    expected=(
                        "The user-visible job log should show the latest-release install command and "
                        "wrapper validation commands."
                    ),
                    actual=(
                        f"Missing log fragments: {missing_log_fragments}. "
                        f"Job excerpt: {workflow_job.log_excerpt or 'no log excerpt available'}"
                    ),
                )
            )

        return WorkflowDispatchValidationAudit(
            workflow_run=workflow_run,
            workflow_job=workflow_job,
            failures=tuple(failures),
        )

    @staticmethod
    def format_failures(
        failures: tuple[WorkflowDispatchAuditFailure, ...] | list[WorkflowDispatchAuditFailure],
    ) -> str:
        lines = ["Workflow-dispatch validation did not match the expected live behavior:"]
        for failure in failures:
            lines.append(failure.format())
        return "\n".join(lines)

    def _wait_for_dispatched_run(
        self,
        *,
        existing_run_ids: set[int],
        dispatch_started_at: datetime,
    ) -> WorkflowDispatchRunObservation | None:
        deadline = self._deadline(self.dispatch_timeout_seconds)
        earliest_created_at = dispatch_started_at - timedelta(seconds=self.poll_interval_seconds)
        while datetime.now(timezone.utc) < deadline:
            runs = self.github_client.workflow_runs_for_workflow(
                self.workflow_file,
                branch=self.workflow_ref,
                event="workflow_dispatch",
                per_page=20,
            )
            candidates: list[tuple[datetime, int, dict[str, Any]]] = []
            for run in runs:
                run_id = run.get("id")
                if run_id is None or int(run_id) in existing_run_ids:
                    continue
                created_at = self._parse_github_datetime(str(run.get("created_at", "")))
                if created_at is None or created_at < earliest_created_at:
                    continue
                candidates.append((created_at, int(run_id), run))
            if candidates:
                _, _, earliest_run = min(candidates, key=lambda candidate: (candidate[0], candidate[1]))
                return self._build_run_observation(earliest_run)
            self._waiter.wait(self.poll_interval_seconds)
        return None

    def _wait_for_completion(self, run_id: int) -> WorkflowDispatchRunObservation:
        deadline = self._deadline(self.completion_timeout_seconds)
        last_seen = self._build_run_observation(self.github_client.workflow_run(run_id))
        while datetime.now(timezone.utc) < deadline:
            current = self._build_run_observation(self.github_client.workflow_run(run_id))
            last_seen = current
            if current.status == "completed":
                return current
            self._waiter.wait(self.poll_interval_seconds)
        return last_seen

    def _job_observation_or_none(self, run_id: int) -> WorkflowDispatchJobObservation | None:
        for job in self.github_client.workflow_jobs(run_id):
            if str(job.get("name", "")) == self.workflow_job_name:
                return self._build_job_observation(job)
        return None

    def _build_job_observation(self, payload: dict[str, Any]) -> WorkflowDispatchJobObservation:
        job_id = int(payload["id"])
        raw_log_text = self.github_client.workflow_job_logs(job_id)
        return WorkflowDispatchJobObservation(
            job_id=job_id,
            name=str(payload.get("name", "")),
            html_url=str(payload.get("html_url", "")),
            status=str(payload.get("status", "")),
            conclusion=str(payload.get("conclusion", "")),
            step_conclusions=self._extract_step_conclusions(payload),
            raw_log_text=raw_log_text,
            log_excerpt=self._log_excerpt(raw_log_text),
        )

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

    @staticmethod
    def _parse_github_datetime(value: str) -> datetime | None:
        if not value:
            return None
        try:
            return datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
        except ValueError:
            return None

    @staticmethod
    def _normalize_visible_text(value: str) -> str:
        return " ".join(value.lower().split())

    @classmethod
    def _log_excerpt(cls, raw_log_text: str, limit: int = 6000) -> str:
        normalized = "\n".join(line.rstrip() for line in raw_log_text.splitlines())
        if len(normalized) <= limit:
            return normalized
        return "..." + normalized[-(limit - 3) :]

    @staticmethod
    def _deadline(timeout_seconds: int) -> datetime:
        return datetime.now(timezone.utc) + timedelta(seconds=timeout_seconds)

    @staticmethod
    def _build_run_observation(payload: dict[str, Any]) -> WorkflowDispatchRunObservation:
        return WorkflowDispatchRunObservation(
            run_id=int(payload["id"]),
            html_url=str(payload.get("html_url", "")),
            event=str(payload.get("event", "")),
            status=str(payload.get("status", "")),
            conclusion=str(payload.get("conclusion", "")),
            head_branch=str(payload.get("head_branch", "")),
            head_sha=str(payload.get("head_sha", "")),
            created_at=str(payload.get("created_at", "")),
            run_number=int(payload.get("run_number", 0) or 0),
        )
