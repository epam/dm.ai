from __future__ import annotations

from pathlib import Path

from testing.components.services.report_generator_rate_limit_service import (
    ReportGeneratorRateLimitService as ReportGeneratorRateLimitServiceImpl,
)
from testing.core.interfaces.report_generator_rate_limit_service import (
    ReportGeneratorRateLimitService,
)
from testing.frameworks.api.rest.subprocess_process_runner import SubprocessProcessRunner


def create_report_generator_rate_limit_service(
    repository_root: Path,
    *,
    workspace: str | None = None,
    repository: str | None = None,
    branch: str | None = None,
    start_date: str | None = None,
    end_date: str | None = None,
    retry_after_seconds: int | None = None,
    minimum_observed_retry_seconds: float | None = None,
    test_class: str = ReportGeneratorRateLimitServiceImpl.DEFAULT_TEST_CLASS,
    test_methods: tuple[str, ...] = ReportGeneratorRateLimitServiceImpl.DEFAULT_TEST_METHODS,
) -> ReportGeneratorRateLimitService:
    return ReportGeneratorRateLimitServiceImpl(
        repository_root=repository_root,
        runner=SubprocessProcessRunner(),
        workspace=workspace,
        repository=repository,
        branch=branch,
        start_date=start_date,
        end_date=end_date,
        retry_after_seconds=retry_after_seconds,
        minimum_observed_retry_seconds=minimum_observed_retry_seconds,
        test_class=test_class,
        test_methods=test_methods,
    )
