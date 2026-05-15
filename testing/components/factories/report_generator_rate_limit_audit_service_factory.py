from __future__ import annotations

from pathlib import Path

from testing.components.services.report_generator_rate_limit_audit_service import (
    ReportGeneratorRateLimitAuditService,
)
from testing.frameworks.api.rest.subprocess_process_runner import SubprocessProcessRunner


def create_report_generator_rate_limit_audit_service(
    repository_root: Path,
    *,
    gradle_task: str,
    target_test: str,
    report_generator_path: str,
    report_generator_test_path: str,
    expected_test_marker: str,
    expected_invalid_reset_test_line: str,
    expected_invalid_reset_warning: str,
    expected_fallback_warning: str,
) -> ReportGeneratorRateLimitAuditService:
    return ReportGeneratorRateLimitAuditService(
        repository_root=repository_root,
        runner=SubprocessProcessRunner(),
        gradle_task=gradle_task,
        target_test=target_test,
        report_generator_path=report_generator_path,
        report_generator_test_path=report_generator_test_path,
        expected_test_marker=expected_test_marker,
        expected_invalid_reset_test_line=expected_invalid_reset_test_line,
        expected_invalid_reset_warning=expected_invalid_reset_warning,
        expected_fallback_warning=expected_fallback_warning,
    )
