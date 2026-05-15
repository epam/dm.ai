from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from testing.core.models.process_execution_result import ProcessExecutionResult
from testing.frameworks.api.rest.report_generator_rate_limit_harness import (
    RecordedRequest,
)


@dataclass(frozen=True)
class ReportGeneratorRateLimitLogAudit:
    bootstrap_result: ProcessExecutionResult
    job_result: ProcessExecutionResult
    recorded_requests: tuple[RecordedRequest, ...]
    report_json_path: Path | None
    report_json_text: str
    rate_limit_warning_line: str | None
    wait_line: str | None
    wait_duration_ms: int | None
    retry_confirmation_line: str | None
    metric_collection_line: str | None
    request_gap_seconds: float | None
