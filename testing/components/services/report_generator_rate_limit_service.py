from __future__ import annotations

import json
import shutil
import tempfile
import time
import xml.etree.ElementTree as ET
from contextlib import AbstractContextManager
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from threading import Lock, Thread
from typing import Any
from urllib.parse import parse_qs, urlparse

from testing.core.interfaces.process_runner import ProcessRunner
from testing.core.models.report_generator_rate_limit_audit import (
    MockRequestRecord,
    ReportGeneratorRateLimitAudit,
    ReportGeneratorRateLimitCheck,
    ReportGeneratorRateLimitFailure,
)


class _MockGitHubState:
    def __init__(self, retry_after_seconds: int) -> None:
        self.retry_after_seconds = retry_after_seconds
        self._lock = Lock()
        self._request_records: list[MockRequestRecord] = []
        self._commits_attempts = 0

    def record(self, path: str, query: dict[str, tuple[str, ...]], status: int) -> None:
        with self._lock:
            self._request_records.append(
                MockRequestRecord(
                    path=path,
                    query=query,
                    status=status,
                    timestamp=time.monotonic(),
                )
            )

    def next_commits_attempt(self) -> int:
        with self._lock:
            self._commits_attempts += 1
            return self._commits_attempts

    def request_records(self) -> list[MockRequestRecord]:
        with self._lock:
            return list(self._request_records)


class _MockGitHubApiServer(AbstractContextManager["_MockGitHubApiServer"]):
    WORKSPACE = "test-workspace"
    REPOSITORY = "test-repo"
    PULL_REQUEST_ID = "101"
    COMMIT_SHA = "abc123"

    def __init__(self, retry_after_seconds: int) -> None:
        self.state = _MockGitHubState(retry_after_seconds=retry_after_seconds)
        self._server = ThreadingHTTPServer(("127.0.0.1", 0), self._build_handler())
        self._thread = Thread(target=self._server.serve_forever, daemon=True)

    def __enter__(self) -> "_MockGitHubApiServer":
        self._thread.start()
        return self

    def __exit__(self, exc_type, exc_value, traceback) -> None:
        self._server.shutdown()
        self._server.server_close()
        self._thread.join(timeout=5)

    @property
    def base_url(self) -> str:
        host, port = self._server.server_address
        return f"http://{host}:{port}"

    def request_records(self) -> list[MockRequestRecord]:
        return self.state.request_records()

    def _build_handler(self) -> type[BaseHTTPRequestHandler]:
        state = self.state
        workspace = self.WORKSPACE
        repository = self.REPOSITORY
        pull_request_id = self.PULL_REQUEST_ID
        commit_sha = self.COMMIT_SHA

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self) -> None:  # noqa: N802
                parsed = urlparse(self.path)
                query = {key: tuple(values) for key, values in parse_qs(parsed.query).items()}
                response_body: Any
                status_code = 200
                headers: dict[str, str] = {"Content-Type": "application/json"}

                if parsed.path == f"/repos/{workspace}/{repository}/pulls":
                    if query.get("page") == ("1",):
                        response_body = [
                            {
                                "number": int(pull_request_id),
                                "title": "Preserve partial progress",
                                "body": "Retry the throttled metric without losing earlier report data.",
                                "user": {"login": "author-user"},
                                "base": {"ref": "main"},
                                "head": {"ref": "feature/rate-limit"},
                                "created_at": "2025-01-10T12:00:00Z",
                                "updated_at": "2025-01-11T12:30:00Z",
                                "closed_at": "2025-01-11T12:30:00Z",
                                "merged_at": "2025-01-11T12:30:00Z",
                                "merged_by": {"login": "reviewer-user"},
                            }
                        ]
                    else:
                        response_body = []
                elif parsed.path == f"/repos/{workspace}/{repository}/pulls/{pull_request_id}/reviews":
                    response_body = [
                        {
                            "id": 9001,
                            "state": "APPROVED",
                            "body": "Approved after validating the retry behaviour.",
                            "user": {"login": "reviewer-user"},
                        }
                    ]
                elif parsed.path == f"/repos/{workspace}/{repository}/pulls/{pull_request_id}/comments":
                    response_body = []
                elif parsed.path == f"/repos/{workspace}/{repository}/issues/{pull_request_id}/comments":
                    response_body = []
                elif parsed.path == f"/repos/{workspace}/{repository}/commits":
                    page = query.get("page", ("1",))
                    if page == ("1",):
                        attempt = state.next_commits_attempt()
                        if attempt == 1:
                            status_code = 429
                            headers["Retry-After"] = str(state.retry_after_seconds)
                            response_body = {"message": "API rate limit exceeded"}
                        else:
                            response_body = [
                                {
                                    "node_id": "commit-node-1",
                                    "sha": commit_sha,
                                    "html_url": f"https://github.example/{workspace}/{repository}/commit/{commit_sha}",
                                    "author": {"login": "commit-author"},
                                    "commit": {
                                        "message": "Preserve partial progress during retry",
                                        "committer": {"date": "2025-01-12T09:15:00Z"},
                                    },
                                }
                            ]
                    else:
                        response_body = []
                else:
                    status_code = 404
                    response_body = {"message": f"Unhandled path: {parsed.path}"}

                state.record(parsed.path, query, status_code)
                payload = json.dumps(response_body).encode("utf-8")
                self.send_response(status_code)
                for header_name, header_value in headers.items():
                    self.send_header(header_name, header_value)
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)

            def log_message(self, format: str, *args: object) -> None:
                del format, args

        return Handler


