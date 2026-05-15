from __future__ import annotations

from testing.core.interfaces.report_generator_rate_limit_harness import (
    ReportGeneratorRateLimitHarness,
    ReportGeneratorRateLimitHarnessFactory,
)
from testing.frameworks.api.rest.report_generator_rate_limit_harness import (
    ReportGeneratorRateLimitHarness as RestReportGeneratorRateLimitHarness,
)


class RestReportGeneratorRateLimitHarnessFactory(ReportGeneratorRateLimitHarnessFactory):
    def create(
        self,
        *,
        workspace: str,
        repository: str,
        branch: str,
    ) -> ReportGeneratorRateLimitHarness:
        return RestReportGeneratorRateLimitHarness(
            workspace=workspace,
            repository=repository,
            branch=branch,
        )
