from __future__ import annotations

from pathlib import Path

from testing.components.services.report_generator_rate_limit_audit_service import (
    ReportGeneratorRateLimitAuditService,
)
from testing.frameworks.api.rest.subprocess_process_runner import SubprocessProcessRunner


def create_report_generator_rate_limit_audit_service(
    repository_root: Path,
    *,
    expected_fallback_delay_ms: int,
) -> ReportGeneratorRateLimitAuditService:
    return ReportGeneratorRateLimitAuditService(
        repository_root=repository_root,
        runner=SubprocessProcessRunner(),
        expected_fallback_delay_ms=expected_fallback_delay_ms,
    )
