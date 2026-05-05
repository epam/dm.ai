from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class StableReleaseInstallPathsFailure:
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
class ReleaseRecord:
    tag_name: str
    html_url: str
    published_at: str
    body: str
    asset_names: tuple[str, ...]


@dataclass(frozen=True)
class WorkflowRunRecord:
    run_id: int
    html_url: str
    status: str
    conclusion: str
    created_at: str
    updated_at: str
    head_branch: str


@dataclass(frozen=True)
class WorkflowJobRecord:
    job_id: int
    name: str
    html_url: str
    step_names: tuple[str, ...]
    log_text: str
    summary_text: str


@dataclass(frozen=True)
class StableReleaseInstallPathsAudit:
    release: ReleaseRecord | None
    workflow_run: WorkflowRunRecord | None
    workflow_job: WorkflowJobRecord | None
    release_urls: tuple[str, ...]
    workflow_summary_urls: tuple[str, ...]
    release_cli_urls: tuple[str, ...]
    release_skill_urls: tuple[str, ...]
    workflow_summary_cli_urls: tuple[str, ...]
    workflow_summary_skill_urls: tuple[str, ...]
    release_forbidden_lines: tuple[str, ...]
    workflow_summary_forbidden_lines: tuple[str, ...]
    failures: tuple[StableReleaseInstallPathsFailure, ...]
