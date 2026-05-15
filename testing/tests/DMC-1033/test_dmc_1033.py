from __future__ import annotations

import json
import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.factories.report_generator_rate_limit_log_service_factory import (  # noqa: E402
    create_report_generator_rate_limit_log_service,
)


def test_dmc_1033_rate_limit_logs_show_wait_time_and_retry_visibility() -> None:
    service = create_report_generator_rate_limit_log_service(REPOSITORY_ROOT)
    audit = service.audit()

    failures: list[str] = []

    if audit.bootstrap_result.returncode != 0:
        failures.append(
            "buildInstallLocal.sh failed while preparing the live DMTools runtime.\n"
            f"{audit.bootstrap_result.combined_output}"
        )

    if audit.job_result.returncode != 0:
        failures.append(
            "The ReportGenerator live run did not complete successfully after the induced rate limit.\n"
            f"{audit.job_result.combined_output}"
        )

    if audit.rate_limit_warning_line is None:
        failures.append(
            "The application logs did not include a warning that the GitHub rate limit was hit.\n"
            f"Combined output:\n{audit.job_result.combined_output}"
        )

    if audit.wait_duration_ms is None:
        failures.append(
            "The application logs did not include the wait duration in milliseconds.\n"
            f"Combined output:\n{audit.job_result.combined_output}"
        )

    if audit.request_gap_seconds is None or audit.request_gap_seconds < 1.5:
        failures.append(
            "The retried GitHub commits request was not observably delayed long enough after the "
            "429 response. The live run should wait before retrying, not retry immediately.\n"
            f"Observed gap: {audit.request_gap_seconds!r} seconds\n"
            f"Recorded requests: {[request.path for request in audit.recorded_requests]}"
        )

    if audit.retry_confirmation_line is None:
        failures.append(
            "The application logs did not include an explicit confirmation that the retry was "
            "initiated after the wait.\n"
            f"Combined output:\n{audit.job_result.combined_output}"
        )

    if audit.metric_collection_line is None:
        failures.append(
            "The report did not log successful metric collection after the induced rate limit.\n"
            f"Combined output:\n{audit.job_result.combined_output}"
        )

    if audit.report_json_path is None:
        failures.append(
            "The report JSON output was not generated after the retry.\n"
            f"Combined output:\n{audit.job_result.combined_output}"
        )
    else:
        report_payload = json.loads(audit.report_json_text)
        assert report_payload["reportName"] == "DMC-1033 Rate Limit Logging"

    assert not failures, "\n\n".join(failures)
