from __future__ import annotations

from typing import Protocol

from testing.core.models.deprecated_workflow_run_audit import (
    DeprecatedWorkflowAuditFailure,
    DeprecatedWorkflowRunAudit,
)


class DeprecatedWorkflowRunAuditService(Protocol):
    def audit(self) -> DeprecatedWorkflowRunAudit:
        raise NotImplementedError

    def format_failures(
        self,
        failures: tuple[DeprecatedWorkflowAuditFailure, ...] | list[DeprecatedWorkflowAuditFailure],
    ) -> str:
        raise NotImplementedError
