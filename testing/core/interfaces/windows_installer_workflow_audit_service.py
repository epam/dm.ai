from __future__ import annotations

from typing import Protocol

from testing.core.models.windows_installer_workflow_audit import (
    WindowsInstallerWorkflowAudit,
    WindowsInstallerWorkflowAuditFailure,
)


class WindowsInstallerWorkflowAuditService(Protocol):
    def audit(self) -> WindowsInstallerWorkflowAudit:
        raise NotImplementedError

    def format_failures(
        self,
        failures: tuple[WindowsInstallerWorkflowAuditFailure, ...]
        | list[WindowsInstallerWorkflowAuditFailure],
    ) -> str:
        raise NotImplementedError
