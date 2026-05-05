from __future__ import annotations

from testing.components.factories.github_actions_release_client_factory import (
    create_github_actions_release_client,
)
from testing.components.services.deprecated_workflow_run_audit_service import (
    DeprecatedWorkflowRunAuditService as DeprecatedWorkflowRunAuditServiceImpl,
)
from testing.core.interfaces.deprecated_workflow_run_audit_service import (
    DeprecatedWorkflowRunAuditService,
)


def create_deprecated_workflow_run_audit_service(
    *,
    owner: str,
    repo: str,
    workflow_file: str,
    workflow_ref: str,
    workflow_name: str,
    release_job_name: str,
    release_tag: str,
    dispatch_timeout_seconds: int,
    completion_timeout_seconds: int,
    poll_interval_seconds: int,
    required_notice_markers: tuple[str, ...],
    forbidden_strings: tuple[str, ...],
    require_step_summary: bool,
    dispatch_inputs: dict[str, object] | None = None,
    reuse_existing_release: bool = False,
    token: str | None = None,
) -> DeprecatedWorkflowRunAuditService:
    return DeprecatedWorkflowRunAuditServiceImpl(
        github_client=create_github_actions_release_client(
            owner=owner,
            repo=repo,
            token=token,
        ),
        workflow_file=workflow_file,
        workflow_ref=workflow_ref,
        workflow_name=workflow_name,
        release_job_name=release_job_name,
        release_tag=release_tag,
        dispatch_timeout_seconds=dispatch_timeout_seconds,
        completion_timeout_seconds=completion_timeout_seconds,
        poll_interval_seconds=poll_interval_seconds,
        required_notice_markers=required_notice_markers,
        forbidden_strings=forbidden_strings,
        require_step_summary=require_step_summary,
        dispatch_inputs=dispatch_inputs,
        reuse_existing_release=reuse_existing_release,
    )
