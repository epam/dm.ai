from __future__ import annotations

import json
import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.services.report_generator_rate_limit_log_service import (  # noqa: E402
    ReportGeneratorRateLimitLogService,
)
from testing.core.models.process_execution_result import ProcessExecutionResult  # noqa: E402
from testing.core.models.recorded_request import RecordedRequest  # noqa: E402


class FakeProcessRunner:
    def __init__(self, job_output: str) -> None:
        self.job_output = job_output
        self.calls: list[tuple[tuple[str, ...], Path, dict[str, str | None] | None]] = []

    def run(
        self,
        args: tuple[str, ...] | list[str],
        cwd: Path,
        env: dict[str, str | None] | None = None,
        trace_network: bool = False,
    ) -> ProcessExecutionResult:
        del trace_network
        call_args = tuple(args)
        self.calls.append((call_args, cwd, dict(env) if env is not None else None))
        if call_args[:2] == ("bash", "./buildInstallLocal.sh"):
            return ProcessExecutionResult(
                args=call_args,
                cwd=cwd,
                returncode=0,
                stdout="",
                stderr="",
            )

        reports_dir = cwd / "reports"
        reports_dir.mkdir(parents=True, exist_ok=True)
        (reports_dir / "report.json").write_text(
            json.dumps({"reportName": "DMC-1033 Rate Limit Logging"}),
            encoding="utf-8",
        )
        return ProcessExecutionResult(
            args=call_args,
            cwd=cwd,
            returncode=0,
            stdout=self.job_output,
            stderr="",
        )


class FakeHarness:
    def __init__(self) -> None:
        self.base_url = "http://127.0.0.1:9999"
        self.requests = (
            RecordedRequest(
                method="GET",
                path="/repos/rate-limit-owner/rate-limit-repo/commits?page=1",
                timestamp=100.0,
            ),
            RecordedRequest(
                method="GET",
                path="/repos/rate-limit-owner/rate-limit-repo/commits?page=1",
                timestamp=102.1,
            ),
        )

    def __enter__(self) -> "FakeHarness":
        return self

    def __exit__(self, exc_type, exc, tb) -> None:  # type: ignore[override]
        del exc_type, exc, tb


class FakeHarnessFactory:
    def __init__(self, harness: FakeHarness) -> None:
        self.harness = harness
        self.calls: list[tuple[str, str, str]] = []

    def create(
        self,
        *,
        workspace: str,
        repository: str,
        branch: str,
    ) -> FakeHarness:
        self.calls.append((workspace, repository, branch))
        return self.harness


def _build_service(job_output: str) -> tuple[ReportGeneratorRateLimitLogService, FakeProcessRunner, FakeHarnessFactory]:
    runner = FakeProcessRunner(job_output=job_output)
    harness_factory = FakeHarnessFactory(harness=FakeHarness())
    service = ReportGeneratorRateLimitLogService(
        repository_root=REPOSITORY_ROOT,
        runner=runner,
        harness_factory=harness_factory,
    )
    return service, runner, harness_factory


def test_service_uses_injected_harness_factory_for_live_audit() -> None:
    service, runner, harness_factory = _build_service(
        "\n".join(
            [
                "[WARN] AbstractRestClient - Rate limit hit for URL: http://127.0.0.1:9999/repos/rate-limit-owner/rate-limit-repo/commits?page=1 (Attempt 1/5). Error: rate limit",
                "[INFO] AbstractRestClient - Waiting 2000 ms before retry (2.000s)",
                "[INFO] AbstractRestClient - Starting retry request now that the wait window has elapsed",
                "[INFO] ReportGenerator - Metric 'GitHub Commits': collected 1 items",
            ]
        )
    )

    audit = service.audit()

    assert harness_factory.calls == [("rate-limit-owner", "rate-limit-repo", "main")]
    assert runner.calls[1][2] is not None
    assert runner.calls[1][2]["SOURCE_GITHUB_BASE_PATH"] == "http://127.0.0.1:9999"
    assert audit.retry_confirmation_line == (
        "[INFO] AbstractRestClient - Starting retry request now that the wait window has elapsed"
    )
    assert audit.request_gap_seconds is not None
    assert abs(audit.request_gap_seconds - 2.1) < 1e-9


def test_service_accepts_retry_confirmation_with_clear_operator_wording() -> None:
    service, _, _ = _build_service(
        "\n".join(
            [
                "[WARN] AbstractRestClient - Rate limit hit for URL: http://127.0.0.1:9999/repos/rate-limit-owner/rate-limit-repo/commits?page=1 (Attempt 1/5). Error: rate limit",
                "[INFO] AbstractRestClient - Waiting 2000 ms before retry (2.000s)",
                "[INFO] AbstractRestClient - Resuming request again after the rate-limit window",
                "[INFO] ReportGenerator - Metric 'GitHub Commits': collected 1 items",
            ]
        )
    )

    audit = service.audit()

    assert audit.retry_confirmation_line == (
        "[INFO] AbstractRestClient - Resuming request again after the rate-limit window"
    )
