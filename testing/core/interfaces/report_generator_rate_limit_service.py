from __future__ import annotations

from typing import Protocol

from testing.core.models.report_generator_rate_limit_audit import (
    ReportGeneratorRateLimitAudit,
)


class ReportGeneratorRateLimitService(Protocol):
    def audit(self) -> ReportGeneratorRateLimitAudit:
        raise NotImplementedError

    def human_observations(self, audit: ReportGeneratorRateLimitAudit) -> list[str]:
        raise NotImplementedError

    def format_failures(self, audit: ReportGeneratorRateLimitAudit) -> str:
        raise NotImplementedError
