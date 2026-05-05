from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class BetaReleaseAuditFailure:
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
class BetaReleaseRunObservation:
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
class BetaReleaseJobObservation:
    job_id: int
    name: str
    html_url: str
    status: str
    conclusion: str
    step_summary_markdown: str
    log_excerpt: str


@dataclass(frozen=True)
class BetaReleaseReleaseObservation:
    tag_name: str
    html_url: str
    is_prerelease: bool
    body: str
    asset_names: tuple[str, ...]


@dataclass(frozen=True)
class BetaReleaseSummaryAudit:
    workflow_run: BetaReleaseRunObservation | None
    release_job: BetaReleaseJobObservation | None
    release: BetaReleaseReleaseObservation | None
    failures: tuple[BetaReleaseAuditFailure, ...]
