from __future__ import annotations

from pathlib import Path

from testing.components.factories.github_actions_release_client_factory import (
    create_github_actions_release_client,
)
from testing.components.services.beta_release_summary_audit_service import (
    BetaReleaseSummaryAuditService as BetaReleaseSummaryAuditServiceImpl,
)
from testing.core.interfaces.beta_release_summary_audit_service import (
    BetaReleaseSummaryAuditService,
)


def create_beta_release_summary_audit_service(
    repository_root: Path,
    *,
    owner: str,
    repo: str,
    workflow_file: str,
    workflow_ref: str,
    workflow_name: str,
    release_job_name: str,
    dispatch_timeout_seconds: int,
    completion_timeout_seconds: int,
    poll_interval_seconds: int,
    token: str | None = None,
) -> BetaReleaseSummaryAuditService:
    return BetaReleaseSummaryAuditServiceImpl(
        github_client=create_github_actions_release_client(
            owner=owner,
            repo=repo,
            token=token,
        ),
        workflow_file=workflow_file,
        workflow_ref=workflow_ref,
        workflow_name=workflow_name,
        release_job_name=release_job_name,
        dispatch_timeout_seconds=dispatch_timeout_seconds,
        completion_timeout_seconds=completion_timeout_seconds,
        poll_interval_seconds=poll_interval_seconds,
    )
