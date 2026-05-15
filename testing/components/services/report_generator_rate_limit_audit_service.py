from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from testing.core.interfaces.process_runner import ProcessRunner
from testing.core.models.process_execution_result import ProcessExecutionResult


@dataclass(frozen=True)
class ReportGeneratorRateLimitAudit:
    implementation_text: str
    regression_test_text: str
    gradle_execution: ProcessExecutionResult
    report_xml_text: str


class ReportGeneratorRateLimitAuditService:
    def __init__(
        self,
        repository_root: Path,
        runner: ProcessRunner,
        *,
        implementation_relative_path: str,
        regression_test_relative_path: str,
        java_test_class: str,
        java_test_method: str,
    ) -> None:
        self.repository_root = repository_root
        self.runner = runner
        self.implementation_path = repository_root / implementation_relative_path
        self.regression_test_path = repository_root / regression_test_relative_path
        self.java_test_class = java_test_class
        self.java_test_method = java_test_method
        self.gradlew_path = repository_root / "gradlew"
        self.report_xml_path = (
            repository_root
            / "dmtools-core"
            / "build"
            / "test-results"
            / "test"
            / f"TEST-{self.java_test_class}.xml"
        )

    @property
    def java_test_selector(self) -> str:
        return f"{self.java_test_class}.{self.java_test_method}"

    def audit(self) -> ReportGeneratorRateLimitAudit:
        gradle_execution = self.runner.run(
            [
                str(self.gradlew_path),
                "--no-daemon",
                ":dmtools-core:test",
                "--tests",
                self.java_test_selector,
                "--rerun-tasks",
                "--console=plain",
            ],
            cwd=self.repository_root,
        )

        return ReportGeneratorRateLimitAudit(
            implementation_text=self.implementation_path.read_text(encoding="utf-8"),
            regression_test_text=self.regression_test_path.read_text(encoding="utf-8"),
            gradle_execution=gradle_execution,
            report_xml_text=(
                self.report_xml_path.read_text(encoding="utf-8")
                if self.report_xml_path.exists()
                else ""
            ),
        )
