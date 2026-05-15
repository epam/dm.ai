from __future__ import annotations

import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.factories.report_generator_rate_limit_service_factory import (  # noqa: E402
    create_report_generator_rate_limit_service,
)
from testing.components.services.report_generator_rate_limit_service import (  # noqa: E402
    ReportGeneratorRateLimitService,
)
from testing.core.utils.ticket_config_loader import load_ticket_config  # noqa: E402


TEST_DIRECTORY = Path(__file__).resolve().parent
CONFIG = load_ticket_config(TEST_DIRECTORY / "config.yaml")


def build_service(
    repository_root: Path = REPOSITORY_ROOT,
) -> ReportGeneratorRateLimitService:
    return create_report_generator_rate_limit_service(
        repository_root=repository_root,
        test_class=str(CONFIG["test_class"]),
        test_methods=tuple(str(value) for value in CONFIG["test_methods"]),
    )


def test_dmc_1030_report_generator_honors_github_reset_header_and_retries_pull_request_collection() -> None:
    service = build_service()

    audit = service.audit()

    assert audit.execution.returncode == 0, service.format_failures(audit)
    assert audit.junit_report_path.exists(), service.format_failures(audit)
    assert not audit.failures, service.format_failures(audit)
    assert "BUILD SUCCESSFUL" in audit.execution.combined_output, service.format_failures(audit)

    observed_names = {check.name for check in audit.observed_checks if check.status == "passed"}
    expected_names = {str(value) for value in CONFIG["test_methods"]}
    assert expected_names.issubset(observed_names), service.format_failures(audit)

    retry_metric_index = audit.system_out.find("Metric 'PullRequestsMetricSource': collected 1 items")
    retry_warning_index = audit.system_out.find(
        "Rate limit interrupted metric 'PullRequestsApprovalsMetricSource' for source 'pullRequests'. "
        "Waiting 0 ms before retry attempt 1/5."
    )
    recovered_metric_index = audit.system_out.find(
        "Metric 'PullRequestsApprovalsMetricSource': collected 1 items"
    )

    assert retry_metric_index != -1, service.format_failures(audit)
    assert retry_warning_index != -1, service.format_failures(audit)
    assert recovered_metric_index != -1, service.format_failures(audit)
    assert retry_metric_index < retry_warning_index < recovered_metric_index, (
        service.format_failures(audit)
    )
