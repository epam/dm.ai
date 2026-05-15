from __future__ import annotations

from pathlib import Path

from testing.components.services.report_generator_rate_limit_log_service import (
    ReportGeneratorRateLimitLogService,
)
from testing.frameworks.api.rest.report_generator_rate_limit_harness_factory import (
    RestReportGeneratorRateLimitHarnessFactory,
)
from testing.frameworks.api.rest.subprocess_process_runner import SubprocessProcessRunner


def create_report_generator_rate_limit_log_service(
    repository_root: Path,
) -> ReportGeneratorRateLimitLogService:
    return ReportGeneratorRateLimitLogService(
        repository_root=repository_root,
        runner=SubprocessProcessRunner(),
        harness_factory=RestReportGeneratorRateLimitHarnessFactory(),
    )
