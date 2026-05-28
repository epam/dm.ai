from __future__ import annotations

from dataclasses import dataclass, field


@dataclass(frozen=True)
class DeprecatedWorkflowAuditFailure:
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
class DeprecatedWorkflowRunObservation:
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
class DeprecatedWorkflowJobObservation:
    job_id: int
    name: str
    html_url: str
    status: str
    conclusion: str
    step_summary_markdown: str
    log_excerpt: str
    step_conclusions: dict[str, str] = field(default_factory=dict)
    raw_log_text: str = ""


@dataclass(frozen=True)
class DeprecatedWorkflowReleaseObservation:
    tag_name: str
    html_url: str
    body: str


@dataclass(frozen=True)
class DeprecatedWorkflowRunAudit:
    workflow_run: DeprecatedWorkflowRunObservation | None
    release_job: DeprecatedWorkflowJobObservation | None
    release: DeprecatedWorkflowReleaseObservation | None
    failures: tuple[DeprecatedWorkflowAuditFailure, ...]
