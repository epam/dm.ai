from __future__ import annotations

import os
from pathlib import Path

from testing.components.services.beta_release_summary_audit_service import (
    BetaReleaseSummaryAuditService,
)
from testing.frameworks.api.rest.github_actions_release_client import (
    GitHubActionsReleaseRestClient,
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
    github_token = token if token is not None else os.environ.get("GH_TOKEN") or os.environ.get(
        "GITHUB_TOKEN"
    )
    return BetaReleaseSummaryAuditService(
        github_client=GitHubActionsReleaseRestClient(
            owner=owner,
            repo=repo,
            token=github_token,
        ),
        workflow_file=workflow_file,
        workflow_ref=workflow_ref,
        workflow_name=workflow_name,
        release_job_name=release_job_name,
        dispatch_timeout_seconds=dispatch_timeout_seconds,
        completion_timeout_seconds=completion_timeout_seconds,
        poll_interval_seconds=poll_interval_seconds,
    )
