from __future__ import annotations

import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.factories.report_generator_rate_limit_audit_service_factory import (  # noqa: E402
    create_report_generator_rate_limit_audit_service,
)
from testing.core.interfaces.report_generator_rate_limit_audit_service import (  # noqa: E402
    ReportGeneratorRateLimitAuditService,
)
from testing.core.models.process_execution_result import ProcessExecutionResult  # noqa: E402
from testing.core.utils.ticket_config_loader import load_ticket_config  # noqa: E402


TEST_DIRECTORY = Path(__file__).resolve().parent
CONFIG = load_ticket_config(TEST_DIRECTORY / "config.yaml")


def create_service(*, repository_root: Path = REPOSITORY_ROOT) -> ReportGeneratorRateLimitAuditService:
    return create_report_generator_rate_limit_audit_service(
        repository_root=repository_root,
        expected_fallback_delay_ms=int(str(CONFIG["expected_fallback_delay_ms"])),
        gradle_task=str(CONFIG["gradle_task"]),
        target_test=str(CONFIG["target_test"]),
        report_generator_path=str(CONFIG["report_generator_path"]),
        expected_rate_limit_status=int(str(CONFIG["expected_rate_limit_status"])),
        expected_invalid_reset_header_name=str(CONFIG["expected_invalid_reset_header_name"]),
        expected_invalid_reset_header_value=str(CONFIG["expected_invalid_reset_header_value"]),
        expected_invalid_reset_warning=str(CONFIG["expected_invalid_reset_warning"]),
        expected_fallback_warning=str(CONFIG["expected_fallback_warning"]),
        expected_retry_log=str(CONFIG["expected_retry_log"]),
    )


def test_dmc_1034_invalid_rate_limit_reset_header_falls_back_without_crashing() -> None:
    service = create_service()
    audit = service.run_audit()

    assert not service.format_failures(audit), (
        "The ReportGenerator ticket scenario did not match the deployed implementation.\n\n"
        "Expected a mocked GitHub HTTP 403 rate-limit response with a malformed "
        "X-RateLimit-Reset header to log the parse failure, fall back to the safe retry "
        "wait strategy, and complete the interrupted metric instead of crashing with "
        "NumberFormatException.\n\n"
        f"{service.format_failures(audit)}"
    )


class FakeRunner:
    def __init__(self, *results: ProcessExecutionResult) -> None:
        self.results = list(results)
        self.calls: list[tuple[tuple[str, ...], Path, bool]] = []

    def run(
        self,
        args: list[str],
        cwd: Path,
        env: dict[str, str | None] | None = None,
        trace_network: bool = False,
    ) -> ProcessExecutionResult:
        del env
        self.calls.append((tuple(args), cwd, trace_network))
        return self.results.pop(0)


def test_dmc_1034_service_runs_targeted_gradle_regression_and_403_invalid_header_probe(
    tmp_path: Path,
) -> None:
    report_generator_path = tmp_path / str(CONFIG["report_generator_path"])
    report_generator_path.parent.mkdir(parents=True, exist_ok=True)

    report_generator_path.write_text(
        "\n".join(
            [
                "logger.warn(\"Could not parse X-RateLimit-Reset header 'invalid_timestamp', falling back to another retry strategy.\");",
                "logger.warn(\"Rate limit metadata unavailable or invalid. Falling back to 60000 ms before retry.\");",
            ]
        ),
        encoding="utf-8",
    )

    fake_runner = FakeRunner(
        ProcessExecutionResult(
            args=(
                str(tmp_path / "gradlew"),
                "--no-daemon",
                str(CONFIG["gradle_task"]),
                "--tests",
                str(CONFIG["target_test"]),
            ),
            cwd=tmp_path,
            returncode=0,
            stdout="BUILD SUCCESSFUL",
            stderr="",
        ),
        ProcessExecutionResult(
            args=("bash", "-lc", "probe"),
            cwd=tmp_path,
            returncode=0,
            stdout="\n".join(
                [
                    "Could not parse X-RateLimit-Reset header 'invalid_timestamp', falling back to another retry strategy.",
                    "Rate limit metadata unavailable or invalid. Falling back to 60000 ms before retry.",
                    "Rate limit interrupted metric 'CommitsMetricSource' for source 'commits'. Waiting 60000 ms before retry attempt 1/5.",
                    "observedDelays=[60000]",
                    "rateLimitStatus=403",
                    "invalidResetHeader=invalid_timestamp",
                ]
            ),
            stderr="",
        )
    )
    service = create_report_generator_rate_limit_audit_service(
        repository_root=tmp_path,
        runner=fake_runner,
        expected_fallback_delay_ms=int(str(CONFIG["expected_fallback_delay_ms"])),
        gradle_task=str(CONFIG["gradle_task"]),
        target_test=str(CONFIG["target_test"]),
        report_generator_path=str(CONFIG["report_generator_path"]),
        expected_rate_limit_status=int(str(CONFIG["expected_rate_limit_status"])),
        expected_invalid_reset_header_name=str(CONFIG["expected_invalid_reset_header_name"]),
        expected_invalid_reset_header_value=str(CONFIG["expected_invalid_reset_header_value"]),
        expected_invalid_reset_warning=str(CONFIG["expected_invalid_reset_warning"]),
        expected_fallback_warning=str(CONFIG["expected_fallback_warning"]),
        expected_retry_log=str(CONFIG["expected_retry_log"]),
    )

    audit = service.run_audit()

    assert fake_runner.calls[:1] == [
        (
            (
                str(tmp_path / "gradlew"),
                "--no-daemon",
                str(CONFIG["gradle_task"]),
                "--tests",
                str(CONFIG["target_test"]),
            ),
            tmp_path,
            False,
        )
    ]
    assert fake_runner.calls[1][0][:2] == ("bash", "-lc")
    probe_script = fake_runner.calls[1][0][2]
    assert 'when(rateLimitResponse.header("X-RateLimit-Reset"))' in probe_script
    assert '.thenReturn("invalid_timestamp");' in probe_script
    assert "EXPECTED_RATE_LIMIT_STATUS = 403" in probe_script
    assert (
        'new RestClient.RateLimitException("rate limit", "rate limit", '
        "rateLimitResponse, EXPECTED_RATE_LIMIT_STATUS)"
    ) in probe_script
    assert audit.execution is not None
    assert audit.invalid_reset_probe_execution is not None
    assert audit.invalid_reset_warning_present is True
    assert audit.fallback_warning_present is True
    assert service.format_failures(audit) == ""
