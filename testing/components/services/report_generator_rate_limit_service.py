from __future__ import annotations

import json
import shutil
import tempfile
import time
from contextlib import AbstractContextManager
from dataclasses import asdict, dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from threading import Lock, Thread
from typing import Any
from urllib.parse import parse_qs, urlparse

from testing.components.services.dmtools_cli_service import DmtoolsCliService
from testing.frameworks.api.rest.subprocess_process_runner import SubprocessProcessRunner


@dataclass(frozen=True)
class MockRequestRecord:
    path: str
    query: dict[str, tuple[str, ...]]
    status: int
    timestamp: float


@dataclass(frozen=True)
class ReportGeneratorRateLimitAudit:
    returncode: int
    stdout: str
    stderr: str
    combined_output: str
    output_json_path: str
    output_html_path: str
    observed_retry_seconds: float
    retry_after_seconds: int
    minimum_observed_retry_seconds: float
    request_records: list[MockRequestRecord]
    report_name: str
    report_metrics: dict[str, dict[str, Any]]
    html_excerpt: str
    html_contains_report_name: bool
    html_contains_pr_approvals: bool
    html_contains_commits: bool

    def to_summary(self) -> dict[str, Any]:
        summary = asdict(self)
        summary["request_records"] = [
            {
                "path": record.path,
                "query": record.query,
                "status": record.status,
                "timestamp": record.timestamp,
            }
            for record in self.request_records
        ]
        return summary


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
    REPORT_NAME = "DMC-1032 Partial Progress Report"
    PR_APPROVALS_LABEL = "PR Approvals"
    COMMITS_LABEL = "Commits"

    def __init__(
        self,
        *,
        repository_root: Path,
        workspace: str,
        repository: str,
        branch: str,
        start_date: str,
        end_date: str,
        retry_after_seconds: int,
        minimum_observed_retry_seconds: float,
    ) -> None:
        self.repository_root = repository_root
        self.workspace = workspace
        self.repository = repository
        self.branch = branch
        self.start_date = start_date
        self.end_date = end_date
        self.retry_after_seconds = retry_after_seconds
        self.minimum_observed_retry_seconds = minimum_observed_retry_seconds
        self.cli = DmtoolsCliService(
            repository_root=repository_root,
            runner=SubprocessProcessRunner(),
        )

    def audit(self) -> ReportGeneratorRateLimitAudit:
        with tempfile.TemporaryDirectory(prefix="dmc-1032-report-") as temp_dir:
            temp_path = Path(temp_dir)
            output_dir = temp_path / "reports"
            output_dir.mkdir(parents=True)

            with _MockGitHubApiServer(retry_after_seconds=self.retry_after_seconds) as server:
                execution = self._run_report_job(
                    base_url=server.base_url,
                    output_dir=output_dir,
                )
                request_records = server.request_records()

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
        retry_window = self._observed_retry_seconds(request_records)
        normalized_html = " ".join(html_text.split())

        return ReportGeneratorRateLimitAudit(
            returncode=execution.returncode,
            stdout=execution.stdout,
            stderr=execution.stderr,
            combined_output=execution.combined_output,
            output_json_path=str(persisted_output_json_path),
            output_html_path=str(persisted_output_html_path),
            observed_retry_seconds=retry_window,
            retry_after_seconds=self.retry_after_seconds,
            minimum_observed_retry_seconds=self.minimum_observed_retry_seconds,
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

    def _run_report_job(self, *, base_url: str, output_dir: Path):
        jar_path = (
            self.cli.find_shadow_jar()
            if any(self.cli.shadow_jar_directory.glob("*-all.jar"))
            else self.cli.build_shadow_jar()
        )
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

        return self.cli.runner.run(
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

    def _observed_retry_seconds(self, request_records: list[MockRequestRecord]) -> float:
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
        artifacts_root = self.repository_root / "testing" / ".artifacts" / "DMC-1032"
        artifacts_root.mkdir(parents=True, exist_ok=True)
        artifact_dir = Path(tempfile.mkdtemp(prefix="report-", dir=artifacts_root))
        persisted_output_json_path = artifact_dir / output_json_path.name
        persisted_output_html_path = artifact_dir / output_html_path.name
        shutil.copy2(output_json_path, persisted_output_json_path)
        shutil.copy2(output_html_path, persisted_output_html_path)
        return persisted_output_json_path, persisted_output_html_path

    def _html_excerpt(self, normalized_html: str) -> str:
        if len(normalized_html) <= 500:
            return normalized_html
        return normalized_html[:500] + "..."
