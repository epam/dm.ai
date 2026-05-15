from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from testing.core.models.process_execution_result import ProcessExecutionResult


@dataclass(frozen=True)
class MockRequestRecord:
    path: str
    query: dict[str, tuple[str, ...]]
    status: int
    timestamp: float


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
    gradle_command: tuple[str, ...] = ()
    execution: ProcessExecutionResult | None = None
    junit_report_path: Path | None = None
    observed_checks: tuple[ReportGeneratorRateLimitCheck, ...] = ()
    system_out: str = ""
    system_err: str = ""
    failures: tuple[ReportGeneratorRateLimitFailure, ...] = ()
    missing_header_probe_execution: ProcessExecutionResult | None = None
    invalid_reset_probe_execution: ProcessExecutionResult | None = None
    report_generator_path: Path | None = None
    invalid_reset_warning_present: bool = False
    fallback_warning_present: bool = False
    returncode: int = 0
    stdout: str = ""
    stderr: str = ""
    output_json_path: str = ""
    output_html_path: str = ""
    observed_retry_seconds: float = 0.0
    retry_after_seconds: int = 0
    minimum_observed_retry_seconds: float = 0.0
    request_records: tuple[MockRequestRecord, ...] = ()
    report_name: str = ""
    report_metrics: dict[str, dict[str, Any]] = field(default_factory=dict)
    html_excerpt: str = ""
    html_contains_report_name: bool = False
    html_contains_pr_approvals: bool = False
    html_contains_commits: bool = False

    @property
    def combined_output(self) -> str:
        if self.execution is not None:
            return self.execution.combined_output
        return "\n".join(part for part in (self.stdout.strip(), self.stderr.strip()) if part)

    def to_summary(self) -> dict[str, Any]:
        return {
            "returncode": self.returncode,
            "stdout": self.stdout,
            "stderr": self.stderr,
            "combined_output": self.combined_output,
            "output_json_path": self.output_json_path,
            "output_html_path": self.output_html_path,
            "observed_retry_seconds": self.observed_retry_seconds,
            "retry_after_seconds": self.retry_after_seconds,
            "minimum_observed_retry_seconds": self.minimum_observed_retry_seconds,
            "request_records": [
                {
                    "path": record.path,
                    "query": record.query,
                    "status": record.status,
                    "timestamp": record.timestamp,
                }
                for record in self.request_records
            ],
            "report_name": self.report_name,
            "report_metrics": self.report_metrics,
            "html_excerpt": self.html_excerpt,
            "html_contains_report_name": self.html_contains_report_name,
            "html_contains_pr_approvals": self.html_contains_pr_approvals,
            "html_contains_commits": self.html_contains_commits,
            "report_generator_path": (
                self.report_generator_path.as_posix()
                if self.report_generator_path is not None
                else ""
            ),
            "invalid_reset_warning_present": self.invalid_reset_warning_present,
            "fallback_warning_present": self.fallback_warning_present,
        }
