from __future__ import annotations

from typing import TYPE_CHECKING, Protocol

if TYPE_CHECKING:
    from testing.components.services.report_generator_rate_limit_service import (
        ReportGeneratorRateLimitAudit,
    )


class ReportGeneratorRateLimitService(Protocol):
    def audit(self) -> "ReportGeneratorRateLimitAudit":
        raise NotImplementedError
