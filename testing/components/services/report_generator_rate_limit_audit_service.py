from __future__ import annotations

from pathlib import Path

from testing.core.interfaces.report_generator_rate_limit_audit_service import (
    ReportGeneratorRateLimitAuditService as ReportGeneratorRateLimitAuditServiceContract,
)
from testing.core.interfaces.process_runner import ProcessRunner
from testing.core.models.report_generator_rate_limit_audit import (
    ReportGeneratorRateLimitAudit,
)


class ReportGeneratorRateLimitAuditService(ReportGeneratorRateLimitAuditServiceContract):
    def __init__(
        self,
        repository_root: Path,
        runner: ProcessRunner,
        *,
        gradle_task: str,
        target_test: str,
        report_generator_path: str,
        report_generator_test_path: str,
        expected_test_marker: str,
        expected_invalid_reset_header_name: str,
        expected_invalid_reset_header_value: str,
        expected_invalid_reset_warning: str,
        expected_fallback_warning: str,
    ) -> None:
        self.repository_root = repository_root
        self.runner = runner
        self.gradle_task = gradle_task
        self.target_test = target_test
        self.report_generator_path = repository_root / report_generator_path
        self.report_generator_test_path = repository_root / report_generator_test_path
        self.expected_test_marker = expected_test_marker
        self.expected_invalid_reset_header_name = expected_invalid_reset_header_name
        self.expected_invalid_reset_header_value = expected_invalid_reset_header_value
        self.expected_invalid_reset_warning = expected_invalid_reset_warning
        self.expected_fallback_warning = expected_fallback_warning
        self.gradlew_path = repository_root / "gradlew"

    def run_audit(self) -> ReportGeneratorRateLimitAudit:
        report_generator_text = self.report_generator_path.read_text(encoding="utf-8")
        report_generator_test_text = self.report_generator_test_path.read_text(encoding="utf-8")
        target_test_body = self._target_test_body(report_generator_test_text)

        execution = self.runner.run(
            [
                str(self.gradlew_path),
                "--no-daemon",
                self.gradle_task,
                "--tests",
                self.target_test,
            ],
            cwd=self.repository_root,
        )

        return ReportGeneratorRateLimitAudit(
            execution=execution,
            report_generator_path=self.report_generator_path,
            report_generator_test_path=self.report_generator_test_path,
            target_test_present=self.expected_test_marker in report_generator_test_text,
            invalid_reset_test_case_present=(
                self.expected_invalid_reset_header_name in target_test_body
                and self.expected_invalid_reset_header_value in target_test_body
            ),
            invalid_reset_warning_present=self.expected_invalid_reset_warning in report_generator_text,
            fallback_warning_present=self.expected_fallback_warning in report_generator_text,
        )

    def _target_test_body(self, report_generator_test_text: str) -> str:
        test_start = report_generator_test_text.find(self.expected_test_marker)
        if test_start < 0:
            return ""

        next_test = report_generator_test_text.find("\n    @Test", test_start)
        if next_test < 0:
            return report_generator_test_text[test_start:]
        return report_generator_test_text[test_start:next_test]

    def format_failures(self, audit: ReportGeneratorRateLimitAudit) -> str:
        failures: list[str] = []

        if not audit.target_test_present:
            failures.append(
                "The Java regression covering the malformed rate-limit metadata scenario "
                f"was not found in {audit.report_generator_test_path.relative_to(self.repository_root)}."
            )
        if not audit.invalid_reset_test_case_present:
            failures.append(
                "The Java regression no longer exercises an invalid X-RateLimit-Reset header "
                "value inside the targeted test, so the ticket scenario is not exercised."
            )
        if not audit.invalid_reset_warning_present:
            failures.append(
                "ReportGenerator.java no longer contains the malformed X-RateLimit-Reset "
                "warning path required for graceful fallback handling."
            )
        if not audit.fallback_warning_present:
            failures.append(
                "ReportGenerator.java no longer contains the safe fallback wait warning "
                "used when rate-limit metadata is unavailable or invalid."
            )
        if audit.execution.returncode != 0:
            failures.append(
                "The targeted ReportGenerator regression failed.\n"
                f"Command: {' '.join(audit.execution.args)}\n"
                f"stdout:\n{audit.execution.stdout}\n\nstderr:\n{audit.execution.stderr}"
            )

        return "\n\n".join(failures)
