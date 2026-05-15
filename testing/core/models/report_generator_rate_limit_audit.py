from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from testing.core.models.process_execution_result import ProcessExecutionResult


@dataclass(frozen=True)
class ReportGeneratorRateLimitAudit:
    execution: ProcessExecutionResult
    report_generator_path: Path
    report_generator_test_path: Path
    target_test_present: bool
    invalid_reset_test_case_present: bool
    invalid_reset_warning_present: bool
    fallback_warning_present: bool
