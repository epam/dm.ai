from __future__ import annotations

from dataclasses import dataclass, field


@dataclass(frozen=True)
class WorkflowDispatchAuditFailure:
    step: int
    summary: str
    expected: str
    actual: str

    def format(self) -> str:
        return (
            f"Step {self.step}: {self.summary}\n"
            f"Expected: {self.expected}\n"
            f"Actual: {self.actual}"
        )


@dataclass(frozen=True)
class WorkflowDispatchRunObservation:
    run_id: int
    html_url: str
    event: str
    status: str
    conclusion: str
    head_branch: str
    head_sha: str
    created_at: str
    run_number: int


@dataclass(frozen=True)
class WorkflowDispatchJobObservation:
    job_id: int
    name: str
    html_url: str
    status: str
    conclusion: str
    step_conclusions: dict[str, str] = field(default_factory=dict)
    raw_log_text: str = ""
    log_excerpt: str = ""


@dataclass(frozen=True)
class WorkflowDispatchValidationAudit:
    workflow_run: WorkflowDispatchRunObservation | None
    workflow_job: WorkflowDispatchJobObservation | None
    failures: tuple[WorkflowDispatchAuditFailure, ...]
