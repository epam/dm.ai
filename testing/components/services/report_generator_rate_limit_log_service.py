from __future__ import annotations

import json
import re
import tempfile
from pathlib import Path

from testing.core.interfaces.process_runner import ProcessRunner
from testing.core.interfaces.report_generator_rate_limit_harness import (
    ReportGeneratorRateLimitHarnessFactory,
)
from testing.core.models.report_generator_rate_limit_log_audit import (
    ReportGeneratorRateLimitLogAudit,
)


class ReportGeneratorRateLimitLogService:
    ANSI_ESCAPE_PATTERN = re.compile(r"\x1B\[[0-?]*[ -/]*[@-~]")
    WAIT_LINE_PATTERN = re.compile(r"Waiting (?P<delay>\d+) ms before retry")
    RETRY_REFERENCE_PATTERN = re.compile(
        r"\b(retry(?:ing|ies|ied)?|re-try|try(?:ing)?\s+again|another\s+attempt|"
        r"second\s+attempt)\b",
        re.IGNORECASE,
    )
    RETRY_ACTION_PATTERN = re.compile(
        r"\b(start(?:ed|ing)?|initiat(?:e|ed|ing)|attempt(?:ing)?|resum(?:e|ed|es|ing)|"
        r"continu(?:e|ed|es|ing)|proceed(?:ed|ing|s)?|issu(?:e|ed|es|ing)|"
        r"send(?:ing|s)?|perform(?:ed|ing|s)?|mak(?:e|es|ing)|"
        r"run(?:ning|s)?|call(?:ing|s)?)\b",
        re.IGNORECASE,
    )
    RETRY_TARGET_PATTERN = re.compile(r"\b(request|call|attempt)\b", re.IGNORECASE)

    def __init__(
        self,
        repository_root: Path,
        runner: ProcessRunner,
        harness_factory: ReportGeneratorRateLimitHarnessFactory,
    ) -> None:
        self.repository_root = repository_root
        self.runner = runner
        self.harness_factory = harness_factory

    def audit(self) -> ReportGeneratorRateLimitLogAudit:
        with tempfile.TemporaryDirectory(prefix="dmc-1033-") as temp_dir:
            temp_root = Path(temp_dir)
            home_dir = temp_root / "home"
            work_dir = temp_root / "work"
            home_dir.mkdir(parents=True)
            work_dir.mkdir(parents=True)

            common_env = {
                "HOME": str(home_dir),
                "GRADLE_USER_HOME": str(home_dir / ".gradle"),
                "XDG_CACHE_HOME": str(home_dir / ".cache"),
                "PYTHONUNBUFFERED": "1",
            }

            bootstrap_result = self.runner.run(
                ["bash", "./buildInstallLocal.sh"],
                cwd=self.repository_root,
                env=common_env,
            )

            workspace = "rate-limit-owner"
            repository = "rate-limit-repo"
            branch = "main"

            with self.harness_factory.create(
                workspace=workspace,
                repository=repository,
                branch=branch,
            ) as harness:
                config_path = work_dir / "report-generator-rate-limit.json"
                config_path.write_text(
                    json.dumps(
                        {
                            "name": "ReportGenerator",
                            "params": {
                                "reportName": "DMC-1033 Rate Limit Logging",
                                "startDate": "2026-05-01",
                                "endDate": "2026-05-31",
                                "dataSources": [
                                    {
                                        "name": "commits",
                                        "params": {
                                            "sourceType": "github",
                                            "workspace": workspace,
                                            "repository": repository,
                                            "branch": branch,
                                        },
                                        "metrics": [
                                            {
                                                "name": "CommitsMetricSource",
                                                "params": {"label": "GitHub Commits"},
                                            }
                                        ],
                                    }
                                ],
                                "timeGrouping": {
                                    "type": "static",
                                    "periods": [
                                        {
                                            "name": "May 2026",
                                            "start": "2026-05-01",
                                            "end": "2026-05-31",
                                        }
                                    ],
                                },
                                "aggregation": {
                                    "formula": "${GitHub Commits}",
                                    "label": "GitHub Commits",
                                },
                                "output": {
                                    "mode": "combined",
                                    "outputPath": str((work_dir / "reports").resolve()),
                                    "visualizer": "none",
                                },
                            },
                        },
                        indent=2,
                    ),
                    encoding="utf-8",
                )

                job_env = {
                    **common_env,
                    "SOURCE_GITHUB_TOKEN": "test-token",
                    "SOURCE_GITHUB_WORKSPACE": workspace,
                    "SOURCE_GITHUB_REPOSITORY": repository,
                    "SOURCE_GITHUB_BRANCH": branch,
                    "SOURCE_GITHUB_BASE_PATH": harness.base_url,
                }
                job_result = self.runner.run(
                    [
                        str((self.repository_root / "dmtools.sh").resolve()),
                        "--debug",
                        "run",
                        str(config_path),
                    ],
                    cwd=work_dir,
                    env=job_env,
                )

                report_paths = sorted((work_dir / "reports").glob("*.json"))
                report_json_path = report_paths[0] if report_paths else None
                report_json_text = (
                    report_json_path.read_text(encoding="utf-8")
                    if report_json_path is not None
                    else ""
                )

                sanitized_output = self.ANSI_ESCAPE_PATTERN.sub(
                    "",
                    job_result.combined_output,
                )
                output_lines = [
                    line.strip()
                    for line in sanitized_output.splitlines()
                    if line.strip()
                ]

                rate_limit_warning_line = next(
                    (
                        line
                        for line in output_lines
                        if "Rate limit hit" in line or "Rate limit interrupted metric" in line
                    ),
                    None,
                )
                wait_line = next(
                    (
                        line
                        for line in output_lines
                        if self.WAIT_LINE_PATTERN.search(line) is not None
                    ),
                    None,
                )
                wait_duration_ms = None
                if wait_line is not None:
                    wait_match = self.WAIT_LINE_PATTERN.search(wait_line)
                    if wait_match is not None:
                        wait_duration_ms = int(wait_match.group("delay"))

                retry_confirmation_line = self._find_retry_confirmation_line(output_lines)
                metric_collection_line = next(
                    (
                        line
                        for line in output_lines
                        if "Metric 'GitHub Commits': collected" in line
                    ),
                    None,
                )

                page_one_requests = [
                    request
                    for request in harness.requests
                    if "/commits" in request.path and "page=1" in request.path
                ]
                request_gap_seconds = None
                if len(page_one_requests) >= 2:
                    request_gap_seconds = (
                        page_one_requests[1].timestamp - page_one_requests[0].timestamp
                    )

                return ReportGeneratorRateLimitLogAudit(
                    bootstrap_result=bootstrap_result,
                    job_result=job_result,
                    recorded_requests=harness.requests,
                    report_json_path=report_json_path,
                    report_json_text=report_json_text,
                    rate_limit_warning_line=rate_limit_warning_line,
                    wait_line=wait_line,
                    wait_duration_ms=wait_duration_ms,
                    retry_confirmation_line=retry_confirmation_line,
                    metric_collection_line=metric_collection_line,
                    request_gap_seconds=request_gap_seconds,
                )

    @classmethod
    def _find_retry_confirmation_line(
        cls,
        output_lines: list[str],
    ) -> str | None:
        for line in output_lines:
            lowered_line = line.lower()
            if "before retry" in lowered_line:
                continue
            if cls._is_retry_confirmation_line(line):
                return line
        return None

    @classmethod
    def _is_retry_confirmation_line(cls, line: str) -> bool:
        if cls.RETRY_ACTION_PATTERN.search(line) is None:
            return False
        if cls.RETRY_REFERENCE_PATTERN.search(line) is not None:
            return True
        return "again" in line.lower() and cls.RETRY_TARGET_PATTERN.search(line) is not None
