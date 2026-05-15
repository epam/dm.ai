from __future__ import annotations

from testing.components.factories.github_actions_release_client_factory import (
    create_github_actions_release_client,
)
from testing.components.services.windows_installer_workflow_audit_service import (
    WindowsInstallerWorkflowAuditService as WindowsInstallerWorkflowAuditServiceImpl,
)
from testing.core.interfaces.windows_installer_workflow_audit_service import (
    WindowsInstallerWorkflowAuditService,
)


def create_windows_installer_workflow_audit_service(
    *,
    owner: str,
    repo: str,
    workflow_file: str,
    workflow_ref: str,
    workflow_name: str,
    workflow_job_name: str,
    dispatch_timeout_seconds: int,
    completion_timeout_seconds: int,
    poll_interval_seconds: int,
    token: str | None = None,
) -> WindowsInstallerWorkflowAuditService:
    return WindowsInstallerWorkflowAuditServiceImpl(
        github_client=create_github_actions_release_client(
            owner=owner,
            repo=repo,
            token=token,
        ),
        workflow_file=workflow_file,
        workflow_ref=workflow_ref,
        workflow_name=workflow_name,
        workflow_job_name=workflow_job_name,
        dispatch_timeout_seconds=dispatch_timeout_seconds,
        completion_timeout_seconds=completion_timeout_seconds,
        poll_interval_seconds=poll_interval_seconds,
    )
