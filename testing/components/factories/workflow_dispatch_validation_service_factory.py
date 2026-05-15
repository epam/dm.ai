from __future__ import annotations

from testing.components.factories.github_actions_release_client_factory import (
    create_github_actions_release_client,
)
from testing.components.services.workflow_dispatch_validation_service import (
    WorkflowDispatchValidationService as WorkflowDispatchValidationServiceImpl,
)
from testing.core.interfaces.workflow_dispatch_validation_service import (
    WorkflowDispatchValidationService,
)


def create_workflow_dispatch_validation_service(
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
    required_step_names: tuple[str, ...],
    required_log_fragments: tuple[str, ...],
    token: str | None = None,
) -> WorkflowDispatchValidationService:
    return WorkflowDispatchValidationServiceImpl(
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
        required_step_names=required_step_names,
        required_log_fragments=required_log_fragments,
    )
