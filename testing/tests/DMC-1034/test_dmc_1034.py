from __future__ import annotations

import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.factories.report_generator_rate_limit_audit_service_factory import (  # noqa: E402
    create_report_generator_rate_limit_audit_service,
)
from testing.components.services.report_generator_rate_limit_audit_service import (  # noqa: E402
    ReportGeneratorRateLimitAuditService,
)
from testing.core.models.process_execution_result import ProcessExecutionResult  # noqa: E402
from testing.core.utils.ticket_config_loader import load_ticket_config  # noqa: E402


TEST_DIRECTORY = Path(__file__).resolve().parent
CONFIG = load_ticket_config(TEST_DIRECTORY / "config.yaml")


def create_service(*, repository_root: Path = REPOSITORY_ROOT) -> ReportGeneratorRateLimitAuditService:
    return create_report_generator_rate_limit_audit_service(
        repository_root=repository_root,
        gradle_task=str(CONFIG["gradle_task"]),
        target_test=str(CONFIG["target_test"]),
        report_generator_path=str(CONFIG["report_generator_path"]),
        report_generator_test_path=str(CONFIG["report_generator_test_path"]),
        expected_test_marker=str(CONFIG["expected_test_marker"]),
        expected_invalid_reset_test_line=str(CONFIG["expected_invalid_reset_test_line"]),
        expected_invalid_reset_warning=str(CONFIG["expected_invalid_reset_warning"]),
        expected_fallback_warning=str(CONFIG["expected_fallback_warning"]),
    )


def test_dmc_1034_invalid_rate_limit_reset_header_falls_back_without_crashing() -> None:
    service = create_service()
    audit = service.run_audit()

    assert not service.format_failures(audit), (
        "The ReportGenerator ticket scenario did not match the deployed implementation.\n\n"
        "Expected a malformed X-RateLimit-Reset header to be covered by the regression, "
        "logged as an invalid header, and retried with the safe fallback wait strategy "
        "instead of crashing with NumberFormatException.\n\n"
        f"{service.format_failures(audit)}"
    )


class FakeRunner:
    def __init__(self, result: ProcessExecutionResult) -> None:
        self.result = result
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
        return self.result


def test_dmc_1034_service_uses_configured_gradle_target_and_detects_expected_markers(
    tmp_path: Path,
) -> None:
    report_generator_path = tmp_path / str(CONFIG["report_generator_path"])
    report_generator_test_path = tmp_path / str(CONFIG["report_generator_test_path"])
    report_generator_path.parent.mkdir(parents=True, exist_ok=True)
    report_generator_test_path.parent.mkdir(parents=True, exist_ok=True)

    report_generator_path.write_text(
        "\n".join(
            [
                "logger.warn(\"Could not parse X-RateLimit-Reset header '{}', falling back to another retry strategy.\");",
                "logger.warn(\"Rate limit metadata unavailable or invalid. Falling back to {} ms before retry.\");",
            ]
        ),
        encoding="utf-8",
    )
    report_generator_test_path.write_text(
        "\n".join(
            [
                str(CONFIG["expected_test_marker"]),
                str(CONFIG["expected_invalid_reset_test_line"]),
            ]
        ),
        encoding="utf-8",
    )

    fake_runner = FakeRunner(
        ProcessExecutionResult(
            args=(),
            cwd=tmp_path,
            returncode=0,
            stdout="BUILD SUCCESSFUL",
            stderr="",
        )
    )
    service = ReportGeneratorRateLimitAuditService(
        repository_root=tmp_path,
        runner=fake_runner,
        gradle_task=str(CONFIG["gradle_task"]),
        target_test=str(CONFIG["target_test"]),
        report_generator_path=str(CONFIG["report_generator_path"]),
        report_generator_test_path=str(CONFIG["report_generator_test_path"]),
        expected_test_marker=str(CONFIG["expected_test_marker"]),
        expected_invalid_reset_test_line=str(CONFIG["expected_invalid_reset_test_line"]),
        expected_invalid_reset_warning=str(CONFIG["expected_invalid_reset_warning"]),
        expected_fallback_warning=str(CONFIG["expected_fallback_warning"]),
    )

    audit = service.run_audit()

    assert fake_runner.calls == [
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
    assert audit.target_test_present is True
    assert audit.invalid_reset_test_case_present is True
    assert audit.invalid_reset_warning_present is True
    assert audit.fallback_warning_present is True
