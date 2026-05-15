from __future__ import annotations

from typing import Protocol

from testing.core.models.recorded_request import RecordedRequest


class ReportGeneratorRateLimitHarness(Protocol):
    @property
    def base_url(self) -> str:
        raise NotImplementedError

    @property
    def requests(self) -> tuple[RecordedRequest, ...]:
        raise NotImplementedError

    def __enter__(self) -> "ReportGeneratorRateLimitHarness":
        raise NotImplementedError

    def __exit__(self, exc_type, exc, tb) -> None:  # type: ignore[override]
        raise NotImplementedError


class ReportGeneratorRateLimitHarnessFactory(Protocol):
    def create(
        self,
        *,
        workspace: str,
        repository: str,
        branch: str,
    ) -> ReportGeneratorRateLimitHarness:
        raise NotImplementedError
