from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from testing.core.models.process_execution_result import ProcessExecutionResult


@dataclass(frozen=True)
class ReportGeneratorRateLimitCheck:
    name: str
    classname: str
    time_seconds: float
    status: str
    failure_message: str = ""

    def describe(self) -> str:
        details = f"{self.classname}.{self.name} [{self.status}]"
        if self.time_seconds > 0:
            details = f"{details} ({self.time_seconds:.3f}s)"
        if self.failure_message:
            return f"{details}: {self.failure_message}"
        return details


@dataclass(frozen=True)
class ReportGeneratorRateLimitFailure:
    step: int
    summary: str
    expected: str
    actual: str

    def format(self) -> str:
        return (
            f"Step {self.step}: {self.summary}\n"
            f"Expected: {self.expected}\n"
            f"Actual: {self.actual}"
        )


@dataclass(frozen=True)
class ReportGeneratorRateLimitAudit:
    gradle_command: tuple[str, ...]
    execution: ProcessExecutionResult
    junit_report_path: Path
    observed_checks: tuple[ReportGeneratorRateLimitCheck, ...]
    failures: tuple[ReportGeneratorRateLimitFailure, ...]
