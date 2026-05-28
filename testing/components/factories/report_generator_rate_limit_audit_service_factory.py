from __future__ import annotations

from pathlib import Path

from testing.components.services.report_generator_rate_limit_audit_service import (
    ReportGeneratorRateLimitAuditService as ReportGeneratorRateLimitAuditServiceImpl,
)
from testing.core.interfaces.process_runner import ProcessRunner
from testing.core.interfaces.report_generator_rate_limit_audit_service import (
    ReportGeneratorRateLimitAuditService,
)
from testing.frameworks.api.rest.subprocess_process_runner import SubprocessProcessRunner


def create_report_generator_rate_limit_audit_service(
    repository_root: Path,
    runner: ProcessRunner | None = None,
    *,
    gradle_task: str | None = None,
    target_test: str | None = None,
    report_generator_path: str | None = None,
    expected_rate_limit_status: int | None = None,
    expected_invalid_reset_header_name: str | None = None,
    expected_invalid_reset_header_value: str | None = None,
    expected_invalid_reset_warning: str | None = None,
    expected_fallback_warning: str | None = None,
    expected_retry_log: str | None = None,
    expected_fallback_delay_ms: int | None = None,
) -> ReportGeneratorRateLimitAuditService:
    return ReportGeneratorRateLimitAuditServiceImpl(
        repository_root=repository_root,
        runner=runner or SubprocessProcessRunner(),
        gradle_task=gradle_task,
        target_test=target_test,
        report_generator_path=report_generator_path,
        expected_rate_limit_status=expected_rate_limit_status,
        expected_invalid_reset_header_name=expected_invalid_reset_header_name,
        expected_invalid_reset_header_value=expected_invalid_reset_header_value,
        expected_invalid_reset_warning=expected_invalid_reset_warning,
        expected_fallback_warning=expected_fallback_warning,
        expected_retry_log=expected_retry_log,
        expected_fallback_delay_ms=expected_fallback_delay_ms,
    )
