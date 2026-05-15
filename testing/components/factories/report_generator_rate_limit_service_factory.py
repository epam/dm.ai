from __future__ import annotations

from pathlib import Path

from testing.components.services.report_generator_rate_limit_service import (
    ReportGeneratorRateLimitService,
)


def create_report_generator_rate_limit_service(
    repository_root: Path,
    *,
    workspace: str,
    repository: str,
    branch: str,
    start_date: str,
    end_date: str,
    retry_after_seconds: int,
    minimum_observed_retry_seconds: float,
) -> ReportGeneratorRateLimitService:
    return ReportGeneratorRateLimitService(
        repository_root=repository_root,
        workspace=workspace,
        repository=repository,
        branch=branch,
        start_date=start_date,
        end_date=end_date,
        retry_after_seconds=retry_after_seconds,
        minimum_observed_retry_seconds=minimum_observed_retry_seconds,
    )
