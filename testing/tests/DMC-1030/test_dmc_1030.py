from __future__ import annotations

import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.services.report_generator_rate_limit_service import (  # noqa: E402
    ReportGeneratorRateLimitService,
)
from testing.core.utils.ticket_config_loader import load_ticket_config  # noqa: E402
from testing.frameworks.api.rest.subprocess_process_runner import (  # noqa: E402
    SubprocessProcessRunner,
)


TEST_DIRECTORY = Path(__file__).resolve().parent
CONFIG = load_ticket_config(TEST_DIRECTORY / "config.yaml")


def build_service(
    repository_root: Path = REPOSITORY_ROOT,
) -> ReportGeneratorRateLimitService:
    return ReportGeneratorRateLimitService(
        repository_root=repository_root,
        runner=SubprocessProcessRunner(),
        test_class=str(CONFIG["test_class"]),
        test_methods=tuple(str(value) for value in CONFIG["test_methods"]),
    )


def test_dmc_1030_report_generator_honors_github_reset_header_and_retries_pull_request_collection() -> None:
    service = build_service()

    audit = service.audit()

    assert audit.execution.returncode == 0, service.format_failures(audit)
    assert audit.junit_report_path.exists(), service.format_failures(audit)
    assert not audit.failures, service.format_failures(audit)

    observed_names = {check.name for check in audit.observed_checks if check.status == "passed"}
    expected_names = {str(value) for value in CONFIG["test_methods"]}
    assert expected_names.issubset(observed_names), service.format_failures(audit)

    observations = service.human_observations(audit)
    assert any("same report run still completed" in observation for observation in observations)
    assert any("honored `X-RateLimit-Reset`" in observation for observation in observations)
