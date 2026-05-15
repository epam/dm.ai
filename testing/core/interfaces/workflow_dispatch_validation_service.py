from __future__ import annotations

from typing import Protocol

from testing.core.models.workflow_dispatch_validation import (
    WorkflowDispatchAuditFailure,
    WorkflowDispatchValidationAudit,
)


class WorkflowDispatchValidationService(Protocol):
    def audit(self) -> WorkflowDispatchValidationAudit:
        raise NotImplementedError

    def format_failures(
        self,
        failures: tuple[WorkflowDispatchAuditFailure, ...] | list[WorkflowDispatchAuditFailure],
    ) -> str:
        raise NotImplementedError