class ReportGeneratorRateLimitService:
    DEFAULT_TEST_CLASS = "com.github.istin.dmtools.reporting.ReportGeneratorTest"
    DEFAULT_TEST_METHODS = (
        "testCollectDataFromAllSources_retriesOnlyInterruptedGitHubMetric",
        "testCalculateRateLimitDelayMs_usesGitHubResetHeaderBeyondDefaultRetryCap",
    )
    REPORT_NAME = "DMC-1032 Partial Progress Report"
    PR_APPROVALS_LABEL = "PR Approvals"
    COMMITS_LABEL = "Commits"

    def __init__(
        self,
        repository_root: Path,
        runner: ProcessRunner,
        *,
        workspace: str | None = None,
        repository: str | None = None,
        branch: str | None = None,
        start_date: str | None = None,
        end_date: str | None = None,
        retry_after_seconds: int | None = None,
        minimum_observed_retry_seconds: float | None = None,
        test_class: str = DEFAULT_TEST_CLASS,
        test_methods: tuple[str, ...] = DEFAULT_TEST_METHODS,
    ) -> None:
        self.repository_root = repository_root
        self.runner = runner
        self.workspace = workspace
        self.repository = repository
        self.branch = branch
        self.start_date = start_date
        self.end_date = end_date
        self.retry_after_seconds = retry_after_seconds
        self.minimum_observed_retry_seconds = minimum_observed_retry_seconds
        self.test_class = test_class
        self.test_methods = test_methods
        self.gradlew_path = repository_root / "gradlew"
        self.shadow_jar_directory = repository_root / "build" / "libs"
        self.junit_report_path = (
            repository_root / "dmtools-core" / "build" / "test-results" / "test" / f"TEST-{test_class}.xml"
        )
        self.gradle_command = self._build_gradle_command()

    def audit(self) -> ReportGeneratorRateLimitAudit:
        if self._is_live_report_mode():
            return self._audit_partial_progress_live_run()
        return self._audit_gradle_regression_run()

    def human_observations(self, audit: ReportGeneratorRateLimitAudit) -> list[str]:
        if audit.observed_checks:
            observations: list[str] = []
            build_outcome = (
                "BUILD SUCCESSFUL"
                if "BUILD SUCCESSFUL" in audit.combined_output
                else "BUILD FAILED"
            )
            observations.append(
                "Maintainer flow: running "
                f"`{' '.join(audit.gradle_command)}` exited {audit.returncode} and showed "
                f"`{build_outcome}`."
            )

            retry_warning = self._first_matching_line(
                audit.system_out,
                "Rate limit interrupted metric 'PullRequestsApprovalsMetricSource'",
            )
            if retry_warning is not None:
                observations.append(f"Observable retry evidence: `{retry_warning}`")

            recovered_metric = self._first_matching_line(
                audit.system_out,
                "Metric 'PullRequestsApprovalsMetricSource': collected 1 items",
            )
            if recovered_metric is not None:
                observations.append(f"Observable completion evidence: `{recovered_metric}`")

            checks = {check.name: check for check in audit.observed_checks}
            delay_check = checks.get(
                "testCalculateRateLimitDelayMs_usesGitHubResetHeaderBeyondDefaultRetryCap"
            )
            if delay_check is not None:
                observations.append(
                    "Reset-header regression evidence: "
                    f"`{self.test_class}.{delay_check.name}` finished with status `{delay_check.status}`."
                )

            if audit.junit_report_path is not None and audit.junit_report_path.exists():
                observations.append(
                    "JUnit evidence: "
                    f"`{audit.junit_report_path.relative_to(self.repository_root).as_posix()}` was "
                    "produced for the executed regression run."
                )
            return observations

        observations = [
            f"Live ReportGenerator run exited {audit.returncode} after observing "
            f"{audit.observed_retry_seconds:.3f}s between the throttled and successful commits requests."
        ]
        if audit.output_json_path:
            observations.append(f"Preserved JSON report artifact: `{audit.output_json_path}`.")
        if audit.output_html_path:
            observations.append(f"Preserved HTML report artifact: `{audit.output_html_path}`.")
        return observations

    def format_failures(self, audit: ReportGeneratorRateLimitAudit) -> str:
        if audit.failures:
            lines = [
                "DMC-1030 ReportGenerator rate-limit regression failed.",
                "",
                *[failure.format() for failure in audit.failures],
                "",
                "Gradle stdout:",
                audit.stdout.rstrip() or "<empty>",
                "",
                "Gradle stderr:",
                audit.stderr.rstrip() or "<empty>",
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

        if audit.request_records:
            return "\n".join(
                [
                    "DMC-1032 ReportGenerator partial-progress retry validation failed.",
                    "",
                    f"Return code: {audit.returncode}",
                    f"Observed retry delay: {audit.observed_retry_seconds:.3f}s",
                    f"Expected minimum delay: {audit.minimum_observed_retry_seconds:.3f}s",
                    "",
                    "stdout:",
                    audit.stdout.rstrip() or "<empty>",
                    "",
                    "stderr:",
                    audit.stderr.rstrip() or "<empty>",
                ]
            )

        return (
            "ReportGenerator should preserve already collected data, honor the retry delay, "
            "and finish the same report run successfully."
        )

    def _audit_gradle_regression_run(self) -> ReportGeneratorRateLimitAudit:
        test_results_path = self.junit_report_path.parent
        test_report_directory = self.repository_root / "dmtools-core" / "build" / "reports" / "tests" / "test"
        if test_results_path.exists():
            shutil.rmtree(test_results_path)
        if test_report_directory.exists():
            shutil.rmtree(test_report_directory)

        execution = self.runner.run(self.gradle_command, cwd=self.repository_root)
        observed_checks, system_out, system_err = self._load_junit_report()
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
            system_out=system_out,
            system_err=system_err,
            failures=tuple(failures),
            returncode=execution.returncode,
            stdout=execution.stdout,
            stderr=execution.stderr,
        )

    def _audit_partial_progress_live_run(self) -> ReportGeneratorRateLimitAudit:
        self._validate_live_report_configuration()

        with tempfile.TemporaryDirectory(prefix="dmc-1032-report-") as temp_dir:
            temp_path = Path(temp_dir)
            output_dir = temp_path / "reports"
            output_dir.mkdir(parents=True)

            with _MockGitHubApiServer(retry_after_seconds=int(self.retry_after_seconds)) as server:
                execution = self._run_live_report_job(
                    base_url=server.base_url,
                    output_dir=output_dir,
                )
                request_records = tuple(server.request_records())

            output_json_path = output_dir / "DMC-1032_Partial_Progress_Report.json"
            output_html_path = output_dir / "DMC-1032_Partial_Progress_Report.html"
            report = json.loads(output_json_path.read_text(encoding="utf-8"))
            html_text = output_html_path.read_text(encoding="utf-8")
            persisted_output_json_path, persisted_output_html_path = self._persist_report_artifacts(
                output_json_path=output_json_path,
                output_html_path=output_html_path,
            )

        period = report["timePeriods"][0]
        metrics = period["metrics"]
        retry_window = self._observed_retry_seconds(list(request_records))
        normalized_html = " ".join(html_text.split())

        return ReportGeneratorRateLimitAudit(
            returncode=execution.returncode,
            stdout=execution.stdout,
            stderr=execution.stderr,
            output_json_path=str(persisted_output_json_path),
            output_html_path=str(persisted_output_html_path),
            observed_retry_seconds=retry_window,
            retry_after_seconds=int(self.retry_after_seconds),
            minimum_observed_retry_seconds=float(self.minimum_observed_retry_seconds),
            request_records=request_records,
            report_name=str(report["reportName"]),
            report_metrics={
                metric_name: {
                    "count": metric_payload["count"],
                    "totalWeight": metric_payload["totalWeight"],
                    "contributors": metric_payload["contributors"],
                }
                for metric_name, metric_payload in metrics.items()
            },
            html_excerpt=self._html_excerpt(normalized_html),
            html_contains_report_name=self.REPORT_NAME in normalized_html,
            html_contains_pr_approvals=self.PR_APPROVALS_LABEL in normalized_html,
            html_contains_commits=self.COMMITS_LABEL in normalized_html,
        )

    def _run_live_report_job(self, *, base_url: str, output_dir: Path):
        jar_path = self._find_shadow_jar() if any(self.shadow_jar_directory.glob("*-all.jar")) else self._build_shadow_jar()
        payload = {
            "name": "ReportGenerator",
            "params": {
                "reportName": self.REPORT_NAME,
                "startDate": self.start_date,
                "endDate": self.end_date,
                "dataSources": [
                    {
                        "name": "pullRequests",
                        "params": {
                            "sourceType": "github",
                            "workspace": self.workspace,
                            "repository": self.repository,
                        },
                        "metrics": [
                            {
                                "name": "PullRequestsApprovalsMetricSource",
                                "params": {
                                    "label": self.PR_APPROVALS_LABEL,
                                },
                            }
                        ],
                    },
                    {
                        "name": "commits",
                        "params": {
                            "sourceType": "github",
                            "workspace": self.workspace,
                            "repository": self.repository,
                            "branch": self.branch,
                        },
                        "metrics": [
                            {
                                "name": "CommitsMetricSource",
                                "params": {
                                    "label": self.COMMITS_LABEL,
                                },
                            }
                        ],
                    },
                ],
                "timeGrouping": {"type": "monthly"},
                "aggregation": {
                    "label": "Total Activity",
                    "formula": f"${{{self.PR_APPROVALS_LABEL}}} + ${{{self.COMMITS_LABEL}}}",
                },
                "output": {
                    "mode": "combined",
                    "outputPath": str(output_dir),
                    "visualizer": "default",
                },
            },
        }

        config_path = output_dir.parent / "report-generator.json"
        config_path.write_text(json.dumps(payload), encoding="utf-8")

        env = {
            "SOURCE_GITHUB_BASE_PATH": base_url,
            "SOURCE_GITHUB_TOKEN": "testing-token",
            "SOURCE_GITHUB_WORKSPACE": self.workspace,
            "SOURCE_GITHUB_REPOSITORY": self.repository,
            "SOURCE_GITHUB_BRANCH": self.branch,
        }

        return self.runner.run(
            [
                "java",
                "-Dlog4j2.configurationFile=classpath:log4j2-cli.xml",
                "-Dlog4j.configuration=log4j2-cli.xml",
                "-Dlog4j2.disable.jmx=true",
                "-Djava.net.preferIPv4Stack=true",
                "-Djava.rmi.server.hostname=127.0.0.1",
                "--add-opens",
                "java.base/java.lang=ALL-UNNAMED",
                "-XX:-PrintWarnings",
                "-Dpolyglot.engine.WarnInterpreterOnly=false",
                "-cp",
                str(jar_path),
                "com.github.istin.dmtools.job.JobRunner",
                "run",
                str(config_path),
            ],
            cwd=self.repository_root,
            env=env,
        )

    def _build_shadow_jar(self) -> Path:
        result = self.runner.run(
            [str(self.gradlew_path), "--no-daemon", ":dmtools-core:shadowJar"],
            cwd=self.repository_root,
        )
        if result.returncode != 0:
            raise AssertionError(
                "Failed to build the DMTools fat JAR.\n"
                f"stdout:\n{result.stdout}\n\nstderr:\n{result.stderr}"
            )
        return self._find_shadow_jar()

    def _find_shadow_jar(self) -> Path:
        jars = sorted(
            self.shadow_jar_directory.glob("*-all.jar"),
            key=lambda candidate: candidate.stat().st_mtime,
            reverse=True,
        )
        if not jars:
            raise FileNotFoundError(
                f"No fat JAR was found under {self.shadow_jar_directory.as_posix()}."
            )
        return jars[0]

    def _build_gradle_command(self) -> tuple[str, ...]:
        command = ["./gradlew", "--no-daemon", ":dmtools-core:test"]
        for method_name in self.test_methods:
            command.extend(["--tests", f"{self.test_class}.{method_name}"])
        return tuple(command)

    def _load_junit_report(
        self,
    ) -> tuple[tuple[ReportGeneratorRateLimitCheck, ...], str, str]:
        if not self.junit_report_path.exists():
            return (), "", ""

        root = ET.fromstring(self.junit_report_path.read_text(encoding="utf-8"))
        checks: list[ReportGeneratorRateLimitCheck] = []
        for testcase in root.findall("testcase"):
            status = "passed"
            failure_message = ""
            if testcase.find("failure") is not None:
                status = "failed"
                failure = testcase.find("failure")
                failure_message = ((failure.text or "").strip() if failure is not None else "")
            elif testcase.find("error") is not None:
                status = "error"
                error = testcase.find("error")
                failure_message = ((error.text or "").strip() if error is not None else "")

            checks.append(
                ReportGeneratorRateLimitCheck(
                    name=str(testcase.attrib.get("name", "")).removesuffix("()"),
                    classname=str(testcase.attrib.get("classname", "")),
                    time_seconds=float(testcase.attrib.get("time", "0") or 0.0),
                    status=status,
                    failure_message=failure_message,
                )
            )
        system_out = (root.findtext("system-out") or "").strip()
        system_err = (root.findtext("system-err") or "").strip()
        return tuple(checks), system_out, system_err

    def _observed_retry_seconds(self, request_records: list[MockRequestRecord]) -> float:
        if self.workspace is None or self.repository is None:
            raise AssertionError("Live report configuration is incomplete.")

        first_attempt: float | None = None
        second_attempt: float | None = None
        for record in request_records:
            if record.path != f"/repos/{self.workspace}/{self.repository}/commits":
                continue
            if record.query.get("page") != ("1",):
                continue
            if first_attempt is None:
                first_attempt = record.timestamp
                continue
            if record.status == 200:
                second_attempt = record.timestamp
                break
        if first_attempt is None or second_attempt is None:
            raise AssertionError(
                "Expected the mock GitHub server to observe a throttled commits request followed by a successful retry.\n"
                f"Recorded requests: {request_records!r}"
            )
        return second_attempt - first_attempt

    def _persist_report_artifacts(self, *, output_json_path: Path, output_html_path: Path) -> tuple[Path, Path]:
        artifact_dir = Path(tempfile.mkdtemp(prefix="dmc-1032-report-artifacts-"))
        persisted_output_json_path = artifact_dir / output_json_path.name
        persisted_output_html_path = artifact_dir / output_html_path.name
        shutil.copy2(output_json_path, persisted_output_json_path)
        shutil.copy2(output_html_path, persisted_output_html_path)
        return persisted_output_json_path, persisted_output_html_path

    def _validate_live_report_configuration(self) -> None:
        missing_values = [
            name
            for name, value in (
                ("workspace", self.workspace),
                ("repository", self.repository),
                ("branch", self.branch),
                ("start_date", self.start_date),
                ("end_date", self.end_date),
                ("retry_after_seconds", self.retry_after_seconds),
                ("minimum_observed_retry_seconds", self.minimum_observed_retry_seconds),
            )
            if value is None
        ]
        if missing_values:
            raise ValueError(
                "Live ReportGenerator audit mode requires: " + ", ".join(missing_values)
            )

    def _is_live_report_mode(self) -> bool:
        return any(
            value is not None
            for value in (
                self.workspace,
                self.repository,
                self.branch,
                self.start_date,
                self.end_date,
                self.retry_after_seconds,
                self.minimum_observed_retry_seconds,
            )
        )

    @staticmethod
    def _first_matching_line(text: str, marker: str) -> str | None:
        for line in text.splitlines():
            normalized = line.strip()
            if marker in normalized:
                return normalized
        return None

    @staticmethod
    def _html_excerpt(normalized_html: str) -> str:
        if len(normalized_html) <= 500:
            return normalized_html
        return normalized_html[:500] + "..."
