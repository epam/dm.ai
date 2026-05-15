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
    test_class: str = ReportGeneratorRateLimitServiceImpl.DEFAULT_TEST_CLASS,
    test_methods: tuple[str, ...] = ReportGeneratorRateLimitServiceImpl.DEFAULT_TEST_METHODS,
) -> ReportGeneratorRateLimitService:
    return ReportGeneratorRateLimitServiceImpl(
        repository_root=repository_root,
        runner=SubprocessProcessRunner(),
        test_class=test_class,
        test_methods=test_methods,
    )
