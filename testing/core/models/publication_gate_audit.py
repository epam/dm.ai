from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class PublicationGateFailure:
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
class PullRequestCandidate:
    number: int
    title: str
    html_url: str
    body: str
    head_sha: str
    author_login: str
    merged_at: str
    changed_files: tuple[str, ...]


@dataclass(frozen=True)
class SignOffRecord:
    login: str
    source: str
    state: str
    author_association: str
    body: str

    def describe(self) -> str:
        details = f"{self.login} via {self.source} ({self.state}, {self.author_association})"
        if not self.body.strip():
            return details
        return f"{details}: {self.body.strip()}"


@dataclass(frozen=True)
class CheckRunRecord:
    name: str
    status: str
    conclusion: str
    html_url: str
    workflow_name: str = ""
    log_excerpt: str = ""
    log_text: str = ""

    def describe(self) -> str:
        display_name = (
            f"{self.workflow_name} / {self.name}"
            if self.workflow_name and self.workflow_name != self.name
            else self.name
        )
        suffix = f"{self.status}/{self.conclusion}".strip("/")
        details = f"{display_name} [{suffix}]"
        if self.html_url:
            details = f"{details} {self.html_url}"
        if self.log_excerpt:
            return f"{details} | log: {self.log_excerpt}"
        return details


@dataclass(frozen=True)
class PublicationGateAudit:
    ticket_comment_preview: str
    pull_request: PullRequestCandidate | None
    validation_failures: tuple[PublicationGateFailure, ...]
    successful_checks: tuple[CheckRunRecord, ...]
    maintainer_signoffs: tuple[SignOffRecord, ...]
    technical_writer_signoffs: tuple[SignOffRecord, ...]
    observed_signoff_records: tuple[SignOffRecord, ...]
