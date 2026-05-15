from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from testing.core.models.process_execution_result import ProcessExecutionResult


@dataclass(frozen=True)
class ReportGeneratorRateLimitAudit:
    execution: ProcessExecutionResult | None = None
    missing_header_probe_execution: ProcessExecutionResult | None = None
    invalid_reset_probe_execution: ProcessExecutionResult | None = None
    report_generator_path: Path | None = None
    invalid_reset_warning_present: bool = False
    fallback_warning_present: bool = False
