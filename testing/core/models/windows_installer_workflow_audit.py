from __future__ import annotations

from dataclasses import dataclass, field


@dataclass(frozen=True)
class WindowsInstallerWorkflowAuditFailure:
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
class WindowsInstallerWorkflowRunObservation:
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
class WindowsInstallerWorkflowJobObservation:
    job_id: int
    name: str
    html_url: str
    status: str
    conclusion: str
    step_conclusions: dict[str, str] = field(default_factory=dict)
    normalized_log_text: str = ""
    log_excerpt: str = ""
    raw_log_text: str = ""


@dataclass(frozen=True)
class WindowsInstallerWorkflowAudit:
    workflow_run: WindowsInstallerWorkflowRunObservation | None
    workflow_job: WindowsInstallerWorkflowJobObservation | None
    failures: tuple[WindowsInstallerWorkflowAuditFailure, ...]
