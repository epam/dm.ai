from __future__ import annotations

from typing import Protocol

from testing.core.models.beta_release_summary_audit import (
    BetaReleaseAuditFailure,
    BetaReleaseSummaryAudit,
)


class BetaReleaseSummaryAuditService(Protocol):
    def audit(self) -> BetaReleaseSummaryAudit:
        raise NotImplementedError

    def format_failures(
        self,
        failures: tuple[BetaReleaseAuditFailure, ...] | list[BetaReleaseAuditFailure],
    ) -> str:
        raise NotImplementedError

    def human_observations(self, audit: BetaReleaseSummaryAudit) -> list[str]:
        raise NotImplementedError
