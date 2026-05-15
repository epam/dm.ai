from __future__ import annotations

import shutil
import xml.etree.ElementTree as ET
from pathlib import Path

from testing.core.interfaces.process_runner import ProcessRunner
from testing.core.models.report_generator_rate_limit_audit import (
    ReportGeneratorRateLimitAudit,
    ReportGeneratorRateLimitCheck,
    ReportGeneratorRateLimitFailure,
)


class ReportGeneratorRateLimitService:
    DEFAULT_TEST_CLASS = "com.github.istin.dmtools.reporting.ReportGeneratorTest"
    DEFAULT_TEST_METHODS = (
        "testCollectDataFromAllSources_retriesOnlyInterruptedGitHubMetric",
        "testCalculateRateLimitDelayMs_usesGitHubResetHeaderBeyondDefaultRetryCap",
    )

    def __init__(
        self,
        repository_root: Path,
        runner: ProcessRunner,
        *,
        test_class: str = DEFAULT_TEST_CLASS,
        test_methods: tuple[str, ...] = DEFAULT_TEST_METHODS,
    ) -> None:
        self.repository_root = repository_root
        self.runner = runner
        self.test_class = test_class
        self.test_methods = test_methods
        self.junit_report_path = (
            repository_root / "dmtools-core" / "build" / "test-results" / "test" / f"TEST-{test_class}.xml"
        )
        self.gradle_command = self._build_gradle_command()

    def audit(self) -> ReportGeneratorRateLimitAudit:
        test_results_path = self.junit_report_path.parent
        test_report_directory = self.repository_root / "dmtools-core" / "build" / "reports" / "tests" / "test"
        if test_results_path.exists():
            shutil.rmtree(test_results_path)
        if test_report_directory.exists():
            shutil.rmtree(test_report_directory)
        execution = self.runner.run(self.gradle_command, cwd=self.repository_root)
        observed_checks = self._load_junit_checks()
        failures: list[ReportGeneratorRateLimitFailure] = []
        command_display = " ".join(self.gradle_command)
        combined_output = execution.combined_output

        if execution.returncode != 0:
            failures.append(
                ReportGeneratorRateLimitFailure(
                    step=1,
                    summary="The maintainer-visible ReportGenerator regression command failed.",
                    expected=(
                        f"`{command_display}` should exit 0 so the live implementation can prove the "
                        "rate-limit recovery flow still passes."
                    ),
                    actual=(
                        f"`{command_display}` exited {execution.returncode}.\n"
                        f"{combined_output or '<no Gradle output>'}"
                    ),
                )
            )
        elif "BUILD SUCCESSFUL" not in combined_output:
            failures.append(
                ReportGeneratorRateLimitFailure(
                    step=1,
                    summary="The Gradle output did not visibly confirm a successful maintainer flow.",
                    expected=(
                        f"`{command_display}` should show `BUILD SUCCESSFUL` after the targeted "
                        "ReportGenerator regression checks complete."
                    ),
                    actual=combined_output or "<no Gradle output>",
                )
            )

        if not self.junit_report_path.exists():
            failures.append(
                ReportGeneratorRateLimitFailure(
                    step=2,
                    summary="The JUnit report for the targeted ReportGenerator regression was not produced.",
                    expected=(
                        "Gradle should write the ReportGenerator test report so the ticket automation can "
                        "confirm which retry checks passed."
                    ),
                    actual=(
                        f"`{self.junit_report_path.relative_to(self.repository_root).as_posix()}` "
                        "does not exist."
                    ),
                )
            )
        else:
            observed_by_name = {check.name: check for check in observed_checks}
            for method_name in self.test_methods:
                observed = observed_by_name.get(method_name)
                if observed is None:
                    failures.append(
                        ReportGeneratorRateLimitFailure(
                            step=3,
                            summary="A required ReportGenerator regression check was not executed.",
                            expected=(
                                "The targeted Gradle run should report the exact JUnit method that "
                                "proves the ticket scenario."
                            ),
                            actual=f"Missing JUnit testcase `{self.test_class}.{method_name}`.",
                        )
                    )
                    continue
                if observed.status != "passed":
                    failures.append(
                        ReportGeneratorRateLimitFailure(
                            step=3,
                            summary="A required ReportGenerator regression check did not pass.",
                            expected=(
                                f"`{self.test_class}.{method_name}` should pass to confirm the "
                                "rate-limit retry behavior is still correct."
                            ),
                            actual=observed.describe(),
                        )
                    )

        return ReportGeneratorRateLimitAudit(
            gradle_command=self.gradle_command,
            execution=execution,
            junit_report_path=self.junit_report_path,
            observed_checks=observed_checks,
            failures=tuple(failures),
        )

    def human_observations(self, audit: ReportGeneratorRateLimitAudit) -> list[str]:
        observations: list[str] = []
        build_outcome = (
            "BUILD SUCCESSFUL"
            if "BUILD SUCCESSFUL" in audit.execution.combined_output
            else "BUILD FAILED"
        )
        observations.append(
            "Maintainer flow: running "
            f"`{' '.join(audit.gradle_command)}` exited {audit.execution.returncode} and showed "
            f"`{build_outcome}`."
        )

        checks = {check.name: check for check in audit.observed_checks}
        retry_check = checks.get(
            "testCollectDataFromAllSources_retriesOnlyInterruptedGitHubMetric"
        )
        if retry_check is not None:
            observations.append(
                "Observable report outcome: "
                f"`{retry_check.name}` was `{retry_check.status}`, confirming the "
                "pull-request data source recovered the interrupted GitHub metric and the same "
                "report run still completed with both pull-request metrics populated."
            )

        delay_check = checks.get(
            "testCalculateRateLimitDelayMs_usesGitHubResetHeaderBeyondDefaultRetryCap"
        )
        if delay_check is not None:
            observations.append(
                "Observable wait outcome: "
                f"`{delay_check.name}` was `{delay_check.status}`, confirming the live "
                "implementation honored `X-RateLimit-Reset` instead of falling back to the default "
                "60-second cap."
            )

        if audit.junit_report_path.exists():
            observations.append(
                "JUnit evidence: "
                f"`{audit.junit_report_path.relative_to(self.repository_root).as_posix()}` was "
                "produced for the executed regression run."
            )
        return observations

    def format_failures(self, audit: ReportGeneratorRateLimitAudit) -> str:
        if not audit.failures:
            return (
                "DMC-1030 expects ReportGenerator to honor `X-RateLimit-Reset`, wait before "
                "retrying the interrupted GitHub pull-request collection, and finish the same "
                "report run successfully."
            )

        lines = [
            "DMC-1030 ReportGenerator rate-limit regression failed.",
            "",
            *[failure.format() for failure in audit.failures],
            "",
            "Gradle stdout:",
            audit.execution.stdout.rstrip() or "<empty>",
            "",
            "Gradle stderr:",
            audit.execution.stderr.rstrip() or "<empty>",
        ]
        if audit.observed_checks:
            lines.extend(
                [
                    "",
                    "Observed JUnit checks:",
                    *[f"- {check.describe()}" for check in audit.observed_checks],
                ]
            )
        return "\n".join(lines)

    def _build_gradle_command(self) -> tuple[str, ...]:
        command = ["./gradlew", "--no-daemon", ":dmtools-core:test"]
        for method_name in self.test_methods:
            command.extend(["--tests", f"{self.test_class}.{method_name}"])
        return tuple(command)

    def _load_junit_checks(self) -> tuple[ReportGeneratorRateLimitCheck, ...]:
        if not self.junit_report_path.exists():
            return ()

        root = ET.fromstring(self.junit_report_path.read_text(encoding="utf-8"))
        checks: list[ReportGeneratorRateLimitCheck] = []
        for testcase in root.findall("testcase"):
            status = "passed"
            failure_message = ""
            if testcase.find("failure") is not None:
                status = "failed"
                failure = testcase.find("failure")
                failure_message = (
                    (failure.text or "").strip()
                    if failure is not None
                    else ""
                )
            elif testcase.find("error") is not None:
                status = "error"
                error = testcase.find("error")
                failure_message = (
                    (error.text or "").strip()
                    if error is not None
                    else ""
                )

            checks.append(
                ReportGeneratorRateLimitCheck(
                    name=str(testcase.attrib.get("name", "")).removesuffix("()"),
                    classname=str(testcase.attrib.get("classname", "")),
                    time_seconds=float(testcase.attrib.get("time", "0") or 0.0),
                    status=status,
                    failure_message=failure_message,
                )
            )
        return tuple(checks)
