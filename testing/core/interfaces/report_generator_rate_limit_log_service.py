from __future__ import annotations

from typing import Protocol

from testing.core.models.report_generator_rate_limit_log_audit import (
    ReportGeneratorRateLimitLogAudit,
)


class ReportGeneratorRateLimitLogService(Protocol):
    def audit(self) -> ReportGeneratorRateLimitLogAudit:
        raise NotImplementedError
